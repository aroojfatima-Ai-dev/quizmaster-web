package com.quizmaster.validation;

import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.User;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The field rules, extracted from the screens so registration, class codes,
 * the test wizard and the import write path all enforce the same limits.
 *
 * <p>Limits mirror the column widths (over-long input used to reach the
 * database and come back as an unexplained error). Every method returns
 * {@code null} when the value is acceptable, or a message to show the user.
 */
public final class InputValidator {

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 50;
    public static final int EMAIL_MAX = 120;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 100;
    public static final int CLASS_NAME_MAX = 100;
    public static final int CLASS_CODE_LENGTH = 6;
    public static final int TEST_TITLE_MAX = 200;
    public static final int TOPIC_MAX = Question.TOPIC_MAX_LENGTH;
    public static final int QUESTION_TEXT_MAX = Question.QUESTION_TEXT_MAX_LENGTH;
    public static final int OPTION_MAX = Question.OPTION_MAX_LENGTH;
    public static final int MIN_DURATION_MINUTES = 1;
    public static final int MAX_DURATION_MINUTES = 1440;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");
    private static final Pattern CLASS_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{" + CLASS_CODE_LENGTH + "}$");

    private InputValidator() {
    }

    /** Validates the profile fields shared by both roles. */
    public static String validateRegistration(String username, String email, String password, String confirmPassword,
                                             String role) {
        String usernameError = validateUsername(username);
        if (usernameError != null) {
            return usernameError;
        }
        String emailError = validateEmail(email);
        if (emailError != null) {
            return emailError;
        }
        String passwordError = validatePassword(password);
        if (passwordError != null) {
            return passwordError;
        }
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        if (role == null
                || !(User.ROLE_TEACHER.equalsIgnoreCase(role) || User.ROLE_STUDENT.equalsIgnoreCase(role))) {
            return "Please choose a role: Teacher or Student.";
        }
        return null;
    }

