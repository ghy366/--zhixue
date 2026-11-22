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
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        /* 角色入口校验：登录页按所选角色登录，选错入口给出明确提示。
         * 密码已验证正确（本人操作），不记入限流失败计数；role 未传时不校验（兼容旧客户端）。 */
        String wantRole = body.get("role");
        if (wantRole != null && !wantRole.isBlank() && !wantRole.equals(u.getRole())) {
            String cn = "student".equals(u.getRole()) ? "学生" : "teacher".equals(u.getRole()) ? "教师" : "管理员";
            return ApiResponse.fail("该账号是「" + cn + "」账号，请切换到「" + cn + "」入口登录");
        }
        // 学生登录时自动安排必修课（幂等）；失败不阻断登录，但必须留痕——静默吞掉会掩盖排课故障
        if ("student".equals(u.getRole())) {
            try {
                enrollmentService.ensureRequiredEnrollments(u.getId());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AuthController.class)
                        .error("学生 {} 自动排必修失败：{}", u.getNo(), e.toString(), e);
            }
        }
        String token = SessionStore.createToken(u.getId(), u.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", safeUser(u));
        return ApiResponse.ok("登录成功", data);
    }

    /* ============================================================
     * 不设自助注册：与真实高校教务一致，账号由教务处统一开户
     * （管理端「新增用户 / Excel 批量导入」），忘记密码由管理员重置。
     * 因此本控制器不提供 /register 与 /register-options 接口。
     * ============================================================ */

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
        map.put("college", u.getCollege());
        map.put("major", u.getMajor());
        map.put("grade", u.getGrade());
        map.put("classNo", u.getClassNo());
        map.put("phone", u.getPhone());
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
        // 姓名/学院/专业/年级/班级属于教务档案，学生不可自行修改（仅管理员在用户管理页维护）。
        // 前端已锁定这些字段，这里再做服务端兜底，防止直接调接口篡改。
        if ("admin".equals(u.getRole())) {
            if (body.containsKey("name")) u.setName(body.get("name"));
            if (body.containsKey("college")) u.setCollege(body.get("college"));
            if (body.containsKey("major")) u.setMajor(body.get("major"));
            if (body.containsKey("classNo")) u.setClassNo(body.get("classNo"));
        }
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
    /**
     * 客户端 IP：使用容器侧的 getRemoteAddr，不采信客户端可控的 X-Forwarded-For。
     * 本项目为直连部署（无反向代理）；若将来置于可信代理后，应由代理覆盖 remoteAddr
     * 或在此处按可信代理白名单校验后再取 XFF，否则伪造 XFF 可绕过登录失败限流（撞库）。
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

}