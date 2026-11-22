package com.zhixue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "course_code", unique = true)
    private String courseCode;

    private Integer hours;

    @Column(name = "teacher_id")
    private Long teacherId;       // 授课老师ID

    private Integer credit;
    private Integer capacity;
    private String category;      // 公共必修/专业必修/专业选修/公共选修

    /** 适用学院：专业必修课只排给该学院学生；null = 全校公共课。对接厦门华厦学院4个招生学院 */
    @Column(name = "target_college")
    private String targetCollege;

    /**
     * 适用专业：按培养方案精确排课，避免同学院不同专业共用课程表。
     * 多个专业用 | 分隔；null / 空 = 全院通用（或全校公共课）。
     */
    @Column(name = "target_major", length = 500)
    private String targetMajor;

    private String time;          // 如 "周一 08:00-09:40 / 周三 10:00-11:40"
    private String location;
    private String campus;        // 校区：主校区/东校区/西校区
    @Column(name = "exam_time")
    private String examTime;      // 考试时间（为空则默认按上课时间）
    @Column(length = 1000)
    private String intro;

    /**
     * 培养方案开课学期序（1~8，参照真实高校教学计划：必修课按学期排定）。
     * 必修课标注后，系统按学生当前学期序自动排课（未来学期不排、历史学期未通过的自动重修）；
     * 选修课/未标注的必修课保持原行为。null = 未排期。
     */
    @Column(name = "semester_no")
    private Integer semesterNo;

    /**
     * 先修课程 ID（参照真实高校：未通过先修课不可选修本课）。null = 无先修要求。
     * 校验口径：该先修课「总评 ≥ 60」才允许报名/录取；挂科重修不影响本课报名资格判定。
     */
    @Column(name = "prereq_id")
    private Long prereqId;

    public Course() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Integer getCredit() { return credit; }
    public void setCredit(Integer credit) { this.credit = credit; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTargetCollege() { return targetCollege; }
    public void setTargetCollege(String targetCollege) { this.targetCollege = targetCollege; }
    public String getTargetMajor() { return targetMajor; }
    public void setTargetMajor(String targetMajor) { this.targetMajor = targetMajor; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }
    public String getExamTime() { return examTime; }
    public void setExamTime(String examTime) { this.examTime = examTime; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public Long getPrereqId() { return prereqId; }
    public void setPrereqId(Long prereqId) { this.prereqId = prereqId; }
}
