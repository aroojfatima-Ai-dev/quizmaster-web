package com.quizmaster.service;

/**
 * The outcome of an action that can fail with a message for the user -
 * registration, joining a class, saving a test. Keeps the "show the reason
 * instead of an unexplained database error" behaviour of the desktop app.
 */
public record ServiceResult<T>(boolean ok, String message, T value) {

    public static <T> ServiceResult<T> success(T value) {
        return new ServiceResult<>(true, null, value);
    }

    public static <T> ServiceResult<T> success(String message, T value) {
        return new ServiceResult<>(true, message, value);
    }

    public static <T> ServiceResult<T> failure(String message) {
        return new ServiceResult<>(false, message, null);
    }

    public boolean isFailure() {
        return !ok;
    }
}
