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

/**
 * A teacher's class. Students join it with the 6-character {@link #classCode}.
 *
 * <p>The entity is called {@code ClassRoom} because {@code Class} is a Java
 * keyword; the table keeps the desktop application's name, {@code classes}.
 */
@Entity
@Table(name = "classes")
public class ClassRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "class_name", length = 100, nullable = false)
    private String className;

    @Column(name = "class_code", length = 20, nullable = false, unique = true)
    private String classCode;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ClassRoom() {
        // for JPA
    }

    public ClassRoom(User teacher, String className, String classCode) {
        this.teacher = teacher;
        this.className = className;
        this.classCode = classCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
