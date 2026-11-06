package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 学生建议箱：学生向管理员提交功能建议 / 优化反馈，管理员回复处理 */
@Entity
@Table(name = "suggestions")
public class Suggestion {

    public static final String STATUS_PENDING = "PENDING";      // 待处理
    public static final String STATUS_ACCEPTED = "ACCEPTED";    // 已采纳
    public static final String STATUS_DONE = "DONE";            // 已完成
    public static final String STATUS_REJECTED = "REJECTED";    // 未采纳

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 提交时对姓名的快照，用户改名后历史记录仍可读 */
    @Column(name = "user_name", length = 50)
    private String userName;

    /** 功能建议 / 体验优化 / 问题反馈 / 其他 */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "admin_reply", length = 1000)
    private String adminReply;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public Suggestion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminReply() { return adminReply; }
    public void setAdminReply(String adminReply) { this.adminReply = adminReply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
}
