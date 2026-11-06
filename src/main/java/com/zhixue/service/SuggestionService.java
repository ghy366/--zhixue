package com.zhixue.service;

import com.zhixue.entity.Suggestion;
import com.zhixue.entity.User;
import com.zhixue.repository.SuggestionRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 建议箱：学生提交建议 → 管理员处理回复；两端各自有通知提醒 */
@Service
public class SuggestionService {

    @Autowired private SuggestionRepository sugRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private NotificationService notificationService;

    /** 学生提交建议；同时给全体管理员发站内通知 */
    @Transactional
    public Map<String, Object> create(Long userId, String category, String content) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (userId == null) { r.put("success", false); r.put("msg", "请先登录"); return r; }
        if (category == null || category.isBlank()) { r.put("success", false); r.put("msg", "请选择建议类型"); return r; }
        if (content == null || content.isBlank()) { r.put("success", false); r.put("msg", "请填写建议内容"); return r; }
        String text = content.trim();
        if (text.length() < 5) { r.put("success", false); r.put("msg", "建议内容至少 5 个字，说清楚一点更容易被采纳"); return r; }
        if (text.length() > 2000) { r.put("success", false); r.put("msg", "建议内容不能超过 2000 字"); return r; }

        Suggestion s = new Suggestion();
        s.setUserId(userId);
        User u = userRepo.findById(userId).orElse(null);
        s.setUserName(u == null ? "用户" + userId : u.getName());
        s.setCategory(category.trim());
        s.setContent(text);
        s.setStatus(Suggestion.STATUS_PENDING);
        s.setCreatedAt(LocalDateTime.now());
        sugRepo.save(s);

        for (User admin : userRepo.findByRole("admin")) {
            notificationService.notify(admin.getId(), "suggestion", "📩 收到新建议",
                    (s.getUserName() == null ? "" : s.getUserName() + "：") + preview(text), s.getId());
        }
        r.put("success", true);
        r.put("msg", "建议已提交，感谢你的反馈！");
        return r;
    }

    /** 学生端：我的建议 */
    public List<Map<String, Object>> listMine(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (userId == null) return out;
        for (Suggestion s : sugRepo.findByUserIdOrderByCreatedAtDesc(userId)) out.add(row(s, false));
        return out;
    }

    /** 管理员端：全部建议，可按状态过滤 */
    public List<Map<String, Object>> listAll(String status) {
        List<Suggestion> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? sugRepo.findAllByOrderByCreatedAtDesc()
                : sugRepo.findByStatusOrderByCreatedAtDesc(status);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Suggestion s : list) out.add(row(s, true));
        return out;
    }

    /** 管理员处理：改状态 + 回复；回复后通知提交人 */
    @Transactional
    public Map<String, Object> handle(Long id, String status, String reply) {
        Map<String, Object> r = new LinkedHashMap<>();
        Suggestion s = sugRepo.findById(id).orElse(null);
        if (s == null) { r.put("success", false); r.put("msg", "建议不存在"); return r; }
        if (!isValidStatus(status)) { r.put("success", false); r.put("msg", "无效的处理状态"); return r; }
        String rep = reply == null ? "" : reply.trim();
        if (Suggestion.STATUS_REJECTED.equals(status) && rep.isEmpty()) {
            r.put("success", false); r.put("msg", "婉拒时请填写回复说明原因"); return r;
        }
        s.setStatus(status);
        s.setAdminReply(rep.isEmpty() ? null : rep);
        s.setHandledAt(LocalDateTime.now());
        sugRepo.save(s);
        if (s.getUserId() != null) {
            notificationService.notify(s.getUserId(), "suggestion_reply", "你的建议有了新进展",
                    "「" + preview(s.getContent()) + "」" + statusCn(status), s.getId());
        }
        r.put("success", true);
        r.put("msg", "已更新为「" + statusCn(status) + "」");
        return r;
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (!sugRepo.existsById(id)) { r.put("success", false); r.put("msg", "建议不存在"); return r; }
        sugRepo.deleteById(id);
        r.put("success", true);
        r.put("msg", "已删除该建议");
        return r;
    }

    public Map<String, Object> statusSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", sugRepo.countByStatus(Suggestion.STATUS_PENDING));
        m.put("accepted", sugRepo.countByStatus(Suggestion.STATUS_ACCEPTED));
        m.put("done", sugRepo.countByStatus(Suggestion.STATUS_DONE));
        m.put("rejected", sugRepo.countByStatus(Suggestion.STATUS_REJECTED));
        m.put("total", sugRepo.count());
        return m;
    }

    /* ---------- 内部工具 ---------- */

    private Map<String, Object> row(Suggestion s, boolean withUser) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        if (withUser) {
            m.put("userId", s.getUserId());
            m.put("userName", s.getUserName());
        }
        m.put("category", s.getCategory());
        m.put("content", s.getContent());
        m.put("status", s.getStatus());
        m.put("statusCn", statusCn(s.getStatus()));
        m.put("adminReply", s.getAdminReply());
        m.put("createdAt", s.getCreatedAt() != null ? Timestamp.valueOf(s.getCreatedAt()).getTime() : null);
        m.put("handledAt", s.getHandledAt() != null ? Timestamp.valueOf(s.getHandledAt()).getTime() : null);
        return m;
    }

    private static boolean isValidStatus(String status) {
        return Suggestion.STATUS_PENDING.equals(status) || Suggestion.STATUS_ACCEPTED.equals(status)
                || Suggestion.STATUS_DONE.equals(status) || Suggestion.STATUS_REJECTED.equals(status);
    }

    public static String statusCn(String status) {
        if (status == null) return "待处理";
        return switch (status) {
            case Suggestion.STATUS_ACCEPTED -> "已采纳";
            case Suggestion.STATUS_DONE -> "已完成";
            case Suggestion.STATUS_REJECTED -> "未采纳";
            default -> "待处理";
        };
    }

    private static String preview(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }
}
