package com.quizmaster.repo;

import com.quizmaster.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Reads and writes the {@code tests} table. */
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCreatedByIdOrderByIdDesc(Long teacherId);

    long countByCreatedById(Long teacherId);

    /** A test the student may see: public, or belonging to a class they are enrolled in. */
    @Query("""
            select q from Quiz q
            where q.publicTest = true
               or q.classRoom.id in (
                    select e.classRoom.id from Enrollment e where e.student.id = :studentId
               )
            order by q.id desc
            """)
    List<Quiz> findAvailableForStudent(@Param("studentId") Long studentId);

    @Query("select q from Quiz q where q.id = :id and q.createdBy.id = :teacherId")
    Optional<Quiz> findOwnedBy(@Param("id") Long id, @Param("teacherId") Long teacherId);
}
