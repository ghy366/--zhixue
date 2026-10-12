package com.zhixue.repository;

import com.zhixue.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacherId(Long teacherId);
    List<Course> findByCategory(String category);
    List<Course> findByNameContaining(String name);

    /** 按报名人数排序前 N 门课 */
    @org.springframework.data.jpa.repository.Query(
            nativeQuery = true,
            value = "SELECT c.*, COALESCE(s.cnt, 0) AS enroll_count "
                  + "FROM courses c LEFT JOIN ("
                  + "    SELECT course_id, COUNT(*) AS cnt FROM enrollments "
                  + "    WHERE status IN ('approved','pending') GROUP BY course_id"
                  + ") s ON s.course_id = c.id "
                  + "ORDER BY enroll_count DESC LIMIT :n")
    java.util.List<Course> findTopByEnrollmentCount(@Param("n") int n);
}
