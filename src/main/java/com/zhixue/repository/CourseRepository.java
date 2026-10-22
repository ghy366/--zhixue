package com.zhixue.repository;

import com.zhixue.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacherId(Long teacherId);
    List<Course> findByCategory(String category);
    List<Course> findByNameContaining(String name);

    /**
     * 锁定课程行：第三轮“先到先得”在锁内统计已录取人数并插入，
     * 保证并发报名不会突破容量。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

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
