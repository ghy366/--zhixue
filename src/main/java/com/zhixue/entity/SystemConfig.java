package com.zhixue.entity;

import jakarta.persistence.*;

/** 单例配置扩展：新增导师选择开关 / 默认容量 / 单导师容量 */
@Entity
@Table(name = "system_config")
public class SystemConfig {
    @Id
    private Long id = 1L;

    @Column(name = "current_round", nullable = false)
    private Integer currentRound = 1;

    @Column(name = "round_open", nullable = false)
    private Boolean roundOpen = true;

    @Column(name = "confirm_minutes")
    private Integer confirmMinutes = 30;

    /** 本轮选课开放时间（用于学生端倒计时；为空则无） */
    @Column(name = "round_open_at")
    private java.time.LocalDateTime roundOpenAt;

    /** 本轮选课结束时间（用于学生端倒计时；为空则无） */
    @Column(name = "round_close_at")
    private java.time.LocalDateTime roundCloseAt;

    @Column(name = "grad_req_required")
    private Integer gradReqRequired = 6;

    @Column(name = "grad_req_elective")
    private Integer gradReqElective = 5;

    @Column(name = "grad_req_general")
    private Integer gradReqGeneral = 3;

    @Column(name = "grad_req_practice")
    private Integer gradReqPractice = 2;

    /** 毕业最少通过课程数：与学分双达标（真实高校「修满学分与课程门数」模式） */
    @Column(name = "grad_req_courses")
    private Integer gradReqCourses = 9;

    /** 导师选择总开关 */
    @Column(name = "advisor_open")
    private Boolean advisorOpen = true;

    /** 导师默认容量上限 */
    @Column(name = "advisor_default_capacity")
    private Integer advisorDefaultCapacity = 15;

    /** 改选惩罚：每次改选额外消耗的积分（0 表示免费改选）。开放期内允许学生改志愿时使用 */
    @Column(name = "reselect_penalty")
    private Integer reselectPenalty = 0;

    /** 每学期学分上限，超过拒绝选课（null/0 表示不限制） */
    @Column(name = "max_credit_per_semester")
    private Integer maxCreditPerSemester = 24;

    /** 每学期选课门数上限，超过拒绝选课/候补确认/结算录取（null/0 表示不限制） */
    @Column(name = "max_courses_per_semester")
    private Integer maxCoursesPerSemester = 2;

    /** 补选开放时间 */
    @Column(name = "batch_open_at")
    private java.time.LocalDateTime batchOpenAt;

    /** 补选结束时间 */
    @Column(name = "batch_close_at")
    private java.time.LocalDateTime batchCloseAt;

    /** 退选阶段开始时间 */
    @Column(name = "reselect_open_at")
    private java.time.LocalDateTime reselectOpenAt;

    /** 退选阶段结束时间 */
    @Column(name = "reselect_close_at")
    private java.time.LocalDateTime reselectCloseAt;

    /** 当前阶段：regular/batch/reselect */
    @Column(name = "current_phase", length = 16)
    private String currentPhase = "regular";

    /** 是否按预设时间窗口自动开放 / 自动结束选课（开启后 currentRound 与 roundOpen 由时间推导） */
    @Column(name = "auto_schedule")
    private Boolean autoSchedule = false;

    /** 教师每学期最多授课门数（真实高校教师工作量约束，课程管理新增/编辑时校验） */
    @Column(name = "teacher_max_courses")
    private Integer teacherMaxCourses = 3;

    /** 学期滚动偏移：管理员推进学期后 +1，当前学期序 = 日期推算 + 偏移（学期滚动机制） */
    @Column(name = "semester_offset")
    private Integer semesterOffset = 0;

    /** 新学期已开启标记：只有先「推进到下一学期」置 true，才允许开启第 1 轮选课；开轮后消费为 false */
    @Column(name = "new_semester_ready")
    private Boolean newSemesterReady = false;

    public SystemConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer currentRound) { this.currentRound = currentRound; }
    public Boolean getRoundOpen() { return roundOpen; }
    public void setRoundOpen(Boolean roundOpen) { this.roundOpen = roundOpen; }
    public Integer getConfirmMinutes() { return confirmMinutes; }
    public void setConfirmMinutes(Integer confirmMinutes) { this.confirmMinutes = confirmMinutes; }
    public java.time.LocalDateTime getRoundOpenAt() { return roundOpenAt; }
    public void setRoundOpenAt(java.time.LocalDateTime roundOpenAt) { this.roundOpenAt = roundOpenAt; }
    public java.time.LocalDateTime getRoundCloseAt() { return roundCloseAt; }
    public void setRoundCloseAt(java.time.LocalDateTime roundCloseAt) { this.roundCloseAt = roundCloseAt; }
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
    public Boolean getAdvisorOpen() { return advisorOpen; }
    public void setAdvisorOpen(Boolean advisorOpen) { this.advisorOpen = advisorOpen; }
    public Integer getAdvisorDefaultCapacity() { return advisorDefaultCapacity; }
    public void setAdvisorDefaultCapacity(Integer advisorDefaultCapacity) { this.advisorDefaultCapacity = advisorDefaultCapacity; }
    public Integer getReselectPenalty() { return reselectPenalty; }
    public void setReselectPenalty(Integer reselectPenalty) { this.reselectPenalty = reselectPenalty; }

    public Integer getMaxCreditPerSemester() { return maxCreditPerSemester; }
    public void setMaxCreditPerSemester(Integer v) { this.maxCreditPerSemester = v; }
    public Integer getMaxCoursesPerSemester() { return maxCoursesPerSemester; }
    public void setMaxCoursesPerSemester(Integer v) { this.maxCoursesPerSemester = v; }
    public java.time.LocalDateTime getBatchOpenAt() { return batchOpenAt; }
    public void setBatchOpenAt(java.time.LocalDateTime v) { this.batchOpenAt = v; }
    public java.time.LocalDateTime getBatchCloseAt() { return batchCloseAt; }
    public void setBatchCloseAt(java.time.LocalDateTime v) { this.batchCloseAt = v; }
    public java.time.LocalDateTime getReselectOpenAt() { return reselectOpenAt; }
    public void setReselectOpenAt(java.time.LocalDateTime v) { this.reselectOpenAt = v; }
    public java.time.LocalDateTime getReselectCloseAt() { return reselectCloseAt; }
    public void setReselectCloseAt(java.time.LocalDateTime v) { this.reselectCloseAt = v; }
    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String v) { this.currentPhase = v; }
    public Boolean getAutoSchedule() { return autoSchedule; }
    public void setAutoSchedule(Boolean v) { this.autoSchedule = v; }
    public Integer getTeacherMaxCourses() { return teacherMaxCourses; }
    public void setTeacherMaxCourses(Integer v) { this.teacherMaxCourses = v; }
    public Boolean getNewSemesterReady() { return newSemesterReady; }
    public void setNewSemesterReady(Boolean v) { this.newSemesterReady = v; }
    public Integer getSemesterOffset() { return semesterOffset; }
    public void setSemesterOffset(Integer v) { this.semesterOffset = v; }

    /** 本学期已完成（关闭并结算）的轮次数：下一可开启轮 = roundProgress + 1 */
    @Column(name = "round_progress")
    private Integer roundProgress = 0;

    public Integer getRoundProgress() { return roundProgress; }
    public void setRoundProgress(Integer v) { this.roundProgress = v; }
}
