package com.quizmaster.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cases real files throw at the parser: a whole question on one line,
 * several questions on one line, answers written as text, difficulty
 * synonyms, wrapped options, and the promise that an ordinary
 * one-field-per-line file is left alone.
 */
class QuestionFileToleranceTest {

    private final QuestionFileParser parser = new QuestionFileParser();

    @Test
    @DisplayName("a whole question on one line is expanded into its fields")
    void wholeQuestionOnOneLine() {
        String text = "Q: Capital of France? A) Berlin B) Paris C) Madrid D) Rome Answer: B\n";
        ImportResult result = parser.parse(text, "one-line.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Capital of France?", question.questionText());
        assertEquals("Berlin", question.optionA());
        assertEquals("Paris", question.optionB());
        assertEquals("Madrid", question.optionC());
        assertEquals("Rome", question.optionD());
        assertEquals("B", question.correctOption());
        assertEquals(1, question.lineNumber());
    }

    @Test
    @DisplayName("several questions on one line are all read")
    void severalQuestionsOnOneLine() {
        String text = "Q: First? A) 1 B) 2 C) 3 D) 4 Answer: A Q: Second? A) 5 B) 6 C) 7 D) 8 Answer: B\n";
        ImportResult result = parser.parse(text, "two-on-one-line.txt", "Text file");

        assertEquals(2, result.getImportableCount());
        assertEquals("First?", result.getQuestions().get(0).questionText());
        assertEquals("A", result.getQuestions().get(0).correctOption());
        assertEquals("Second?", result.getQuestions().get(1).questionText());
        assertEquals("8", result.getQuestions().get(1).optionD());
        assertEquals("B", result.getQuestions().get(1).correctOption());
    }

