package com.zhixue.repository;

import com.zhixue.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentIdAndRound(Long studentId, Integer round);
    List<Enrollment> findByCourseIdAndRound(Long courseId, Integer round);
    Optional<Enrollment> findByStudentIdAndCourseIdAndRound(Long studentId, Long courseId, Integer round);
    List<Enrollment> findByStudentId(Long studentId);

    /** 一次性取出某轮全部报名记录，供结算统一分配（避免逐门课查询产生 N+1） */
    List<Enrollment> findByRound(Integer round);

    /** 是否还有未结算的报名记录，供定时任务快速短路 */
    boolean existsByRoundAndStatus(Integer round, String status);
}
