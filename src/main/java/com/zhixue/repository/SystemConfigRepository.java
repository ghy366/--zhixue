package com.zhixue.repository;

import com.zhixue.entity.SystemConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /**
     * 锁定系统配置行，用作结算的全局互斥锁：
     * 手动结算与定时结算并发时，只有一个事务能进入结算逻辑。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SystemConfig c where c.id = :id")
    Optional<SystemConfig> findByIdForUpdate(@Param("id") Long id);
}
