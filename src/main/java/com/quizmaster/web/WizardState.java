package com.quizmaster.web;

import com.quizmaster.importing.ParsedQuestion;
import com.quizmaster.model.Question;
import com.quizmaster.service.QuizService;
import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The two-step "Create Test" wizard, held in the session between requests: the
 * step-1 details, the questions typed by hand so far, and the question
 * currently open for editing.
 *
 * <p>Imported questions are merged in when "Confirm &amp; Save All" is pressed,
 * so a test can combine typed and imported questions - as the desktop version
 * allowed.
 */
public class WizardState implements Serializable {

    private QuizService.TestDetails details;
    private final List<ParsedQuestion> manualQuestions = new ArrayList<>();
    /** Index of the manual question currently open in the form, or null for a new one. */
    private Integer editingIndex;
    /** Where the last import left off, so the step-2 screen can mention it. */
    private String importedFrom;

    public static WizardState get(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.WIZARD);
        return value instanceof WizardState state ? state : null;
    }

    public static WizardState start(HttpSession session, QuizService.TestDetails details) {
        WizardState state = new WizardState();
        state.details = details;
        session.setAttribute(SessionKeys.WIZARD, state);
        session.removeAttribute(SessionKeys.IMPORT_DRAFT);
        return state;
    }

    public static void clear(HttpSession session) {
        session.removeAttribute(SessionKeys.WIZARD);
        session.removeAttribute(SessionKeys.IMPORT_DRAFT);
    }

    public QuizService.TestDetails getDetails() {
        return details;
    }

    public void setDetails(QuizService.TestDetails details) {
        this.details = details;
    }

    public List<ParsedQuestion> getManualQuestions() {
        return manualQuestions;
    }

    public int getManualCount() {
        return manualQuestions.size();
    }

    public Integer getEditingIndex() {
        return editingIndex;
    }

    public void setEditingIndex(Integer editingIndex) {
        this.editingIndex = editingIndex;
    }

    public String getImportedFrom() {
        return importedFrom;
    }

    public void setImportedFrom(String importedFrom) {
        this.importedFrom = importedFrom;
    }

    /** The question open in the form, or null when adding a new one. */
    public ParsedQuestion editing() {
        if (editingIndex == null || editingIndex < 0 || editingIndex >= manualQuestions.size()) {
            return null;
        }
        return manualQuestions.get(editingIndex);
    }

    /** Adds or replaces the question the form submitted. */
    public void saveManual(ParsedQuestion question) {
        if (editingIndex != null && editingIndex >= 0 && editingIndex < manualQuestions.size()) {
            manualQuestions.set(editingIndex, question);
        } else {
            manualQuestions.add(question);
        }
        editingIndex = null;
    }

    public void removeManual(int index) {
        if (index >= 0 && index < manualQuestions.size()) {
            manualQuestions.remove(index);
        }
        editingIndex = null;
    }

    /** Every question of the test: typed first, then imported, in the order they were added. */
    public List<Question> allQuestions(List<Question> importedQuestions) {
        List<Question> questions = new ArrayList<>();
        for (ParsedQuestion parsed : manualQuestions) {
            questions.add(parsed.toEntity(null));
        }
        if (importedQuestions != null) {
            questions.addAll(importedQuestions);
        }
        return questions;
    }
}
