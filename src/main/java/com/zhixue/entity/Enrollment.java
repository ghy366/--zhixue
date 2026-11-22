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

    /**
     * 报名来源：null/normal 普通选课；retake 跟班重修（由教务/学生选班后自动写入，占 0 积分，
     * 只用于让重修课程出现在课表与「我的课程」，不参与积分池与容量结算）。
     */
    @Column(name = "source", length = 16)
    private String source;

    /** 组班重修所属教学班（teaching_classes.id）：课表/名单读取该班的时间与教师 */
    @Column(name = "teaching_class_id")
    private Long teachingClassId;

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
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public Long getTeachingClassId() { return teachingClassId; }
    public void setTeachingClassId(Long v) { this.teachingClassId = v; }
}
