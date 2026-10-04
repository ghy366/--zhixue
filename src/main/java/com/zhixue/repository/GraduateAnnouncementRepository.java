package com.zhixue.repository;

import com.zhixue.entity.GraduateAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GraduateAnnouncementRepository extends JpaRepository<GraduateAnnouncement, Long> {
    List<GraduateAnnouncement> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}
