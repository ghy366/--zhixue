package com.zhixue.repository;

import com.zhixue.entity.RetakeApply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RetakeApplyRepository extends JpaRepository<RetakeApply, Long> {

    List<RetakeApply> findByStudentIdAndCourseIdOrderByIdAsc(Long studentId, Long courseId);

    Optional<RetakeApply> findFirstByStudentIdAndCourseIdOrderByIdDesc(Long studentId, Long courseId);

    List<RetakeApply> findByStudentIdOrderByIdDesc(Long studentId);

    List<RetakeApply> findByStatusInOrderByIdAsc(Collection<String> statuses);

    List<RetakeApply> findBySemesterNoOrderByIdAsc(Integer semesterNo);

    long countByStudentIdAndCourseId(Long studentId, Long courseId);

    long countByStudentIdAndCourseIdAndStatusIn(Long studentId, Long courseId, Collection<String> statuses);

    List<RetakeApply> findByTeachingClassIdOrderByIdAsc(Long teachingClassId);

    long countByTeachingClassId(Long teachingClassId);
}
