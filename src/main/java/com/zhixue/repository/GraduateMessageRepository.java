package com.zhixue.repository;

import com.zhixue.entity.GraduateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraduateMessageRepository extends JpaRepository<GraduateMessage, Long> {
    List<GraduateMessage> findByAdvisorIdOrderBySentAtAsc(Long advisorId);

    Long countByAdvisorIdAndReceiverIdAndReadAtIsNull(Long advisorId, Long receiverId);

    @Modifying
    @Transactional
    @Query("UPDATE GraduateMessage m SET m.readAt = CURRENT_TIMESTAMP WHERE m.advisorId = :aid AND m.receiverId = :rid AND m.readAt IS NULL")
    void markReadByAdvisorAndReceiver(@Param("aid") Long advisorId, @Param("rid") Long receiverId);
}
