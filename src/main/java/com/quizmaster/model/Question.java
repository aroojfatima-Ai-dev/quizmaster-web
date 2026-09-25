package com.quizmaster.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Locale;

/** A four-option multiple-choice question belonging to a test. */
@Entity
@Table(name = "questions")
public class Question {

    public static final int QUESTION_TEXT_MAX_LENGTH = 2000;
    public static final int OPTION_MAX_LENGTH = 500;
    public static final int TOPIC_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private Quiz quiz;

    @Column(name = "question_text", length = QUESTION_TEXT_MAX_LENGTH, nullable = false)
    private String questionText;

    @Column(name = "option_a", length = OPTION_MAX_LENGTH, nullable = false)
    private String optionA;

    @Column(name = "option_b", length = OPTION_MAX_LENGTH, nullable = false)
    private String optionB;

    @Column(name = "option_c", length = OPTION_MAX_LENGTH, nullable = false)
    private String optionC;

    @Column(name = "option_d", length = OPTION_MAX_LENGTH, nullable = false)
    private String optionD;

    /** One of A, B, C, D - stored as CHAR(1) like the desktop schema. */
    @Column(name = "correct_option", length = 1, nullable = false)
    private String correctOption;

    @Column(name = "topic", length = TOPIC_MAX_LENGTH, nullable = false)
    private String topic = "General";

    @Column(name = "difficulty", length = 10, nullable = false)
    private String difficulty = "MEDIUM";

    protected Question() {
        // for JPA
    }

    public Question(Quiz quiz) {
        this.quiz = quiz;
    }

    /** The option text for a letter key ("A".."D"), or an empty string. */
    public String optionText(String key) {
        if (key == null) {
            return "";
        }
        return switch (key.trim().toUpperCase(Locale.ROOT)) {
            case "A" -> nullToEmpty(optionA);
            case "B" -> nullToEmpty(optionB);
            case "C" -> nullToEmpty(optionC);
            case "D" -> nullToEmpty(optionD);
            default -> "";
        };
    }

    /** True when the given answer letter matches this question's key. */
    public boolean isCorrect(String answerLetter) {
        return correctOption != null && answerLetter != null
                && correctOption.equalsIgnoreCase(answerLetter.trim());
    }

    public String getDifficultyLabel() {
        if (difficulty == null) {
            return "MEDIUM";
        }
        String value = difficulty.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "EASY" -> "Easy";
            case "HARD" -> "Hard";
            default -> "Medium";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(String correctOption) {
        this.correctOption = correctOption;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
