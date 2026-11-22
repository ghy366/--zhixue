package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 毕业设计查重登记（对齐真实流程：论文定稿 → 查重 → 达标获得答辩资格）。
 * 学生提交重复率 + 查重报告单，导师审核；重复率超阈值只能退回。
 * 一对师生一条记录（幂等创建）。
 */
@Entity
@Table(name = "graduate_plagiarism_checks")
public class GraduatePlagiarism {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 双选关系 id（GraduateAdvisor.id） */
    @Column(name = "advisor_id", nullable = false)
    private Long advisorId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** 重复率（百分比，如 12.5） */
    @Column(name = "repeat_rate")
    private Double repeatRate;

    /** none 未提交 / submitted 待审核 / passed 已达标 / returned 已退回 */
    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** 学生说明（查重系统 / 检测版本） */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 导师审核评语 */
    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public GraduatePlagiarism() {}
    public Long getId() { return id; }
    public Long getAdvisorId() { return advisorId; }
    public void setAdvisorId(Long advisorId) { this.advisorId = advisorId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Double getRepeatRate() { return repeatRate; }
    public void setRepeatRate(Double repeatRate) { this.repeatRate = repeatRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
