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

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** One test attempt by a student. */
@Entity
@Table(name = "results")
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "overtime_seconds", nullable = false)
    private int overtimeSeconds;

    @Column(name = "taken_at")
    private Instant takenAt = Instant.now();

    protected Result() {
        // for JPA
    }

    public Result(Quiz quiz, User student, int score, int total, int overtimeSeconds) {
        this.quiz = quiz;
        this.student = student;
        this.score = score;
        this.total = total;
        this.overtimeSeconds = overtimeSeconds;
    }

    /** Percentage scored, rounded to one decimal - formatted with Locale.ROOT so a Turkish locale cannot change the separator. */
    public String getPercentageLabel() {
        double percentage = total > 0 ? ((double) score / total) * 100.0 : 0.0;
        return String.format(Locale.ROOT, "%.1f%%", percentage);
    }

    public double getPercentage() {
        return total > 0 ? ((double) score / total) * 100.0 : 0.0;
    }

    /** "mm:ss" of overtime used. */
    public String getOvertimeLabel() {
        return String.format(Locale.ROOT, "%02d:%02d", overtimeSeconds / 60, overtimeSeconds % 60);
    }

    public String getTakenAtLabel() {
        if (takenAt == null) {
            return "";
        }
        return java.time.format.DateTimeFormatter
                .ofPattern("d MMM yyyy, HH:mm", Locale.ROOT)
                .withZone(java.time.ZoneId.systemDefault())
                .format(takenAt);
    }

    public String getRelativeTimeLabel() {
        if (takenAt == null) {
            return "";
        }
        Duration elapsed = Duration.between(takenAt, Instant.now());
        long minutes = elapsed.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = elapsed.toHours();
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = elapsed.toDays();
        return days + (days == 1 ? " day ago" : " days ago");
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

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getOvertimeSeconds() {
        return overtimeSeconds;
    }

    public void setOvertimeSeconds(int overtimeSeconds) {
        this.overtimeSeconds = overtimeSeconds;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public void setTakenAt(Instant takenAt) {
        this.takenAt = takenAt;
    }
}
