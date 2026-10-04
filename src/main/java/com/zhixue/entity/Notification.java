package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 站内消息（候补递补 / 审批结果 / 系统通知） */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 30)
    private String type;            // waitlist_confirm / waitlist_timeout / enroll / system

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String content;

    @Column(name = "ref_id")
    private Long refId;             // 关联对象 id（候补记录 / 课程）

    @Column(name = "read_flag")
    private Boolean readFlag;       // 是否已读

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Notification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public Boolean getReadFlag() { return readFlag; }
    public void setReadFlag(Boolean readFlag) { this.readFlag = readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
