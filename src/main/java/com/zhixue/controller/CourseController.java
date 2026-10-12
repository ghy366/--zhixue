package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.User;
import com.zhixue.service.CourseService;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.IntentService;
import com.zhixue.service.ReviewService;
import com.zhixue.service.WaitlistService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired private CourseService courseService;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private IntentService intentService;
    @Autowired private ReviewService reviewService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false, defaultValue = "全部") String category,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = token != null ? SessionStore.getUserId(token.replace("Bearer ", "")) : null;
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Course> courses = courseService.search(keyword, category);
        Map<String, Object> ratings = reviewService.allRatings();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) {
            result.add(courseToMap(c, userId, round, ratings));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<List<Map<String, Object>>> teacherCourses(@PathVariable Long teacherId) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Course> courses = courseService.findByTeacher(teacherId);
        Map<String, Object> ratings = reviewService.allRatings();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) result.add(courseToMap(c, null, round, ratings));
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<Course> add(@RequestBody Course c) {
        return ApiResponse.ok("添加成功", courseService.save(c));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/teachers")
    public ApiResponse<List<User>> teachers() {
        return ApiResponse.ok(courseService.getAllTeachers());
    }

    /** 热门课程排行 */
    @GetMapping("/hot")
    public ApiResponse<List<Map<String, Object>>> hotCourses(@RequestParam(defaultValue = "10") int n) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        Map<String, Object> ratings = reviewService.allRatings();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Course cc : courseService.hotCourses(n)) {
            result.add(courseToMap(cc, null, round, ratings));
        }
        return ApiResponse.ok(result);
    }



    private Map<String, Object> courseToMap(Course c, Long userId, int round, Map<String, Object> ratings) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("courseCode", c.getCourseCode());
        m.put("hours", c.getHours());
        m.put("teacherId", c.getTeacherId());
        m.put("teacherName", courseService.teacherName(c.getTeacherId()));
        m.put("credit", c.getCredit());
        m.put("capacity", c.getCapacity());
        m.put("category", c.getCategory());
        m.put("time", c.getTime());
        m.put("location", c.getLocation());
        m.put("campus", c.getCampus());
        m.put("examTime", c.getExamTime());
        m.put("intro", c.getIntro());
        m.put("stat", enrollmentService.courseStat(c.getId(), round));
        m.put("oversubscribed", enrollmentService.isOversubscribed(c.getId(), round));
        m.put("full", waitlistService.isFull(c.getId(), round));
        if (userId != null) {
            var enr = enrollmentService.getEnrollment(userId, c.getId(), round);
            m.put("myStatus", enr != null ? enr.getStatus() : null);
            m.put("myPoints", enr != null ? enr.getPoints() : null);
            var wl = waitlistService.findActive(userId, c.getId(), round).orElse(null);
            m.put("myWaitStatus", wl != null ? wl.getStatus() : null);
            m.put("myWaitPosition", wl != null ? waitlistService.position(wl) : null);
            m.put("myWaitId", wl != null ? wl.getId() : null);
            var it = intentService.find(userId, c.getId());
            m.put("myIntent", it != null);
            m.put("myIntentPoints", it != null ? it.getDesiredPoints() : null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rt = ratings == null ? null : (Map<String, Object>) ratings.get(String.valueOf(c.getId()));
        m.put("rating", rt != null ? rt.get("avg") : null);
        m.put("ratingCount", rt != null ? rt.get("count") : 0);
        return m;
    }
}