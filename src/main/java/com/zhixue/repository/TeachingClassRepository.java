package com.zhixue.repository;

import com.zhixue.entity.TeachingClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeachingClassRepository extends JpaRepository<TeachingClass, Long> {

    List<TeachingClass> findAllByOrderByIdDesc();

    List<TeachingClass> findByCourseIdOrderByIdDesc(Long courseId);

    /** 某课程某状态的平行班（按班号升序）：成绩录入按平行班切分名单用 */
    List<TeachingClass> findByCourseIdAndStatusOrderByClassNoAsc(Long courseId, String status);

    List<TeachingClass> findByTeacherIdOrderByIdDesc(Long teacherId);

    Optional<TeachingClass> findFirstByCourseIdAndSemesterNoAndStatusOrderByIdDesc(
            Long courseId, Integer semesterNo, String status);

    Optional<TeachingClass> findByClassNo(String classNo);

    boolean existsByClassNo(String classNo);
}
