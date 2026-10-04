package com.zhixue.repository;

import com.zhixue.entity.CourseIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseIntentRepository extends JpaRepository<CourseIntent, Long> {
    List<CourseIntent> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<CourseIntent> findByStudentIdOrderByCreatedAtAsc(Long studentId);
    Optional<CourseIntent> findByStudentIdAndCourseId(Long studentId, Long courseId);
    List<CourseIntent> findByCourseId(Long courseId);
    long countByStudentId(Long studentId);
}
