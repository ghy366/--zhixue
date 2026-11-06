package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.AiContextService;
import com.zhixue.service.AiService;
import com.zhixue.service.WebSearchService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能体对话：免登录公开功能，代理转发到大模型（API Key 只存在后端）。
 *
 * 识别链路：问题 → AiContextService.recognize()（意图 + 课程实体）
 *          → 按意图装配权威数据块 → system 提示词 → 大模型 → 流式返回。
 * 带 token 时自动注入该用户的个人数据（选课/意向/候补/毕业进度）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired private AiService aiService;
    @Autowired private AiContextService contextService;
    @Autowired private WebSearchService webSearch;
    @Autowired private UserRepository userRepo;

    // ============ 非流式 ============

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.isEmpty()) return ApiResponse.fail("消息不能为空");
        if (message.length() > 2000) return ApiResponse.fail("消息过长，请分条发送");

        Long userId = userIdOf(authHeader);
        String role = roleOf(userId);
        AiContextService.Recognition rec = contextService.recognize(message, userId, role);
        // 时效性问题（最新/今天/报名/放假等）触发联网搜索，结果块注入 system 提示词
        String webBlock = webSearch.shouldSearch(message) ? webSearch.webBlock(message) : null;
        String sys = contextService.buildSystemPrompt(rec, message, userId, role);
        if (webBlock != null) sys = sys + "\n" + webBlock;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", sys));
        appendHistory(messages, body.get("history"));
        messages.add(Map.of("role", "user", "content", message));

        try {
            String reply = aiService.chat(messages);
            Map<String, Object> data = new HashMap<>();
            data.put("reply", reply);
            data.put("intent", rec.intent.name());
            data.put("intentCn", rec.intentCn());
            data.put("sources", rec.sources);
            data.put("webSearch", webBlock != null);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    // ============ 流式（SSE）============

    @PostMapping("/chat/stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);

        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.isEmpty()) {
            sendEvent(emitter, "error", "消息不能为空");
            emitter.complete();
            return emitter;
        }
        if (message.length() > 2000) message = message.substring(0, 2000);

        Long userId = userIdOf(authHeader);
        String role = roleOf(userId);
        AiContextService.Recognition rec = contextService.recognize(message, userId, role);
        // 时效性问题触发联网搜索（与 /chat 同一套触发词与数据块）
        String webBlock = webSearch.shouldSearch(message) ? webSearch.webBlock(message) : null;
        String sys = contextService.buildSystemPrompt(rec, message, userId, role);
        if (webBlock != null) sys = sys + "\n" + webBlock;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", sys));
        appendHistory(messages, body.get("history"));
        messages.add(Map.of("role", "user", "content", message));

        // 先装配完数据块再发 meta（sources 在装配过程中填充）
        Map<String, Object> meta = new HashMap<>();
        meta.put("intent", rec.intent.name());
        meta.put("intentCn", rec.intentCn());
        meta.put("sources", rec.sources);
        meta.put("webSearch", webBlock != null);
        List<String> courseNames = new ArrayList<>();
        rec.courses.forEach(c -> courseNames.add(c.getName()));
        meta.put("courses", courseNames);
        sendEvent(emitter, "meta", toJson(meta));

        new Thread(() -> {
            try {
                aiService.chatStream(messages, new AiService.DeltaListener() {
                    @Override public void onDelta(String chunk) { sendEvent(emitter, "delta", chunk); }
                    @Override public void onReasoning(String chunk) { sendEvent(emitter, "reasoning", chunk); }
                });
                sendEvent(emitter, "done", "ok");
                emitter.complete();
            } catch (Exception e) {
                sendEvent(emitter, "error", String.valueOf(e.getMessage()));
                emitter.complete();
            }
        }, "ai-chat-stream").start();
        return emitter;
    }

    /**
     * 识别探针：不调用大模型，只回显「这句话被理解成什么、注入了哪些数据」。
     * 用于自测识别覆盖率，也方便前端做「本次参考」提示。
     */
    @PostMapping("/probe")
    public ApiResponse<Map<String, Object>> probe(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        Long userId = userIdOf(authHeader);
        String role = roleOf(userId);
        AiContextService.Recognition rec = contextService.recognize(message, userId, role);
        String prompt = contextService.buildSystemPrompt(rec, message, userId, role);

        Map<String, Object> data = new HashMap<>();
        data.put("intent", rec.intent.name());
        data.put("intentCn", rec.intentCn());
        data.put("sources", rec.sources);
        data.put("note", rec.note);
        List<String> courseNames = new ArrayList<>();
        rec.courses.forEach(c -> courseNames.add(c.getName()));
        data.put("courses", courseNames);
        data.put("promptLength", prompt.length());
        data.put("prompt", body.get("withPrompt") != null ? prompt : null);
        return ApiResponse.ok(data);
    }

    // ============ 内部工具 ============

    private void appendHistory(List<Map<String, String>> messages, Object historyObj) {
        if (!(historyObj instanceof List)) return;
        for (Object o : (List<?>) historyObj) {
            if (!(o instanceof Map)) continue;
            String role = String.valueOf(((Map<?, ?>) o).get("role"));
            String content = String.valueOf(((Map<?, ?>) o).get("content"));
            if (("user".equals(role) || "assistant".equals(role))
                    && content != null && !content.isBlank() && !"null".equals(content)) {
                messages.add(Map.of(role, content));
            }
        }
        while (messages.size() > 17) messages.remove(1);   // system + 16 条历史
    }

    private void sendEvent(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
                           String name, String data) {
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name(name).data(data == null ? "" : data));
        } catch (Exception ignored) {}
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{\"intent\":\"GENERAL\",\"sources\":[]}";
        }
    }

    /** 从 Authorization header 解出登录用户 id（无 token / 无效 token 返回 null，保持匿名可用） */
    private Long userIdOf(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
        return SessionStore.getUserId(token);
    }

    private String roleOf(Long userId) {
        if (userId == null) return null;
        User u = userRepo.findById(userId).orElse(null);
        return u == null ? null : u.getRole();
    }
}
