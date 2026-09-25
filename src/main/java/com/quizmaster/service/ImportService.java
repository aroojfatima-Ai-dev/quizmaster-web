package com.quizmaster.service;

import com.quizmaster.importing.ImportResult;
import com.quizmaster.importing.ParseFailure;
import com.quizmaster.importing.ParsedQuestion;
import com.quizmaster.importing.QuestionFileParser;
import com.quizmaster.importing.QuestionFileReader;
import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.User;
import com.quizmaster.validation.InputValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an uploaded question file and keeps the reviewable working list.
 *
 * <p>Nothing is saved until "Confirm &amp; Save All" on the preview screen: the
 * teacher can edit or remove each imported question first, and the edit screen
 * returns to the same working list (the desktop version rebuilt that list from
 * the parse result, so edits and removals silently reverted).
 */
@Service
public class ImportService {

    private final QuestionFileParser parser = new QuestionFileParser();
    private final QuizService quizService;

    public ImportService(QuizService quizService) {
        this.quizService = quizService;
    }

    /** The working list held in the HTTP session between the preview and the save. */
    public static class ImportDraft implements Serializable {

        private final String fileName;
        private final String fileType;
        private final List<ParsedQuestion> questions = new ArrayList<>();
        private final List<ParseFailure> failures = new ArrayList<>();
        private int removedCount;

        public ImportDraft(String fileName, String fileType, List<ParsedQuestion> questions,
                           List<ParseFailure> failures) {
            this.fileName = fileName;
            this.fileType = fileType;
            this.questions.addAll(questions);
            this.failures.addAll(failures);
        }

        public String getFileName() {
            return fileName;
        }

        public String getFileType() {
            return fileType;
        }

        public List<ParsedQuestion> getQuestions() {
            return questions;
        }

        public List<ParseFailure> getFailures() {
            return failures;
        }

        public int getRemovedCount() {
            return removedCount;
        }

        /** Every question block the file contained - imported, removed plus skipped. */
        public int getQuestionsFound() {
            return questions.size() + removedCount + failures.size();
        }

        public boolean isEmpty() {
            return questions.isEmpty();
        }

        public boolean isEverythingRemoved() {
            return questions.isEmpty() && removedCount > 0;
        }

        public void removeAt(int index) {
            if (index >= 0 && index < questions.size()) {
                questions.remove(index);
                removedCount++;
            }
        }

        public ParsedQuestion get(int index) {
            return index >= 0 && index < questions.size() ? questions.get(index) : null;
        }

        public void replace(int index, ParsedQuestion question) {
            if (index >= 0 && index < questions.size() && question != null) {
                questions.set(index, question);
            }
        }

        /** Draft questions, unattached to a test until the save assigns one. */
        public List<Question> toQuestions() {
            List<Question> drafts = new ArrayList<>(questions.size());
            for (ParsedQuestion parsed : questions) {
                drafts.add(parsed.toEntity(null));
            }
            return drafts;
        }
    }

    /** Reads and parses an uploaded file. */
    public ServiceResult<ImportResult> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ServiceResult.failure("Please choose a file to upload.");
        }
        String fileName = file.getOriginalFilename() == null ? "questions" : file.getOriginalFilename();
        try {
            byte[] bytes = file.getBytes();
            String text = QuestionFileReader.read(bytes, fileName);
            ImportResult result = parser.parse(text, fileName, QuestionFileReader.describeType(fileName));
            return ServiceResult.success(result);
        } catch (QuestionFileReader.UnreadableFileException ex) {
            return ServiceResult.failure(ex.getMessage());
        } catch (IOException ex) {
            return ServiceResult.failure("Could not read " + fileName + ": " + ex.getMessage());
        }
    }

    /** Parses text directly - used by the tests and by the "paste questions" helper. */
    public ImportResult parseText(String text, String fileName) {
        return parser.parse(text, fileName, "Text file");
    }

    public ImportDraft newDraft(ImportResult result) {
        return new ImportDraft(result.getFileName(), result.getFileType(),
                result.getQuestions(), result.getFailures());
    }

    /**
     * Saves the test from the typed questions plus the reviewed import.
     *
     * <p>Imported rows are validated here as well, so a bad row is reported with
     * its file line number, and a failure cannot leave a half-saved test behind
     * (the test and all of its questions are written on one transaction).
     */
    public ServiceResult<Quiz> createTest(User teacher, QuizService.TestDetails details,
                                          List<ParsedQuestion> manualQuestions, ImportDraft draft) {
        List<Question> questions = new ArrayList<>();
        if (manualQuestions != null) {
            for (ParsedQuestion parsed : manualQuestions) {
                questions.add(parsed.toEntity(null));
            }
        }

        if (draft != null) {
            int position = 0;
            for (ParsedQuestion parsed : draft.getQuestions()) {
                position++;
                Question question = parsed.toEntity(null);
                String error = InputValidator.validateQuestion(question);
                if (error != null) {
                    return ServiceResult.failure("Imported question " + position
                            + " (line " + parsed.lineNumber() + "): " + error);
                }
                questions.add(question);
            }
        }

        return quizService.createTest(teacher, details, questions);
    }
}
