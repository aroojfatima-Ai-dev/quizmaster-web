package com.quizmaster.web;

import com.quizmaster.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the teacher and student halves of the app apart: an unauthenticated
 * request is sent to the login screen, and a signed-in user who lands on the
 * other role's screens is sent to their own hub instead of seeing a broken page.
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SessionKeys.USER_ID);
        Object role = session == null ? null : session.getAttribute(SessionKeys.USER_ROLE);

        if (!(userId instanceof Long)) {
            String target = request.getRequestURI();
            response.sendRedirect(request.getContextPath() + "/login?message="
                    + URLEncoder.encode("Please sign in to continue.", StandardCharsets.UTF_8)
                    + "&next=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
            return false;
        }

        boolean teacherArea = request.getRequestURI().startsWith(request.getContextPath() + "/teacher");
        boolean isTeacher = User.ROLE_TEACHER.equalsIgnoreCase(role == null ? "" : role.toString());
        if (teacherArea && !isTeacher) {
            response.sendRedirect(request.getContextPath() + "/student/dashboard");
            return false;
        }
        if (!teacherArea && isTeacher) {
            response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
            return false;
        }
        return true;
    }
}
