package com.quizmaster.web;

import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.Result;
import com.quizmaster.model.User;
import com.quizmaster.service.AttemptService;
import com.quizmaster.service.AttemptSession;
import com.quizmaster.service.QuizService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Taking a test: one question at a time with a countdown, then the score and a
 * per-question answer review.
 *
 * <p>The countdown is enforced on the server against the attempt's start time,
 * so reloading the page (or editing the clock in the page) cannot buy extra
 * time. When the test's expiry action is auto-submit, an attempt that is already
 * past its deadline is graded on the next request; with allow-overtime, the
 * overtime used is recorded against the attempt.
 */
@Controller
public class AttemptController {

    /** One row of the answer review. */
    public record ReviewRow(Question question, String studentAnswer, String studentText,
                            String correctAnswer, String correctText, boolean correct) {
    }

    private final QuizService quizService;
    private final AttemptService attemptService;
    private final CurrentUserAdvice currentUserAdvice;

    public AttemptController(QuizService quizService, AttemptService attemptService,
                             CurrentUserAdvice currentUserAdvice) {
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.currentUserAdvice = currentUserAdvice;
    }

    @GetMapping("/student/tests/{quizId}/take")
    public String take(@PathVariable Long quizId,
                       @RequestParam(required = false) Integer q,
                       @RequestParam(defaultValue = "false") boolean restart,
                       HttpSession session, Model model, RedirectAttributes redirect) {
        User student = currentUserAdvice.currentUser(session);
        Quiz quiz = quizService.findVisibleTo(student, quizId);
        if (quiz == null) {
            return error(model, "This test is not available to you. It may have been removed, or it is for another class.");
        }

        List<Question> questions = quizService.questionsOf(quizId);
        if (questions.isEmpty()) {
            return error(model, "This test does not contain any questions yet.");
        }

        AttemptSession attempt = attempt(session);
        boolean belongsHere = attempt != null && quizId.equals(attempt.getQuizId());
        if (restart || !belongsHere) {
            attempt = attemptService.startOrResume(null, quizId);
            session.setAttribute(SessionKeys.ATTEMPT, attempt);
        }

        if (attempt.isGraded()) {
            return "redirect:/student/tests/" + quizId + "/result";
        }
        if (!quiz.allowsOvertime() && attempt.isExpired(quiz.getTotalTimeSeconds())) {
            // The countdown ran out while the page was closed.
            return finalize(session, redirect, student, quiz, questions, attempt, true);
        }

        int index = clamp(q == null ? firstUnanswered(questions, attempt) : q, questions.size());
        addAttemptAttributes(model, quiz, questions, attempt, index);
        return "student/test-taking";
    }

    @PostMapping("/student/tests/{quizId}/answer")
    public String answer(@PathVariable Long quizId,
                         @RequestParam int index,
                         @RequestParam(required = false) String choice,
                         @RequestParam(defaultValue = "next") String action,
                         @RequestParam(required = false) Integer q,
                         HttpSession session, Model model, RedirectAttributes redirect) {
        User student = currentUserAdvice.currentUser(session);
        Quiz quiz = quizService.findVisibleTo(student, quizId);
        if (quiz == null) {
            return error(model, "This test is not available to you.");
        }

        List<Question> questions = quizService.questionsOf(quizId);
        AttemptSession attempt = attempt(session);
        if (questions.isEmpty() || attempt == null || !quizId.equals(attempt.getQuizId())) {
            return "redirect:/student/tests/" + quizId + "/take";
        }
        if (attempt.isGraded()) {
            return "redirect:/student/tests/" + quizId + "/result";
        }
        if (!quiz.allowsOvertime() && attempt.isExpired(quiz.getTotalTimeSeconds())) {
            return finalize(session, redirect, student, quiz, questions, attempt, true);
        }

        int safeIndex = clamp(index, questions.size());
        if (choice != null && !choice.isBlank()) {
            attempt.setAnswer(questions.get(safeIndex).getId(), choice);
        }

        if ("submit".equals(action)) {
            return finalize(session, redirect, student, quiz, questions, attempt, false);
        }

        int next = switch (action) {
            case "prev" -> safeIndex - 1;
            case "goto" -> q == null ? safeIndex : q;
            default -> safeIndex + 1;
        };
        return "redirect:/student/tests/" + quizId + "/take?q=" + clamp(next, questions.size());
    }

