package com.zhixue.repository;

import com.zhixue.entity.GraduateTopicProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GraduateTopicProposalRepository extends JpaRepository<GraduateTopicProposal, Long> {

    List<GraduateTopicProposal> findByStudentIdOrderByIdDesc(Long studentId);

    List<GraduateTopicProposal> findByTeacherIdOrderByIdDesc(Long teacherId);

    List<GraduateTopicProposal> findByTeacherIdAndStatusOrderByIdDesc(Long teacherId, String status);

    /** 学生同时只能有一条待审核的自选课题 */
    Optional<GraduateTopicProposal> findByStudentIdAndStatus(Long studentId, String status);
}
