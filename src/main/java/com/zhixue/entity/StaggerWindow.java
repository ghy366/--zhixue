package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 错峰开放窗口：在某一轮内，指定年级的学生从该时间点起才可提交选课。
 * 仅作用于第 1、2 轮（积分制，错峰只影响提交时间，不影响截止后统一排序结果）；
 * 第 3 轮先到先得补选不参与错峰，全局开放即全员可抢。
 * 某年级未配置窗口时，视为跟随全局开放时间（不错峰）。
 */
@Entity
@Table(name = "stagger_window",
       uniqueConstraints = @UniqueConstraint(columnNames = {"round_no", "grade"}))
public class StaggerWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 轮次号（1 / 2） */
    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** 年级，如 2023 表示 2023 级 */
    @Column(name = "grade", nullable = false)
    private Integer grade;

    /** 该年级在本轮的开放时间（不早于全局开放时间才有意义） */
    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    public StaggerWindow() {}

    public StaggerWindow(Integer roundNo, Integer grade, LocalDateTime openAt) {
        this.roundNo = roundNo;
        this.grade = grade;
        this.openAt = openAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    public LocalDateTime getOpenAt() { return openAt; }
    public void setOpenAt(LocalDateTime openAt) { this.openAt = openAt; }
}
