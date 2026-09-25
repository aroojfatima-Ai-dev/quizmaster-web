package com.quizmaster.importing;

import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.validation.InputValidator;

/**
 * One question read from an uploaded file, with the source line it came from
 * so a skipped block can be reported at a line that actually exists.
 *
 * <p>Parsed questions are immutable; editing one on the preview screen replaces
 * it in the working list, which is what makes "editing an imported question
 * sticks" possible (the desktop version rebuilt the list from the parse result
 * and silently reverted edits and removals).
 */
public record ParsedQuestion(
        int lineNumber,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption,
        String topic,
        String difficulty) implements java.io.Serializable {

    /** Builds a persistable entity. The caller must still validate it. */
    public Question toEntity(Quiz quiz) {
        Question question = new Question(quiz);
        question.setQuestionText(questionText);
        question.setOptionA(optionA);
        question.setOptionB(optionB);
        question.setOptionC(optionC);
        question.setOptionD(optionD);
        question.setCorrectOption(correctOption);
        question.setTopic(topic);
        question.setDifficulty(difficulty);
        InputValidator.normalise(question);
        return question;
    }

    /** The option text for a letter key, used by the review screens. */
    public String optionText(String key) {
        if (key == null) {
            return "";
        }
        return switch (key.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "A" -> optionA;
            case "B" -> optionB;
            case "C" -> optionC;
            case "D" -> optionD;
            default -> "";
        };
    }

    public ParsedQuestion withCorrectOption(String newCorrectOption) {
        return new ParsedQuestion(lineNumber, questionText, optionA, optionB, optionC, optionD,
                newCorrectOption, topic, difficulty);
    }

    public ParsedQuestion withTopic(String newTopic) {
        return new ParsedQuestion(lineNumber, questionText, optionA, optionB, optionC, optionD,
                correctOption, newTopic, difficulty);
    }

    public ParsedQuestion withDifficulty(String newDifficulty) {
        return new ParsedQuestion(lineNumber, questionText, optionA, optionB, optionC, optionD,
                correctOption, topic, newDifficulty);
    }

    public ParsedQuestion withQuestionText(String newQuestionText) {
        return new ParsedQuestion(lineNumber, newQuestionText, optionA, optionB, optionC, optionD,
                correctOption, topic, difficulty);
    }
}
