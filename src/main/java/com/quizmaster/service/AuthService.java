package com.quizmaster.service;

import com.quizmaster.model.User;
import com.quizmaster.repo.UserRepository;
import com.quizmaster.security.PasswordUtil;
import com.quizmaster.validation.InputValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Registration and login. */
@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Creates an account, refusing duplicates and over-long fields. */
    @Transactional
    public ServiceResult<User> register(String username, String email, String password,
                                        String confirmPassword, String role) {
        String error = InputValidator.validateRegistration(username, email, password, confirmPassword, role);
        if (error != null) {
            return ServiceResult.failure(error);
        }

        String cleanUsername = username.trim();
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            return ServiceResult.failure("That username is already taken. Please choose another one.");
        }
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            return ServiceResult.failure("That email is already registered. Try logging in instead.");
        }

        String normalisedRole = User.ROLE_TEACHER.equalsIgnoreCase(role) ? User.ROLE_TEACHER : User.ROLE_STUDENT;
        User user = new User(cleanUsername, cleanEmail, PasswordUtil.hash(password), normalisedRole);
        return ServiceResult.success(userRepository.save(user));
    }

    /**
     * Signs a user in.
     *
     * <p>Every account whose username or e-mail matches is checked, so an
     * account whose e-mail equals another user's username can still sign in.
     * A row still holding a plain-text password is upgraded to a PBKDF2 hash on
     * the first successful login.
     *
     * @return the user, or {@code null} when the credentials do not match
     */
    @Transactional
    public User login(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isEmpty()) {
            return null;
        }
        List<User> candidates = userRepository.findLoginCandidates(usernameOrEmail.trim());
        for (User candidate : candidates) {
            if (PasswordUtil.verify(password, candidate.getPassword())) {
                if (PasswordUtil.needsUpgrade(candidate.getPassword())) {
                    candidate.setPassword(PasswordUtil.hash(password));
                    userRepository.save(candidate);
                }
                return candidate;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return id == null ? null : userRepository.findById(id).orElse(null);
    }
}
