package com.quizmaster.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The documented question-file format and every documented skip reason. */
class QuestionFileParserTest {

    private final QuestionFileParser parser = new QuestionFileParser();

    private static final String DOCUMENTED_EXAMPLE = """
            Q: What is the capital of France?
            A) Berlin
            B) Paris
            C) Madrid
            D) Rome
            Answer: B
            Topic: Geography
            Difficulty: EASY

            Q: Which keyword declares a constant in Java?
            A) static
            B) final
            C) const
            D) var
            Answer: B
            """;

    @Test
    @DisplayName("the documented example parses with every field in place")
    void documentedExample() {
        ImportResult result = parser.parse(DOCUMENTED_EXAMPLE, "sample.txt", "Text file");

        assertEquals(2, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals("sample.txt", result.getFileName());

        ParsedQuestion first = result.getQuestions().get(0);
        assertEquals("What is the capital of France?", first.questionText());
        assertEquals("Berlin", first.optionA());
        assertEquals("Paris", first.optionB());
        assertEquals("Madrid", first.optionC());
        assertEquals("Rome", first.optionD());
        assertEquals("B", first.correctOption());
        assertEquals("Geography", first.topic());
        assertEquals("EASY", first.difficulty());
        assertEquals(1, first.lineNumber());

        ParsedQuestion second = result.getQuestions().get(1);
        assertEquals("Which keyword declares a constant in Java?", second.questionText());
        assertEquals("General", second.topic());
        assertEquals("MEDIUM", second.difficulty());
    }

    @Test
    @DisplayName("file order is preserved")
    void orderIsPreserved() {
        StringBuilder text = new StringBuilder();
        for (int index = 1; index <= 6; index++) {
            text.append("Q: Question ").append(index).append("?\n")
                    .append("A) a\nB) b\nC) c\nD) d\nAnswer: A\n\n");
        }
        ImportResult result = parser.parse(text.toString(), "ordered.txt", "Text file");
        assertEquals(6, result.getImportableCount());
        for (int index = 0; index < 6; index++) {
            assertEquals("Question " + (index + 1) + "?", result.getQuestions().get(index).questionText());
        }
    }

    @Test
    @DisplayName("blank lines are conventional, not required - a PDF keeps none")
    void blankLinesAreOptional() {
        String text = "Q: First?\nA) 1\nB) 2\nC) 3\nD) 4\nAnswer: A\n"
                + "Q: Second?\nA) 1\nB) 2\nC) 3\nD) 4\nAnswer: B\n";
        ImportResult result = parser.parse(text, "no-blanks.txt", "Text file");
        assertEquals(2, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    @DisplayName("wrapped lines are joined onto the field above them")
    void wrappedLinesAreJoined() {
        String text = """
                Q: Which data structure works on the principle of first in,
                first out?
                A) Stack
                B) Queue
                C) Binary
                tree
                D) Hash map
                Answer: B
                Topic: Data
                Structures
                """;
        ImportResult result = parser.parse(text, "wrapped.txt", "Text file");
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Which data structure works on the principle of first in, first out?", question.questionText());
        assertEquals("Binary tree", question.optionC());
        assertEquals("Data Structures", question.topic());
    }

    @Test
    @DisplayName("keyword and option spellings are all understood")
    void keywordSpellings() {
        String text = """
                Question 2: Picked up with a spelled-out label?
                A. first
                (B) second
                C: third
                D) fourth
                Ans: B
                Subject: Spelling
                Level: EASY
                """;
        ImportResult result = parser.parse(text, "spellings.txt", "Text file");
        assertEquals(1, result.getImportableCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Picked up with a spelled-out label?", question.questionText());
        assertEquals("first", question.optionA());
        assertEquals("second", question.optionB());
        assertEquals("third", question.optionC());
        assertEquals("B", question.correctOption());
        assertEquals("Spelling", question.topic());
        assertEquals("EASY", question.difficulty());
    }

    @Test
    @DisplayName("anything before the first Q: line is a heading and is ignored")
    void headingsAreIgnored() {
        String text = """
                Chapter 4 - Cell Biology
                Prepared by Prof. Smith
                Answer: this line belongs to no question

                Q: What is the powerhouse of the cell?
                A) Nucleus
                B) Mitochondria
                C) Ribosome
                D) Vacuole
                Answer: B
                """;
        ImportResult result = parser.parse(text, "heading.txt", "Text file");
        assertEquals(1, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals("What is the powerhouse of the cell?", result.getQuestions().get(0).questionText());
    }

    @Test
    @DisplayName("every documented skip reason is reported with its line number")
    void skipReasons() {
        String overlong = "x".repeat(501);
        String text = """
                Q: Missing the fourth option?
                A) one
                B) two
                C) three
                Answer: A

                Q: No options at all?
                Answer: A

                Q: Option too long?
                A) %s
                B) two
                C) three
                D) four
                Answer: A

                Q: Answer that cannot be read?
                A) one
                B) two
                C) three
                D) four
                Answer: seven
                """.formatted(overlong);

        ImportResult result = parser.parse(text, "skips.txt", "Text file");
        assertEquals(0, result.getImportableCount());
        assertEquals(4, result.getSkippedCount());

        List<String> reasons = new ArrayList<>();
        for (ParseFailure failure : result.getFailures()) {
            reasons.add(failure.lineNumber() + ":" + failure.reason());
        }
        assertTrue(reasons.get(0).contains("Missing option D"), reasons.get(0));
        assertTrue(reasons.get(1).contains("Missing option A"), reasons.get(1));
        assertTrue(reasons.get(2).contains("longer than 500"), reasons.get(2));
        assertTrue(reasons.get(3).contains("does not match exactly one"), reasons.get(3));
        assertEquals(1, result.getFailures().get(0).lineNumber());
        assertEquals(7, result.getFailures().get(1).lineNumber());
        assertEquals(10, result.getFailures().get(2).lineNumber());
        assertEquals(17, result.getFailures().get(3).lineNumber());
    }

    @Test
    @DisplayName("a missing answer is a skip reason")
    void missingAnswer() {
        String text = "Q: No answer?\nA) one\nB) two\nC) three\nD) four\n";
        ImportResult result = parser.parse(text, "no-answer.txt", "Text file");
        assertEquals(0, result.getImportableCount());
        assertEquals(1, result.getSkippedCount());
        assertTrue(result.getFailures().get(0).reason().contains("answer is missing"));
    }

    @Test
    @DisplayName("a question block with no question text is reported, not guessed")
    void emptyQuestionText() {
        String text = "Q:\nA) one\nB) two\nC) three\nD) four\nAnswer: A\n";
        ImportResult result = parser.parse(text, "blank-question.txt", "Text file");
        assertEquals(1, result.getSkippedCount());
        assertTrue(result.getFailures().get(0).reason().contains("question text is missing"));
    }

    @Test
    @DisplayName("CRLF, a byte order mark, non-breaking spaces and page breaks are cleaned up")
    void extractionArtefactsAreRemoved() {
        String text = "\uFEFFQ: What is 2\u00A0+\u00A02?\r\n"
                + "A) 4\r\nB) 5\u200B\r\nC) 6\r\nD) 7\r\nAnswer: A\r\n"
                + "\fQ: Second question?\r\nA) a\r\nB) b\r\nC) c\r\nD) d\r\nAnswer: D\r\n";
        ImportResult result = parser.parse(text, "artefacts.txt", "Text file");

        assertEquals(2, result.getImportableCount());
        assertEquals("What is 2 + 2?", result.getQuestions().get(0).questionText());
        assertEquals("5", result.getQuestions().get(0).optionB());
        // The form feed would have glued page one's last line to page two's first.
        assertEquals("Second question?", result.getQuestions().get(1).questionText());
    }

    @Test
    @DisplayName("at most 200 questions are imported; the rest are reported as skipped")
    void questionCap() {
        StringBuilder text = new StringBuilder();
        for (int index = 1; index <= 205; index++) {
            text.append("Q: Question ").append(index).append("?\n")
                    .append("A) a\nB) b\nC) c\nD) d\nAnswer: A\n\n");
        }
        ImportResult result = parser.parse(text.toString(), "many.txt", "Text file");
        assertEquals(QuestionFileParser.MAX_QUESTIONS, result.getImportableCount());
        assertEquals(5, result.getSkippedCount());
        assertTrue(result.getFailures().get(0).reason().contains("Only the first 200"));
    }

    @Test
    @DisplayName("no input at all can make the parser throw")
    void parserNeverThrows() {
        assertNotNull(parser.parse(null));
        assertNotNull(parser.parse(""));
        assertNotNull(parser.parse("   \n\n  "));
        assertNotNull(parser.parse("\u0000\u0001\u0002"));
        assertNotNull(parser.parse("Q: only a question line"));
        assertNotNull(parser.parse("Answer: B\nTopic: x\nA) one"));
        assertNotNull(parser.parse("A) one B) two C) three D) four"));
        assertNotNull(parser.parse("Q: weird ?\uD83D\uDE00 A) 1 B) 2 C) 3 D) 4 Answer: Z"));
        assertNotNull(parser.parse("Q: " + "x".repeat(5000) + "\nA) a\nB) b\nC) c\nD) d\nAnswer: A"));
    }

    @Test
    @DisplayName("a file with no Q: line at all explains what is missing")
    void noQuestionsInFile() {
        ImportResult result = parser.parse("Just some notes that are not a quiz at all.", "notes.txt", "Text file");
        assertEquals(0, result.getImportableCount());
        assertEquals(1, result.getSkippedCount());
        assertTrue(result.getFailures().get(0).reason().contains("Start each question with a line like"));
    }

    @Test
    @DisplayName("a skipped block after an inline question keeps the real line numbers")
    void lineNumbersSurviveInlineExpansion() {
        String text = """
                Q: First? A) 1 B) 2 C) 3 D) 4 Answer: A
                Q: Second?
                A) 1
                B) 2
                C) 3
                Answer: C
                """;
        ImportResult result = parser.parse(text, "lines.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        assertEquals(1, lineNumbersOf(result.getQuestions()));
        assertEquals(1, result.getSkippedCount());
        assertEquals(2, result.getFailures().get(0).lineNumber(),
                "the skipped block must be reported at the line it really came from");
    }

    @Test
    void summaryCountsFoundQuestions() {
        ImportResult result = parser.parse(DOCUMENTED_EXAMPLE, "sample.txt", "Text file");
        assertEquals(2, result.getQuestionsFound());
        assertFalse(result.isEmpty());
        assertTrue(result.getSummary().contains("2 questions found"));
        assertTrue(result.getSummary().contains("sample.txt"));
    }

    private int lineNumbersOf(List<ParsedQuestion> questions) {
        return questions.isEmpty() ? -1 : questions.get(0).lineNumber();
    }
}
