package com.zhixue.repository;

import com.zhixue.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    List<OperationLog> findAllByOrderByCreatedAtDesc();

    @Query("SELECT o.type, COUNT(o) FROM OperationLog o GROUP BY o.type")
    List<Object[]> countByType();

    @Query("SELECT o.status, COUNT(o) FROM OperationLog o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query("SELECT o.userName, COUNT(o) as c FROM OperationLog o GROUP BY o.userName ORDER BY c DESC")
    List<Object[]> countByUser();

    List<OperationLog> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime start, LocalDateTime end);
}
