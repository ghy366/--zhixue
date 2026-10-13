package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 选课轮次时间窗口：为每一轮预设「开始时间 ~ 结束时间」。
 * 到开始时间自动开放选课，到结束时间自动关闭，无需人工切换。
 * 主键即轮次号（1 / 2 / 3）。
 */
@Entity
@Table(name = "round_schedule")
public class RoundSchedule {

    @Id
    @Column(name = "round_no")
    private Integer roundNo;

    /** 开放时间 */
    @Column(name = "open_at")
    private LocalDateTime openAt;

    /** 结束时间 */
    @Column(name = "close_at")
    private LocalDateTime closeAt;

    public RoundSchedule() {}

    public RoundSchedule(Integer roundNo) { this.roundNo = roundNo; }

    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public LocalDateTime getOpenAt() { return openAt; }
    public void setOpenAt(LocalDateTime openAt) { this.openAt = openAt; }
    public LocalDateTime getCloseAt() { return closeAt; }
    public void setCloseAt(LocalDateTime closeAt) { this.closeAt = closeAt; }
}
