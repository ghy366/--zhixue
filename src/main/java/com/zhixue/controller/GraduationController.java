package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.GraduationApplication;
import com.zhixue.entity.User;
import com.zhixue.repository.GraduationApplicationRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.GraduationService;
import com.zhixue.service.GradeService;
import com.zhixue.service.GraduateWorkspaceService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/graduation")
public class GraduationController {

    @Autowired private GraduationService graduationService;
    @Autowired private UserRepository userRepo;
    @Autowired private GradeService gradeService;
    @Autowired private GraduationApplicationRepository applicationRepo;
    @Autowired private GraduateWorkspaceService wsService;

    @GetMapping("/gpa")
    public ApiResponse<Map<String, Object>> gpa(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(gradeService.gpaOfStudent(userId));
    }

    /** 专业 GPA 排名（同 major 学生按 GPA 降序的名次/百分位，用于奖学金评定等场景） */
    @GetMapping("/ranking")
    public ApiResponse<Map<String, Object>> ranking(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User me = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (me == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(gradeService.majorRanking(me));
    }

    /** 毕业进度汇总（仅学生） */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生可查看毕业进度");
        Map<String, Object> sum = graduationService.summary(userId);
        // 申请状态：NONE 未申请 / PENDING 审核中 / APPROVED 已批准 / REJECTED 已驳回
        applicationRepo.findFirstByStudentIdOrderByIdDesc(userId).ifPresentOrElse(
                a -> { sum.put("applicationStatus", a.getStatus()); sum.put("applicationRemark", a.getRemark()); },
                () -> sum.put("applicationStatus", "NONE"));
        return ApiResponse.ok(sum);
    }

    /** 学生提交毕业申请（须已满足全部毕业要求） */
    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        Map<String, Object> sum = graduationService.summary(userId);
        if (!Boolean.TRUE.equals(sum.get("graduated"))) {
            return ApiResponse.fail("尚未满足全部毕业要求，无法提交申请");
        }
        Optional<GraduationApplication> last = applicationRepo.findFirstByStudentIdOrderByIdDesc(userId);
        if (last.isPresent() && "PENDING".equals(last.get().getStatus())) {
            return ApiResponse.fail("毕业申请已在审核中，请耐心等待");
        }
        if (last.isPresent() && "APPROVED".equals(last.get().getStatus())) {
            return ApiResponse.fail("毕业申请已通过，无需重复提交");
        }
        Map<String, Object> total = (Map<String, Object>) sum.get("total");
        Map<String, Object> courses = (Map<String, Object>) sum.get("courses");
        GraduationApplication a = new GraduationApplication();
        a.setStudentId(userId);
        a.setCreditsEarned((Integer) total.get("earned"));
        a.setCreditsRequired((Integer) total.get("required"));
        a.setCourseCount((Integer) courses.get("earned"));
        Map<String, Object> gpaInfo = gradeService.gpaOfStudent(userId);
        a.setGpaSnapshot(gpaInfo.get("gpa") == null ? null
                : Double.valueOf(String.valueOf(gpaInfo.get("gpa"))));
        // 毕设总评快照（导师评分 40% + 答辩组评分 60%）
        a.setThesisScore(wsService.studentThesisScore(userId));
        a.setStatus("PENDING");
        a.setApplyTime(LocalDateTime.now());
        applicationRepo.save(a);
        return ApiResponse.ok("毕业申请已提交，请等待教务审核", statusMap(a));
    }

    /** 学生查自己的申请状态 */
    @GetMapping("/my-application")
    public ApiResponse<Map<String, Object>> myApplication(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(applicationRepo.findFirstByStudentIdOrderByIdDesc(userId)
                .map(this::statusMap).orElse(Collections.singletonMap("status", "NONE")));
    }

    /** 管理员：毕业申请列表 */
    @GetMapping("/applications")
    public ApiResponse<List<Map<String, Object>>> applications(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User admin = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (admin == null || !"admin".equals(admin.getRole())) return ApiResponse.fail("仅管理员可查看毕业申请");
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraduationApplication a : applicationRepo.findAllByOrderByIdDesc()) {
            Map<String, Object> m = statusMap(a);
            User stu = userRepo.findById(a.getStudentId()).orElse(null);
            m.put("studentNo", stu != null ? stu.getNo() : "");
            m.put("studentName", stu != null ? stu.getName() : "未知");
            m.put("college", stu != null ? stu.getCollege() : "");
            m.put("major", stu != null ? stu.getMajor() : "");
            m.put("grade", stu != null ? stu.getGrade() : null);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 管理员审批：APPROVED 批准毕业 / REJECTED 驳回（须填原因） */
    @PostMapping("/applications/{id}/audit")
    public ApiResponse<Map<String, Object>> audit(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body,
                                                  @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User admin = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (admin == null || !"admin".equals(admin.getRole())) return ApiResponse.fail("仅管理员可审批毕业申请");
        String status = String.valueOf(body.getOrDefault("status", ""));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) return ApiResponse.fail("无效的审核状态");
        String remark = body.get("remark") == null ? "" : body.get("remark").toString().trim();
        if ("REJECTED".equals(status) && remark.isEmpty()) return ApiResponse.fail("驳回时请填写原因");
        GraduationApplication a = applicationRepo.findById(id).orElse(null);
        if (a == null) return ApiResponse.fail("申请不存在");
        a.setStatus(status);
        a.setRemark(remark);
        a.setReviewTime(LocalDateTime.now());
        a.setReviewerId(userId);
        a.setReviewerName(admin.getName());
        applicationRepo.save(a);
        return ApiResponse.ok("APPROVED".equals(status) ? "已批准毕业 🎓" : "已驳回申请", statusMap(a));
    }

    private Map<String, Object> statusMap(GraduationApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("studentId", a.getStudentId());
        m.put("creditsEarned", a.getCreditsEarned());
        m.put("creditsRequired", a.getCreditsRequired());
        m.put("courseCount", a.getCourseCount());
        m.put("gpa", a.getGpaSnapshot());
        m.put("thesisScore", a.getThesisScore());
        m.put("status", a.getStatus());
        m.put("applyTime", a.getApplyTime());
        m.put("reviewTime", a.getReviewTime());
        m.put("reviewerName", a.getReviewerName());
        m.put("remark", a.getRemark());
        return m;
    }
}
