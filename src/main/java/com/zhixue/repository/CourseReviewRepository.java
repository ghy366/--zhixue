package com.zhixue.repository;

import com.zhixue.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {
    List<CourseReview> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<CourseReview> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    Optional<CourseReview> findByStudentIdAndCourseId(Long studentId, Long courseId);
    List<CourseReview> findByCourseIdIn(Collection<Long> courseIds);
    long countByCourseId(Long courseId);
}
