package com.quizmaster.service;

import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Question;
import com.quizmaster.model.Quiz;
import com.quizmaster.model.User;
import com.quizmaster.repo.ClassRoomRepository;
import com.quizmaster.repo.QuestionRepository;
import com.quizmaster.repo.QuizRepository;
import com.quizmaster.repo.ResultRepository;
import com.quizmaster.validation.InputValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Creating and reading tests. */
@Service
public class QuizService {

    /** The details collected on wizard step 1. */
    public record TestDetails(String title, String language, int totalTimeSeconds, String expiryAction,
                              boolean publicTest, Long classId) implements java.io.Serializable {
    }

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ResultRepository resultRepository;

    public QuizService(QuizRepository quizRepository, QuestionRepository questionRepository,
                       ClassRoomRepository classRoomRepository, ResultRepository resultRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.classRoomRepository = classRoomRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public List<Quiz> testsOf(User teacher) {
        return quizRepository.findByCreatedByIdOrderByIdDesc(teacher.getId());
    }

    @Transactional(readOnly = true)
    public List<Quiz> availableTo(User student) {
        return quizRepository.findAvailableForStudent(student.getId());
    }

    @Transactional(readOnly = true)
    public Quiz findVisibleTo(User user, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null || user == null) {
            return null;
        }
        if (quiz.isPublicTest() || quiz.getCreatedBy().getId().equals(user.getId())) {
            return quiz;
        }
        boolean enrolled = quiz.getClassRoom() != null
                && quizRepository.findAvailableForStudent(user.getId()).stream()
                .anyMatch(candidate -> candidate.getId().equals(quiz.getId()));
        return enrolled ? quiz : null;
    }

    @Transactional(readOnly = true)
    public Quiz findOwned(User teacher, Long quizId) {
        return quizRepository.findOwnedBy(quizId, teacher.getId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Question> questionsOf(Long quizId) {
        return questionRepository.findByQuizIdOrderByIdAsc(quizId);
    }

    @Transactional(readOnly = true)
    public long questionCount(Quiz quiz) {
        return questionRepository.countByQuizId(quiz.getId());
    }

    @Transactional(readOnly = true)
    public long submissionCount(Quiz quiz) {
        return resultRepository.countByQuizId(quiz.getId());
    }

    /** Validates the wizard's step-1 fields. */
    public String validate(TestDetails details) {
        return InputValidator.firstError(
                InputValidator.validateTestTitle(details.title()),
                InputValidator.validateLanguage(details.language()),
                InputValidator.validateExpiryAction(details.expiryAction()),
                details.totalTimeSeconds() <= 0 ? "Duration must be greater than zero." : null);
    }

    /**
     * Saves the test and every question on one transaction, so a failure cannot
     * leave an orphaned test with only some of its questions. Each question is
     * re-validated here rather than only on screen: a half-filled question must
     * never reach the database.
     */
    @Transactional
    public ServiceResult<Quiz> createTest(User teacher, TestDetails details, List<Question> questions) {
        String error = validate(details);
        if (error != null) {
            return ServiceResult.failure(error);
        }
        if (questions == null || questions.isEmpty()) {
            return ServiceResult.failure("A test needs at least one question.");
        }

        ClassRoom targetClass = null;
        if (details.classId() != null) {
            targetClass = classRoomRepository.findById(details.classId()).orElse(null);
            if (targetClass == null || !targetClass.getTeacher().getId().equals(teacher.getId())) {
                return ServiceResult.failure("That class does not exist, or is not one of your classes.");
            }
        }

        Quiz quiz = new Quiz(details.title().trim(), teacher);
        quiz.setLanguage(InputValidator.normaliseLanguage(details.language()));
        quiz.setTotalTimeSeconds(details.totalTimeSeconds());
        quiz.setExpiryAction(InputValidator.normaliseExpiryAction(details.expiryAction()));
        quiz.setClassRoom(targetClass);
        quiz.setPublicTest(targetClass == null && details.publicTest());

        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            String questionError = InputValidator.validateQuestion(question);
            if (questionError != null) {
                return ServiceResult.failure("Question " + (index + 1) + ": " + questionError);
            }
            InputValidator.normalise(question);
            question.setQuiz(quiz);
        }

        Quiz saved = quizRepository.save(quiz);
        questionRepository.saveAll(questions);
        return ServiceResult.success(saved);
    }
}
