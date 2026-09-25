package com.quizmaster.web;

import com.quizmaster.model.User;
import com.quizmaster.service.AuthService;
import com.quizmaster.service.ServiceResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Welcome, login, registration and logout. */
@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** The splash screen: dark card, floating bubbles, "Get Started". */
    @GetMapping("/")
    public String welcome(HttpSession session) {
        if (session.getAttribute(SessionKeys.USER_ID) instanceof Long) {
            return redirectFor(session);
        }
        return "welcome";
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String message,
                            @RequestParam(required = false) String next, Model model) {
        model.addAttribute("message", message);
        model.addAttribute("next", next);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String usernameOrEmail,
                        @RequestParam String password,
                        @RequestParam(required = false) String next,
                        HttpServletRequest request,
                        Model model) {
        if (usernameOrEmail.isBlank() || password.isEmpty()) {
            model.addAttribute("error", "Please enter both username/email and password.");
            model.addAttribute("usernameOrEmail", usernameOrEmail);
            return "login";
        }

        User user = authService.login(usernameOrEmail, password);
        if (user == null) {
            model.addAttribute("error", "Invalid username/email or password.");
            model.addAttribute("usernameOrEmail", usernameOrEmail);
            return "login";
        }

        // A fresh session id after signing in.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setAttribute(SessionKeys.USER_ROLE, user.getRole());
        session.setMaxInactiveInterval(3 * 60 * 60);

        if (isSafeRedirect(next)) {
            return "redirect:" + next;
        }
        return user.isTeacher() ? "redirect:/teacher/dashboard" : "redirect:/student/dashboard";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam(required = false) String confirmPassword,
                           @RequestParam String role,
                           Model model) {
        ServiceResult<User> result = authService.register(username, email, password, confirmPassword, role);
        if (result.isFailure()) {
            model.addAttribute("error", result.message());
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("role", role);
            return "register";
        }
        model.addAttribute("success", "Your account was created. Please sign in.");
        model.addAttribute("usernameOrEmail", username);
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?message=" + "You have been signed out.";
    }

    private String redirectFor(HttpSession session) {
        Object role = session.getAttribute(SessionKeys.USER_ROLE);
        return User.ROLE_TEACHER.equalsIgnoreCase(role == null ? "" : role.toString())
                ? "redirect:/teacher/dashboard"
                : "redirect:/student/dashboard";
    }

    /** Only relative paths are ever redirected to, so a link cannot bounce to another site. */
    private boolean isSafeRedirect(String next) {
        return next != null && next.startsWith("/") && !next.startsWith("//") && !next.contains("\\");
    }
}
