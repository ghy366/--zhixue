package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.DropRequestService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 轮次结束后的退课申请：学生查看自己的申请，任课老师/管理员审批。 */
@RestController
@RequestMapping("/api/drop-requests")
public class DropRequestController {

    @Autowired private DropRequestService dropRequestService;
    @Autowired private UserRepository userRepo;

    private Long uid(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        return SessionStore.getUserId(authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim());
    }

    private String role(Long userId) {
        if (userId == null) return null;
        User u = userRepo.findById(userId).orElse(null);
        return u == null ? null : u.getRole();
    }

    private boolean isAdmin(Long userId) { return "admin".equals(role(userId)); }

    /** 学生：我的退课申请（用于「我的课程」显示待处理状态） */
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        if (!"student".equals(role(userId))) return ApiResponse.fail("仅学生可查看自己的退课申请");
        return ApiResponse.ok(dropRequestService.listForStudent(userId));
    }

    /** 学生撤回自己的待处理退课申请：教师端待处理列表同步消失 */
    @PostMapping("/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        if (!"student".equals(role(userId))) return ApiResponse.fail("仅学生可撤回自己的退课申请");
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        Map<String, Object> res = dropRequestService.cancel(id, userId);
        if (Boolean.TRUE.equals(res.get("success"))) return ApiResponse.ok((String) res.get("msg"), res);
        return ApiResponse.fail((String) res.get("msg"));
    }

    /** 老师看自己课程的申请；管理员看全部 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        String r = role(userId);
        if (!"teacher".equals(r) && !"admin".equals(r)) return ApiResponse.fail("仅任课老师或管理员可处理退课申请");
        return ApiResponse.ok(dropRequestService.listForTeacher(userId, "admin".equals(r)));
    }

    /** 同意退课：真实退课 + 通知学生 */
    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        String r = role(userId);
        if (!"teacher".equals(r) && !"admin".equals(r)) return ApiResponse.fail("仅任课老师或管理员可处理退课申请");
        String remark = body == null || body.get("remark") == null ? "" : body.get("remark").toString();
        Map<String, Object> res = dropRequestService.approve(id, userId, "admin".equals(r), remark);
        if (Boolean.TRUE.equals(res.get("success"))) return ApiResponse.ok((String) res.get("msg"), (Map<String, Object>) res.get("data"));
        return ApiResponse.fail((String) res.get("msg"));
    }

    /** 驳回：保留选课记录 + 通知学生 */
    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        String r = role(userId);
        if (!"teacher".equals(r) && !"admin".equals(r)) return ApiResponse.fail("仅任课老师或管理员可处理退课申请");
        String remark = body == null || body.get("remark") == null ? "" : body.get("remark").toString();
        Map<String, Object> res = dropRequestService.reject(id, userId, "admin".equals(r), remark);
        if (Boolean.TRUE.equals(res.get("success"))) return ApiResponse.ok((String) res.get("msg"), (Map<String, Object>) res.get("data"));
        return ApiResponse.fail((String) res.get("msg"));
    }
}
