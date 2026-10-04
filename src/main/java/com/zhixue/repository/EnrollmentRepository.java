package com.zhixue.repository;

import com.zhixue.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentIdAndRound(Long studentId, Integer round);
    List<Enrollment> findByCourseIdAndRound(Long courseId, Integer round);
    Optional<Enrollment> findByStudentIdAndCourseIdAndRound(Long studentId, Long courseId, Integer round);
    List<Enrollment> findByStudentId(Long studentId);
}
