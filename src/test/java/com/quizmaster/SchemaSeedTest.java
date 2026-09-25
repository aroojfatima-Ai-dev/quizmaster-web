package com.quizmaster;

import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.Result;
import com.quizmaster.model.User;
import com.quizmaster.repo.ClassRoomRepository;
import com.quizmaster.repo.EnrollmentRepository;
import com.quizmaster.repo.QuestionRepository;
import com.quizmaster.repo.QuizRepository;
import com.quizmaster.repo.ResultRepository;
import com.quizmaster.repo.UserRepository;
import com.quizmaster.seed.DemoDataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desktop application created its schema on first run and shipped a small
 * demo data set; the web build does the same. This test pins both: the tables
 * Hibernate creates on an empty database, and the exact demo world a fresh
 * deploy wakes up with - including the promise that seeding runs once, no
 * matter how often the container restarts.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:quizmaster-seed;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "quizmaster.demo-seed=true"
})
class SchemaSeedTest {

    private static final List<String> TABLES = List.of(
            "USERS", "CLASSES", "ENROLLMENTS", "TESTS", "QUESTIONS", "RESULTS");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private DemoDataSeeder seeder;

    @Test
    @DisplayName("the schema is created from the entities on a brand new database")
    void schemaIsCreated() {
        for (String table : TABLES) {
            List<String> schemas = jdbcTemplate.queryForList(
                    "select table_schema from information_schema.tables where table_name = ?",
                    String.class, table);
            assertEquals(1, schemas.stream().filter("PUBLIC"::equals).count(),
                    table + " should have been created automatically in PUBLIC, found in " + schemas);
        }
    }

    @Test
    @DisplayName("a fresh deploy wakes up with the demo teacher, class and tests")
    void demoWorldIsSeeded() {
        User teacher = userRepository.findLoginCandidates("prof_smith").get(0);
        assertTrue(teacher.isTeacher());
        User student = userRepository.findLoginCandidates("ali_student").get(0);
        assertTrue(student.isStudent());

        ClassRoom physics = classRoomRepository.findByClassCodeIgnoreCase("PHY101")
                .orElseThrow(() -> new AssertionError("the demo class should exist"));
        assertEquals("Physics 101", physics.getClassName());
        assertEquals(teacher.getId(), physics.getTeacher().getId());
        assertTrue(enrollmentRepository.existsByClassRoomIdAndStudentId(physics.getId(), student.getId()));

        List<Quiz> tests = quizRepository.findByCreatedByIdOrderByIdDesc(teacher.getId());
        assertEquals(2, tests.size(), "the demo teacher ships two tests");

        Quiz publicTest = tests.stream().filter(Quiz::isPublicTest).findFirst().orElseThrow();
        assertEquals("General Knowledge Quiz", publicTest.getTitle());
        assertEquals(5, questionRepository.countByQuizId(publicTest.getId()));
        assertEquals(600, publicTest.getTotalTimeSeconds());

        Quiz classTest = tests.stream().filter(quiz -> !quiz.isPublicTest()).findFirst().orElseThrow();
        assertTrue(classTest.getTextDirection().contains("rtl"), "the class-only demo test is Urdu");
        assertEquals(2, questionRepository.countByQuizId(classTest.getId()));

        Result result = resultRepository.findByStudentIdOrderByIdDesc(student.getId()).get(0);
        assertEquals(3, result.getScore());
        assertEquals(5, result.getTotal());
        assertEquals(publicTest.getId(), result.getQuiz().getId());
    }

    @Test
    @DisplayName("seeding again on a populated database changes nothing")
    void seedingIsIdempotent() throws Exception {
        long users = userRepository.count();
        long classes = classRoomRepository.count();
        long tests = quizRepository.count();
        long questions = questionRepository.count();
        long results = resultRepository.count();
        assertTrue(users > 0, "the first pass must have seeded something");

        seeder.run();

        assertEquals(users, userRepository.count(), "no extra accounts");
        assertEquals(classes, classRoomRepository.count(), "no extra classes");
        assertEquals(tests, quizRepository.count(), "no extra tests");
        assertEquals(questions, questionRepository.count(), "no extra questions");
        assertEquals(results, resultRepository.count(), "no extra submissions");
        assertNotNull(userRepository.findLoginCandidates("prof_smith").get(0));
    }
}
