package com.quizmaster.seed;

import com.quizmaster.importing.ImportResult;
import com.quizmaster.importing.ParsedQuestion;
import com.quizmaster.importing.QuestionFileParser;
import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Enrollment;
import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.Result;
import com.quizmaster.model.User;
import com.quizmaster.security.PasswordUtil;
import com.quizmaster.service.QuizService;
import com.quizmaster.service.ServiceResult;
import com.quizmaster.repo.ClassRoomRepository;
import com.quizmaster.repo.EnrollmentRepository;
import com.quizmaster.repo.QuestionRepository;
import com.quizmaster.repo.QuizRepository;
import com.quizmaster.repo.ResultRepository;
import com.quizmaster.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with the demo accounts the desktop application
 * documented ({@code prof_smith/teacher123} and {@code ali_student/student123}),
 * a class with the code {@code PHY101}, one public test, one class-only test in
 * Urdu, and one submitted attempt so the submissions screen has something to
 * show.
 *
 * <p>Seeding happens only when the {@code users} table is empty, and can be
 * switched off with {@code QUIZMASTER_DEMO_SEED=false}.
 */
@Component
@ConditionalOnProperty(name = "quizmaster.demo-seed", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String TEACHER_USERNAME = "prof_smith";
    private static final String TEACHER_PASSWORD = "teacher123";
    private static final String STUDENT_USERNAME = "ali_student";
    private static final String STUDENT_PASSWORD = "student123";
    private static final String CLASS_CODE = "PHY101";

    /** A second test in Urdu, to show the right-to-left question support. */
    private static final String URDU_QUESTIONS = """
            Q: پاکستان کا دارالحکومت کون سا شہر ہے؟
            A) کراچی
            B) اسلام آباد
            C) لاہور
            D) پشاور
            Answer: B
            Topic: عمومی معلومات
            Difficulty: EASY

            Q: اردو زبان کا رسم الخط کون سا ہے؟
            A) رومن
            B) دیوناگری
            C) نستعلیق
            D) سیریلک
            Answer: C
            Topic: زبان
            Difficulty: MEDIUM
            """;

    private final UserRepository userRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ResultRepository resultRepository;
    private final QuizService quizService;
    private final QuestionFileParser parser = new QuestionFileParser();

    public DemoDataSeeder(UserRepository userRepository, ClassRoomRepository classRoomRepository,
                          EnrollmentRepository enrollmentRepository, QuizRepository quizRepository,
                          QuestionRepository questionRepository, ResultRepository resultRepository,
                          QuizService quizService) {
        this.userRepository = userRepository;
        this.classRoomRepository = classRoomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.resultRepository = resultRepository;
        this.quizService = quizService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User teacher = userRepository.save(new User(TEACHER_USERNAME, "smith@example.com",
                PasswordUtil.hash(TEACHER_PASSWORD), User.ROLE_TEACHER));
        User student = userRepository.save(new User(STUDENT_USERNAME, "ali@example.com",
                PasswordUtil.hash(STUDENT_PASSWORD), User.ROLE_STUDENT));

        ClassRoom classRoom = classRoomRepository.save(new ClassRoom(teacher, "Physics 101", CLASS_CODE));
        enrollmentRepository.save(new Enrollment(classRoom, student));

        Quiz general = createTest(teacher, "General Knowledge Quiz", Quiz.LANGUAGE_ENGLISH,
                10 * 60, Quiz.EXPIRY_AUTO_SUBMIT, null, readSampleQuestions());

        Quiz classTest = createTest(teacher, "Physics 101 - Class Test", Quiz.LANGUAGE_URDU,
                5 * 60, Quiz.EXPIRY_ALLOW_OVERTIME, classRoom,
                parser.parse(URDU_QUESTIONS, "urdu-sample.txt", "Text file").getQuestions());

        // One finished attempt, so "View Student Submissions" is not empty.
        if (general != null) {
            resultRepository.save(new Result(general, student, 3, 5, 0));
        }

        log.info("""

                ==========================================================
                 QuizMaster Web - demo data created
                   Teacher : {} / {}
                   Student : {} / {}
                   Class   : Physics 101, code {}
                   Tests   : {} (5 questions, English, public)
                             {} (2 questions, Urdu, class only)
                 Set QUIZMASTER_DEMO_SEED=false to skip this on the next run.
                ==========================================================""",
                TEACHER_USERNAME, TEACHER_PASSWORD, STUDENT_USERNAME, STUDENT_PASSWORD, CLASS_CODE,
                general == null ? "-" : general.getTitle(),
                classTest == null ? "-" : classTest.getTitle());
    }

    private Quiz createTest(User teacher, String title, String language, int seconds, String expiryAction,
                            ClassRoom classRoom, List<ParsedQuestion> parsed) {
        List<Question> questions = new ArrayList<>();
        for (ParsedQuestion question : parsed) {
            questions.add(question.toEntity(null));
        }
        if (questions.isEmpty()) {
            log.warn("Skipping demo test \"{}\": no questions could be read.", title);
            return null;
        }

        ServiceResult<Quiz> result = quizService.createTest(teacher,
                new QuizService.TestDetails(title, language, seconds, expiryAction,
                        classRoom == null, classRoom == null ? null : classRoom.getId()),
                questions);
        if (result.isFailure()) {
            log.warn("Skipping demo test \"{}\": {}", title, result.message());
            return null;
        }
        return result.value();
    }

    /** The demo test is built from the same sample file the parser tests use. */
    private List<ParsedQuestion> readSampleQuestions() {
        try (InputStream stream = new ClassPathResource("samples/sample-questions.txt").getInputStream()) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            ImportResult result = parser.parse(text, "sample-questions.txt", "Text file");
            if (result.getSkippedCount() > 0) {
                log.warn("The bundled sample file had {} skipped block(s): {}",
                        result.getSkippedCount(), result.getFailures());
            }
            return result.getQuestions();
        } catch (IOException ex) {
            log.warn("Could not read samples/sample-questions.txt: {}", ex.getMessage());
            return List.of();
        }
    }
}
