package com.quizmaster.repo;

import com.quizmaster.model.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Reads and writes the {@code results} table. */
public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByStudentIdOrderByIdDesc(Long studentId);

    List<Result> findByQuizIdOrderByIdDesc(Long quizId);

    Optional<Result> findFirstByQuizIdAndStudentIdOrderByIdDesc(Long quizId, Long studentId);

    /** Every submission for every test this teacher created - the "View Student Submissions" screen. */
    @Query("""
            select r from Result r
            where r.quiz.createdBy.id = :teacherId
            order by r.id desc
            """)
    List<Result> findForTeacher(@Param("teacherId") Long teacherId);

    @Query("""
            select r from Result r
            where r.quiz.createdBy.id = :teacherId and r.quiz.id = :quizId
            order by r.id desc
            """)
    List<Result> findForTeacherAndQuiz(@Param("teacherId") Long teacherId, @Param("quizId") Long quizId);

    long countByQuizId(Long quizId);

    @Query("select count(r) from Result r where r.quiz.createdBy.id = :teacherId")
    long countForTeacher(@Param("teacherId") Long teacherId);
}
