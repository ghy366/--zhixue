package com.zhixue.repository;

import com.zhixue.entity.GraduateRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GraduateRequirementRepository extends JpaRepository<GraduateRequirement, Long> {
    List<GraduateRequirement> findByAdvisorIdOrderByCreatedAtDesc(Long advisorId);
    GraduateRequirement findFirstByAdvisorIdOrderByCreatedAtDesc(Long advisorId);
}
