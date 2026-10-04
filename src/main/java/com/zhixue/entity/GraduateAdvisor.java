package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 毕业设计导师选择记录：学生申请 -> 教师审批 */
@Entity
@Table(name = "graduate_advisors")
public class GraduateAdvisor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** pending 申请中 / approved 已确认 / rejected 已拒绝 / cancelled 学生撤销 */
    @Column(length = 20, nullable = false)
    private String status;

    /** 学生意向论文方向 */
    @Column(length = 200)
    private String topic;

    /** 学生备注（自我介绍 / 想跟老师做的方向） */
    @Column(name = "preference_text", length = 500)
    private String preferenceText;

    /** 教师的评语/回复 */
    @Column(name = "teacher_reply", length = 500)
    private String teacherReply;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GraduateAdvisor() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getPreferenceText() { return preferenceText; }
    public void setPreferenceText(String preferenceText) { this.preferenceText = preferenceText; }
    public String getTeacherReply() { return teacherReply; }
    public void setTeacherReply(String teacherReply) { this.teacherReply = teacherReply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
