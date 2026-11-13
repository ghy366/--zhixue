package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** AI 助手单条消息：payload 直接存整条 Msg 的 JSON（含文本、图片 data URL、meta 等），避免拆字段。 */
@Entity
@Table(name = "ai_chat_messages")
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** LONGTEXT：图片压缩后的 data URL 可能上百 KB，TEXT(64KB) 不够 */
    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiChatMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
