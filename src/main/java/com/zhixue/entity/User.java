package com.zhixue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String no;          // 学号/工号/管理员账号

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;        // student / teacher / admin

    private String college;
    private String major;
    private Integer grade;
    private String classNo;
    private String gender;      // 性别：男 / 女（学籍导入字段，可为空——历史数据兼容）
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String avatar;  // emoji 或 base64 图片

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNo() { return no; }
    public void setNo(String no) { this.no = no; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}

