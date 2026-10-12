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
    private Integer gradReqRequired = 12;

    @Column(name = "grad_req_elective")
    private Integer gradReqElective = 10;

    @Column(name = "grad_req_general")
    private Integer gradReqGeneral = 6;

    @Column(name = "grad_req_practice")
    private Integer gradReqPractice = 4;

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
    public Boolean getAdvisorOpen() { return advisorOpen; }
    public void setAdvisorOpen(Boolean advisorOpen) { this.advisorOpen = advisorOpen; }
    public Integer getAdvisorDefaultCapacity() { return advisorDefaultCapacity; }
    public void setAdvisorDefaultCapacity(Integer advisorDefaultCapacity) { this.advisorDefaultCapacity = advisorDefaultCapacity; }
    public Integer getReselectPenalty() { return reselectPenalty; }
    public void setReselectPenalty(Integer reselectPenalty) { this.reselectPenalty = reselectPenalty; }

    public Integer getMaxCreditPerSemester() { return maxCreditPerSemester; }
    public void setMaxCreditPerSemester(Integer v) { this.maxCreditPerSemester = v; }
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
}
