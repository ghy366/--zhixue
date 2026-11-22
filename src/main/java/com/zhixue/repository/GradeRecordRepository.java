package com.zhixue.repository;

import com.zhixue.entity.GradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeRecordRepository extends JpaRepository<GradeRecord, Long> {

    List<GradeRecord> findByStudentIdAndCourseIdOrderByAttemptNoAsc(Long studentId, Long courseId);

    Optional<GradeRecord> findFirstByStudentIdAndCourseIdOrderByAttemptNoDesc(Long studentId, Long courseId);

    Optional<GradeRecord> findByStudentIdAndCourseIdAndAttemptNo(Long studentId, Long courseId, Integer attemptNo);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    boolean existsByStudentIdAndCourseIdAndTotalScoreGreaterThanEqual(Long studentId, Long courseId, Double score);

    long countByStudentIdAndCourseId(Long studentId, Long courseId);

    List<GradeRecord> findByStudentIdOrderByRecordedAtDesc(Long studentId);

    List<GradeRecord> findByStudentIdOrderByCourseIdAscAttemptNoAsc(Long studentId);
}
