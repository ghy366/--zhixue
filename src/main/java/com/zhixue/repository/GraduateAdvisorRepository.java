package com.zhixue.repository;

import com.zhixue.entity.GraduateAdvisor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GraduateAdvisorRepository extends JpaRepository<GraduateAdvisor, Long> {
    List<GraduateAdvisor> findByStudentIdOrderByIdDesc(Long studentId);
    List<GraduateAdvisor> findByTeacherIdOrderByIdDesc(Long teacherId);
    List<GraduateAdvisor> findByTeacherIdAndStatusOrderByIdDesc(Long teacherId, String status);
    Optional<GraduateAdvisor> findByStudentIdAndTeacherIdAndStatusIn(Long studentId, Long teacherId, List<String> statuses);
    Optional<GraduateAdvisor> findByStudentIdAndStatus(Long studentId, String status);
    long countByTeacherIdAndStatus(Long teacherId, String status);
    long countByTeacherId(Long teacherId);
    long countByStatus(String status);
}