    @GetMapping("/student/tests/{quizId}/result")
    public String result(@PathVariable Long quizId, HttpSession session, Model model,
                         RedirectAttributes redirect) {
        User student = currentUserAdvice.currentUser(session);
        Quiz quiz = quizService.findVisibleTo(student, quizId);
        if (quiz == null) {
            return error(model, "This test is not available to you.");
        }

        AttemptSession attempt = attempt(session);
        if (attempt != null && quizId.equals(attempt.getQuizId()) && attempt.isGraded()) {
            List<Question> questions = quizService.questionsOf(quizId);
            model.addAttribute("quiz", quiz);
            model.addAttribute("questions", questions);
            model.addAttribute("review", reviewRows(questions, attempt));
            model.addAttribute("score", attempt.getScore());
            model.addAttribute("total", attempt.getTotal());
            model.addAttribute("overtimeSeconds", attempt.getOvertimeSeconds());
            model.addAttribute("autoSubmitted", attempt.isAutoSubmitted());
            model.addAttribute("percentage", percentage(attempt.getScore(), attempt.getTotal()));
            model.addAttribute("detailedReview", true);
            return "student/result";
        }

        // Coming back later: only the recorded score is available, because the
        // per-question answers are not stored in the database (the desktop app
        // did not store them either).
        Result saved = attemptService.latestResultFor(quizId, student.getId());
        if (saved == null) {
            redirect.addFlashAttribute("error", "You have not taken this test yet.");
            return "redirect:/student/tests";
        }
        model.addAttribute("quiz", saved.getQuiz());
        model.addAttribute("result", saved);
        model.addAttribute("detailedReview", false);
        return "student/result";
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /**
     * Grades the attempt, records it and sends the student to the review screen.
     * A score that could not be written is reported on that screen rather than
     * being presented as a saved attempt.
     */
    private String finalize(HttpSession session, RedirectAttributes redirect, User student, Quiz quiz,
                            List<Question> questions, AttemptSession attempt, boolean autoSubmitted) {
        AttemptService.GradedAttempt graded =
                attemptService.submit(student, quiz, attempt, questions, autoSubmitted);
        session.setAttribute(SessionKeys.ATTEMPT, attempt);
        if (graded.saveWarning() != null) {
            redirect.addFlashAttribute("error", graded.saveWarning());
        }
        return "redirect:/student/tests/" + quiz.getId() + "/result";
    }

    private void addAttemptAttributes(Model model, Quiz quiz, List<Question> questions,
                                      AttemptSession attempt, int index) {
        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", questions);
        model.addAttribute("question", questions.get(index));
        model.addAttribute("index", index);
        model.addAttribute("total", questions.size());
        model.addAttribute("answeredCount", attempt.answerCount());
        model.addAttribute("selected", attempt.answerFor(questions.get(index).getId()));
        model.addAttribute("answers", attempt.getAnswers());
        model.addAttribute("remainingSeconds", attempt.remainingSeconds(quiz.getTotalTimeSeconds()));
        model.addAttribute("deadlineMillis", attempt.getStartedAtMillis() + quiz.getTotalTimeSeconds() * 1000L);
        model.addAttribute("direction", quiz.getTextDirection());
    }

    private List<ReviewRow> reviewRows(List<Question> questions, AttemptSession attempt) {
        List<ReviewRow> rows = new ArrayList<>(questions.size());
        for (Question question : questions) {
            String studentAnswer = attempt.answerFor(question.getId());
            String correct = question.getCorrectOption();
            rows.add(new ReviewRow(question, studentAnswer,
                    question.optionText(studentAnswer),
                    correct,
                    question.optionText(correct),
                    question.isCorrect(studentAnswer)));
        }
        return rows;
    }

    private int firstUnanswered(List<Question> questions, AttemptSession attempt) {
        for (int index = 0; index < questions.size(); index++) {
            String answer = attempt.answerFor(questions.get(index).getId());
            if (answer == null || answer.isBlank()) {
                return index;
            }
        }
        return 0;
    }

    private int clamp(int index, int size) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, size - 1);
    }

    private String percentage(int score, int total) {
        double value = total > 0 ? ((double) score / total) * 100.0 : 0.0;
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private AttemptSession attempt(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.ATTEMPT);
        return value instanceof AttemptSession attempt ? attempt : null;
    }

    private String error(Model model, String message) {
        model.addAttribute("errorMessage", message);
        return "error";
    }
}
