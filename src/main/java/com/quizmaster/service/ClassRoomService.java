package com.quizmaster.service;

import com.quizmaster.model.ClassRoom;
import com.quizmaster.model.Enrollment;
import com.quizmaster.model.User;
import com.quizmaster.repo.ClassRoomRepository;
import com.quizmaster.repo.EnrollmentRepository;
import com.quizmaster.validation.InputValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Classes (with their 6-character join codes) and enrolments. */
@Service
public class ClassRoomService {

    /** Unambiguous alphabet: no O/0, I/1/L, so a code read off a whiteboard cannot be mistyped. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = InputValidator.CLASS_CODE_LENGTH;
    private static final int MAX_CODE_ATTEMPTS = 40;

    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SecureRandom random = new SecureRandom();

    public ClassRoomService(ClassRoomRepository classRoomRepository, EnrollmentRepository enrollmentRepository) {
        this.classRoomRepository = classRoomRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** Creates a class and returns it with its freshly generated join code. */
    @Transactional
    public ServiceResult<ClassRoom> createClass(User teacher, String className) {
        String error = InputValidator.validateClassName(className);
        if (error != null) {
            return ServiceResult.failure(error);
        }
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (classRoomRepository.existsByClassCodeIgnoreCase(code)) {
                continue;
            }
            try {
                ClassRoom classRoom = classRoomRepository.save(new ClassRoom(teacher, className.trim(), code));
                return ServiceResult.success(classRoom);
            } catch (DataIntegrityViolationException ex) {
                // Someone else claimed the same code between the check and the insert.
            }
        }
        return ServiceResult.failure("Could not generate a unique class code. Please try again.");
    }

    @Transactional(readOnly = true)
    public List<ClassRoom> classesOf(User teacher) {
        return classRoomRepository.findByTeacherIdOrderByIdDesc(teacher.getId());
    }

    /** The classes a student has joined. */
    @Transactional(readOnly = true)
    public List<Enrollment> enrollmentsOf(User student) {
        return enrollmentRepository.findByStudentIdOrderByIdDesc(student.getId());
    }

    @Transactional(readOnly = true)
    public List<ClassRoom> joinedClassesOf(User student) {
        List<ClassRoom> classes = new ArrayList<>();
        for (Enrollment enrollment : enrollmentsOf(student)) {
            classes.add(enrollment.getClassRoom());
        }
        return classes;
    }

    @Transactional(readOnly = true)
    public long studentCount(ClassRoom classRoom) {
        return enrollmentRepository.countByClassRoomId(classRoom.getId());
    }

    @Transactional(readOnly = true)
    public List<Enrollment> studentsOf(ClassRoom classRoom) {
        return enrollmentRepository.findByClassRoomIdOrderByIdDesc(classRoom.getId());
    }

    /**
     * Joins a class by code. The unique key on (class_id, student_id) is what
     * actually prevents a duplicate enrolment, and its violation is mapped to
     * "already enrolled" rather than surfacing as a database error.
     */
    @Transactional
    public ServiceResult<ClassRoom> joinByCode(User student, String rawCode) {
        String error = InputValidator.validateClassCode(rawCode);
        if (error != null) {
            return ServiceResult.failure(error);
        }
        String code = InputValidator.normaliseClassCode(rawCode);

        ClassRoom classRoom = classRoomRepository.findByClassCodeIgnoreCase(code).orElse(null);
        if (classRoom == null) {
            return ServiceResult.failure("No class found with the code " + code + ". Please check it with your teacher.");
        }
        if (enrollmentRepository.existsByClassRoomIdAndStudentId(classRoom.getId(), student.getId())) {
            return ServiceResult.failure("You have already joined " + classRoom.getClassName() + ".");
        }
        try {
            enrollmentRepository.save(new Enrollment(classRoom, student));
        } catch (DataIntegrityViolationException ex) {
            return ServiceResult.failure("You have already joined " + classRoom.getClassName() + ".");
        }
        return ServiceResult.success("You joined " + classRoom.getClassName() + ".", classRoom);
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }
}
