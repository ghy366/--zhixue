package com.zhixue.repository;

import com.zhixue.entity.GraduateGuidanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraduateGuidanceRecordRepository extends JpaRepository<GraduateGuidanceRecord, Long> {

    List<GraduateGuidanceRecord> findByAdvisorIdOrderByRecordDateDescIdDesc(Long advisorId);

    long countByAdvisorId(Long advisorId);
}
