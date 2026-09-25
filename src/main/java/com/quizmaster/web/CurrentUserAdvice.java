package com.quizmaster.web;

import com.quizmaster.model.User;
import com.quizmaster.repo.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the signed-in user, the app version and the database driver on every
 * model, so every screen can render the header (or its absence) itself.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final UserRepository userRepository;

    @Value("${spring.application.name:QuizMaster Web}")
    private String applicationName;

    public CurrentUserAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model, HttpSession session) {
        model.addAttribute("currentUser", currentUser(session));
        model.addAttribute("applicationName", applicationName);
    }

    /**
     * The signed-in user, or {@code null} on public screens.
     *
     * <p>If the account disappeared while the session was alive, the stale
     * session is dropped here rather than throwing on every screen.
     */
    User currentUser(HttpSession session) {
        Object userId = session == null ? null : session.getAttribute(SessionKeys.USER_ID);
        if (!(userId instanceof Long id)) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }
}
