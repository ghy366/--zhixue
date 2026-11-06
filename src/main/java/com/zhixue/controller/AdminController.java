package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private UserRepository userRepo;
    @Autowired private com.zhixue.util.PasswordUtil passwordEncoder;

    /**
     * 用户列表（全校账号，服务端分页）。传 page/size 时返回 {items,total,page,size}，
     * 未传时保持旧的全量数组（旧调用方零破坏）。role 角色过滤；keyword 匹配 学号/姓名/学院/专业。
     */
    @GetMapping("/users")
    public ApiResponse<Object> users(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        List<User> all = userRepo.findAll();
        if (role != null && !role.isBlank()) {
            all = all.stream().filter(u -> role.equals(u.getRole())).toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            all = all.stream().filter(u -> (str(u.getNo()) + " " + str(u.getName()) + " "
                    + str(u.getCollege()) + " " + str(u.getMajor())).toLowerCase().contains(k)).toList();
        }
        List<Map<String, Object>> list = all.stream().map(u -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("no", u.getNo());
            m.put("name", u.getName());
            m.put("role", u.getRole());
            m.put("college", u.getCollege());
            m.put("major", u.getMajor());
            m.put("grade", u.getGrade());
            m.put("avatar", u.getAvatar());
            return m;
        }).collect(Collectors.toList());
        if (page == null || size == null || page < 1 || size < 1) {
            return ApiResponse.ok(list);   // 兼容模式
        }
        int from = Math.min(list.size(), (page - 1) * size);
        int to = Math.min(list.size(), from + size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", list.subList(from, to));
        data.put("total", list.size());
        data.put("page", page);
        data.put("size", size);
        return ApiResponse.ok(data);
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }

    // 管理员新增任意角色用户（教师/管理员/学生）
    @PostMapping("/users")
    public ApiResponse<User> createUser(@RequestBody Map<String, String> body) {
        String no = body.get("no");
        if (userRepo.findByNo(no).isPresent()) return ApiResponse.fail("该账号已存在");
        User u = new User();
        u.setNo(no);
        u.setPassword(passwordEncoder.encode(com.zhixue.util.RsaCipher.decryptIfEncrypted(body.getOrDefault("password", "123456"))));
        u.setName(body.get("name"));
        u.setRole(body.getOrDefault("role", "teacher"));
        u.setCollege(body.get("college"));
        u.setMajor(body.get("major"));
        String g = body.get("grade");
        if (g != null && !g.isEmpty()) u.setGrade(Integer.parseInt(g));
        u.setClassNo(body.get("classNo"));
        u.setPhone(body.get("phone"));
        u.setAvatar(body.getOrDefault("avatar", "👤"));
        return ApiResponse.ok("创建成功", userRepo.save(u));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User u = userRepo.findById(id).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        if (body.containsKey("name")) u.setName(body.get("name"));
        if (body.containsKey("college")) u.setCollege(body.get("college"));
        if (body.containsKey("major")) u.setMajor(body.get("major"));
        if (body.containsKey("classNo")) u.setClassNo(body.get("classNo"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        if (body.containsKey("grade")) {
            String g = body.get("grade");
            if (g == null || g.isEmpty()) {
                u.setGrade(null);            // 显式传空：允许清掉配错的年级
            } else {
                try { u.setGrade(Integer.parseInt(g)); } catch (NumberFormatException ignored) {}
            }
        }
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").isEmpty()) {
            u.setPassword(passwordEncoder.encode(com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("password"))));
        }
        return ApiResponse.ok("更新成功", userRepo.save(u));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userRepo.deleteById(id);
        return ApiResponse.ok("已删除", null);
    }

    /** 管理员一键重置密码 */
    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                          @RequestBody(required = false) Map<String, String> body) {
        User u = userRepo.findById(id).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        String newPwd = (body != null && body.get("newPassword") != null && !body.get("newPassword").isEmpty())
                ? com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("newPassword"))
                : "123456";
        u.setPassword(passwordEncoder.encode(newPwd));
        userRepo.save(u);
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("tempPassword", newPwd);
        return ApiResponse.ok("密码已重置为 " + newPwd, r);
    }
}