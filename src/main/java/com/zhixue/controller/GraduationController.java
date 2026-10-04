package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.GraduationService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/graduation")
public class GraduationController {

    @Autowired private GraduationService graduationService;
    @Autowired private UserRepository userRepo;

    /** 毕业进度汇总（仅学生） */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生可查看毕业进度");
        return ApiResponse.ok(graduationService.summary(userId));
    }
}
