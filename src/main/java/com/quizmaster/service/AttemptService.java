package com.quizmaster.service;

import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.Result;
import com.quizmaster.model.User;
import com.quizmaster.repo.ResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Starting a test, grading it and recording the attempt. */
@Service
public class AttemptService {

    private final ResultRepository resultRepository;

    public AttemptService(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /** The result of grading an attempt, including anything that went wrong while saving. */
    public record GradedAttempt(Result result, int score, int total, int overtimeSeconds,
                                boolean autoSubmitted, String saveWarning) {
    }

    /** Starts a fresh attempt, or resumes the one already in progress for this test. */
    public AttemptSession startOrResume(AttemptSession existing, Long quizId) {
        if (existing != null && quizId != null && quizId.equals(existing.getQuizId()) && !existing.isGraded()) {
            return existing;
        }
        return new AttemptSession(quizId, System.currentTimeMillis());
    }

    /** Counts the answers that match the key. */
    public int score(List<Question> questions, Map<Long, String> answers) {
        int score = 0;
        for (Question question : questions) {
            if (question.isCorrect(answers.get(question.getId()))) {
                score++;
            }
        }
        return score;
    }

    /**
     * Grades the attempt and records it.
     *
     * <p>If the row cannot be written, the student still sees their score but
     * with a warning - the desktop version printed a normal result screen for an
     * attempt that was never saved, so the teacher never saw it.
     *
     * @param autoSubmitted true when the countdown ran out and the test was submitted for the student
     */
    @Transactional
    public GradedAttempt submit(User student, Quiz quiz, AttemptSession attempt,
                                List<Question> questions, boolean autoSubmitted) {
        int total = questions.size();
        int score = score(questions, attempt.getAnswers());
        int overtime = attempt.overtimeSecondsFor(quiz.getTotalTimeSeconds(), quiz.allowsOvertime());

        String warning = null;
        Result saved = null;
        try {
            saved = resultRepository.save(new Result(quiz, student, score, total, overtime));
        } catch (RuntimeException ex) {
            warning = "Warning: your score could not be saved (" + ex.getMessage()
                    + "). Please tell your teacher.";
        }

        attempt.markGraded(score, total, overtime, autoSubmitted);
        return new GradedAttempt(saved, score, total, overtime, autoSubmitted, warning);
    }

    @Transactional(readOnly = true)
    public List<Result> resultsOf(User student) {
        return resultRepository.findByStudentIdOrderByIdDesc(student.getId());
    }

    @Transactional(readOnly = true)
    public List<Result> submissionsFor(User teacher) {
        return resultRepository.findForTeacher(teacher.getId());
    }

    @Transactional(readOnly = true)
    public List<Result> submissionsFor(User teacher, Long quizId) {
        if (quizId == null) {
            return submissionsFor(teacher);
        }
        return resultRepository.findForTeacherAndQuiz(teacher.getId(), quizId);
    }

    @Transactional(readOnly = true)
    public Result latestResultFor(Long quizId, Long studentId) {
        return resultRepository.findFirstByQuizIdAndStudentIdOrderByIdDesc(quizId, studentId).orElse(null);
    }

    @Transactional(readOnly = true)
    public long totalSubmissionsFor(User teacher) {
        return resultRepository.countForTeacher(teacher.getId());
    }
}
