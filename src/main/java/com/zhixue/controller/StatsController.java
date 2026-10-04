package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.StatsService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端统计报表 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired private StatsService statsService;
    @Autowired private UserRepository userRepo;

    @GetMapping("/report")
    public ApiResponse<Map<String, Object>> report(@RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        if (u == null || !"admin".equals(u.getRole())) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.report());
    }
}
