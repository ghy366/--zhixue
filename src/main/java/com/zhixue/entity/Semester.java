package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "semesters")
public class Semester {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "selection_start")
    private LocalDate selectionStart;

    @Column(name = "selection_end")
    private LocalDate selectionEnd;

    @Column(nullable = false)
    private String status;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    public Semester() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getSelectionStart() { return selectionStart; }
    public void setSelectionStart(LocalDate selectionStart) { this.selectionStart = selectionStart; }
    public LocalDate getSelectionEnd() { return selectionEnd; }
    public void setSelectionEnd(LocalDate selectionEnd) { this.selectionEnd = selectionEnd; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
}
