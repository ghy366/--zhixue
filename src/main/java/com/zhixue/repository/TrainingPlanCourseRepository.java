package com.zhixue.repository;

import com.zhixue.entity.TrainingPlanCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TrainingPlanCourseRepository extends JpaRepository<TrainingPlanCourse, Long> {

    List<TrainingPlanCourse> findAllByOrderByMajorAscGradeAscSemesterNoAscIdAsc();

    List<TrainingPlanCourse> findByMajorOrderBySemesterNoAscIdAsc(String major);

    List<TrainingPlanCourse> findByMajorAndGradeOrderBySemesterNoAscIdAsc(String major, Integer grade);

    /** 取某专业的年级专属方案 + 通用方案（grade is null），由 Service 决定优先级 */
    @Query("select t from TrainingPlanCourse t where t.major = :major and (t.grade = :grade or t.grade is null) and t.enabled = true")
    List<TrainingPlanCourse> findApplicable(@Param("major") String major, @Param("grade") Integer grade);

    /** 取某专业的全部方案（用于批量排定：该专业下所有学生） */
    @Query("select t from TrainingPlanCourse t where t.major = :major and t.enabled = true")
    List<TrainingPlanCourse> findEnabledByMajor(@Param("major") String major);

    List<TrainingPlanCourse> findByEnabledTrue();

    void deleteByMajorAndGrade(String major, Integer grade);

    long countByEnabledTrue();
}
