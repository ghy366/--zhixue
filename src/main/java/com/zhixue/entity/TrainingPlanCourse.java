package com.zhixue.entity;

import jakarta.persistence.*;

/**
 * 培养方案课程清单：按「专业 + 入学年级 + 学期」排定该修哪些课。
 * 管理员在「培养方案」页维护，系统按此表批量排定学生必修课（教务维度，学生不选）。
 * grade 为 null 时表示该专业所有年级通用；同时存在年级专属与通用方案时优先年级专属。
 */
@Entity
@Table(name = "training_plan_course")
public class TrainingPlanCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 适用专业（华厦官方专业名，如 环境科学与工程） */
    @Column(nullable = false, length = 100)
    private String major;

    /** 入学年级，null = 该专业所有年级通用 */
    private Integer grade;

    /** 开课学期 1~8 */
    @Column(name = "semester_no", nullable = false)
    private Integer semesterNo;

    /** 课程ID */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 课程性质：公共必修/专业必修/专业选修 */
    @Column(length = 30)
    private String category;

    /** 学分（冗余，便于方案页展示统计） */
    private Integer credit;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 备注 */
    @Column(length = 200)
    private String note;

    public TrainingPlanCourse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getCredit() { return credit; }
    public void setCredit(Integer credit) { this.credit = credit; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
