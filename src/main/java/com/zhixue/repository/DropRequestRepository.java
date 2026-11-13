package com.zhixue.repository;

import com.zhixue.entity.DropRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DropRequestRepository extends JpaRepository<DropRequest, Long> {

    List<DropRequest> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    List<DropRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<DropRequest> findAllByOrderByCreatedAtDesc();

    Optional<DropRequest> findFirstByStudentIdAndCourseIdAndRoundNoAndStatusOrderByIdDesc(
            Long studentId, Long courseId, Integer roundNo, String status);

    /** 容量释放：统计并清理待处理申请，已处理的历史申请保留 */
    long countByStatus(String status);

    long deleteByStatus(String status);
}
