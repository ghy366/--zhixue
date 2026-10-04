package com.zhixue.entity;

import jakarta.persistence.*;

/** 单导师容量覆盖（未配置则用 system_config.advisor_default_capacity） */
@Entity
@Table(name = "advisor_capacities")
public class AdvisorCapacity {
    @Id
    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(nullable = false)
    private Integer capacity;

    public AdvisorCapacity() {}
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
