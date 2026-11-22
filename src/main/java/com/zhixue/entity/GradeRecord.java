package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 成绩流水：正考、每次补考、每次重修各占一行，只追加不覆盖。
 * grades 表保留为「最新一次成绩」视图，兼容现有读取方。
 */
@Entity
@Table(name = "grade_records", indexes = {
        @Index(name = "idx_grade_record_student_course", columnList = "student_id,course_id")
})
public class GradeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 第几次修读：正考=1，第一次补考=2，第一次重修=3，依此类推 */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    /** normal 正考 / makeup 补考 / retake 重修 */
    @Column(name = "exam_type", nullable = false, length = 16)
    private String examType = "normal";

    @Column(name = "regular_score")
    private Double regularScore;

    @Column(name = "final_score")
    private Double finalScore;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "semester_no")
    private Integer semesterNo;

    /** 来源类型：makeup / retake；正考为空 */
    @Column(name = "source_type", length = 16)
    private String sourceType;

    /** 来源记录 ID（makeup_exams.id / retake_applies.id） */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    public GradeRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getExamType() { return examType; }
    public void setExamType(String examType) { this.examType = examType; }
    public Double getRegularScore() { return regularScore; }
    public void setRegularScore(Double regularScore) { this.regularScore = regularScore; }
    public Double getFinalScore() { return finalScore; }
    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
