package com.quizmaster.service;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A student's in-progress attempt, kept in the HTTP session so a page refresh
 * (or a dropped connection) never loses answers.
 *
 * <p>The deadline is enforced against {@link #startedAtMillis} rather than only
 * against the browser's countdown, so a student cannot gain time by reloading
 * the page or by editing the clock in the page.
 */
public class AttemptSession implements Serializable {

    private final Long quizId;
    private final long startedAtMillis;
    private final LinkedHashMap<Long, String> answers = new LinkedHashMap<>();

    private boolean graded;
    private int score;
    private int total;
    private int overtimeSeconds;
    private boolean autoSubmitted;

    public AttemptSession(Long quizId, long startedAtMillis) {
        this.quizId = quizId;
        this.startedAtMillis = startedAtMillis;
    }

    public Long getQuizId() {
        return quizId;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public Map<Long, String> getAnswers() {
        return Collections.unmodifiableMap(answers);
    }

    public String answerFor(Long questionId) {
        return answers.get(questionId);
    }

    public int answerCount() {
        return (int) answers.values().stream().filter(value -> value != null && !value.isBlank()).count();
    }

    public void setAnswer(Long questionId, String letter) {
        if (letter == null || letter.isBlank()) {
            answers.remove(questionId);
        } else {
            answers.put(questionId, letter.trim().toUpperCase(java.util.Locale.ROOT));
        }
    }

    public long elapsedSeconds() {
        return Math.max(0, (System.currentTimeMillis() - startedAtMillis) / 1000);
    }

    /** Seconds left before the countdown reaches zero (never negative). */
    public int remainingSeconds(int allowedSeconds) {
        return (int) Math.max(0, allowedSeconds - elapsedSeconds());
    }

    /** True once the countdown has reached zero. */
    public boolean isExpired(int allowedSeconds) {
        return elapsedSeconds() >= allowedSeconds;
    }

    /**
     * Overtime used, or 0 for a test that auto-submits. Overtime is recorded
     * against the attempt, as the desktop application did.
     */
    public int overtimeSecondsFor(int allowedSeconds, boolean allowsOvertime) {
        if (!allowsOvertime) {
            return 0;
        }
        return (int) Math.max(0, elapsedSeconds() - allowedSeconds);
    }

    public boolean isGraded() {
        return graded;
    }

    public void markGraded(int score, int total, int overtimeSeconds, boolean autoSubmitted) {
        this.graded = true;
        this.score = score;
        this.total = total;
        this.overtimeSeconds = overtimeSeconds;
        this.autoSubmitted = autoSubmitted;
    }

    public int getScore() {
        return score;
    }

    public int getTotal() {
        return total;
    }

    public int getOvertimeSeconds() {
        return overtimeSeconds;
    }

    public boolean isAutoSubmitted() {
        return autoSubmitted;
    }
}
