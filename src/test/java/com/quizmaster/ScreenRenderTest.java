package com.quizmaster;

import com.quizmaster.model.Quiz;
import com.quizmaster.model.Result;
import com.quizmaster.model.User;
import com.quizmaster.repo.QuizRepository;
import com.quizmaster.repo.ResultRepository;
import com.quizmaster.repo.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every screen of the application against the demo data a fresh deploy
 * seeds, so a broken template or a controller that cannot build its model fails
 * the build instead of failing in front of a teacher or a student.
 *
 * <p>This is the automated twin of {@code scripts/smoke.py}: same list of pages,
 * but run in the test suite on every change.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:quizmaster-screens;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "quizmaster.demo-seed=true"
})
class ScreenRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Test
    @DisplayName("the public screens and the health probe render")
    void publicScreens() throws Exception {
        render(null, "/", "QuizMaster");
        render(null, "/login", "Sign in");
        render(null, "/register", "Create");

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")));
    }

    @Test
    @DisplayName("every teacher screen renders with the seeded teaching data")
    void teacherScreens() throws Exception {
        MockHttpSession session = signIn("prof_smith", "teacher123");

        render(session, "/teacher/dashboard", "Welcome, prof_smith");
        render(session, "/teacher/classes", "PHY101");
        render(session, "/teacher/tests", "General Knowledge Quiz");
        render(session, "/teacher/submissions", "ali_student");

        Result submission = resultRepository.findAll().get(0);
        render(session, "/teacher/submissions/" + submission.getId(), "Score Percentage");

        // Step 2 and the import screen only exist once the wizard has state.
        render(session, "/teacher/tests/new", "Step 1 of 2");
        mockMvc.perform(post("/teacher/tests/new").session(session)
                        .param("title", "Screen Render Test")
                        .param("language", "English")
                        .param("durationMinutes", "10")
                        .param("expiryAction", "AUTO_SUBMIT")
                        .param("visibility", "public"))
                .andExpect(status().is3xxRedirection());
        render(session, "/teacher/tests/new/questions", "Questions in this test");
        render(session, "/teacher/tests/new/import", "Upload Questions File");
    }

    @Test
    @DisplayName("every student screen renders with the seeded class data")
    void studentScreens() throws Exception {
        MockHttpSession session = signIn("ali_student", "student123");
        User student = userRepository.findLoginCandidates("ali_student").get(0);

        render(session, "/student/dashboard", "Welcome, ali_student");
        render(session, "/student/join", "Join a Class");
        render(session, "/student/tests", "General Knowledge Quiz");
        render(session, "/student/results", "My Test Results");

        Result result = resultRepository.findByStudentIdOrderByIdDesc(student.getId()).get(0);
        render(session, "/student/results/" + result.getId(), "General Knowledge Quiz");

        Quiz quiz = quizRepository.findAvailableForStudent(student.getId()).get(0);
        render(session, "/student/tests/" + quiz.getId() + "/take", "Submit");
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private MockHttpSession signIn(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("usernameOrEmail", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        HttpSession session = login.getRequest().getSession(false);
        assertNotNull(session, "signing in must create a session");
        return (MockHttpSession) session;
    }

    /** Opens a page and checks it rendered - no error page, no stack trace. */
    private void render(MockHttpSession session, String path, String expected) throws Exception {
        var request = get(path);
        if (session != null) {
            request.session(session);
        }
        String html = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(expected),
                path + " should contain " + expected + " but rendered " + html.length() + " characters");
        assertFalse(html.contains("Whitelabel Error Page"), path + " rendered an error page");
        assertFalse(html.contains("Exception processing template"), path + " failed inside Thymeleaf");
        assertFalse(html.contains("Internal Server Error"), path + " failed inside a controller");
    }
}
