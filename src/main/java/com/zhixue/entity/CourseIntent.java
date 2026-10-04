package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 预选意向单：学生先收藏课程，轮次开放时一键提交 */
@Entity
@Table(name = "course_intents")
public class CourseIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "desired_points")
    private Integer desiredPoints;      // 预填的投入积分

    @Column(length = 200)
    private String note;                // 备注

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CourseIntent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDesiredPoints() { return desiredPoints; }
    public void setDesiredPoints(Integer desiredPoints) { this.desiredPoints = desiredPoints; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
