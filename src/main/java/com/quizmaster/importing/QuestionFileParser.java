package com.quizmaster.importing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the documented question-file format:
 *
 * <pre>
 * Q: &lt;question text&gt;
 * A) &lt;option A&gt;
 * B) &lt;option B&gt;
 * C) &lt;option C&gt;
 * D) &lt;option D&gt;
 * Answer: &lt;A, B, C, or D&gt;
 * Topic: &lt;topic text&gt;             (optional, defaults to General)
 * Difficulty: &lt;EASY|MEDIUM|HARD&gt;  (optional, defaults to MEDIUM, synonyms accepted)
 * </pre>
 *
 * <p>The rules the desktop application documented, all preserved here:
 * <ul>
 *   <li>Order is preserved - questions are returned in file order.</li>
 *   <li>Only a {@code Q:} line separates questions; blank lines are conventional
 *       but not required, because text extracted from a PDF does not keep them.</li>
 *   <li>Wrapped lines are joined onto the field above them.</li>
 *   <li>A whole question on one line is expanded into its fields, but only when
 *       the line holds all four option markers in order and the first is not at
 *       the start of the line - or the line also carries a keyword label.</li>
 *   <li>An answer may be written out instead of as a letter; when two options
 *       fit, nothing is guessed and the block is reported.</li>
 *   <li>Extraction artefacts are removed: page breaks, non-breaking spaces, the
 *       byte order mark and the Unicode line/paragraph separators.</li>
 *   <li>Blocks with no question text, a missing option, an option over 500
 *       characters or a missing/unreadable answer are skipped and listed with
 *       the line number they came from.</li>
 *   <li>Anything before the first {@code Q:} line is a heading and is ignored.</li>
 *   <li>At most 200 questions per file; the rest are reported as skipped.</li>
 * </ul>
 *
 * <p>No input can make {@link #parse(String)} throw.
 */
public class QuestionFileParser {

    /** Maximum number of questions imported from one file. */
    public static final int MAX_QUESTIONS = 200;

    private static final int OPTION_MAX_LENGTH = 500;
    private static final int QUESTION_TEXT_MAX_LENGTH = 2000;

    /** A question starts here: {@code Q:}, {@code Q1:}, {@code Question 2:}, {@code Q3.}, {@code Question-4}. */
    private static final Pattern QUESTION_LABEL = Pattern.compile(
            "^(?i)(?:question|ques|q)\\s*\\d*\\s*[.:)\\-]\\s*(.*)$");
    /** An option: {@code A)}, {@code A.}, {@code A:}, {@code (A)}. A dash is deliberately not a delimiter. */
    private static final Pattern OPTION_LABEL = Pattern.compile(
            "^(?i)\\(?([a-d])\\)?\\s*[.:)]\\s*(.*)$");
    private static final Pattern ANSWER_LABEL = Pattern.compile(
            "^(?i)(?:correct\\s+answer|answer|ans|correct)\\s*[.:\\-]?\\s*(.*)$");
    private static final Pattern TOPIC_LABEL = Pattern.compile(
            "^(?i)(?:topic|subject)\\s*[.:\\-]?\\s*(.*)$");
    private static final Pattern DIFFICULTY_LABEL = Pattern.compile(
            "^(?i)(?:difficulty|level)\\s*[.:\\-]?\\s*(.*)$");

    /**
     * Every token that begins a new field when it shares a line with other
     * content: the four option markers and the keyword labels. Used both to
     * decide whether a line needs expanding and to actually break it up.
     */
    private static final Pattern INLINE_TOKEN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])"
                    + "(?:\\(?[A-Da-d][.:)]"
                    + "|(?:correct\\s+answer|answer|ans|correct|topic|subject|difficulty|level)\\s*[.:\\-]"
                    + "|(?:question|ques|q)\\s*\\d*\\s*[.:)\\-])");

    private static final Pattern WHOLE_LETTER = Pattern.compile("^\\(?\\[?([A-Da-d])\\)?\\]?$");
    private static final Pattern LEADING_LETTER = Pattern.compile(
            "^\\(?([A-Da-d])\\)?\\s*(?:[.):]|[\u2013\u2014-])\\s*\\S");
    private static final Pattern PHRASE_LETTER = Pattern.compile(
            "(?i)(?:answer|option|choice|correct)\\s*(?:is|:|=|-)?\\s*\\(?([A-Da-d])\\)?(?![A-Za-z])");

    /** Which field a continuation line belongs to. */
    private enum Field { NONE, QUESTION, OPTION_A, OPTION_B, OPTION_C, OPTION_D, TOPIC, DIFFICULTY, ANSWER }

    /** Parses already-extracted text. */
    public ImportResult parse(String rawText) {
        return parse(rawText, "questions", "");
    }

    /** Parses already-extracted text, tagging the result with the upload's name and type. */
    public ImportResult parse(String rawText, String fileName, String fileType) {
        List<ParsedQuestion> questions = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        try {
            if (rawText != null && !rawText.isBlank()) {
                parseInto(rawText, questions, failures);
            }
        } catch (RuntimeException ex) {
            // A parser must never blow up on an odd file; report it instead.
            failures.add(new ParseFailure(0, "The file could not be parsed: " + ex.getMessage(), ""));
        }
        if (questions.isEmpty() && failures.isEmpty() && rawText != null && !rawText.isBlank()) {
            failures.add(new ParseFailure(1,
                    "No question found. Start each question with a line like \"Q: <question text>\".",
                    ""));
        }
        return new ImportResult(fileName, fileType, questions, failures);
    }

    private void parseInto(String rawText, List<ParsedQuestion> questions, List<ParseFailure> failures) {
        String[] lines = normalise(rawText).split("\n", -1);
        Draft draft = null;

        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }

            for (String piece : expandLine(line)) {
                String text = piece.trim();
                if (text.isEmpty()) {
                    continue;
                }

                Matcher question = QUESTION_LABEL.matcher(text);
                if (question.matches()) {
                    if (draft != null) {
                        flush(draft, questions, failures);
                    }
                    draft = new Draft(lineNumber);
                    draft.append(text.substring(question.start(1)), Field.QUESTION);
                    continue;
                }

                Matcher option = OPTION_LABEL.matcher(text);
                if (option.matches()) {
                    if (draft == null) {
                        continue; // heading material before the first Q: line
                    }
                    draft.append(option.group(2), optionField(option.group(1)));
                    continue;
                }

                Matcher answer = ANSWER_LABEL.matcher(text);
                if (answer.matches()) {
                    if (draft == null) {
                        continue;
                    }
                    draft.append(answer.group(1), Field.ANSWER);
                    continue;
                }

                Matcher topic = TOPIC_LABEL.matcher(text);
                if (topic.matches()) {
                    if (draft == null) {
                        continue;
                    }
                    draft.append(topic.group(1), Field.TOPIC);
                    continue;
                }

                Matcher difficulty = DIFFICULTY_LABEL.matcher(text);
                if (difficulty.matches()) {
                    if (draft == null) {
                        continue;
                    }
                    draft.append(difficulty.group(1), Field.DIFFICULTY);
                    continue;
                }

                if (draft != null) {
                    // A line that is not a keyword continues the field above it.
                    draft.appendContinuation(text);
                }
            }
        }

        if (draft != null) {
            flush(draft, questions, failures);
        }
    }

    // ---------------------------------------------------------------------
    //  Inline expansion
    // ---------------------------------------------------------------------

    /**
     * Expands a line that holds a whole question, or whose options and answer
     * share a line, into one piece per field.
     *
     * <p>Splitting happens only when all four option markers appear in order -
     * and either the first marker is not at the start of the line or the line
     * also carries a keyword label - so an ordinary one-field-per-line file,
     * including one whose question text mentions "(B)", is passed through
     * untouched.
     */
    List<String> expandLine(String line) {
        if (!needsExpansion(line)) {
            return List.of(line);
        }
        return List.of(INLINE_TOKEN.matcher(line).replaceAll("\n$0").split("\n"));
    }

    private boolean needsExpansion(String line) {
        int[] positions = optionMarkerPositions(line);
        if (positions == null) {
            return false;
        }
        if (positions[0] > 0) {
            return true;
        }
        // The line starts with "A)" - only expand it when it also carries a keyword label.
        return INLINE_TOKEN.matcher(line).results()
                .anyMatch(match -> !isOptionMarker(match.group()));
    }

    private static boolean isOptionMarker(String token) {
        String trimmed = token.trim();
        String withoutParen = trimmed.startsWith("(") ? trimmed.substring(1) : trimmed;
        return withoutParen.length() >= 2
                && Character.isLetter(withoutParen.charAt(0))
                && "ABCDabcd".indexOf(withoutParen.charAt(0)) >= 0
                && ".:)".indexOf(withoutParen.charAt(1)) >= 0;
    }

    /**
     * Positions of the {@code A)}, {@code B)}, {@code C)} and {@code D)} markers,
     * or {@code null} when the line does not hold all four in order with content
     * after the first three.
     */
    private int[] optionMarkerPositions(String line) {
        int[] positions = new int[4];
        for (int letter = 0; letter < 4; letter++) {
            int position = findMarker(line, (char) ('A' + letter));
            if (position < 0) {
                return null;
            }
            positions[letter] = position;
            if (letter < 3 && !hasContentAfter(line, position)) {
                return null;
            }
        }
        for (int letter = 1; letter < 4; letter++) {
            if (positions[letter] <= positions[letter - 1]) {
                return null; // the markers must appear in order
            }
        }
        return positions;
    }

    /** Finds a standalone {@code A)}, {@code A.}, {@code A:} or {@code (A)} marker, ignoring "MADRID)". */
    private int findMarker(String line, char letter) {
        Pattern pattern = Pattern.compile("(?i)(?:^|[\\s(])\\(?" + letter + "[.:)]");
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            int letterIndex = matcher.end() - 2;
            int before = letterIndex - 1;
            if (before >= 0 && line.charAt(before) == '(') {
                return before; // "(A)" is a valid marker form
            }
            if (before < 0 || !Character.isLetterOrDigit(line.charAt(before))) {
                return letterIndex;
            }
        }
        return -1;
    }

    private boolean hasContentAfter(String line, int markerPosition) {
        int from = Math.min(markerPosition + 2, line.length());
        return !line.substring(from).trim().isEmpty();
    }

    // ---------------------------------------------------------------------
    //  Flushing and validation
    // ---------------------------------------------------------------------

    private void flush(Draft draft, List<ParsedQuestion> questions, List<ParseFailure> failures) {
        String questionText = draft.field(Field.QUESTION).trim();
        String optionA = draft.field(Field.OPTION_A).trim();
        String optionB = draft.field(Field.OPTION_B).trim();
        String optionC = draft.field(Field.OPTION_C).trim();
        String optionD = draft.field(Field.OPTION_D).trim();
        String answerRaw = draft.field(Field.ANSWER).trim();
        String preview = draft.preview();

        if (questionText.isEmpty()) {
            failures.add(new ParseFailure(draft.lineNumber, "The question text is missing.", preview));
            return;
        }
        if (questionText.length() > QUESTION_TEXT_MAX_LENGTH) {
            failures.add(new ParseFailure(draft.lineNumber,
                    "The question text is longer than " + QUESTION_TEXT_MAX_LENGTH + " characters.", preview));
            return;
        }
        String missing = firstMissingOption(optionA, optionB, optionC, optionD);
        if (missing != null) {
            failures.add(new ParseFailure(draft.lineNumber, "Missing option " + missing + ".", preview));
            return;
        }
        String tooLong = firstOverlongOption(optionA, optionB, optionC, optionD);
        if (tooLong != null) {
            failures.add(new ParseFailure(draft.lineNumber,
                    "Option " + tooLong + " is longer than " + OPTION_MAX_LENGTH + " characters.", preview));
            return;
        }

        String letter = resolveAnswer(answerRaw, List.of(optionA, optionB, optionC, optionD));
        if (letter == null) {
            String reason = answerRaw.isEmpty()
                    ? "The answer is missing."
                    : "The answer \"" + shorten(answerRaw) + "\" does not match exactly one of the options.";
            failures.add(new ParseFailure(draft.lineNumber, reason, preview));
            return;
        }

        if (questions.size() >= MAX_QUESTIONS) {
            failures.add(new ParseFailure(draft.lineNumber,
                    "Only the first " + MAX_QUESTIONS + " questions of a file are imported.", preview));
            return;
        }

        questions.add(new ParsedQuestion(
                draft.lineNumber,
                questionText,
                optionA, optionB, optionC, optionD,
                letter,
                defaultIfBlank(draft.field(Field.TOPIC), "General"),
                normaliseDifficulty(draft.field(Field.DIFFICULTY))));
    }

    private String firstMissingOption(String a, String b, String c, String d) {
        if (a.isEmpty()) {
            return "A";
        }
        if (b.isEmpty()) {
            return "B";
        }
        if (c.isEmpty()) {
            return "C";
        }
        if (d.isEmpty()) {
            return "D";
        }
        return null;
    }

    private String firstOverlongOption(String a, String b, String c, String d) {
        if (a.length() > OPTION_MAX_LENGTH) {
            return "A";
        }
        if (b.length() > OPTION_MAX_LENGTH) {
            return "B";
        }
        if (c.length() > OPTION_MAX_LENGTH) {
            return "C";
        }
        if (d.length() > OPTION_MAX_LENGTH) {
            return "D";
        }
        return null;
    }

    /** A letter answer, or an answer written out that matches exactly one option. */
    private String resolveAnswer(String answerRaw, List<String> optionTexts) {
        String letter = extractAnswerLetter(answerRaw);
        if (letter != null) {
            return letter;
        }
        return matchAnswerToOptions(answerRaw, optionTexts);
    }

    /**
     * Reads only a whole-value letter ("b", "(c)"), a leading marker ("B)",
     * "A - the first one") or an explicit phrase ("answer is B", "option D").
     *
     * <p>It deliberately does <em>not</em> take any single-letter token, so an
     * answer written as a sentence ("Answer: a good idea") is not read as
     * option A and turned into a wrong answer.
     */
    public static String extractAnswerLetter(String answerRaw) {
        if (answerRaw == null) {
            return null;
        }
        String value = answerRaw.trim();
        if (value.isEmpty()) {
            return null;
        }
        Matcher whole = WHOLE_LETTER.matcher(value);
        if (whole.matches()) {
            return whole.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher leading = LEADING_LETTER.matcher(value);
        if (leading.find()) {
            return leading.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher phrase = PHRASE_LETTER.matcher(value);
        if (phrase.find()) {
            return phrase.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Matches an answer written as text against the option texts, so
     * "Answer: Paris" and "Answer: Pacific Ocean - it covers ..." both work.
     * When two options fit, nothing is guessed: {@code null} is returned and the
     * block is reported.
     */
    public static String matchAnswerToOptions(String answerRaw, List<String> optionTexts) {
        if (answerRaw == null || answerRaw.isBlank()) {
            return null;
        }
        String value = answerRaw.trim().toLowerCase(Locale.ROOT);
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < optionTexts.size(); index++) {
            String option = optionTexts.get(index) == null ? "" : optionTexts.get(index).trim().toLowerCase(Locale.ROOT);
            if (option.isEmpty()) {
                continue;
            }
            if (value.equals(option) || value.startsWith(option)) {
                if (!matches.contains(index)) {
                    matches.add(index);
                }
            }
        }
        if (matches.size() == 1) {
            return String.valueOf((char) ('A' + matches.get(0)));
        }
        return null;
    }

    /**
     * Difficulty synonyms map to the nearest level; a genuinely unrecognised
     * value keeps the documented MEDIUM fallback rather than costing the question.
     */
    public static String normaliseDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MEDIUM";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.contains("easy") || value.contains("beginner") || value.contains("simple")
                || value.contains("basic") || value.contains("level 0") || value.contains("level 1")) {
            return "EASY";
        }
        if (value.contains("hard") || value.contains("difficult") || value.contains("advanced")
                || value.contains("expert") || value.contains("tough") || value.contains("level 3")) {
            return "HARD";
        }
        if (value.contains("med") || value.contains("moderate") || value.contains("intermediate")
                || value.contains("level 2")) {
            return "MEDIUM";
        }
        return "MEDIUM";
    }

    /** Removes the artefacts of PDF/Word extraction. */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1); // the byte order mark a Windows editor adds
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\f', '\n')                       // where a PDF page ends
                .replace('\u2028', '\n')
                .replace('\u2029', '\n')
                .replace('\u00A0', ' ')                    // non-breaking space
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .replace("\u200B", "")                     // zero width space
                .replace("\uFEFF", "")
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"');
    }

    private static Field optionField(String letter) {
        return switch (letter.toUpperCase(Locale.ROOT)) {
            case "A" -> Field.OPTION_A;
            case "B" -> Field.OPTION_B;
            case "C" -> Field.OPTION_C;
            default -> Field.OPTION_D;
        };
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String shorten(String value) {
        String single = value.replaceAll("\\s+", " ").trim();
        return single.length() > 60 ? single.substring(0, 60) + "\u2026" : single;
    }

    /** The question being assembled. Fields are appended to as lines arrive. */
    private static final class Draft {
        private final int lineNumber;
        private final StringBuilder questionText = new StringBuilder();
        private final StringBuilder optionA = new StringBuilder();
        private final StringBuilder optionB = new StringBuilder();
        private final StringBuilder optionC = new StringBuilder();
        private final StringBuilder optionD = new StringBuilder();
        private final StringBuilder topic = new StringBuilder();
        private final StringBuilder difficulty = new StringBuilder();
        private final StringBuilder answer = new StringBuilder();
        private Field lastField = Field.NONE;

        Draft(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        void append(String text, Field field) {
            StringBuilder target = builderFor(field);
            if (target == null) {
                return;
            }
            String value = text == null ? "" : text.trim();
            if (target.length() > 0 && !value.isEmpty()) {
                target.append(' ');
            }
            target.append(value);
            lastField = field;
        }

        /** A line that is not a keyword continues the field above it (PDF wrapping). */
        void appendContinuation(String text) {
            if (lastField == Field.NONE) {
                return;
            }
            append(text, lastField);
        }

        String field(Field field) {
            StringBuilder builder = builderFor(field);
            return builder == null ? "" : builder.toString();
        }

        String preview() {
            return questionText.length() > 0 ? questionText.toString() : "";
        }

        private StringBuilder builderFor(Field field) {
            return switch (field) {
                case QUESTION -> questionText;
                case OPTION_A -> optionA;
                case OPTION_B -> optionB;
                case OPTION_C -> optionC;
                case OPTION_D -> optionD;
                case TOPIC -> topic;
                case DIFFICULTY -> difficulty;
                case ANSWER -> answer;
                case NONE -> null;
            };
        }
    }
}
