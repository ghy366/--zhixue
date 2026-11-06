package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 毕业设计课题库：教师发布的可选课题。
 * <p>
 * 课题按教师所属学院自然分组（计算机学院课题库 / 通识教育中心课题库 …），
 * 学生在双选时可"申这个题"——带着课题标题向该导师提交志愿。
 * 状态：open 开放申领 / closed 已下架；若与某条已确认导师关系的课题名一致则视为已选定。
 */
@Entity
@Table(name = "graduate_topics")
public class GraduateTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 200)
    private String title;

    /** 研究方向标签（如：后端开发 / 人工智能 / 移动应用） */
    @Column(length = 60)
    private String direction;

    /** 课题描述与要求 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** open 开放申领 / closed 已下架 */
    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GraduateTopic() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
