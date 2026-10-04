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

    public SystemConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer currentRound) { this.currentRound = currentRound; }
    public Boolean getRoundOpen() { return roundOpen; }
    public void setRoundOpen(Boolean roundOpen) { this.roundOpen = roundOpen; }
    public Integer getConfirmMinutes() { return confirmMinutes; }
    public void setConfirmMinutes(Integer confirmMinutes) { this.confirmMinutes = confirmMinutes; }
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
}
