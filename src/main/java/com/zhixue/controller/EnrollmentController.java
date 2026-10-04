package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.User;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.CourseService;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.GradeService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseService courseService;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private GradeService gradeService;

    @PostMapping("/enroll")
    public ApiResponse<Void> enroll(@RequestBody Map<String, Object> body,
                                    @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long courseId = Long.valueOf(body.get("courseId").toString());
        int points = Integer.parseInt(body.get("points").toString());
        Map<String, Object> r = enrollmentService.enroll(userId, courseId, points);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/drop")
    public ApiResponse<Void> drop(@RequestBody Map<String, Object> body,
                                  @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Map<String, Object> r = enrollmentService.drop(userId, courseId);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String token,
                                                       @RequestParam(required = false) Integer round) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (round == null) round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> list = enrollmentRepo.findByStudentIdAndRound(userId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus())).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : list) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;   // 课程已被删除，跳过该脏记录
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("courseId", e.getCourseId());
            m.put("courseName", c != null ? c.getName() : "");
            m.put("category", c != null ? c.getCategory() : "");
            m.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
            m.put("credit", c != null ? c.getCredit() : 0);
            m.put("points", e.getPoints());
            m.put("status", e.getStatus());
            m.put("round", e.getRound());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/approved")
    public ApiResponse<List<Map<String, Object>>> approved(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> list = enrollmentRepo.findByStudentIdAndRound(userId, round).stream()
                .filter(e -> "approved".equals(e.getStatus())).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : list) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("credit", c.getCredit());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/points")
    public ApiResponse<Map<String, Object>> points(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        Map<String, Object> m = new HashMap<>();
        m.put("round", round);
        m.put("total", EnrollmentService.POINTS_PER_ROUND);
        m.put("used", enrollmentService.getUsedPoints(userId, round));
        m.put("remaining", enrollmentService.getRemainingPoints(userId, round));
        return ApiResponse.ok(m);
    }

    @GetMapping("/scores")
    public ApiResponse<List<Map<String, Object>>> scores(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        List<Enrollment> list = enrollmentRepo.findByStudentId(userId).stream()
                .filter(e -> "approved".equals(e.getStatus())).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : list) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;
            Grade g = gradeService.findByStudentAndCourse(userId, c.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("credit", c.getCredit());
            m.put("regularScore", g != null ? g.getRegularScore() : null);
            m.put("finalScore", g != null ? g.getFinalScore() : null);
            m.put("totalScore", g != null ? g.getTotalScore() : null);
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> courseEnrollments(@PathVariable Long courseId) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus()))
                .sorted((a, b) -> b.getPoints() - a.getPoints())
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : list) {
            User stu = userRepo.findById(e.getStudentId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("studentName", stu != null ? stu.getName() : "未知");
            m.put("studentNo", stu != null ? stu.getNo() : "");
            m.put("studentMajor", stu != null ? stu.getMajor() : "");
            m.put("studentClass", stu != null ? stu.getClassNo() : "");
            m.put("studentPhone", stu != null ? stu.getPhone() : "");
            m.put("points", e.getPoints());
            m.put("status", e.getStatus());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/settle")
    public ApiResponse<Map<String, Object>> settle(@RequestBody(required = false) Map<String, Object> body) {
        Integer round = (body == null || body.get("round") == null)
                ? null : Integer.parseInt(body.get("round").toString());
        Map<String, Object> r = enrollmentService.settleRound(round);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        return ApiResponse.ok((String) r.get("msg"), data);
    }

    @PostMapping("/approve")
    public ApiResponse<Void> approve(@RequestBody Map<String, Object> body) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        @SuppressWarnings("unchecked")
        List<Long> selected = ((List<Object>) body.get("studentIds")).stream()
                .map(o -> Long.valueOf(o.toString())).toList();
        Map<String, Object> r = enrollmentService.approve(courseId, selected);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }
}