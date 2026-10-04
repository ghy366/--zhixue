package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.AnnouncementService;
import com.zhixue.service.NotificationService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/** 站内消息 + SSE 实时推送 */
@RestController
@RequestMapping("/api/notify")
public class NotifyController {

    @Autowired private NotificationService notifyService;
    @Autowired private AnnouncementService annService;
    @Autowired private UserRepository userRepo;

    private Long uid(String token) {
        return token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
    }

    /** SSE 订阅（EventSource 无法带 Header，token 走查询参数） */
    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam("token") String token) {
        Long userId = SessionStore.getUserId(token);
        if (userId == null) {
            SseEmitter e = new SseEmitter(500L);
            e.complete();
            return e;
        }
        return notifyService.subscribe(userId);
    }

    @GetMapping("/messages")
    public ApiResponse<List<Map<String, Object>>> messages(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(notifyService.list(uid(token)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unread(@RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = uid(token);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", notifyService.unreadCount(userId));
        long annUnread = 0;
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        if (u != null && !"admin".equals(u.getRole())) annUnread = annService.unreadCount(userId);
        m.put("announcement", annUnread);
        return ApiResponse.ok(m);
    }

    @PostMapping("/read/{id}")
    public ApiResponse<Void> read(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String token) {
        notifyService.markRead(uid(token), id);
        return ApiResponse.ok("已读", null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@RequestHeader(value = "Authorization", required = false) String token) {
        notifyService.markAllRead(uid(token));
        return ApiResponse.ok("全部已读", null);
    }
}
