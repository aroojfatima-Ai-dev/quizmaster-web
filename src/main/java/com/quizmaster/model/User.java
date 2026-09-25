package com.quizmaster.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A teacher or a student. Both roles share one table, distinguished by
 * {@link #role}, exactly as the desktop application's {@code users} table did.
 */
@Entity
@Table(name = "users")
public class User {

    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_STUDENT = "student";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "email", length = 120, nullable = false, unique = true)
    private String email;

    /** A PBKDF2 hash (see {@link com.quizmaster.security.PasswordUtil}), never plain text. */
    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "role", length = 20, nullable = false)
    private String role = ROLE_STUDENT;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected User() {
        // for JPA
    }

    public User(String username, String email, String password, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public boolean isTeacher() {
        return ROLE_TEACHER.equalsIgnoreCase(role);
    }

    public boolean isStudent() {
        return !isTeacher();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
