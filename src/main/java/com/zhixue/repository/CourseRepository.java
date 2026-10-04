package com.zhixue.repository;

import com.zhixue.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacherId(Long teacherId);
    List<Course> findByCategory(String category);
    List<Course> findByNameContaining(String name);
}
