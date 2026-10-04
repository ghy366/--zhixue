package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 课程评价：学生对已结课/已录取课程打分与留言 */
@Entity
@Table(name = "course_reviews")
public class CourseReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Integer rating;             // 1-5 星

    @Column(length = 1000)
    private String content;             // 评价内容

    @Column(name = "teaching_rating")
    private Integer teachingRating;     // 教学水平 1-5

    @Column(name = "content_rating")
    private Integer contentRating;      // 教学内容 1-5

    @Column(name = "homework_rating")
    private Integer homeworkRating;     // 作业安排 1-5

    @Column(name = "anonymous")
    private Boolean anonymous;          // 是否匿名展示

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CourseReview() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTeachingRating() { return teachingRating; }
    public void setTeachingRating(Integer teachingRating) { this.teachingRating = teachingRating; }
    public Integer getContentRating() { return contentRating; }
    public void setContentRating(Integer contentRating) { this.contentRating = contentRating; }
    public Integer getHomeworkRating() { return homeworkRating; }
    public void setHomeworkRating(Integer homeworkRating) { this.homeworkRating = homeworkRating; }
    public Boolean getAnonymous() { return anonymous; }
    public void setAnonymous(Boolean anonymous) { this.anonymous = anonymous; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
