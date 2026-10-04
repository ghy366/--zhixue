package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private UserRepository userRepo;

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users() {
        List<Map<String, Object>> list = userRepo.findAll().stream().map(u -> {
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
        return ApiResponse.ok(list);
    }

    // 管理员新增任意角色用户（教师/管理员/学生）
    @PostMapping("/users")
    public ApiResponse<User> createUser(@RequestBody Map<String, String> body) {
        String no = body.get("no");
        if (userRepo.findByNo(no).isPresent()) return ApiResponse.fail("该账号已存在");
        User u = new User();
        u.setNo(no);
        u.setPassword(body.getOrDefault("password", "123456"));
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
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").isEmpty()) {
            u.setPassword(body.get("password"));
        }
        return ApiResponse.ok("更新成功", userRepo.save(u));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userRepo.deleteById(id);
        return ApiResponse.ok("已删除", null);
    }
}