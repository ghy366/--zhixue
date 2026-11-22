package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 毕业设计指导记录：对齐高校毕设过程管理（维普/超星同款环节）。
 * 导师每次指导登记一条（时间 / 方式 / 内容），师生双方可见，教务检查归档依据。
 */
@Entity
@Table(name = "graduate_guidance_records")
public class GraduateGuidanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 双选关系 id（GraduateAdvisor.id，一对师生一个工作台） */
    @Column(name = "advisor_id", nullable = false)
    private Long advisorId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** 指导日期 */
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 指导方式：面谈 / 线上 / 电话 / 其他 */
    @Column(length = 20, nullable = false)
    private String method;

    /** 指导内容（讨论要点 / 修改意见 / 下一步安排） */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public GraduateGuidanceRecord() {}
    public Long getId() { return id; }
    public Long getAdvisorId() { return advisorId; }
    public void setAdvisorId(Long advisorId) { this.advisorId = advisorId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
