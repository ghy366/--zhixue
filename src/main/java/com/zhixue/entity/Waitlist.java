package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "waitlists")
public class Waitlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Integer round;          // 选课轮次

    @Column(name = "join_time", nullable = false)
    private LocalDateTime joinTime; // 加入候补时间

    @Column(nullable = false)
    private String status;          // waiting / confirming / confirmed / timeout / cancelled

    @Column(name = "deadline")
    private LocalDateTime deadline; // confirming 状态下的确认截止时间

    @Column(name = "promote_time")
    private LocalDateTime promoteTime; // 被递补（进入 confirming）的时间

    /**
     * 换课保底标记（Swap）：候补时指明「递补成功确认后自动退掉该课」，
     * 确认时按换课口径校验（剔除该课再查门数/学分/时间冲突）并原子互换。
     */
    @Column(name = "swap_from_course_id")
    private Long swapFromCourseId;

    public Waitlist() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public LocalDateTime getJoinTime() { return joinTime; }
    public void setJoinTime(LocalDateTime joinTime) { this.joinTime = joinTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public LocalDateTime getPromoteTime() { return promoteTime; }
    public void setPromoteTime(LocalDateTime promoteTime) { this.promoteTime = promoteTime; }
    public Long getSwapFromCourseId() { return swapFromCourseId; }
    public void setSwapFromCourseId(Long v) { this.swapFromCourseId = v; }
}
