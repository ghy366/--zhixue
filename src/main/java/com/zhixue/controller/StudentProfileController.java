package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.StudentProfileService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 教师端学生画像（教师 / 管理员可查看） */
@RestController
@RequestMapping("/api/students")
public class StudentProfileController {

    @Autowired private StudentProfileService profileService;
    @Autowired private UserRepository userRepo;

    @GetMapping("/{studentId}/profile")
    public ApiResponse<Map<String, Object>> profile(@PathVariable Long studentId,
                                                    @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        if (u == null) return ApiResponse.fail("未登录");
        if (!"teacher".equals(u.getRole()) && !"admin".equals(u.getRole())) return ApiResponse.fail("仅教师/管理员可查看");
        return ApiResponse.ok(profileService.profile(studentId));
    }
}
