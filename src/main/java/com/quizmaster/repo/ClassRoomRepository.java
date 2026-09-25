package com.quizmaster.repo;

import com.quizmaster.model.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Reads and writes the {@code classes} table. */
public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {

    List<ClassRoom> findByTeacherIdOrderByIdDesc(Long teacherId);

    Optional<ClassRoom> findByClassCodeIgnoreCase(String classCode);

    boolean existsByClassCodeIgnoreCase(String classCode);

    long countByTeacherId(Long teacherId);
}
