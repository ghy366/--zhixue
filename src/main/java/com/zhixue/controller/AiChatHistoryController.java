package com.zhixue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.AiChatMessage;
import com.zhixue.entity.AiChatSession;
import com.zhixue.repository.AiChatMessageRepository;
import com.zhixue.repository.AiChatSessionRepository;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * AI 助手会话持久化：登录用户的历史对话存 MySQL，不再只放浏览器 localStorage。
 * 归属校验按 token 解析 userId；未登录时前端自动降级为本地存储。
 */
@RestController
@RequestMapping("/api/ai/history")
public class AiChatHistoryController {

    @Autowired private AiChatSessionRepository sessionRepo;
    @Autowired private AiChatMessageRepository messageRepo;
    private final ObjectMapper om = new ObjectMapper();

    private Long uid(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
        return SessionStore.getUserId(token);
    }

    private long ts(LocalDateTime t) {
        return t == null ? 0L : java.sql.Timestamp.valueOf(t).getTime();
    }

    private AiChatSession owned(String id, Long userId) {
        AiChatSession s = sessionRepo.findById(id).orElse(null);
        return (s != null && Objects.equals(s.getUserId(), userId)) ? s : null;
    }

    /** 会话列表（只回元数据，消息按需拉，避免一次传回全部图片） */
    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiChatSession s : sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle() == null ? "新对话" : s.getTitle());
            m.put("updatedAt", ts(s.getUpdatedAt()));
            m.put("messageCount", messageRepo.countBySessionId(s.getId()));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 会话详情：整条消息 JSON 原样回显（含图片 data URL） */
    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        AiChatSession s = owned(id, userId);
        if (s == null) return ApiResponse.fail("会话不存在或无权访问");
        List<Object> msgs = new ArrayList<>();
        for (AiChatMessage m : messageRepo.findBySessionIdOrderByIdAsc(id)) {
            try { msgs.add(om.readValue(m.getPayload(), Object.class)); } catch (Exception ignored) {}
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", s.getId());
        data.put("title", s.getTitle() == null ? "新对话" : s.getTitle());
        data.put("updatedAt", ts(s.getUpdatedAt()));
        data.put("messages", msgs);
        return ApiResponse.ok(data);
    }

    /** 全量覆盖保存一个会话（前端每次发完消息调用；不存在则创建） */
    @PutMapping("/sessions/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> save(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        AiChatSession existing = sessionRepo.findById(id).orElse(null);
        if (existing != null && !Objects.equals(existing.getUserId(), userId)) {
            return ApiResponse.fail("会话不存在或无权访问");
        }
        int n = saveOne(userId, id, str(body.get("title")), LocalDateTime.now(), body.get("messages"));
        return ApiResponse.ok(Map.of("id", id, "saved", n));
    }

    /** 一次性把浏览器 localStorage 里的旧会话迁移进 MySQL */
    @PostMapping("/sessions")
    @Transactional
    public ApiResponse<Map<String, Object>> importSessions(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        int count = 0;
        if (body.get("sessions") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> sm) || count >= 30) continue;
                Object idObj = sm.get("id");
                if (idObj == null) continue;
                String id = idObj.toString();
                if (sessionRepo.existsById(id)) continue;   // 幂等：已迁移过就跳过
                long ms = 0L;
                try { ms = Long.parseLong(String.valueOf(sm.get("updatedAt"))); } catch (Exception ignored) {}
                LocalDateTime updated = ms > 0
                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
                        : LocalDateTime.now();
                saveOne(userId, id, str(sm.get("title")), updated, sm.get("messages"));
                count++;
            }
        }
        return ApiResponse.ok(Map.of("imported", count));
    }

    @DeleteMapping("/sessions/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = uid(authHeader);
        if (userId == null) return ApiResponse.fail("请先登录");
        if (owned(id, userId) == null) return ApiResponse.fail("会话不存在或无权访问");
        messageRepo.deleteBySessionId(id);
        sessionRepo.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", id));
    }

    private int saveOne(Long userId, String id, String title, LocalDateTime updatedAt, Object messagesObj) {
        AiChatSession s = sessionRepo.findById(id).orElse(null);
        if (s == null) {
            s = new AiChatSession();
            s.setId(id);
            s.setUserId(userId);
            s.setCreatedAt(LocalDateTime.now());
        }
        s.setTitle(title == null || title.isBlank() ? "新对话" : title);
        s.setUpdatedAt(updatedAt);
        sessionRepo.save(s);

        messageRepo.deleteBySessionId(id);
        int n = 0;
        if (messagesObj instanceof List<?> list) {
            for (Object o : list) {
                if (o == null || n >= 100) break;
                AiChatMessage m = new AiChatMessage();
                m.setSessionId(id);
                try { m.setPayload(o instanceof String ? (String) o : om.writeValueAsString(o)); }
                catch (Exception e) { continue; }
                m.setCreatedAt(LocalDateTime.now());
                messageRepo.save(m);
                n++;
            }
        }
        return n;
    }

    private String str(Object o) { return o == null ? "" : o.toString().trim(); }
}
