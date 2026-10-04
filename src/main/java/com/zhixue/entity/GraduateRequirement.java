package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "graduate_requirements")
public class GraduateRequirement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "advisor_id", nullable = false)
    private Long advisorId;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(name = "topics_json", columnDefinition = "TEXT")
    private String topicsJson;
    @Column(name = "deadline")
    private LocalDateTime deadline;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public GraduateRequirement() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAdvisorId() { return advisorId; }
    public void setAdvisorId(Long advisorId) { this.advisorId = advisorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTopicsJson() { return topicsJson; }
    public void setTopicsJson(String topicsJson) { this.topicsJson = topicsJson; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