    @Test
    @DisplayName("options and answer on their own line are split when a keyword label is present")
    void optionsLineAfterTheQuestion() {
        String text = """
                Q: Capital of France?
                A) Berlin B) Paris C) Madrid D) Rome Answer: B
                """;
        ImportResult result = parser.parse(text, "options-line.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("Capital of France?", question.questionText());
        assertEquals("Paris", question.optionB());
        assertEquals("B", question.correctOption());
    }

    @Test
    @DisplayName("an answer written out is matched against the option texts")
    void answerWrittenOut() {
        String text = """
                Q: Which data structure works on the principle of first in, first out?
                A) Stack
                B) Queue
                C) Binary tree
                D) Hash map
                Answer: Queue
                """;
        ImportResult result = parser.parse(text, "text-answer.txt", "Text file");
        assertEquals(1, result.getImportableCount());
        assertEquals("B", result.getQuestions().get(0).correctOption());
    }

    @Test
    @DisplayName("an answer with trailing words still matches exactly one option")
    void answerWithTrailingWords() {
        String text = """
                Q: Which ocean is the largest?
                A) Atlantic Ocean
                B) Pacific Ocean
                C) Indian Ocean
                D) Arctic Ocean
                Answer: Pacific Ocean - it is the largest by far
                """;
        ImportResult result = parser.parse(text, "trailing.txt", "Text file");
        assertEquals(1, result.getImportableCount());
        assertEquals("B", result.getQuestions().get(0).correctOption());
    }

    @Test
    @DisplayName("an ambiguous answer is reported, never guessed")
    void ambiguousAnswerIsReported() {
        String text = """
                Q: Which one?
                A) Paris
                B) Paris
                C) Madrid
                D) Rome
                Answer: Paris
                """;
        ImportResult result = parser.parse(text, "ambiguous.txt", "Text file");
        assertEquals(0, result.getImportableCount());
        assertEquals(1, result.getSkippedCount());
        assertTrue(result.getFailures().get(0).reason().contains("does not match exactly one"));
    }

    @Test
    @DisplayName("a sentence answer is not read as the letter A")
    void sentenceAnswerIsNotLetterA() {
        assertNull(QuestionFileParser.extractAnswerLetter("a good idea"));
        assertNull(QuestionFileParser.extractAnswerLetter("an approach that works"));
        assertNull(QuestionFileParser.extractAnswerLetter("all of the above"));

        assertEquals("A", QuestionFileParser.extractAnswerLetter("A"));
        assertEquals("B", QuestionFileParser.extractAnswerLetter(" b "));
        assertEquals("C", QuestionFileParser.extractAnswerLetter("(c)"));
        assertEquals("B", QuestionFileParser.extractAnswerLetter("B)"));
        assertEquals("A", QuestionFileParser.extractAnswerLetter("A - the first one"));
        assertEquals("D", QuestionFileParser.extractAnswerLetter("answer is D"));
        assertEquals("B", QuestionFileParser.extractAnswerLetter("option B"));
        assertEquals("C", QuestionFileParser.extractAnswerLetter("correct: C"));
    }

    @Test
    @DisplayName("difficulty synonyms map to the nearest level, unknown values fall back to MEDIUM")
    void difficultySynonyms() {
        assertEquals("EASY", QuestionFileParser.normaliseDifficulty("Easy"));
        assertEquals("EASY", QuestionFileParser.normaliseDifficulty("beginner"));
        assertEquals("MEDIUM", QuestionFileParser.normaliseDifficulty("Med"));
        assertEquals("MEDIUM", QuestionFileParser.normaliseDifficulty("moderate"));
        assertEquals("HARD", QuestionFileParser.normaliseDifficulty("Difficult"));
        assertEquals("HARD", QuestionFileParser.normaliseDifficulty("Hard (level 3)"));
        assertEquals("HARD", QuestionFileParser.normaliseDifficulty("ADVANCED"));
        assertEquals("MEDIUM", QuestionFileParser.normaliseDifficulty("roughly medium-ish"));
        assertEquals("MEDIUM", QuestionFileParser.normaliseDifficulty("hairy"));
        assertEquals("MEDIUM", QuestionFileParser.normaliseDifficulty(null));
    }

    @Test
    @DisplayName("an ordinary file is passed through untouched, even with (B) inside a question")
    void ordinaryFileIsUntouched() {
        String text = """
                Q: What does the reference (B) in the diagram point to?
                A) The nucleus
                B) The ribosome
                C) The membrane
                D) The cytoplasm
                Answer: B
                Topic: Cells
                Difficulty: MEDIUM
                """;
        ImportResult result = parser.parse(text, "ordinary.txt", "Text file");

        assertEquals(1, result.getImportableCount());
        ParsedQuestion question = result.getQuestions().get(0);
        assertEquals("What does the reference (B) in the diagram point to?", question.questionText());
        assertEquals("The ribosome", question.optionB());
        assertEquals("B", question.correctOption());
        assertEquals("Cells", question.topic());
    }

    @Test
    @DisplayName("the bundled sample file parses on every build")
    void bundledSampleFile() throws Exception {
        String text;
        try (var stream = getClass().getClassLoader().getResourceAsStream("samples/sample-questions.txt")) {
            text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        ImportResult result = parser.parse(text, "sample-questions.txt", "Text file");

        assertEquals(5, result.getImportableCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(5, result.getQuestionsFound());
        // The optional fields, a text answer and a decorated difficulty are all exercised.
        assertEquals("B", result.getQuestions().get(0).correctOption());
        assertEquals("Queue", result.getQuestions().get(2).optionB());
        assertEquals("B", result.getQuestions().get(2).correctOption());
        assertEquals("EASY", result.getQuestions().get(2).difficulty());
        assertEquals("HARD", result.getQuestions().get(3).difficulty());
    }

    @Test
    void matchAnswerToOptionsRules() {
        List<String> options = List.of("Stack", "Queue", "Binary tree", "Hash map");
        assertEquals("B", QuestionFileParser.matchAnswerToOptions("queue", options));
        assertEquals("B", QuestionFileParser.matchAnswerToOptions("Queue - first in, first out", options));
        assertNull(QuestionFileParser.matchAnswerToOptions("Linked list", options));
        assertNull(QuestionFileParser.matchAnswerToOptions("", options));
        assertNull(QuestionFileParser.matchAnswerToOptions(null, options));
        // An exact match wins, but a value that fits two options is not guessed.
        assertEquals("A", QuestionFileParser.matchAnswerToOptions("Tree", List.of("Tree", "Tree house")));
        assertNull(QuestionFileParser.matchAnswerToOptions("Tree house", List.of("Tree", "Tree house")));
    }
}
