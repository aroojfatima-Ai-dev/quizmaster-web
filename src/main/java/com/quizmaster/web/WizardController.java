package com.quizmaster.web;

import com.quizmaster.importing.ParsedQuestion;
import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.User;
import com.quizmaster.service.ClassRoomService;
import com.quizmaster.service.ImportService;
import com.quizmaster.service.QuizService;
import com.quizmaster.service.ServiceResult;
import com.quizmaster.validation.InputValidator;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * The two-step "Create Test" wizard.
 *
 * <p>Step 1 collects the test details; step 2 collects the questions, either
 * typed one at a time or imported from a file (manual entry and import can be
 * combined in the same test).
 */
@Controller
@RequestMapping("/teacher/tests/new")
public class WizardController {

    private final QuizService quizService;
    private final ClassRoomService classRoomService;
    private final ImportService importService;
    private final CurrentUserAdvice currentUserAdvice;

    public WizardController(QuizService quizService, ClassRoomService classRoomService,
                            ImportService importService, CurrentUserAdvice currentUserAdvice) {
        this.quizService = quizService;
        this.classRoomService = classRoomService;
        this.importService = importService;
        this.currentUserAdvice = currentUserAdvice;
    }

    // ------------------------------------------------------------------
    //  Step 1 - test details
    // ------------------------------------------------------------------

    @GetMapping
    public String step1(HttpSession session, Model model) {
        User teacher = currentUserAdvice.currentUser(session);
        WizardState state = WizardState.get(session);

        model.addAttribute("classes", classRoomService.classesOf(teacher));
        model.addAttribute("details", state == null ? null : state.getDetails());
        model.addAttribute("step", 1);
        return "teacher/step1";
    }

    @PostMapping
    public String step1Submit(@RequestParam String title,
                              @RequestParam(defaultValue = Quiz.LANGUAGE_ENGLISH) String language,
                              @RequestParam String durationMinutes,
                              @RequestParam(defaultValue = Quiz.EXPIRY_AUTO_SUBMIT) String expiryAction,
                              @RequestParam(defaultValue = "public") String visibility,
                              @RequestParam(required = false) Long classId,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirect) {
        User teacher = currentUserAdvice.currentUser(session);
        Integer totalTimeSeconds = InputValidator.parseDurationMinutesToSeconds(durationMinutes);

        String error = InputValidator.firstError(
                InputValidator.validateTestTitle(title),
                InputValidator.validateLanguage(language),
                InputValidator.validateExpiryAction(expiryAction),
                totalTimeSeconds == null ? InputValidator.durationError() : null);

        boolean classOnly = "class".equalsIgnoreCase(visibility);
        if (error == null && classOnly && classId == null) {
            error = "Choose which class this test is for, or make the test public.";
        }

        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("classes", classRoomService.classesOf(teacher));
            model.addAttribute("step", 1);
            model.addAttribute("titleValue", title);
            model.addAttribute("durationValue", durationMinutes);
            return "teacher/step1";
        }

        QuizService.TestDetails details = new QuizService.TestDetails(
                title.trim(),
                InputValidator.normaliseLanguage(language),
                totalTimeSeconds,
                InputValidator.normaliseExpiryAction(expiryAction),
                !classOnly,
                classOnly ? classId : null);

