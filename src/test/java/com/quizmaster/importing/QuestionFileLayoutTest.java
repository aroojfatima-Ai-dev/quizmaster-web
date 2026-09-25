package com.quizmaster.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout rules of a questions file: which keyword spellings start a field,
 * which option markers are honoured, what happens to wrapped lines and to the
 * material that sits before the first question. A teacher's file can look like
 * a textbook export, a Word document or something typed by hand, and it should
 * still come out the same way.
 */
class QuestionFileLayoutTest {

    private final QuestionFileParser parser = new QuestionFileParser();

    @Test
    @DisplayName("long keyword spellings and bracketed option markers")
    void longKeywordsAndBracketedOptions() {
        ImportResult result = parser.parse("""
                Question 1: Which planet is known as the red planet?
                (A) Venus
                (B) Mars
                (C) Jupiter
                (D) Mercury
                Ans: B
                Topic: Astronomy
                Difficulty: easy
                """, "layout.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Which planet is known as the red planet?", question.questionText());
        assertEquals("B", question.correctOption());
        assertEquals("Mars", question.optionText("B"));
        assertEquals("Astronomy", question.topic());
        assertEquals("EASY", question.difficulty());
    }

    @Test
    @DisplayName("short labels, dotted options and lowercase keywords")
    void shortLabelsAndDottedOptions() {
        ImportResult result = parser.parse("""
                Q1. Who wrote the play Hamlet?
                A. Charles Dickens
                B. William Shakespeare
                C. Jane Austen
                D. Leo Tolstoy
                answer: b
                """, "layout.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Who wrote the play Hamlet?", question.questionText());
        assertEquals("B", question.correctOption());
        assertEquals("General", question.topic(), "an omitted topic falls back to General");
        assertEquals("MEDIUM", question.difficulty(), "an omitted difficulty falls back to Medium");
    }

    @Test
    @DisplayName("numbered labels, colon options and mixed markers in one question")
    void mixedMarkersInOneQuestion() {
        ImportResult result = parser.parse("""
                Q 2) What is 6 multiplied by 7?
                A) 36
                B. 42
                C: 48
                (D) 56
                Answer: 42
                """, "layout.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("What is 6 multiplied by 7?", question.questionText());
        assertEquals("36", question.optionA());
        assertEquals("42", question.optionB());
        assertEquals("48", question.optionC());
        assertEquals("56", question.optionD());
        assertEquals("B", question.correctOption(), "an answer written as text is matched to its option");
    }

    @Test
    @DisplayName("wrapped question and option lines are joined with a single space")
    void wrappedLinesAreJoined() {
        ImportResult result = parser.parse("""
                Q: Which data structure works on the principle of
                first in, first out?
                A) Stack
                B) Queue that grows
                at the back
                C) Binary tree
                D) Hash map
                Answer: B
                """, "layout.txt", "Text file");

        assertEquals(1, result.getImportableCount(),
                "a wrapped line must not be mistaken for the next question");
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Which data structure works on the principle of first in, first out?",
                question.questionText());
        assertEquals("Queue that grows at the back", question.optionB());
    }

    @Test
    @DisplayName("notes before the first question are ignored, not reported as failures")
    void preambleIsIgnored() {
        ImportResult result = parser.parse("""
                Physics 101 - Midterm Practice
                Chapter 3: Forces and motion
                Prepared for the evening section

                Q: What unit measures force?
                A) Joule
                B) Newton
                C) Watt
                D) Pascal
                Answer: B
                """, "layout.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        assertEquals(0, result.getSkippedCount(), "a title block is not a mistake");
        assertEquals(5, result.getQuestions().get(0).lineNumber(),
                "the question is reported at the line it really starts on");
    }

    @Test
    @DisplayName("blank lines and uneven spacing do not split a question")
    void blankLinesDoNotSplitQuestions() {
        ImportResult result = parser.parse("""
                Q: First question?

                A) one
                B) two

                C) three
                D) four

                Answer: A


                Q: Second question?
                A) one
                B) two
                C) three
                D) four
                Answer: D
                """, "layout.txt", "Text file");

        assertEquals(2, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals("First question?", result.getQuestions().get(0).questionText());
        assertEquals("A", result.getQuestions().get(0).correctOption());
        assertEquals("Second question?", result.getQuestions().get(1).questionText());
        assertEquals("D", result.getQuestions().get(1).correctOption());
    }

    @Test
    @DisplayName("a file with options but no question marker is refused with a usable reason")
    void optionsWithoutQuestionAreReported() {
        ImportResult result = parser.parse("""
                A) stray option
                B) stray option
                C) stray option
                D) stray option
                """, "layout.txt", "Text file");

        assertEquals(0, result.getImportableCount());
        assertTrue(result.getQuestions().isEmpty());
        assertEquals(1, result.getSkippedCount(), "nothing imported, and the teacher is told why");
        assertTrue(result.getFailures().get(0).reason().contains("Q:"),
                "the reason should show the marker the file is missing");
    }

    @Test
    @DisplayName("keywords are recognised whichever way the teacher capitalises them")
    void keywordCasingIsIrrelevant() {
        ImportResult result = parser.parse("""
                q: Which gas do plants absorb?
                a) Oxygen
                b) Carbon dioxide
                c) Nitrogen
                d) Hydrogen
                ANSWER: B
                TOPIC: Biology
                DIFFICULTY: Easy (level 1)
                """, "layout.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Which gas do plants absorb?", question.questionText());
        assertEquals("B", question.correctOption());
        assertEquals("Biology", question.topic());
        assertEquals("EASY", question.difficulty(), "a difficulty with a parenthesised level still maps");
    }
}