    public static String validateUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) {
            return "Username is required.";
        }
        if (value.length() < USERNAME_MIN || value.length() > USERNAME_MAX) {
            return "Username must be between " + USERNAME_MIN + " and " + USERNAME_MAX + " characters.";
        }
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            return "Username may only contain letters, digits, dots, dashes and underscores.";
        }
        return null;
    }

    public static String validateEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            return "Email is required.";
        }
        if (value.length() > EMAIL_MAX) {
            return "Email must be at most " + EMAIL_MAX + " characters.";
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            return "Please enter a valid email address.";
        }
        return null;
    }

    public static String validatePassword(String password) {
        String value = password == null ? "" : password;
        if (value.isEmpty()) {
            return "Password is required.";
        }
        if (value.length() < PASSWORD_MIN) {
            return "Password must be at least " + PASSWORD_MIN + " characters.";
        }
        if (value.length() > PASSWORD_MAX) {
            return "Password must be at most " + PASSWORD_MAX + " characters.";
        }
        return null;
    }

    public static String validateClassName(String className) {
        String value = className == null ? "" : className.trim();
        if (value.isEmpty()) {
            return "Class name is required.";
        }
        if (value.length() > CLASS_NAME_MAX) {
            return "Class name must be at most " + CLASS_NAME_MAX + " characters.";
        }
        return null;
    }

    /**
     * Normalises a class code: trimmed and upper-cased with {@link Locale#ROOT}
     * so a Turkish system cannot turn an 'i' into 'İ' and store a code students
     * are unable to type.
     */
    public static String normaliseClassCode(String classCode) {
        return classCode == null ? "" : classCode.trim().toUpperCase(Locale.ROOT);
    }

    public static String validateClassCode(String classCode) {
        String value = normaliseClassCode(classCode);
        if (value.isEmpty()) {
            return "Class code is required.";
        }
        if (!CLASS_CODE_PATTERN.matcher(value).matches()) {
            return "Class code must be exactly " + CLASS_CODE_LENGTH + " letters or digits.";
        }
        return null;
    }

    /** Topics longer than the column are refused on the write path (they used to fail the whole save). */
    public static String validateTopic(String topic) {
        String value = topic == null ? "" : topic.trim();
        if (value.isEmpty()) {
            return null; // normalised to "General" by the caller
        }
        if (value.length() > TOPIC_MAX) {
            return "Topic must be at most " + TOPIC_MAX + " characters.";
        }
        return null;
    }

    public static String validateDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return null; // normalised to MEDIUM by the caller
        }
        String value = difficulty.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("EASY") && !value.equals("MEDIUM") && !value.equals("HARD")) {
            return "Difficulty must be EASY, MEDIUM or HARD.";
        }
        return null;
    }

    /** A blank topic or difficulty is normalised instead of reaching a NOT NULL column. */
    public static String normaliseTopic(String topic) {
        String value = topic == null ? "" : topic.trim();
        return value.isEmpty() ? "General" : value;
    }

    public static String normaliseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "MEDIUM";
        }
        return difficulty.trim().toUpperCase(Locale.ROOT);
    }

    /** A NULL expiry action used to fail the AUTO_SUBMIT check and silently become unlimited overtime. */
    public static String normaliseExpiryAction(String expiryAction) {
        if (expiryAction == null || expiryAction.isBlank()) {
            return Quiz.EXPIRY_AUTO_SUBMIT;
        }
        return Quiz.EXPIRY_ALLOW_OVERTIME.equalsIgnoreCase(expiryAction.trim())
                ? Quiz.EXPIRY_ALLOW_OVERTIME
                : Quiz.EXPIRY_AUTO_SUBMIT;
    }

    public static String normaliseLanguage(String language) {
        if (language == null || language.isBlank()) {
            return Quiz.LANGUAGE_ENGLISH;
        }
        return Quiz.LANGUAGE_URDU.equalsIgnoreCase(language.trim())
                ? Quiz.LANGUAGE_URDU
                : Quiz.LANGUAGE_ENGLISH;
    }

    public static String validateTestTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            return "Test title is required.";
        }
        if (value.length() > TEST_TITLE_MAX) {
            return "Test title must be at most " + TEST_TITLE_MAX + " characters.";
        }
        return null;
    }

    /** Parses the duration field (minutes) into seconds, refusing values that would overflow. */
    public static Integer parseDurationMinutesToSeconds(String minutesText) {
        if (minutesText == null || minutesText.isBlank()) {
            return null;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(minutesText.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (minutes < MIN_DURATION_MINUTES || minutes > MAX_DURATION_MINUTES) {
            return null;
        }
        return minutes * 60;
    }

    public static String durationError() {
        return "Duration must be a whole number of minutes between "
                + MIN_DURATION_MINUTES + " and " + MAX_DURATION_MINUTES + ".";
    }

    public static String validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "Language is required.";
        }
        if (!Quiz.LANGUAGE_ENGLISH.equalsIgnoreCase(language) && !Quiz.LANGUAGE_URDU.equalsIgnoreCase(language)) {
            return "Language must be English or Urdu.";
        }
        return null;
    }

    public static String validateExpiryAction(String expiryAction) {
        if (expiryAction == null || expiryAction.isBlank()) {
            return "Please choose what happens when the time runs out.";
        }
        if (!Quiz.EXPIRY_AUTO_SUBMIT.equalsIgnoreCase(expiryAction)
                && !Quiz.EXPIRY_ALLOW_OVERTIME.equalsIgnoreCase(expiryAction)) {
            return "Expiry action must be auto-submit or allow overtime.";
        }
        return null;
    }

    /** Bounds the question text as well as the four options. */
    public static String validateQuestionText(String questionText) {
        String value = questionText == null ? "" : questionText.trim();
        if (value.isEmpty()) {
            return "Question text is required.";
        }
        if (value.length() > QUESTION_TEXT_MAX) {
            return "Question text must be at most " + QUESTION_TEXT_MAX + " characters.";
        }
        return null;
    }

    public static String validateOption(String label, String option) {
        String value = option == null ? "" : option.trim();
        if (value.isEmpty()) {
            return "Option " + label + " is required.";
        }
        if (value.length() > OPTION_MAX) {
            return "Option " + label + " must be at most " + OPTION_MAX + " characters.";
        }
        return null;
    }

    public static String validateCorrectOption(String correctOption) {
        if (correctOption == null || correctOption.isBlank()) {
            return "Please choose the correct option.";
        }
        String value = correctOption.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("A") && !value.equals("B") && !value.equals("C") && !value.equals("D")) {
            return "The correct option must be A, B, C or D.";
        }
        return null;
    }

    /**
     * Validates a whole question, for both typed and imported rows. The write
     * path calls this so a half-typed question can never be persisted (MySQL
     * happily stores an empty string in a NOT NULL column, which showed
     * students four empty radio buttons).
     */
    public static String validateQuestion(Question question) {
        if (question == null) {
            return "Question is missing.";
        }
        String textError = validateQuestionText(question.getQuestionText());
        if (textError != null) {
            return textError;
        }
        String a = validateOption("A", question.getOptionA());
        if (a != null) {
            return a;
        }
        String b = validateOption("B", question.getOptionB());
        if (b != null) {
            return b;
        }
        String c = validateOption("C", question.getOptionC());
        if (c != null) {
            return c;
        }
        String d = validateOption("D", question.getOptionD());
        if (d != null) {
            return d;
        }
        String correct = validateCorrectOption(question.getCorrectOption());
        if (correct != null) {
            return correct;
        }
        String topic = validateTopic(question.getTopic());
        if (topic != null) {
            return topic;
        }
        return validateDifficulty(question.getDifficulty());
    }

    /** Applies the defaults and limits in place, so an imported row is always storable. */
    public static void normalise(Question question) {
        question.setQuestionText(question.getQuestionText() == null ? "" : question.getQuestionText().trim());
        question.setOptionA(trimOrEmpty(question.getOptionA()));
        question.setOptionB(trimOrEmpty(question.getOptionB()));
        question.setOptionC(trimOrEmpty(question.getOptionC()));
        question.setOptionD(trimOrEmpty(question.getOptionD()));
        question.setCorrectOption(
                question.getCorrectOption() == null ? "" : question.getCorrectOption().trim().toUpperCase(Locale.ROOT));
        question.setTopic(normaliseTopic(question.getTopic()));
        question.setDifficulty(normaliseDifficulty(question.getDifficulty()));
    }

    /** The first problem in the list, or {@code null} when there is none. */
    public static String firstError(String... messages) {
        for (String message : messages) {
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return null;
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
