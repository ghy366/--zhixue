package com.zhixue.repository;

import com.zhixue.entity.GraduatePaper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface GraduatePaperRepository extends JpaRepository<GraduatePaper, Long> {
    List<GraduatePaper> findByAdvisorIdOrderByVersionNoDesc(Long advisorId);

    @Query("SELECT COALESCE(MAX(p.versionNo), 0) FROM GraduatePaper p WHERE p.advisorId = :advisorId")
    Integer findMaxVersionByAdvisorId(Long advisorId);
}
