package com.zhixue.repository;

import com.zhixue.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
    List<AiChatMessage> findBySessionIdOrderByIdAsc(String sessionId);
    long countBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
}
