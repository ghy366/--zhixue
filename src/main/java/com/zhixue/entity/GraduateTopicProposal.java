package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 自选课题申请（学生 → 毕业导师）：
 * 学生在完成导师双选后，可自拟与本专业相关的课题提交导师审批——
 * 导师通过则写入双选关系的课题（双选流程「确定课题」往下走），打回则学生修改后重新提交。
 * 状态：pending 待审核 / approved 已通过 / rejected 已打回 / withdrawn 已撤回。
 */
@Entity
@Table(name = "graduate_topic_proposals")
public class GraduateTopicProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 提交对象 = 双选确认的毕业导师 */
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 200)
    private String title;

    /** 研究方向（可选） */
    @Column(length = 60)
    private String direction;

    /** 选题说明：研究内容、与本专业的关联、可行性等 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** pending / approved / rejected / withdrawn */
    @Column(nullable = false, length = 20)
    private String status;

    /** 导师批复：通过备注或打回理由 */
    @Column(name = "teacher_reply", length = 500)
    private String teacherReply;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GraduateTopicProposal() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
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
    public String getTeacherReply() { return teacherReply; }
    public void setTeacherReply(String teacherReply) { this.teacherReply = teacherReply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
