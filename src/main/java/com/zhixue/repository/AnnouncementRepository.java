package com.zhixue.repository;

import com.zhixue.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findAllByOrderByPinnedDescCreatedAtDesc();
    List<Announcement> findByTypeOrderByCreatedAtDesc(String type);
}
