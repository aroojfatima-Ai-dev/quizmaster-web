package com.quizmaster.validation;

import com.quizmaster.model.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The registration, class-code, question and duration rules. */
class InputValidatorTest {

    @Test
    void registrationRules() {
        assertNull(InputValidator.validateRegistration("ali_student", "ali@example.com", "student123",
                "student123", "student"));
        assertNotNull(InputValidator.validateRegistration("al", "ali@example.com", "student123", "student123", "student"));
        assertNotNull(InputValidator.validateRegistration("ali student", "ali@example.com", "student123",
                "student123", "student"));
        assertNotNull(InputValidator.validateRegistration("ali_student", "not-an-email", "student123",
                "student123", "student"));
        assertNotNull(InputValidator.validateRegistration("ali_student", "ali@example.com", "short",
                "short", "student"));
        assertNotNull(InputValidator.validateRegistration("ali_student", "ali@example.com", "student123",
                "different", "student"));
        assertNotNull(InputValidator.validateRegistration("ali_student", "ali@example.com", "student123",
                "student123", "wizard"));
    }

    @Test
    @DisplayName("field limits mirror the column widths")
    void fieldLimits() {
        assertNotNull(InputValidator.validateUsername("a".repeat(51)));
        assertNotNull(InputValidator.validateEmail("a".repeat(120) + "@example.com"));
        assertNotNull(InputValidator.validateClassName("a".repeat(101)));
        assertNotNull(InputValidator.validateTestTitle("a".repeat(201)));
        assertNotNull(InputValidator.validateTopic("a".repeat(101)));
        assertNotNull(InputValidator.validateQuestionText("a".repeat(2001)));
        assertNotNull(InputValidator.validateOption("A", "a".repeat(501)));
        assertNull(InputValidator.validateQuestionText("a".repeat(2000)));
        assertNull(InputValidator.validateOption("A", "a".repeat(500)));
    }

    @Test
    @DisplayName("class-code normalisation is locale independent (Turkish 'i' stays 'i')")
    void classCodeIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("PHY101", InputValidator.normaliseClassCode(" phy101 "));
            assertEquals("ABC123", InputValidator.normaliseClassCode("abc123"));
            assertNull(InputValidator.validateClassCode("abc123"));
            assertNotNull(InputValidator.validateClassCode("ABC12"));   // five characters
            assertNotNull(InputValidator.validateClassCode("ABC12!"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void durationParsing() {
        assertEquals(600, InputValidator.parseDurationMinutesToSeconds("10"));
        assertEquals(60, InputValidator.parseDurationMinutesToSeconds("1"));
        assertEquals(86_400, InputValidator.parseDurationMinutesToSeconds("1440"));
        assertNull(InputValidator.parseDurationMinutesToSeconds("0"));
        assertNull(InputValidator.parseDurationMinutesToSeconds("1441"));
        assertNull(InputValidator.parseDurationMinutesToSeconds("ten"));
        assertNull(InputValidator.parseDurationMinutesToSeconds(""));
    }

    @Test
    @DisplayName("blank topic, difficulty, language and expiry action are normalised, never stored blank")
    void blanksAreNormalised() {
        assertEquals("General", InputValidator.normaliseTopic("  "));
        assertEquals("MEDIUM", InputValidator.normaliseDifficulty(null));
        assertEquals("HARD", InputValidator.normaliseDifficulty("hard"));
        assertEquals("English", InputValidator.normaliseLanguage(null));
        assertEquals("Urdu", InputValidator.normaliseLanguage("urdu"));
        assertEquals("AUTO_SUBMIT", InputValidator.normaliseExpiryAction(null));
        assertEquals("AUTO_SUBMIT", InputValidator.normaliseExpiryAction("something else"));
        assertEquals("ALLOW_OVERTIME", InputValidator.normaliseExpiryAction("allow_overtime"));
    }

    @Test
    @DisplayName("a half-typed question is refused on the write path")
    void questionValidation() {
        Question question = new Question(null);
        assertNotNull(InputValidator.validateQuestion(question));

        question.setQuestionText("What is 2 + 2?");
        question.setOptionA("4");
        question.setOptionB("5");
        question.setOptionC("");
        question.setOptionD("6");
        question.setCorrectOption("A");
        assertNotNull(InputValidator.validateQuestion(question));

        question.setOptionC("3");
        assertNull(InputValidator.validateQuestion(question));

        question.setCorrectOption("");
        assertNotNull(InputValidator.validateQuestion(question));

        question.setCorrectOption("A");
        question.setTopic("a".repeat(101));
        assertNotNull(InputValidator.validateQuestion(question));

        question.setTopic("Maths");
        question.setDifficulty("IMPOSSIBLE");
        assertNotNull(InputValidator.validateQuestion(question));
    }

    @Test
    void firstErrorPicksTheFirstProblem() {
        assertNull(InputValidator.firstError(null, null));
        assertEquals("second", InputValidator.firstError(null, "second", "third"));
    }
}
