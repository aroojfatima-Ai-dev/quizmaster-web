package com.quizmaster.repo;

import com.quizmaster.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Reads and writes the {@code questions} table. */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** File order and manual entry order are both the row order, as in the desktop app. */
    List<Question> findByQuizIdOrderByIdAsc(Long quizId);

    List<Question> findByQuizId(Long quizId);

    long countByQuizId(Long quizId);
}
