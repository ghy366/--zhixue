package com.zhixue.repository;

import com.zhixue.entity.AnnouncementRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, Long> {
    boolean existsByAnnouncementIdAndUserId(Long announcementId, Long userId);
    long countByAnnouncementId(Long announcementId);
    void deleteByAnnouncementId(Long announcementId);
}
