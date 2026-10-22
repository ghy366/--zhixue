package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.User;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.GradeService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/grades")
public class GradeController {

    @Autowired private GradeService gradeService;
    @Autowired private UserRepository userRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private EnrollmentService enrollmentService;

    // 老师查看某课程的学生成绩：以本轮已录取选课记录为准（未录入成绩的学生也显示，成绩为空）
    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> courseGrades(@PathVariable Long courseId) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> enrolls = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .sorted(Comparator.comparing(Enrollment::getId))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : enrolls) {
            User u = userRepo.findById(e.getStudentId()).orElse(null);
            Grade g = gradeService.findByStudentAndCourse(e.getStudentId(), courseId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gradeId", g != null ? g.getId() : null);
            m.put("studentId", e.getStudentId());
            m.put("studentNo", u != null ? u.getNo() : "");
            m.put("studentName", u != null ? u.getName() : "未知");
            m.put("studentMajor", u != null ? u.getMajor() : "");
            m.put("studentClass", u != null ? u.getClassNo() : "");
            m.put("regularScore", g != null ? g.getRegularScore() : null);
            m.put("finalScore", g != null ? g.getFinalScore() : null);
            m.put("totalScore", g != null ? g.getTotalScore() : null);
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    // 老师录入/更新成绩
    @PostMapping
    public ApiResponse<Grade> saveGrade(@RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Double regular = body.get("regularScore") != null ? Double.valueOf(body.get("regularScore").toString()) : null;
        Double finalScore = body.get("finalScore") != null ? Double.valueOf(body.get("finalScore").toString()) : null;
        Grade g = gradeService.save(studentId, courseId, regular, finalScore);
        return ApiResponse.ok("成绩已保存", g);
    }

    // 学生查看自己的成绩
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> myGrades(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        List<Grade> grades = gradeService.findByStudent(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Grade g : grades) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", g.getCourseId());
            m.put("regularScore", g.getRegularScore());
            m.put("finalScore", g.getFinalScore());
            m.put("totalScore", g.getTotalScore());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }
}
