package com.quizmaster.web;

import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.Result;
import com.quizmaster.model.User;
import com.quizmaster.service.AttemptService;
import com.quizmaster.service.ClassRoomService;
import com.quizmaster.service.QuizService;
import com.quizmaster.service.ServiceResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The teacher's hub, classes, test list and submission review. */
@Controller
@RequestMapping("/teacher")
public class TeacherController {

    private final ClassRoomService classRoomService;
    private final QuizService quizService;
    private final AttemptService attemptService;
    private final CurrentUserAdvice currentUserAdvice;

    public TeacherController(ClassRoomService classRoomService, QuizService quizService,
                             AttemptService attemptService, CurrentUserAdvice currentUserAdvice) {
        this.classRoomService = classRoomService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.currentUserAdvice = currentUserAdvice;
    }

    private User requireTeacher(HttpSession session) {
        return currentUserAdvice.currentUser(session);
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User teacher = requireTeacher(session);
        List<ClassRoom> classes = classRoomService.classesOf(teacher);
        List<Quiz> tests = quizService.testsOf(teacher);
        List<Result> submissions = attemptService.submissionsFor(teacher);

        model.addAttribute("classCount", classes.size());
        model.addAttribute("testCount", tests.size());
        model.addAttribute("submissionCount", submissions.size());
        model.addAttribute("recentSubmissions", submissions.size() > 5 ? submissions.subList(0, 5) : submissions);
        model.addAttribute("latestClassCode", classes.isEmpty() ? null : classes.get(0).getClassCode());
        return "teacher/dashboard";
    }

    @GetMapping("/classes")
    public String classes(HttpSession session, Model model) {
        User teacher = requireTeacher(session);
        List<ClassRoom> classes = classRoomService.classesOf(teacher);

        Map<Long, Long> studentCounts = new LinkedHashMap<>();
        for (ClassRoom classRoom : classes) {
            studentCounts.put(classRoom.getId(), classRoomService.studentCount(classRoom));
        }
        model.addAttribute("classes", classes);
        model.addAttribute("studentCounts", studentCounts);
        return "teacher/classes";
    }

    @PostMapping("/classes")
    public String createClass(@RequestParam String className, HttpSession session, RedirectAttributes redirect) {
        User teacher = requireTeacher(session);
        ServiceResult<ClassRoom> result = classRoomService.createClass(teacher, className);
        if (result.isFailure()) {
            redirect.addFlashAttribute("error", result.message());
        } else {
            redirect.addFlashAttribute("success", "Class created. Share the code "
                    + result.value().getClassCode() + " with your students.");
        }
        return "redirect:/teacher/classes";
    }

    @GetMapping("/tests")
    public String tests(HttpSession session, Model model) {
        User teacher = requireTeacher(session);
        List<Quiz> tests = quizService.testsOf(teacher);

        Map<Long, Long> questionCounts = new LinkedHashMap<>();
        Map<Long, Long> submissionCounts = new LinkedHashMap<>();
        for (Quiz quiz : tests) {
            questionCounts.put(quiz.getId(), quizService.questionCount(quiz));
            submissionCounts.put(quiz.getId(), quizService.submissionCount(quiz));
        }
        model.addAttribute("tests", tests);
        model.addAttribute("questionCounts", questionCounts);
        model.addAttribute("submissionCounts", submissionCounts);
        return "teacher/tests";
    }

    /** "View Student Submissions": every attempt, optionally filtered to one test. */
    @GetMapping("/submissions")
    public String submissions(@RequestParam(required = false) Long testId, HttpSession session, Model model) {
        User teacher = requireTeacher(session);
        List<Quiz> tests = quizService.testsOf(teacher);
        List<Result> results = attemptService.submissionsFor(teacher, testId);

        model.addAttribute("tests", tests);
        model.addAttribute("results", results);
        model.addAttribute("filterTestId", testId);
        model.addAttribute("filterTest", tests.stream()
                .filter(quiz -> quiz.getId().equals(testId))
                .findFirst()
                .orElse(null));
        return "teacher/submissions";
    }

    /** One submission in detail: the paper, the answer key and the score. */
    @GetMapping("/submissions/{resultId}")
    public String submissionDetail(@PathVariable Long resultId, HttpSession session, Model model) {
        User teacher = requireTeacher(session);

        Result result = null;
        for (Result candidate : attemptService.submissionsFor(teacher)) {
            if (candidate.getId().equals(resultId)) {
                result = candidate;
                break;
            }
        }
        if (result == null) {
            return error(model, "That submission does not exist, or belongs to another teacher.");
        }

        List<Question> questions = quizService.questionsOf(result.getQuiz().getId());
        model.addAttribute("result", result);
        model.addAttribute("questions", questions);
        return "teacher/submission-detail";
    }

    private String error(Model model, String message) {
        model.addAttribute("errorMessage", message);
        return "error";
    }
}
