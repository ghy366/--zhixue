package com.zhixue.repository;

import com.zhixue.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop30ByUserIdOrderByIdDesc(Long userId);
    List<Notification> findByUserIdAndReadFlagFalse(Long userId);
    long countByUserIdAndReadFlagFalse(Long userId);
}
