package com.zhixue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "grades")
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "regular_score")
    private Double regularScore;

    @Column(name = "final_score")
    private Double finalScore;

    @Column(name = "total_score")
    private Double totalScore;

    /** 最近一次成绩性质：normal 正常 / makeup 补考 / retake 重修（真实高校：挂科先补考，再不过重修） */
    @Column(name = "exam_type", length = 16)
    private String examType = "normal";

    /** 修读次数：首次录课为 1，每次补考/重修 +1 */
    @Column(name = "attempt")
    private Integer attempt = 1;

    /** 成绩归属学期序（= 课程培养方案学期；选修为录课时的当前学期序） */
    @Column(name = "semester_no")
    private Integer semesterNo;

    public Grade() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Double getRegularScore() { return regularScore; }
    public void setRegularScore(Double regularScore) { this.regularScore = regularScore; }
    public Double getFinalScore() { return finalScore; }
    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public String getExamType() { return examType; }
    public void setExamType(String examType) { this.examType = examType; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
}
