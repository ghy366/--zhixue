package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.service.AuthService;
import com.zhixue.service.EnrollmentService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private EnrollmentService enrollmentService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        String no = body.get("no");
        String password = com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("password"));
        String ip = clientIp(request);
        /* 撞库防护：同 IP + 同账号 1 分钟内失败 5 次 → 锁定 5 分钟（登录成功即清零） */
        if (com.zhixue.util.LoginRateLimiter.isLocked(ip, no)) {
            return ApiResponse.fail("登录失败次数过多，该账号已被临时锁定，请 5 分钟后再试或联系管理员重置密码");
        }
        User u = authService.login(no, password);
        if (u == null) {
            boolean lockedNow = com.zhixue.util.LoginRateLimiter.recordFailure(ip, no);
            return ApiResponse.fail(lockedNow
                    ? "连续登录失败次数过多，该账号已被临时锁定 5 分钟"
                    : "账号或密码错误");
        }
        com.zhixue.util.LoginRateLimiter.reset(ip, no);
        // 学生登录时自动安排必修课（幂等）
        if ("student".equals(u.getRole())) {
            try { enrollmentService.ensureRequiredEnrollments(u.getId()); } catch (Exception ignored) {}
        }
        String token = SessionStore.createToken(u.getId(), u.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", safeUser(u));
        return ApiResponse.ok("登录成功", data);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        if (body.containsKey("password")) body.put("password", com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("password")));
        User u = authService.registerStudent(body);
        if (u == null) return ApiResponse.fail("该学号已注册");
        return ApiResponse.ok("注册成功，请登录", null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null) SessionStore.remove(token.replace("Bearer ", ""));
        return ApiResponse.ok("已退出", null);
    }
    @GetMapping("/me")
    public ApiResponse<java.util.Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        Long uid = com.zhixue.util.SessionStore.getUserId(token);
        if (uid == null) return ApiResponse.fail("未登录");
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", u.getId());
        map.put("no", u.getNo());
        map.put("name", u.getName());
        map.put("role", u.getRole());
        map.put("avatar", u.getAvatar());
        return ApiResponse.ok("ok", map);
    }


    @Autowired private com.zhixue.repository.UserRepository userRepo;

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestHeader(value = "Authorization", required = false) String token,
                                                          @RequestBody Map<String, String> body) {
        if (token == null) return ApiResponse.fail("未登录");
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("登录已失效，请重新登录");
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        if (body.containsKey("name")) u.setName(body.get("name"));
        if (body.containsKey("college")) u.setCollege(body.get("college"));
        if (body.containsKey("major")) u.setMajor(body.get("major"));
        if (body.containsKey("classNo")) u.setClassNo(body.get("classNo"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        userRepo.save(u);
        return ApiResponse.ok("更新成功", safeUser(u));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String token,
                                            @RequestBody Map<String, String> body) {
        if (token == null) return ApiResponse.fail("未登录");
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("登录已失效，请重新登录");
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        String oldPwd = com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("oldPassword"));
        String newPwd = com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("newPassword"));
        if (oldPwd == null || !authService.matches(oldPwd, u.getPassword())) return ApiResponse.fail("原密码错误");
        if (newPwd == null || newPwd.length() < 4) return ApiResponse.fail("新密码至少4位");
        u.setPassword(authService.encode(newPwd));
        userRepo.save(u);
        return ApiResponse.ok("密码修改成功", null);
    }

    public static Map<String, Object> safeUser(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("no", u.getNo());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("college", u.getCollege());
        m.put("major", u.getMajor());
        m.put("grade", u.getGrade());
        m.put("classNo", u.getClassNo());
        m.put("phone", u.getPhone());
        m.put("avatar", u.getAvatar());
        return m;
    }
    /** 客户端 IP：优先取反向代理透传的 X-Forwarded-For 首段 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

}