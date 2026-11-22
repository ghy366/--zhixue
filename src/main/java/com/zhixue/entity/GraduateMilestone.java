package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 毕业设计流程里程碑：开题报告 → 中期检查 → 论文终稿 → 答辩。
 * <p>
 * 每条已确认的导师关系（graduate_advisors.approved）对应 4 条里程碑记录，
 * 串行推进：前置节点通过（passed）后下一节点才可提交。
 * 答辩节点通过时录入导师评分与答辩组评分，按 40% / 60% 合成毕设总评。
 */
@Entity
@Table(name = "graduate_milestones")
public class GraduateMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advisor_id", nullable = false)
    private Long advisorId;

    /** opening 开题报告 / midterm 中期检查 / final 论文终稿 / defense 答辩 */
    @Column(name = "stage", length = 20, nullable = false)
    private String stage;

    /** none 未提交 / submitted 待审核 / passed 已通过 / returned 已退回 */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 材料标题（如：开题报告 v1） */
    @Column(length = 200)
    private String title;

    /** 学生说明（进展 / 方案概述） */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** 导师评语（退回原因 / 通过寄语） */
    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment;

    /** 导师评分（0-100，答辩节点使用） */
    @Column(name = "advisor_score")
    private Integer advisorScore;

    /** 评阅教师 id（教务指派的交叉评阅，不能是导师本人；答辩节点使用） */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 评阅教师姓名（冗余，指派时快照） */
    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    /** 评阅教师评分（0-100，答辩节点使用） */
    @Column(name = "reviewer_score")
    private Integer reviewerScore;

    /** 评阅教师评语 */
    @Column(name = "reviewer_comment", columnDefinition = "TEXT")
    private String reviewerComment;

    /** 评阅时间 */
    @Column(name = "reviewer_at")
    private LocalDateTime reviewerAt;

    /** 答辩组评分（0-100，答辩节点使用） */
    @Column(name = "defense_score")
    private Integer defenseScore;

    /** 毕设总评 = 导师评分×40% + 评阅教师×20% + 答辩组评分×40%（三方评分链） */
    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public GraduateMilestone() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAdvisorId() { return advisorId; }
    public void setAdvisorId(Long advisorId) { this.advisorId = advisorId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public Integer getAdvisorScore() { return advisorScore; }
    public void setAdvisorScore(Integer advisorScore) { this.advisorScore = advisorScore; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public Integer getReviewerScore() { return reviewerScore; }
    public void setReviewerScore(Integer reviewerScore) { this.reviewerScore = reviewerScore; }
    public String getReviewerComment() { return reviewerComment; }
    public void setReviewerComment(String reviewerComment) { this.reviewerComment = reviewerComment; }
    public LocalDateTime getReviewerAt() { return reviewerAt; }
    public void setReviewerAt(LocalDateTime reviewerAt) { this.reviewerAt = reviewerAt; }
    public Integer getDefenseScore() { return defenseScore; }
    public void setDefenseScore(Integer defenseScore) { this.defenseScore = defenseScore; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
