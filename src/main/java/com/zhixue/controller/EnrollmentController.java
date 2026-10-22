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

    @PostMapping("/reselect")
    public ApiResponse<Void> reselect(@RequestBody Map<String, Object> body,
                                      @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long oldCourseId = Long.valueOf(body.get("oldCourseId").toString());
        Long newCourseId = Long.valueOf(body.get("newCourseId").toString());
        int points = Integer.parseInt(body.get("points").toString());
        Map<String, Object> r = enrollmentService.reselect(userId, oldCourseId, newCourseId, points);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String token,
                                                       @RequestParam(required = false) Integer round) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (round == null) round = enrollmentService.getSystemConfig().getCurrentRound();
        // 必修课由系统自动排课：读取前先补齐，保证课表/我的课程里一定有必修课
        try { enrollmentService.ensureRequiredEnrollments(userId); } catch (Exception ignored) {}
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
            Grade g = gradeService.findByStudentAndCourse(userId, e.getCourseId());
            if (g != null && g.getTotalScore() != null) {
                double sc = g.getTotalScore();
                m.put("grade", sc == Math.floor(sc) ? String.valueOf((long) sc) : String.valueOf(sc));
            } else {
                m.put("grade", null);
            }
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
        // 同一门课可能存在多条历史轮次记录：按课程去重，优先保留已有成绩的那条
        Map<Long, Enrollment> byCourse = new LinkedHashMap<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(userId)) {
            if (!"approved".equals(e.getStatus())) continue;
            Enrollment prev = byCourse.get(e.getCourseId());
            if (prev == null) { byCourse.put(e.getCourseId(), e); continue; }
            boolean curScored = gradeService.findByStudentAndCourse(userId, e.getCourseId()) != null;
            if (curScored) byCourse.put(e.getCourseId(), e);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : byCourse.values()) {
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

    @PostMapping("/enroll-batch")
    public ApiResponse<Map<String, Object>> enrollBatch(@RequestBody Map<String, Object> body,
                                                         @RequestHeader("Authorization") String token) {
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("未登录");
        List<?> raw = (List<?>) body.get("courseIds");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw != null) { for (Object o : raw) ids.add(Long.valueOf(o.toString())); }
        int points = body.get("points") == null ? 1 : Integer.parseInt(body.get("points").toString());
        return ApiResponse.ok(enrollmentService.enrollBatch(uid, ids, points));
    }

    @PostMapping("/drop-batch")
    public ApiResponse<Map<String, Object>> dropBatch(@RequestBody Map<String, Object> body,
                                                       @RequestHeader("Authorization") String token) {
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("未登录");
        List<?> raw = (List<?>) body.get("courseIds");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw != null) { for (Object o : raw) ids.add(Long.valueOf(o.toString())); }
        return ApiResponse.ok(enrollmentService.dropBatch(uid, ids));
    }
}
