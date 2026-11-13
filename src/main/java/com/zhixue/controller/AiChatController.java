package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.AiContextService;
import com.zhixue.service.AiPageBrowserService;
import com.zhixue.service.AiPageCatalogService;
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
    @Autowired private AiPageCatalogService pageCatalog;
    @Autowired private AiPageBrowserService pageBrowser;

    /** 可用通道 / 模型清单：供前端「自选模型」下拉使用，只返回名称与模型 id，不含密钥 */
    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(Map.of("channels", aiService.options(), "default", "auto"));
    }

    // ============ 非流式 ============

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        List<String> images = readImages(body.get("images"));
        if (message.isEmpty() && images.isEmpty()) return ApiResponse.fail("消息不能为空");
        if (message.length() > 2000) return ApiResponse.fail("消息过长，请分条发送");

        String channel = str(body.get("channel"));
        String model = str(body.get("model"));
        if (!images.isEmpty()) {
            if (model.isBlank()) model = aiService.defaultVisionModel();   // 自动模式带图 → 切到识图模型
            if (!aiService.supportsVision(model)) {
                return ApiResponse.fail("当前模型不支持识图，请在下拉里选择带识图能力的模型（如 deepseek-v4.1-flash-1017）");
            }
        }
        Long userId = userIdOf(authHeader);
        String role = roleOf(userId);
        AiContextService.Recognition rec = contextService.recognize(message, userId, role);
        // 时效性问题（最新/今天/报名/放假等）触发联网搜索，结果块注入 system 提示词
        String webBlock = webSearch.shouldSearch(message) ? webSearch.webBlock(message) : null;
        Map<String, Object> meta = new HashMap<>();
        String sys = withPageData(contextService.buildSystemPrompt(rec, message, userId, role),
                rec, message, role, authHeader, userId, meta);
        if (webBlock != null) sys = sys + "\n" + webBlock;

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.<String, Object>of("role", "system", "content", sys));
        appendHistory(messages, body.get("history"));
        messages.add(Map.<String, Object>of("role", "user", "content", userContent(message, images)));

        try {
            String reply = aiService.chat(messages, channel, model);
            Map<String, Object> data = new HashMap<>();
            data.put("reply", reply);
            data.put("intent", rec.intent.name());
            data.put("intentCn", rec.intentCn());
            data.put("sources", rec.sources);
            data.put("webSearch", webBlock != null);
            data.putAll(meta);
            data.putAll(aiService.selectionInfo(channel, model));
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
        List<String> images = readImages(body.get("images"));
        if (message.isEmpty() && images.isEmpty()) {
            sendEvent(emitter, "error", "消息不能为空");
            emitter.complete();
            return emitter;
        }
        if (message.length() > 2000) message = message.substring(0, 2000);

        String channel = str(body.get("channel"));
        String model = str(body.get("model"));
        if (!images.isEmpty()) {
            if (model.isBlank()) model = aiService.defaultVisionModel();
            if (!aiService.supportsVision(model)) {
                sendEvent(emitter, "error", "当前模型不支持识图，请在下拉里选择带识图能力的模型（如 deepseek-v4.1-flash-1017）");
                emitter.complete();
                return emitter;
            }
        }
        final String callModel = model;   // lambda 内需要 effectively final
        Long userId = userIdOf(authHeader);
        String role = roleOf(userId);
        AiContextService.Recognition rec = contextService.recognize(message, userId, role);
        // 时效性问题触发联网搜索（与 /chat 同一套触发词与数据块）
        String webBlock = webSearch.shouldSearch(message) ? webSearch.webBlock(message) : null;
        // 先装配完数据块（含页面实况）再发 meta，sources / pages 在装配过程中填充
        Map<String, Object> meta = new HashMap<>();
        String sys = withPageData(contextService.buildSystemPrompt(rec, message, userId, role),
                rec, message, role, authHeader, userId, meta);
        if (webBlock != null) sys = sys + "\n" + webBlock;

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.<String, Object>of("role", "system", "content", sys));
        appendHistory(messages, body.get("history"));
        messages.add(Map.<String, Object>of("role", "user", "content", userContent(message, images)));

        // 先装配完数据块再发 meta（sources / pages 在装配过程中填充）
        meta.put("intent", rec.intent.name());
        meta.put("intentCn", rec.intentCn());
        meta.put("sources", rec.sources);
        meta.put("webSearch", webBlock != null);
        meta.putAll(aiService.selectionInfo(channel, model));
        List<String> courseNames = new ArrayList<>();
        rec.courses.forEach(c -> courseNames.add(c.getName()));
        meta.put("courses", courseNames);
        sendEvent(emitter, "meta", toJson(meta));

        new Thread(() -> {
            try {
                aiService.chatStream(messages, new AiService.DeltaListener() {
                    @Override public void onDelta(String chunk) { sendEvent(emitter, "delta", chunk); }
                    @Override public void onReasoning(String chunk) { sendEvent(emitter, "reasoning", chunk); }
                }, channel, callModel);
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
        Map<String, Object> meta = new HashMap<>();
        String prompt = withPageData(contextService.buildSystemPrompt(rec, message, userId, role),
                rec, message, role, authHeader, userId, meta);

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
        data.putAll(meta);
        return ApiResponse.ok(data);
    }

    /**
     * 页面入口增强：按问题选出本角色可访问的页面，带着当前 token 真正读取这些页面的接口数据，
     * 再把「可进入的页面目录 + 页面实况」追加进 system 提示词；同时把入口清单交给前端做快捷跳转。
     */
    private String withPageData(String sys, AiContextService.Recognition rec, String message,
                                String role, String authHeader, Long userId, Map<String, Object> meta) {
        List<AiPageCatalogService.Page> pages = pageCatalog.select(message, role, rec.intent.name());
        AiPageBrowserService.BrowseResult r = pageBrowser.browse(pages, authHeader, userId);
        List<Map<String, String>> links = new ArrayList<>();
        for (AiPageCatalogService.Page p : pages) {
            links.add(Map.of("id", p.id(), "name", p.name(), "path", p.path()));
        }
        meta.put("pages", links);
        meta.put("visitedPages", r.visited());
        StringBuilder sb = new StringBuilder(sys).append(pageCatalog.catalogText(role));
        if (!r.block().isBlank()) {
            sb.append(r.block());
            rec.sources.add("页面实况：" + String.join("、", r.visited()));
        }
        return sb.toString();
    }

    // ============ 内部工具 ============

    /** 自选通道/模型参数：空值统一成 ""，非法值由 AiService.resolve 兜底为自动级联 */
    private String str(Object o) { return o == null ? "" : o.toString().trim(); }

    /** 读取前端已压缩的图片 data URL：最多 3 张、单张字符串约 1MB，非法数据忽略 */
    private List<String> readImages(Object v) {
        List<String> out = new ArrayList<>();
        if (!(v instanceof List<?> list)) return out;
        for (Object o : list) {
            if (o == null) continue;
            String s = o.toString();
            if (!s.startsWith("data:image/") || s.length() > 1_500_000) continue;
            out.add(s);
            if (out.size() >= 3) break;
        }
        return out;
    }

    /** 带图时构造 OpenAI 多模态 content 数组；不带图仍是纯文本字符串 */
    private Object userContent(String message, List<String> images) {
        if (images.isEmpty()) return message;
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.<String, Object>of("type", "text", "text",
                message.isEmpty() ? "请描述图片内容。" : message));
        for (String img : images) {
            parts.add(Map.<String, Object>of("type", "image_url", "image_url", Map.of("url", img)));
        }
        return parts;
    }

    private void appendHistory(List<Map<String, Object>> messages, Object historyObj) {
        if (!(historyObj instanceof List)) return;
        for (Object o : (List<?>) historyObj) {
            if (!(o instanceof Map)) continue;
            String role = String.valueOf(((Map<?, ?>) o).get("role"));
            String content = String.valueOf(((Map<?, ?>) o).get("content"));
            if (("user".equals(role) || "assistant".equals(role))
                    && content != null && !content.isBlank() && !"null".equals(content)) {
                // 必须是 OpenAI 标准的 {role, content}；写成 Map.of(role, content) 会生成 {"user":"..."}，
                // 模型侧拿不到 role → 百炼报 400 'null' is not one of ['system','assistant','user',...]
                messages.add(Map.<String, Object>of("role", role, "content", content));
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
