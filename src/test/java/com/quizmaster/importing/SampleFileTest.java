package com.quizmaster.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bundled {@code samples/sample-questions.txt} is both the file a teacher
 * downloads to see the expected format and the source of the demo test the
 * seeder builds. Its exact content is pinned here: if the file is edited, the
 * questions a visitor sees on a fresh deployment have to be checked on purpose.
 */
class SampleFileTest {

    private final QuestionFileParser parser = new QuestionFileParser();

    private ImportResult parseSample() throws Exception {
        try (InputStream stream = new ClassPathResource("samples/sample-questions.txt").getInputStream()) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return parser.parse(text, "sample-questions.txt", "Text file");
        }
    }

    @Test
    @DisplayName("the bundled sample file is readable from the jar and imports cleanly")
    void sampleFileImportsWithoutSkip() throws Exception {
        ImportResult result = parseSample();

        assertEquals(5, result.getQuestions().size());
        assertEquals(5, result.getQuestionsFound());
        assertEquals(0, result.getSkippedCount());
        assertEquals(5, result.getImportableCount());
    }

    @Test
    @DisplayName("the sample questions keep their text, answers, topics and difficulties")
    void sampleQuestionContent() throws Exception {
        List<ParsedQuestion> questions = parseSample().getQuestions();

        ParsedQuestion capital = questions.get(0);
        assertEquals("What is the capital of France?", capital.questionText());
        assertEquals(List.of("Berlin", "Paris", "Madrid", "Rome"),
                List.of(capital.optionA(), capital.optionB(), capital.optionC(), capital.optionD()));
        assertEquals("B", capital.correctOption());
        assertEquals("Geography", capital.topic());
        assertEquals("EASY", capital.difficulty());

        ParsedQuestion constant = questions.get(1);
        assertEquals("final", constant.optionB());
        assertEquals("B", constant.correctOption());
        assertEquals("Java Basics", constant.topic());
        assertEquals("MEDIUM", constant.difficulty());

        ParsedQuestion queue = questions.get(2);
        assertEquals("B", queue.correctOption(),
                "\"Answer: Queue\" refers to option B by its text");
        assertEquals("Queue", queue.optionText("B"));
        assertEquals("Data Structures", queue.topic());

        ParsedQuestion binarySearch = questions.get(3);
        assertEquals("C", binarySearch.correctOption());
        assertEquals("Algorithms", binarySearch.topic());
        assertEquals("HARD", binarySearch.difficulty(), "\"Hard (level 3)\" maps to HARD");

        ParsedQuestion primitive = questions.get(4);
        assertEquals("C", primitive.correctOption());
        assertEquals("String", primitive.optionText("C"));
        assertEquals("Java Basics", primitive.topic());
    }
}
