package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.User;
import com.zhixue.entity.Waitlist;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.CourseService;
import com.zhixue.service.WaitlistService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    @Autowired private WaitlistService waitlistService;
    @Autowired private CourseService courseService;
    @Autowired private UserRepository userRepo;

    @PostMapping("/join")
    public ApiResponse<Map<String, Object>> join(@RequestBody Map<String, Object> body,
                                                 @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Map<String, Object> r = waitlistService.join(userId, courseId);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), (Map<String, Object>) r.get("data"));
        return ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body,
                                    @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Map<String, Object> r = waitlistService.cancel(userId, courseId);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/confirm/{id}")
    public ApiResponse<Void> confirm(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Map<String, Object> r = waitlistService.confirm(id, userId);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int round = waitlistService.getConfig().getCurrentRound();
        List<Waitlist> list = waitlistService.activeForStudent(userId, round);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Waitlist w : list) {
            Course c = courseService.findById(w.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("waitlistId", w.getId());
            m.put("courseId", w.getCourseId());
            m.put("courseName", c != null ? c.getName() : "");
            m.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
            m.put("credit", c != null ? c.getCredit() : 0);
            int pos = waitlistService.position(w);
            m.put("status", w.getStatus());
            m.put("position", pos);
            m.put("estimatedWait", waitlistService.estimatedWait(w.getCourseId(), pos));
            m.put("queueTotal", waitlistService.activeForCourse(w.getCourseId(), round).size());
            m.put("deadline", w.getDeadline() != null ? java.sql.Timestamp.valueOf(w.getDeadline()).getTime() : null);
            m.put("joinTime", w.getJoinTime() != null ? java.sql.Timestamp.valueOf(w.getJoinTime()).getTime() : null);
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> courseQueue(@PathVariable Long courseId) {
        int round = waitlistService.getConfig().getCurrentRound();
        List<Waitlist> list = waitlistService.activeForCourse(courseId, round);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Waitlist w : list) {
            User stu = userRepo.findById(w.getStudentId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("waitlistId", w.getId());
            m.put("studentId", w.getStudentId());
            m.put("studentName", stu != null ? stu.getName() : "未知");
            m.put("studentNo", stu != null ? stu.getNo() : "");
            m.put("studentMajor", stu != null ? stu.getMajor() : "");
            m.put("status", w.getStatus());
            m.put("position", waitlistService.position(w));
            m.put("deadline", w.getDeadline() != null ? java.sql.Timestamp.valueOf(w.getDeadline()).getTime() : null);
            result.add(m);
        }
        return ApiResponse.ok(result);
    }
}
