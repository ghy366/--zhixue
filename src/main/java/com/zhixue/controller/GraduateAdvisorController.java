package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.GraduateAdvisorService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/advisor")
public class GraduateAdvisorController {

    @Autowired private GraduateAdvisorService gaService;
    @Autowired private UserRepository userRepo;

    private User authUser(String token) {
        String t = token.replace("Bearer ", "");
        Long uid = SessionStore.getUserId(t);
        return uid != null ? userRepo.findById(uid).orElse(null) : null;
    }

    private ApiResponse<?> needLogin() { return ApiResponse.fail("登录已过期，请重新登录"); }
    private ApiResponse<?> needRole(String role) { return ApiResponse.fail("仅" + role + "可访问"); }

    @GetMapping("/config")
    public ApiResponse<?> config(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        return ApiResponse.ok(gaService.cfgMap());
    }

    @GetMapping("/teachers")
    public ApiResponse<?> teachers(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"student".equals(u.getRole())) return needRole("学生");
        return ApiResponse.ok(gaService.listTeachers());
    }

    @GetMapping("/mine")
    public ApiResponse<?> mine(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"student".equals(u.getRole())) return needRole("学生");
        return ApiResponse.ok(gaService.mineStudent(u.getId()));
    }

    @PostMapping("/apply")
    public ApiResponse<?> apply(@RequestHeader("Authorization") String token,
                                @RequestBody Map<String, Object> body) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"student".equals(u.getRole())) return needRole("学生");
        Object ti = body.get("teacherId");
        if (ti == null) return ApiResponse.fail("缺少 teacherId");
        Long teacherId = Long.valueOf(ti.toString());
        String topic = (String) body.get("topic");
        String pref = (String) body.get("preferenceText");
        Map<String, Object> r = gaService.apply(u.getId(), teacherId, topic, pref);
        if (Boolean.TRUE.equals(r.get("success"))) {
            return ApiResponse.ok((String) r.get("msg"), r.get("data"));
        }
        return ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/cancel/{id}")
    public ApiResponse<?> cancel(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"student".equals(u.getRole())) return needRole("学生");
        Map<String, Object> r = gaService.cancelByStudent(u.getId(), id);
        if (Boolean.TRUE.equals(r.get("success"))) return ApiResponse.ok((String) r.get("msg"));
        return ApiResponse.fail((String) r.get("msg"));
    }

    @GetMapping("/teacher/list")
    public ApiResponse<?> teacherList(@RequestHeader("Authorization") String token,
                                      @RequestParam(required = false) String status) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"teacher".equals(u.getRole())) return needRole("导师");
        return ApiResponse.ok(gaService.mineTeacher(u.getId(), status));
    }

    @GetMapping("/teacher/stats")
    public ApiResponse<?> teacherStats(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"teacher".equals(u.getRole())) return needRole("导师");
        return ApiResponse.ok(gaService.teacherStats(u.getId()));
    }

    @PostMapping("/teacher/decide")
    public ApiResponse<?> decide(@RequestHeader("Authorization") String token,
                                 @RequestBody Map<String, Object> body) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"teacher".equals(u.getRole())) return needRole("导师");
        Object ii = body.get("id");
        String decision = (String) body.get("decision");
        if (ii == null || decision == null) return ApiResponse.fail("缺少 id / decision");
        Long id = Long.valueOf(ii.toString());
        String reply = (String) body.get("reply");
        Map<String, Object> r = gaService.decide(u.getId(), id, decision, reply);
        if (Boolean.TRUE.equals(r.get("success"))) return ApiResponse.ok((String) r.get("msg"));
        return ApiResponse.fail((String) r.get("msg"));
    }

    @GetMapping("/admin/overview")
    public ApiResponse<?> adminOverview(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"admin".equals(u.getRole())) return needRole("管理员");
        return ApiResponse.ok(gaService.adminOverview());
    }

    @GetMapping("/admin/list")
    public ApiResponse<?> adminList(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"admin".equals(u.getRole())) return needRole("管理员");
        return ApiResponse.ok(gaService.adminList());
    }

    @GetMapping("/admin/capacities")
    public ApiResponse<?> adminCapacities(@RequestHeader("Authorization") String token) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"admin".equals(u.getRole())) return needRole("管理员");
        return ApiResponse.ok(gaService.allTeacherCapacities());
    }

    @PostMapping("/admin/cfg")
    public ApiResponse<?> adminCfg(@RequestHeader("Authorization") String token,
                                   @RequestBody Map<String, Object> body) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"admin".equals(u.getRole())) return needRole("管理员");
        Boolean open = null;
        Object op = body.get("open");
        if (op != null) open = Boolean.valueOf(op.toString());
        Integer defaultCap = null;
        Object dc = body.get("defaultCapacity");
        if (dc != null) defaultCap = Integer.valueOf(dc.toString());
        Long teacherId = null;
        Object ti = body.get("teacherId");
        if (ti != null) teacherId = Long.valueOf(ti.toString());
        Integer teacherCap = null;
        Object tc = body.get("teacherCapacity");
        if (tc != null) teacherCap = Integer.valueOf(tc.toString());
        Map<String, Object> r = gaService.saveCfg(open, defaultCap, teacherId, teacherCap);
        if (Boolean.TRUE.equals(r.get("success"))) {
            return ApiResponse.ok((String) r.get("msg"), r.get("data"));
        }
        return ApiResponse.fail((String) r.get("msg"));
    }

    @DeleteMapping("/admin/{id}")
    public ApiResponse<?> adminDelete(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        User u = authUser(token);
        if (u == null) return needLogin();
        if (!"admin".equals(u.getRole())) return needRole("管理员");
        Map<String, Object> r = gaService.adminDelete(id);
        if (Boolean.TRUE.equals(r.get("success"))) return ApiResponse.ok((String) r.get("msg"));
        return ApiResponse.fail((String) r.get("msg"));
    }
}
