package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 教学班：重修「组班」时真正开出的班级实体。
 * 与课程（Course）不同，教学班有独立的教学班号、授课教师、上课时间/地点与容量，
 * 组班学生通过 RetakeApply.teachingClassId 与 Enrollment.teachingClassId 归属到本班，
 * 课表读取教学班时间，因此开班后学生课表会真正出现这门课。
 * 状态：OPEN 开班中 / CLOSED 已停开
 */
@Entity
@Table(name = "teaching_classes", indexes = {
        @Index(name = "idx_tc_course", columnList = "course_id"),
        @Index(name = "idx_tc_teacher", columnList = "teacher_id")
})
public class TeachingClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 课程库课程 ID（重修课程本身） */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 教学班号，如 RX-CS103-4-01 */
    @Column(name = "class_no", nullable = false, unique = true, length = 64)
    private String classNo;

    @Column(name = "semester_no")
    private Integer semesterNo;

    @Column(name = "teacher_id")
    private Long teacherId;

    /** 如 "周六 08:00-09:40 / 周日 08:00-09:40" */
    @Column(length = 255)
    private String time;

    @Column(length = 255)
    private String location;

    @Column(length = 32)
    private String campus;

    private Integer capacity;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    /** 班级来源：RETAKE 重修组班（预留其他来源） */
    @Column(length = 16)
    private String source = "RETAKE";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(length = 255)
    private String note;

    public TeachingClass() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