        WizardState.start(session, details);
        redirect.addFlashAttribute("success", "Test details saved. Now add the questions.");
        return "redirect:/teacher/tests/new/questions";
    }

    // ------------------------------------------------------------------
    //  Step 2 - questions
    // ------------------------------------------------------------------

    @GetMapping("/questions")
    public String step2(HttpSession session, Model model, RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        WizardState state = WizardState.get(session);
        if (state == null) {
            redirect.addFlashAttribute("error", "Please fill in the test details first.");
            return "redirect:/teacher/tests/new";
        }

        model.addAttribute("details", state.getDetails());
        model.addAttribute("manualQuestions", state.getManualQuestions());
        model.addAttribute("manualCount", state.getManualCount());
        model.addAttribute("editing", state.editing());
        model.addAttribute("editingIndex", state.getEditingIndex());
        model.addAttribute("importedFrom", state.getImportedFrom());
        model.addAttribute("step", 2);
        model.addAttribute("direction", state.getDetails().language().equalsIgnoreCase(Quiz.LANGUAGE_URDU) ? "rtl" : "ltr");
        return "teacher/step2";
    }

    /**
     * Saves the question on screen and either adds it to the list or finishes
     * the test.
     *
     * <p>Every draft already in the list is re-validated before the save, so a
     * half-typed question can never be persisted (the database would happily
     * store an empty option and show students four empty radio buttons).
     */
    @PostMapping("/questions/submit")
    public String step2Submit(@RequestParam(defaultValue = "add") String action,
                              @RequestParam(required = false) String questionText,
                              @RequestParam(required = false) String optionA,
                              @RequestParam(required = false) String optionB,
                              @RequestParam(required = false) String optionC,
                              @RequestParam(required = false) String optionD,
                              @RequestParam(required = false) String correctOption,
                              @RequestParam(required = false) String topic,
                              @RequestParam(required = false) String difficulty,
                              HttpSession session,
                              RedirectAttributes redirect) {
        User teacher = currentUserAdvice.currentUser(session);
        WizardState state = WizardState.get(session);
        if (state == null) {
            return "redirect:/teacher/tests/new";
        }

        if ("back".equals(action)) {
            return "redirect:/teacher/tests/new";
        }

        ImportService.ImportDraft importDraft = importDraft(session);
        ParsedQuestion typed = draftQuestion(state, questionText, optionA, optionB, optionC, optionD,
                correctOption, topic, difficulty);

        if (typed != null) {
            String error = InputValidator.validateQuestion(typed.toEntity(null));
            if (error != null) {
                redirect.addFlashAttribute("error", error);
                return "redirect:/teacher/tests/new/questions";
            }
            state.saveManual(typed);
        } else if ("finish".equals(action)) {
            // Nothing on screen: finish with what the list already holds.
            state.setEditingIndex(null);
        }

        if ("finish".equals(action)) {
            return finish(session, teacher, state, importDraft, redirect);
        }

        int count = state.getManualCount();
        redirect.addFlashAttribute("success", typed == null
                ? "Question removed from the form."
                : "Question " + count + " added. Add another, or finish and save the test.");
        return "redirect:/teacher/tests/new/questions";
    }

    @PostMapping("/questions/remove")
    public String removeQuestion(@RequestParam int index, HttpSession session, RedirectAttributes redirect) {
        WizardState state = WizardState.get(session);
        if (state != null) {
            state.removeManual(index);
            redirect.addFlashAttribute("success", "Question removed.");
        }
        return "redirect:/teacher/tests/new/questions";
    }

    @PostMapping("/questions/edit")
    public String editQuestion(@RequestParam int index, HttpSession session) {
        WizardState state = WizardState.get(session);
        if (state != null) {
            state.setEditingIndex(index);
        }
        return "redirect:/teacher/tests/new/questions";
    }

    @PostMapping("/questions/cancel-edit")
    public String cancelEdit(HttpSession session) {
        WizardState state = WizardState.get(session);
        if (state != null) {
            state.setEditingIndex(null);
        }
        return "redirect:/teacher/tests/new/questions";
    }

    /** Creates the test from the typed list plus anything imported. */
    private String finish(HttpSession session, User teacher, WizardState state,
                          ImportService.ImportDraft importDraft, RedirectAttributes redirect) {
        int questionCount = state.getManualCount() + (importDraft == null ? 0 : importDraft.getQuestions().size());
        if (questionCount == 0) {
            redirect.addFlashAttribute("error", "A test needs at least one question.");
            return "redirect:/teacher/tests/new/questions";
        }

        ServiceResult<Quiz> result = importService.createTest(teacher, state.getDetails(),
                state.getManualQuestions(), importDraft);
        if (result.isFailure()) {
            redirect.addFlashAttribute("error", result.message());
            return importDraft != null && !importDraft.isEmpty()
                    ? "redirect:/teacher/tests/new/import/preview"
                    : "redirect:/teacher/tests/new/questions";
        }

        WizardState.clear(session);
        redirect.addFlashAttribute("success", "Test \"" + result.value().getTitle() + "\" saved with "
                + questionCount + (questionCount == 1 ? " question." : " questions."));
        return "redirect:/teacher/dashboard";
    }

    /** Builds the question from the form, or {@code null} when nothing was typed. */
    private ParsedQuestion draftQuestion(WizardState state, String questionText, String optionA, String optionB,
                                         String optionC, String optionD, String correctOption,
                                         String topic, String difficulty) {
        boolean empty = isBlank(questionText) && isBlank(optionA) && isBlank(optionB) && isBlank(optionC)
                && isBlank(optionD) && isBlank(correctOption);
        if (empty) {
            return null;
        }
        int lineNumber = state.getEditingIndex() != null ? state.getEditingIndex() + 1 : 0;
        return new ParsedQuestion(lineNumber,
                trim(questionText), trim(optionA), trim(optionB), trim(optionC), trim(optionD),
                trim(correctOption).toUpperCase(java.util.Locale.ROOT),
                InputValidator.normaliseTopic(topic),
                InputValidator.normaliseDifficulty(difficulty));
    }

    private ImportService.ImportDraft importDraft(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.IMPORT_DRAFT);
        return value instanceof ImportService.ImportDraft draft ? draft : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
