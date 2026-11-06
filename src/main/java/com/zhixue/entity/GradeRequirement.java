package com.zhixue.entity;

import jakarta.persistence.*;

/**
 * 按年级差异化毕业要求：不同入学年级（培养方案）适用不同的毕业学分与课程数。
 * 参考真实高校模式——普通本科与专升本（学制短）的毕业要求不同。
 * 未配置的年级回退到 SystemConfig 的全校默认要求。
 */
@Entity
@Table(name = "grade_requirement")
public class GradeRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 入学年级（如 2023）；同年级可按专业细分多行 */
    @Column(nullable = false)
    private Integer grade;

    /** 适用专业（如 软件工程）；空 = 该年级全部专业通用的默认培养方案 */
    @Column(length = 50)
    private String major;

    /** 培养方案标签（如 普通本科 / 专升本） */
    @Column(length = 30)
    private String label;

    @Column(name = "grad_req_required")
    private Integer gradReqRequired;

    @Column(name = "grad_req_elective")
    private Integer gradReqElective;

    @Column(name = "grad_req_general")
    private Integer gradReqGeneral;

    @Column(name = "grad_req_practice")
    private Integer gradReqPractice;

    @Column(name = "grad_req_courses")
    private Integer gradReqCourses;

    public GradeRequirement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getGradReqRequired() { return gradReqRequired; }
    public void setGradReqRequired(Integer gradReqRequired) { this.gradReqRequired = gradReqRequired; }
    public Integer getGradReqElective() { return gradReqElective; }
    public void setGradReqElective(Integer gradReqElective) { this.gradReqElective = gradReqElective; }
    public Integer getGradReqGeneral() { return gradReqGeneral; }
    public void setGradReqGeneral(Integer gradReqGeneral) { this.gradReqGeneral = gradReqGeneral; }
    public Integer getGradReqPractice() { return gradReqPractice; }
    public void setGradReqPractice(Integer gradReqPractice) { this.gradReqPractice = gradReqPractice; }
    public Integer getGradReqCourses() { return gradReqCourses; }
    public void setGradReqCourses(Integer gradReqCourses) { this.gradReqCourses = gradReqCourses; }
}
