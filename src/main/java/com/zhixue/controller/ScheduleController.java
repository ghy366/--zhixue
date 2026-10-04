package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.ScheduleService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Autowired private ScheduleService scheduleService;
    @Autowired private UserRepository userRepo;

    /** 冲突检测（仅学生）：时间/校区/考试冲突 */
    @GetMapping("/conflicts")
    public ApiResponse<Map<String, Object>> conflicts(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生可检测冲突");
        return ApiResponse.ok(scheduleService.conflicts(userId));
    }

    /** 智能排课（仅学生）：锁定 + 候选，生成无冲突课表 */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> body,
                                                     @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生可使用智能排课");
        List<Long> candidates = new ArrayList<>();
        List<Long> locks = new ArrayList<>();
        if (body.get("candidates") instanceof List<?> l1) for (Object o : l1) candidates.add(Long.valueOf(o.toString()));
        if (body.get("locks") instanceof List<?> l2) for (Object o : l2) locks.add(Long.valueOf(o.toString()));
        return ApiResponse.ok(scheduleService.generate(userId, candidates, locks));
    }
}
