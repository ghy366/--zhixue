package com.zhixue.repository;

import com.zhixue.entity.GraduateMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GraduateMilestoneRepository extends JpaRepository<GraduateMilestone, Long> {
    List<GraduateMilestone> findByAdvisorId(Long advisorId);
    long countByAdvisorIdAndStatus(Long advisorId, String status);
}
