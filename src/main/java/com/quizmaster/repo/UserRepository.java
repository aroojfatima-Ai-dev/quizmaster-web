package com.quizmaster.repo;

import com.quizmaster.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Reads and writes the {@code users} table. */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Every account whose username <em>or</em> e-mail matches the value the
     * student/teacher typed.
     *
     * <p>A list, not an optional: the desktop application only looked at the
     * first row of {@code WHERE username = ? OR email = ?}, which left an
     * account whose e-mail equals another user's username unable to sign in.
     * The caller verifies the password against each candidate instead.
     */
    @Query("""
            select u from User u
            where lower(u.username) = lower(:value) or lower(u.email) = lower(:value)
            order by u.id
            """)
    List<User> findLoginCandidates(@Param("value") String value);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleIgnoreCase(String role);
}
