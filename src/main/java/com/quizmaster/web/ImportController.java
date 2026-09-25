package com.quizmaster.web;

import com.quizmaster.importing.ImportResult;
import com.quizmaster.importing.ParsedQuestion;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.User;
import com.quizmaster.service.ImportService;
import com.quizmaster.service.QuizService;
import com.quizmaster.service.ServiceResult;
import com.quizmaster.validation.InputValidator;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

/**
 * "Upload Questions File": reads a PDF, Word .docx or .txt file, shows a
 * preview of every question found (and every block skipped, with its line
 * number), and saves nothing until "Confirm &amp; Save All".
 */
@Controller
@RequestMapping("/teacher/tests/new/import")
public class ImportController {

    private final ImportService importService;
    private final CurrentUserAdvice currentUserAdvice;

    public ImportController(ImportService importService, CurrentUserAdvice currentUserAdvice) {
        this.importService = importService;
        this.currentUserAdvice = currentUserAdvice;
    }

    /** The bundled sample file, so a teacher can see the format before writing one. */
    @GetMapping("/sample")
    public ResponseEntity<ClassPathResource> downloadSample() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sample-questions.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(new ClassPathResource("samples/sample-questions.txt"));
    }

    @GetMapping
    public String uploadForm(HttpSession session, Model model, RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        if (WizardState.get(session) == null) {
            redirect.addFlashAttribute("error", "Please fill in the test details first.");
            return "redirect:/teacher/tests/new";
        }
        addWizardAttributes(session, model);
        return "teacher/import-upload";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file, HttpSession session,
                         RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        WizardState state = WizardState.get(session);
        if (state == null) {
            redirect.addFlashAttribute("error", "Please fill in the test details first.");
            return "redirect:/teacher/tests/new";
        }

        ServiceResult<ImportResult> read = importService.read(file);
        if (read.isFailure()) {
            redirect.addFlashAttribute("error", read.message());
            return "redirect:/teacher/tests/new/import";
        }

        ImportResult result = read.value();
        session.setAttribute(SessionKeys.IMPORT_DRAFT, importService.newDraft(result));
        state.setImportedFrom(result.getFileName());

        redirect.addFlashAttribute("success", result.getSummary());
        if (result.getQuestions().isEmpty()) {
            redirect.addFlashAttribute("error", result.isEmpty()
                    ? "Nothing could be read from " + result.getFileName() + "."
                    : "No question in " + result.getFileName() + " could be imported. See the list below.");
        }
        return "redirect:/teacher/tests/new/import/preview";
    }

    @GetMapping("/preview")
    public String preview(HttpSession session, Model model, RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        if (WizardState.get(session) == null) {
            return "redirect:/teacher/tests/new";
        }
        ImportService.ImportDraft draft = importDraft(session);
        if (draft == null) {
            redirect.addFlashAttribute("error", "Please upload a questions file first.");
            return "redirect:/teacher/tests/new/import";
        }
        addWizardAttributes(session, model);
        model.addAttribute("draft", draft);
        model.addAttribute("questions", draft.getQuestions());
        model.addAttribute("failures", draft.getFailures());
        return "teacher/import-preview";
    }

    @PostMapping("/remove")
    public String remove(@RequestParam int index, HttpSession session, RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        ImportService.ImportDraft draft = importDraft(session);
        if (draft != null) {
            draft.removeAt(index);
            redirect.addFlashAttribute("success", "Question removed from the import.");
        }
        return "redirect:/teacher/tests/new/import/preview";
    }

    @GetMapping("/edit")
    public String editForm(@RequestParam int index, HttpSession session, Model model, RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        ImportService.ImportDraft draft = importDraft(session);
        ParsedQuestion question = draft == null ? null : draft.get(index);
        if (question == null) {
            redirect.addFlashAttribute("error", "That question is no longer in the import.");
            return "redirect:/teacher/tests/new/import/preview";
        }
        addWizardAttributes(session, model);
        model.addAttribute("question", question);
        model.addAttribute("index", index);
        model.addAttribute("direction", questionTextDirection(session));
        return "teacher/import-edit";
    }

    /**
     * Saves the edited question back into the working list and returns to the
     * preview of that same list - editing or removing an imported question must
     * stick, and must not be undone by rebuilding the list from the parse result.
     */
    @PostMapping("/edit")
    public String editSubmit(@RequestParam int index,
                             @RequestParam String questionText,
                             @RequestParam String optionA,
                             @RequestParam String optionB,
                             @RequestParam String optionC,
                             @RequestParam String optionD,
                             @RequestParam String correctOption,
                             @RequestParam(required = false) String topic,
                             @RequestParam(required = false) String difficulty,
                             HttpSession session,
                             RedirectAttributes redirect) {
        currentUserAdvice.currentUser(session);
        ImportService.ImportDraft draft = importDraft(session);
        ParsedQuestion existing = draft == null ? null : draft.get(index);
        if (existing == null) {
            redirect.addFlashAttribute("error", "That question is no longer in the import.");
            return "redirect:/teacher/tests/new/import/preview";
        }

        ParsedQuestion updated = new ParsedQuestion(existing.lineNumber(),
                questionText.trim(), optionA.trim(), optionB.trim(), optionC.trim(), optionD.trim(),
                correctOption.trim().toUpperCase(Locale.ROOT),
                InputValidator.normaliseTopic(topic),
                InputValidator.normaliseDifficulty(difficulty));

        String error = InputValidator.validateQuestion(updated.toEntity(null));
        if (error != null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/teacher/tests/new/import/edit?index=" + index;
        }

        draft.replace(index, updated);
        redirect.addFlashAttribute("success", "Question " + (index + 1) + " updated.");
        return "redirect:/teacher/tests/new/import/preview";
    }

    /** "Confirm & Save All": the single-transaction save of the test and its questions. */
    @PostMapping("/save")
    public String saveAll(HttpSession session, RedirectAttributes redirect) {
        User teacher = currentUserAdvice.currentUser(session);
        WizardState state = WizardState.get(session);
        ImportService.ImportDraft draft = importDraft(session);
        if (state == null || draft == null) {
            redirect.addFlashAttribute("error", "The import expired. Please upload the file again.");
            return "redirect:/teacher/tests/new/import";
        }
        if (draft.isEmpty() && state.getManualCount() == 0) {
            redirect.addFlashAttribute("error", "There are no questions left to save.");
            return "redirect:/teacher/tests/new/import/preview";
        }

        ServiceResult<Quiz> result = importService.createTest(teacher, state.getDetails(),
                state.getManualQuestions(), draft);
        if (result.isFailure()) {
            redirect.addFlashAttribute("error", result.message());
            return "redirect:/teacher/tests/new/import/preview";
        }

        int saved = state.getManualCount() + draft.getQuestions().size();
        WizardState.clear(session);
        redirect.addFlashAttribute("success", "Test \"" + result.value().getTitle() + "\" saved with "
                + saved + (saved == 1 ? " question." : " questions."));
        return "redirect:/teacher/dashboard";
    }

    private void addWizardAttributes(HttpSession session, Model model) {
        WizardState state = WizardState.get(session);
        if (state != null) {
            model.addAttribute("details", state.getDetails());
            model.addAttribute("manualCount", state.getManualCount());
        }
    }

    private String questionTextDirection(HttpSession session) {
        WizardState state = WizardState.get(session);
        return state != null && Quiz.LANGUAGE_URDU.equalsIgnoreCase(state.getDetails().language()) ? "rtl" : "ltr";
    }

    private ImportService.ImportDraft importDraft(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.IMPORT_DRAFT);
        return value instanceof ImportService.ImportDraft draft ? draft : null;
    }
}
