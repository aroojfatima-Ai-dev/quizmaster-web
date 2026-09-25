package com.quizmaster.web;

import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Enrollment;
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

/** The student's hub, class joining, available tests and result history. */
@Controller
@RequestMapping("/student")
public class StudentController {

    private final ClassRoomService classRoomService;
    private final QuizService quizService;
    private final AttemptService attemptService;
    private final CurrentUserAdvice currentUserAdvice;

    public StudentController(ClassRoomService classRoomService, QuizService quizService,
                             AttemptService attemptService, CurrentUserAdvice currentUserAdvice) {
        this.classRoomService = classRoomService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.currentUserAdvice = currentUserAdvice;
    }

    private User requireStudent(HttpSession session) {
        return currentUserAdvice.currentUser(session);
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User student = requireStudent(session);
        List<Quiz> tests = quizService.availableTo(student);
        List<Result> results = attemptService.resultsOf(student);
        List<Enrollment> enrollments = classRoomService.enrollmentsOf(student);

        model.addAttribute("testCount", tests.size());
        model.addAttribute("resultCount", results.size());
        model.addAttribute("classCount", enrollments.size());
        model.addAttribute("recentResults", results.size() > 5 ? results.subList(0, 5) : results);
        model.addAttribute("latestTest", tests.isEmpty() ? null : tests.get(0));
        return "student/dashboard";
    }

    @GetMapping("/join")
    public String joinForm(HttpSession session, Model model) {
        User student = requireStudent(session);
        model.addAttribute("joinedClasses", classRoomService.joinedClassesOf(student));
        return "student/join";
    }

    @PostMapping("/join")
    public String join(@RequestParam String classCode, HttpSession session, RedirectAttributes redirect) {
        User student = requireStudent(session);
        ServiceResult<ClassRoom> result = classRoomService.joinByCode(student, classCode);
        if (result.isFailure()) {
            redirect.addFlashAttribute("error", result.message());
        } else {
            redirect.addFlashAttribute("success", result.message());
        }
        return "redirect:/student/join";
    }

    @GetMapping("/tests")
    public String availableTests(HttpSession session, Model model) {
        User student = requireStudent(session);
        List<Quiz> tests = quizService.availableTo(student);

        Map<Long, Long> questionCounts = new LinkedHashMap<>();
        Map<Long, Result> lastAttempts = new LinkedHashMap<>();
        for (Quiz quiz : tests) {
            questionCounts.put(quiz.getId(), quizService.questionCount(quiz));
            Result latest = attemptService.latestResultFor(quiz.getId(), student.getId());
            if (latest != null) {
                lastAttempts.put(quiz.getId(), latest);
            }
        }
        model.addAttribute("tests", tests);
        model.addAttribute("questionCounts", questionCounts);
        model.addAttribute("lastAttempts", lastAttempts);
        return "student/tests";
    }

    @GetMapping("/results")
    public String results(HttpSession session, Model model) {
        User student = requireStudent(session);
        List<Result> results = attemptService.resultsOf(student);
        model.addAttribute("results", results);
        model.addAttribute("averagePercentage", averageOf(results));
        model.addAttribute("bestPercentage", results.stream().mapToDouble(Result::getPercentage).max().orElse(0.0));
        return "student/results";
    }

    @GetMapping("/results/{resultId}")
    public String resultDetail(@PathVariable Long resultId, HttpSession session, Model model) {
        User student = requireStudent(session);
        for (Result result : attemptService.resultsOf(student)) {
            if (result.getId().equals(resultId)) {
                model.addAttribute("result", result);
                model.addAttribute("difficultyBreakdown", difficultyBreakdown(result));
                return "student/result-detail";
            }
        }
        model.addAttribute("errorMessage", "That result does not exist, or belongs to another student.");
        return "error";
    }

    /** The per-topic spread of the questions on that test. */
    private Map<String, Integer> difficultyBreakdown(Result result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var question : quizService.questionsOf(result.getQuiz().getId())) {
            counts.merge(question.getDifficultyLabel(), 1, Integer::sum);
        }
        return counts;
    }

    private String averageOf(List<Result> results) {
        if (results.isEmpty()) {
            return "0.0";
        }
        double average = results.stream().mapToDouble(Result::getPercentage).average().orElse(0.0);
        return String.format(java.util.Locale.ROOT, "%.1f", average);
    }
}
