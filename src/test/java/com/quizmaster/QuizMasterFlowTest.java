package com.quizmaster;

import com.quizmaster.model.Quiz;
import com.quizmaster.repo.QuizRepository;
import com.quizmaster.repo.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks the whole product on a real (in-memory) database: register, sign in,
 * create a class, build a test by hand, import a questions file, join the
 * class, take the test and read the result - the same end-to-end path the
 * JavaFX application was verified with.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:quizmaster-flow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "quizmaster.demo-seed=false"
})
class QuizMasterFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String SAMPLE_FILE = """
            Q: What is the capital of France?
            A) Berlin
            B) Paris
            C) Madrid
            D) Rome
            Answer: B
            Topic: Geography
            Difficulty: EASY

            Q: Which keyword declares a constant in Java?
            A) static
            B) final
            C) const
            D) var
            Answer: B

            Q: Which data structure is first in, first out?
            A) Stack
            B) Queue
            C) Tree
            D) Map
            Answer: Queue
            """;

    @Test
    @DisplayName("teacher creates a class and a test, student joins and takes it")
    void fullFlow() throws Exception {
        MockHttpSession teacherSession = registerAndLogin("prof_smith", "smith@example.com", "teacher123", "teacher");
        MockHttpSession studentSession = registerAndLogin("ali_student", "ali@example.com", "student123", "student");

        // --- teacher: create a class -------------------------------------
        mockMvc.perform(post("/teacher/classes").session(teacherSession).param("className", "Physics 101"))
                .andExpect(redirectedUrl("/teacher/classes"));
        mockMvc.perform(get("/teacher/classes").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Physics 101")));

        // --- teacher: wizard step 1 --------------------------------------
        mockMvc.perform(post("/teacher/tests/new").session(teacherSession)
                        .param("title", "General Knowledge Quiz")
                        .param("language", "English")
                        .param("durationMinutes", "10")
                        .param("expiryAction", "AUTO_SUBMIT")
                        .param("visibility", "public"))
                .andExpect(redirectedUrl("/teacher/tests/new/questions"));

        // --- teacher: wizard step 2, one typed question -------------------
        mockMvc.perform(post("/teacher/tests/new/questions/submit").session(teacherSession)
                        .param("action", "add")
                        .param("questionText", "What is 2 + 2?")
                        .param("optionA", "4")
                        .param("optionB", "5")
                        .param("optionC", "6")
                        .param("optionD", "7")
                        .param("correctOption", "A")
                        .param("topic", "Maths")
                        .param("difficulty", "EASY"))
                .andExpect(redirectedUrl("/teacher/tests/new/questions"));

        mockMvc.perform(get("/teacher/tests/new/questions").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What is 2 + 2?")));

        // --- teacher: import three more from a file ----------------------
        MockMultipartFile file = new MockMultipartFile("file", "sample-questions.txt", "text/plain",
                SAMPLE_FILE.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/teacher/tests/new/import").file(file).session(teacherSession))
                .andExpect(redirectedUrl("/teacher/tests/new/import/preview"));

        mockMvc.perform(get("/teacher/tests/new/import/preview").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review Imported Questions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paris")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm &amp; Save All")));

        // Editing an imported question must stick (it used to revert).
        mockMvc.perform(post("/teacher/tests/new/import/edit").session(teacherSession)
                        .param("index", "0")
                        .param("questionText", "What is the capital city of France?")
                        .param("optionA", "Berlin")
                        .param("optionB", "Paris")
                        .param("optionC", "Madrid")
                        .param("optionD", "Rome")
                        .param("correctOption", "B")
                        .param("topic", "Geography")
                        .param("difficulty", "EASY"))
                .andExpect(redirectedUrl("/teacher/tests/new/import/preview"));
        mockMvc.perform(get("/teacher/tests/new/import/preview").session(teacherSession))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("capital city of France")));

        // Removing an imported question must stick too.
        mockMvc.perform(post("/teacher/tests/new/import/remove").session(teacherSession).param("index", "1"))
                .andExpect(redirectedUrl("/teacher/tests/new/import/preview"));
        mockMvc.perform(get("/teacher/tests/new/import/preview").session(teacherSession))
                // the removed question is gone, and the counter still reports it as found
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("declares a constant in Java"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("3 question(s) found")));

        // --- teacher: save the whole test in one go ----------------------
        mockMvc.perform(post("/teacher/tests/new/import/save").session(teacherSession))
                .andExpect(redirectedUrl("/teacher/dashboard"));

        Quiz quiz = publishedQuiz();
        assertEquals("General Knowledge Quiz", quiz.getTitle());
        assertEquals(10 * 60, quiz.getTotalTimeSeconds());

        // --- student: join the class and see the test --------------------
        mockMvc.perform(get("/student/join").session(studentSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/student/tests").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("General Knowledge Quiz")));

        // --- student: take the test --------------------------------------
        mockMvc.perform(get("/student/tests/" + quiz.getId() + "/take?restart=true").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Time Remaining")));

        mockMvc.perform(post("/student/tests/" + quiz.getId() + "/answer").session(studentSession)
                        .param("index", "0")
                        .param("choice", "A")
                        .param("action", "next"))
                .andExpect(redirectedUrl("/student/tests/" + quiz.getId() + "/take?q=1"));

        mockMvc.perform(post("/student/tests/" + quiz.getId() + "/answer").session(studentSession)
                        .param("index", "1")
                        .param("choice", "B")
                        .param("action", "submit"))
                .andExpect(redirectedUrl("/student/tests/" + quiz.getId() + "/result"));

        // --- student: the result and its review --------------------------
        mockMvc.perform(get("/student/tests/" + quiz.getId() + "/result").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You scored 2 out of 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Detailed Answer Review")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Correct")));

        // The results history keeps the attempt.
        mockMvc.perform(get("/student/results").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("General Knowledge Quiz")));

        // --- teacher: the submission shows up ----------------------------
        mockMvc.perform(get("/teacher/submissions").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ali_student")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2/3")));
    }

    @Test
    @DisplayName("the health endpoint answers for the container probe")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("H2")));
    }

    @Test
    @DisplayName("teacher screens are closed to students and to visitors")
    void accessControl() throws Exception {
        mockMvc.perform(get("/teacher/dashboard"))
                .andExpect(status().is3xxRedirection());

        MockHttpSession studentSession = registerAndLogin("bob_student", "bob@example.com", "student123", "student");
        mockMvc.perform(get("/teacher/dashboard").session(studentSession))
                .andExpect(redirectedUrl("/student/dashboard"));

        MockHttpSession teacherSession = registerAndLogin("ms_teacher", "ms@example.com", "teacher123", "teacher");
        mockMvc.perform(get("/student/dashboard").session(teacherSession))
                .andExpect(redirectedUrl("/teacher/dashboard"));
    }

    @Test
    @DisplayName("a half-typed question is refused before anything is written")
    void incompleteQuestionIsRefused() throws Exception {
        MockHttpSession teacherSession = registerAndLogin("t_typo", "t_typo@example.com", "teacher123", "teacher");

        mockMvc.perform(post("/teacher/tests/new").session(teacherSession)
                        .param("title", "Broken Test")
                        .param("language", "English")
                        .param("durationMinutes", "5")
                        .param("expiryAction", "AUTO_SUBMIT")
                        .param("visibility", "public"))
                .andExpect(redirectedUrl("/teacher/tests/new/questions"));

        mockMvc.perform(post("/teacher/tests/new/questions/submit").session(teacherSession)
                        .param("action", "add")
                        .param("questionText", "Only two options typed")
                        .param("optionA", "one")
                        .param("optionB", "two")
                        .param("optionC", "")
                        .param("optionD", "")
                        .param("correctOption", "A"))
                .andExpect(redirectedUrl("/teacher/tests/new/questions"));

        mockMvc.perform(post("/teacher/tests/new/questions/submit").session(teacherSession)
                        .param("action", "finish"))
                .andExpect(redirectedUrl("/teacher/tests/new/questions"));

        Long teacherId = userRepository.findLoginCandidates("t_typo").get(0).getId();
        assertTrue(quizRepository.findByCreatedByIdOrderByIdDesc(teacherId).stream()
                .noneMatch(candidate -> "Broken Test".equals(candidate.getTitle())));
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private MockHttpSession registerAndLogin(String username, String email, String password, String role)
            throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", username)
                        .param("email", email)
                        .param("password", password)
                        .param("confirmPassword", password)
                        .param("role", role))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("QuizMaster Login")));

        MvcResult login = mockMvc.perform(post("/login")
                        .param("usernameOrEmail", username)
                        .param("password", password))
                .andExpect(redirectedUrl(role.equals("teacher") ? "/teacher/dashboard" : "/student/dashboard"))
                .andReturn();

        HttpSession session = login.getRequest().getSession(false);
        assertNotNull(session, "login must create a session");
        return (MockHttpSession) session;
    }

    private Quiz publishedQuiz() {
        List<Quiz> quizzes = quizRepository.findAll();
        assertEquals(1, quizzes.size(), "exactly one test should have been saved");
        return quizzes.get(0);
    }
}
