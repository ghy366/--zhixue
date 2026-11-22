package com.zhixue.repository;

import com.zhixue.entity.MakeupExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MakeupExamRepository extends JpaRepository<MakeupExam, Long> {

    List<MakeupExam> findByStudentIdAndCourseIdOrderByIdAsc(Long studentId, Long courseId);

    Optional<MakeupExam> findFirstByStudentIdAndCourseIdOrderByIdDesc(Long studentId, Long courseId);

    List<MakeupExam> findByStudentIdOrderByIdDesc(Long studentId);

    List<MakeupExam> findByStatusInOrderByIdAsc(Collection<String> statuses);

    List<MakeupExam> findBySemesterNoOrderByIdAsc(Integer semesterNo);

    long countByStudentIdAndCourseId(Long studentId, Long courseId);
}
