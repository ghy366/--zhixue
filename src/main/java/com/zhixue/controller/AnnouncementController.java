package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.AnnouncementService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 系统公告 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    @Autowired private AnnouncementService annService;
    @Autowired private UserRepository userRepo;

    private Long uid(String token) {
        return token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
    }

    private boolean isAdmin(Long userId) {
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        return u != null && "admin".equals(u.getRole());
    }

    /** 前端传毫秒时间戳；空串表示清除有效期 */
    private LocalDateTime parseTime(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(v.toString().trim())), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Integer limit,
                                                       @RequestParam(required = false) String all,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = uid(token);
        boolean admin = "1".equals(all) && isAdmin(userId);
        return ApiResponse.ok(annService.list(limit, userId, admin));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(Map.of("count", annService.unreadCount(uid(token))));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String token) {
        annService.markRead(uid(token), id);
        return ApiResponse.ok("已读", null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@RequestHeader(value = "Authorization", required = false) String token) {
        annService.markAllRead(uid(token));
        return ApiResponse.ok("全部已读", null);
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> r = annService.create(uid(token),
                body.get("title") == null ? null : body.get("title").toString(),
                body.get("content") == null ? null : body.get("content").toString(),
                body.get("type") == null ? null : body.get("type").toString(),
                body.get("pinned") == null ? null : Boolean.valueOf(body.get("pinned").toString()),
                parseTime(body.get("expireTime")));
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = annService.update(id,
                body.get("title") == null ? null : body.get("title").toString(),
                body.get("content") == null ? null : body.get("content").toString(),
                body.get("type") == null ? null : body.get("type").toString(),
                body.get("pinned") == null ? null : Boolean.valueOf(body.get("pinned").toString()),
                parseTime(body.get("expireTime")),
                body.containsKey("expireTime"));
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Map<String, Object> r = annService.delete(id);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }
}
