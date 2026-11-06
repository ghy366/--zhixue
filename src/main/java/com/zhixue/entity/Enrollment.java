package com.zhixue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollment_student_course_round",
                columnNames = {"student_id", "course_id", "round"}))
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Integer round;       // 选课轮次 1/2/3

    @Column(nullable = false)
    private Integer points;      // 投入积分

    @Column(nullable = false)
    private String status;       // pending / approved / rejected / dropped

    /**
     * 学生修读学期序（1~8，参照培养方案）：真实高校每学期的选课/修读记录归属该学期，
     * 「我的选课/课表/冲突检测」只看当前学期，毕业学分累计才跨全部学期。
     */
    @Column(name = "semester_no")
    private Integer semesterNo;

    /**
     * 换课保底标记（Swap，借鉴 Auto_courseGrabber）：本条报名是新课 B，
     * swapFromCourseId 指向被换的原课 A。结算时 B 被录取 → 自动退掉 A 完成互换；
     * B 未录取 → 本条落选退分、A 原样保留。换课成交（或保底失效）后置回 null。
     */
    @Column(name = "swap_from_course_id")
    private Long swapFromCourseId;

    public Enrollment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public Long getSwapFromCourseId() { return swapFromCourseId; }
    public void setSwapFromCourseId(Long v) { this.swapFromCourseId = v; }
}
