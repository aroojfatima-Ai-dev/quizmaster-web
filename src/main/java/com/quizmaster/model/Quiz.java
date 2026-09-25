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

import java.time.Instant;
import java.util.Locale;

/**
 * A test created by a teacher: public, or visible only to one class.
 *
 * <p>Named {@code Quiz} in Java (rather than {@code Test}, which collides with
 * JUnit's annotation in the test sources) but stored in the desktop
 * application's {@code tests} table.
 */
@Entity
@Table(name = "tests")
public class Quiz {

    /** Auto-submit when the countdown reaches zero (the default). */
    public static final String EXPIRY_AUTO_SUBMIT = "AUTO_SUBMIT";
    /** Keep accepting answers after zero and record the overtime used. */
    public static final String EXPIRY_ALLOW_OVERTIME = "ALLOW_OVERTIME";

    public static final String LANGUAGE_ENGLISH = "English";
    public static final String LANGUAGE_URDU = "Urdu";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "language", length = 20, nullable = false)
    private String language = LANGUAGE_ENGLISH;

    @Column(name = "total_time_seconds", nullable = false)
    private int totalTimeSeconds = 900;

    /**
     * A row written before this column existed used to fail the AUTO_SUBMIT
     * check and silently become unlimited overtime; auto-submit is the default.
     */
    @Column(name = "expiry_action", length = 20, nullable = false)
    private String expiryAction = EXPIRY_AUTO_SUBMIT;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Only set when the test is class-only. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "class_id")
    private ClassRoom classRoom;

    @Column(name = "is_public", nullable = false)
    private boolean publicTest = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Quiz() {
        // for JPA
    }

    public Quiz(String title, User createdBy) {
        this.title = title;
        this.createdBy = createdBy;
    }

    /** True when the countdown is allowed to run past zero and the overtime is recorded. */
    public boolean allowsOvertime() {
        return EXPIRY_ALLOW_OVERTIME.equalsIgnoreCase(expiryAction);
    }

    public boolean isUrdu() {
        return LANGUAGE_URDU.equalsIgnoreCase(language);
    }

    /** The text direction to render question content in. */
    public String getTextDirection() {
        return isUrdu() ? "rtl" : "ltr";
    }

    public String getDurationLabel() {
        int minutes = totalTimeSeconds / 60;
        int seconds = totalTimeSeconds % 60;
        if (minutes > 0 && seconds > 0) {
            return String.format(Locale.ROOT, "%d min %d sec", minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%d min", minutes);
        }
        return String.format(Locale.ROOT, "%d sec", seconds);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getTotalTimeSeconds() {
        return totalTimeSeconds;
    }

    public void setTotalTimeSeconds(int totalTimeSeconds) {
        this.totalTimeSeconds = totalTimeSeconds;
    }

    public String getExpiryAction() {
        return expiryAction;
    }

    public void setExpiryAction(String expiryAction) {
        this.expiryAction = expiryAction;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public ClassRoom getClassRoom() {
        return classRoom;
    }

    public void setClassRoom(ClassRoom classRoom) {
        this.classRoom = classRoom;
    }

    public boolean isPublicTest() {
        return publicTest;
    }

    public void setPublicTest(boolean publicTest) {
        this.publicTest = publicTest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
