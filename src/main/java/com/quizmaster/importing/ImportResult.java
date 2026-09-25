package com.quizmaster.importing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of reading and parsing one uploaded file: the questions that can
 * be imported and the blocks that could not, each with its line number.
 */
public class ImportResult {

    private final String fileName;
    private final String fileType;
    private final List<ParsedQuestion> questions;
    private final List<ParseFailure> failures;

    public ImportResult(String fileName, String fileType,
                        List<ParsedQuestion> questions, List<ParseFailure> failures) {
        this.fileName = fileName == null ? "questions" : fileName;
        this.fileType = fileType == null ? "" : fileType;
        this.questions = new ArrayList<>(questions);
        this.failures = new ArrayList<>(failures);
    }

    /** An empty result, used when a read fails before parsing starts. */
    public static ImportResult empty(String fileName, String fileType) {
        return new ImportResult(fileName, fileType, List.of(), List.of());
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    /** Importable questions, in file order. */
    public List<ParsedQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    /** Blocks that were skipped, with reasons. */
    public List<ParseFailure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    /** Every question block the file contains - imported plus skipped. */
    public int getQuestionsFound() {
        return questions.size() + failures.size();
    }

    public int getSkippedCount() {
        return failures.size();
    }

    public int getImportableCount() {
        return questions.size();
    }

    public boolean isEmpty() {
        return questions.isEmpty() && failures.isEmpty();
    }

    public String getSummary() {
        if (getQuestionsFound() == 0) {
            return "No questions found in " + fileName + ".";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getQuestionsFound()).append(getQuestionsFound() == 1 ? " question" : " questions")
                .append(" found in ").append(fileName);
        if (!failures.isEmpty()) {
            sb.append(" — ").append(failures.size()).append(" skipped");
        }
        return sb.toString();
    }
}
