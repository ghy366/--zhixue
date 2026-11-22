package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 补考册：正考挂科后自动生成一条，学生报名、教务排考、教师录分都作用在这条记录上。
 * 状态：ELIGIBLE 待报名 / REGISTERED 已报名 / SCHEDULED 已排考 /
 *       PASSED 已通过 / FAILED 未通过 / ABSENT 缺考 / CANCELLED 已取消
 */
@Entity
@Table(name = "makeup_exams", indexes = {
        @Index(name = "idx_makeup_student_course", columnList = "student_id,course_id")
})
public class MakeupExam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 触发本补考的正考流水 ID */
    @Column(name = "grade_record_id")
    private Long gradeRecordId;

    @Column(name = "semester_no")
    private Integer semesterNo;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Column(nullable = false, length = 16)
    private String status = "ELIGIBLE";

    @Column(name = "score")
    private Double score;

    @Column(name = "exam_time")
    private LocalDateTime examTime;

    @Column(name = "location", length = 64)
    private String location;

    @Column(name = "invigilator", length = 64)
    private String invigilator;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "note", length = 255)
    private String note;

    public MakeupExam() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getGradeRecordId() { return gradeRecordId; }
    public void setGradeRecordId(Long gradeRecordId) { this.gradeRecordId = gradeRecordId; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public LocalDateTime getExamTime() { return examTime; }
    public void setExamTime(LocalDateTime examTime) { this.examTime = examTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getInvigilator() { return invigilator; }
    public void setInvigilator(String invigilator) { this.invigilator = invigilator; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
