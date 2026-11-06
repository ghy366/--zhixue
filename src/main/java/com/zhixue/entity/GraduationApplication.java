package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 毕业申请：学生满足毕业要求后提交申请，教务审核通过即「批准毕业」。
 * 把毕业从实时计算值升级为真实高校的申请-审核工作流。
 */
@Entity
@Table(name = "graduation_application")
public class GraduationApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 申请时快照：总学分进度（已获/要求） */
    @Column(name = "credits_earned", nullable = false)
    private Integer creditsEarned;

    @Column(name = "credits_required", nullable = false)
    private Integer creditsRequired;

    /** 申请时快照：已通过课程数 */
    @Column(name = "course_count", nullable = false)
    private Integer courseCount;

    /** 申请时快照：GPA（用于审核列表一览） */
    @Column(name = "gpa_snapshot")
    private Double gpaSnapshot;

    /** 申请时快照：毕设总评（导师评分 40% + 答辩组评分 60%；未出成绩为 null） */
    @Column(name = "thesis_score")
    private Integer thesisScore;

    /** PENDING 待审核 / APPROVED 已批准 / REJECTED 已驳回 */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "apply_time", nullable = false)
    private LocalDateTime applyTime;

    @Column(name = "review_time")
    private LocalDateTime reviewTime;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    /** 审核备注（驳回时必填原因） */
    @Column(length = 300)
    private String remark;

    public GraduationApplication() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Integer getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(Integer creditsEarned) { this.creditsEarned = creditsEarned; }
    public Integer getCreditsRequired() { return creditsRequired; }
    public void setCreditsRequired(Integer creditsRequired) { this.creditsRequired = creditsRequired; }
    public Integer getCourseCount() { return courseCount; }
    public void setCourseCount(Integer courseCount) { this.courseCount = courseCount; }
    public Double getGpaSnapshot() { return gpaSnapshot; }
    public void setGpaSnapshot(Double gpaSnapshot) { this.gpaSnapshot = gpaSnapshot; }
    public Integer getThesisScore() { return thesisScore; }
    public void setThesisScore(Integer thesisScore) { this.thesisScore = thesisScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getApplyTime() { return applyTime; }
    public void setApplyTime(LocalDateTime applyTime) { this.applyTime = applyTime; }
    public LocalDateTime getReviewTime() { return reviewTime; }
    public void setReviewTime(LocalDateTime reviewTime) { this.reviewTime = reviewTime; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
