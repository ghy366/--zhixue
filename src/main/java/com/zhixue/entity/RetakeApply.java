package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 重修申请：补考未通过（或弃考）后自动生成，学生报名、教务定形式/指定班级、教师录分都作用在这条记录上。
 * 状态：ELIGIBLE 待报名 / APPLIED 已报名 / APPROVED 已同意 / ENROLLED 已进班 /
 *       PASSED 已通过 / FAILED 未通过 / CANCELLED 已取消
 */
@Entity
@Table(name = "retake_applies", indexes = {
        @Index(name = "idx_retake_student_course", columnList = "student_id,course_id")
})
public class RetakeApply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 触发本重修的补考记录 ID（可能为空） */
    @Column(name = "makeup_exam_id")
    private Long makeupExamId;

    /** 最近一次成绩流水 ID */
    @Column(name = "grade_record_id")
    private Long gradeRecordId;

    /** 第几次重修（从 1 开始） */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Column(nullable = false, length = 16)
    private String status = "ELIGIBLE";

    /** GROUP 组班 / FOLLOW 跟班 / SELF 自修；为空表示教务未定 */
    @Column(name = "retake_form", length = 16)
    private String form;

    /** 跟班重修指定的教学班 ID（沿用课程记录作为教学班，指向 courses.id） */
    @Column(name = "target_class_id")
    private Long targetClassId;

    /** 组班重修所属的教学班 ID（teaching_classes.id）；跟班/自修为空 */
    @Column(name = "teaching_class_id")
    private Long teachingClassId;

    @Column(name = "semester_no")
    private Integer semesterNo;

    @Column(name = "score")
    private Double score;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "note", length = 255)
    private String note;

    public RetakeApply() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getMakeupExamId() { return makeupExamId; }
    public void setMakeupExamId(Long makeupExamId) { this.makeupExamId = makeupExamId; }
    public Long getGradeRecordId() { return gradeRecordId; }
    public void setGradeRecordId(Long gradeRecordId) { this.gradeRecordId = gradeRecordId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public Long getTargetClassId() { return targetClassId; }
    public void setTargetClassId(Long targetClassId) { this.targetClassId = targetClassId; }
    public Long getTeachingClassId() { return teachingClassId; }
    public void setTeachingClassId(Long teachingClassId) { this.teachingClassId = teachingClassId; }
    public Integer getSemesterNo() { return semesterNo; }
    public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
