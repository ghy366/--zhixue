package com.zhixue.repository;

import com.zhixue.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, String> {
    List<AiChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
