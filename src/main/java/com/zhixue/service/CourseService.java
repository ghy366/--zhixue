package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.CourseIntentRepository;
import com.zhixue.repository.CourseReviewRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseService {

    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private CourseIntentRepository intentRepo;
    @Autowired private CourseReviewRepository reviewRepo;

    public List<Course> findAll() { return courseRepo.findAll(); }

    public List<Course> findByCategory(String category) {
        if ("全部".equals(category)) return courseRepo.findAll();
        return courseRepo.findByCategory(category);
    }

    public List<Course> search(String keyword, String category) {
        List<Course> list = findByCategory(category);
        if (keyword == null || keyword.isEmpty()) return list;
        String kw = keyword.toLowerCase();
        return list.stream().filter(c ->
                c.getName().toLowerCase().contains(kw) ||
                teacherName(c.getTeacherId()).toLowerCase().contains(kw)
        ).collect(Collectors.toList());
    }

    public List<Course> findByTeacher(Long teacherId) {
        return courseRepo.findById(teacherId).isPresent() ? courseRepo.findByTeacherId(teacherId) : List.of();
    }

    public Course findById(Long id) { return courseRepo.findById(id).orElse(null); }

    public String teacherName(Long teacherId) {
        if (teacherId == null) return "未分配";
        User u = userRepo.findById(teacherId).orElse(null);
        return u != null ? u.getName() : "未分配";
    }

    public Course save(Course c) { return courseRepo.save(c); }

    /** 删除课程，并级联清理选课/候补/意向/评价，避免产生"孤儿记录" */
    @Transactional
    public void delete(Long id) {
        enrollmentRepo.deleteAll(enrollmentRepo.findAll().stream().filter(e -> id.equals(e.getCourseId())).toList());
        waitlistRepo.deleteAll(waitlistRepo.findAll().stream().filter(w -> id.equals(w.getCourseId())).toList());
        intentRepo.deleteAll(intentRepo.findAll().stream().filter(i -> id.equals(i.getCourseId())).toList());
        reviewRepo.deleteAll(reviewRepo.findAll().stream().filter(r -> id.equals(r.getCourseId())).toList());
        courseRepo.deleteById(id);
    }

    public List<User> getAllTeachers() { return userRepo.findByRole("teacher"); }

    public java.util.List<Course> hotCourses(int n) {
        return courseRepo.findTopByEnrollmentCount(java.lang.Math.max(1, java.lang.Math.min(n, 50)));
    }
}
