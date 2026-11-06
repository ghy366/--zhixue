package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.SuggestionService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 建议箱：学生提交功能/优化建议，管理员处理回复 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    @Autowired private SuggestionService sugService;
    @Autowired private UserRepository userRepo;

    private Long uid(String token) {
        return token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
    }

    private boolean isAdmin(Long userId) {
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        return u != null && "admin".equals(u.getRole());
    }

    /** 提交建议（学生/教师均可，主要面向学生） */
    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> r = sugService.create(uid(token),
                body.get("category") == null ? null : body.get("category").toString(),
                body.get("content") == null ? null : body.get("content").toString());
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    /** 我的建议（学生端） */
    @GetMapping("/mine")
    public ApiResponse<?> mine(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(sugService.listMine(uid(token)));
    }

    /** 全部建议（管理员，可按状态过滤）+ 状态统计 */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String status,
                               @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = uid(token);
        if (!isAdmin(userId)) return ApiResponse.fail("仅管理员可查看全部建议");
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("items", sugService.listAll(status));
        data.put("summary", sugService.statusSummary());
        return ApiResponse.ok(data);
    }

    /** 处理建议（管理员）：改状态 + 回复 */
    @PostMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(uid(token))) return ApiResponse.fail("仅管理员可处理建议");
        Map<String, Object> r = sugService.handle(id,
                body.get("status") == null ? null : body.get("status").toString(),
                body.get("reply") == null ? "" : body.get("reply").toString());
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    /** 删除建议（管理员，清理灌水） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(uid(token))) return ApiResponse.fail("仅管理员可删除建议");
        Map<String, Object> r = sugService.delete(id);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }
}
