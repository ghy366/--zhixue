package com.zhixue.repository;

import com.zhixue.entity.GraduateTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GraduateTopicRepository extends JpaRepository<GraduateTopic, Long> {
    List<GraduateTopic> findByTeacherIdOrderByIdDesc(Long teacherId);
    List<GraduateTopic> findByStatusOrderByIdDesc(String status);
}
