package com.quizmaster.repo;

import com.quizmaster.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Reads and writes the {@code enrollments} table. */
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudentIdOrderByIdDesc(Long studentId);

    List<Enrollment> findByClassRoomIdOrderByIdDesc(Long classRoomId);

    Optional<Enrollment> findByClassRoomIdAndStudentId(Long classRoomId, Long studentId);

    boolean existsByClassRoomIdAndStudentId(Long classRoomId, Long studentId);

    long countByClassRoomId(Long classRoomId);
}
