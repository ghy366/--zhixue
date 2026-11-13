package com.zhixue.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 大模型对话服务：对接 OpenAI 兼容接口。
 * 主通道默认 Intern-AI 发现 API（带鉴权，聚合 deepseek/glm/qwen/intern 等多模型）；
 * 主通道失败（额度用尽 402 / 限流 / 网络异常等）时自动回退到免费兜底通道，保证始终可聊天。
 * 配置项见 application.yml 的 ai.* 节，可用环境变量覆盖。
 */
@Service
public class AiService {

    @Value("${ai.base-url:https://discovery-api.intern-ai.org.cn/v1}")
    private String baseUrl;

    /** 主通道显示名（前端下拉用；不含密钥） */
    @Value("${ai.channel-name:主通道}")
    private String channelName;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-v4-flash-0731}")
    private String model;

    /** 备用模型链（同一网关）：主模型频道限流时依次切换，逗号分隔 */
    @Value("${ai.fallback-models:qwen3.8-27b,glm-5.3}")
    private String fallbackModels;

    /** 免费兜底通道：主通道失败时自动使用（无需任何密钥） */
    @Value("${ai.fallback-base-url:https://text.pollinations.ai/v1}")
    private String fallbackBaseUrl;

    /** 备用通道列表（不同网关，按序切换）：JSON 数组
     *  [{"baseUrl":"https://api.x.com/v1","apiKey":"sk-..","models":["m1","m2"]}, ...]
     *  配置放 application-local.yml（不入 git），缺省为空=不启用 */
    @Value("${ai.extra-channels:}")
    private String extraChannels;

    /** 需显式关闭深度思考的模型名（逗号分隔）：按模型名精确匹配后才附加 thinking:disabled，
     *  避免其他网关因未知参数报错。当前主通道 DeepSeek 不需要，默认留空；
     *  留空时思考模型的推理过程会在前端 Chat.vue 的「思考过程」折叠块中展示。 */
    @Value("${ai.no-thinking-models:}")
    private String noThinkingModels;

    /** 支持图片输入的模型（OpenAI 多模态 image_url）；带图请求只允许落到这些模型 */
    @Value("${ai.vision-models:deepseek-v4.1-flash-1017,qwen3.8-27b,Agents-A1,Sage-A1.5,intern-s2,deepseek-flash}")
    private String visionModels;

    /** 带图且未手动指定模型时，自动切换到的识图模型 */
    @Value("${ai.vision-default-model:deepseek-v4.1-flash-1017}")
    private String visionDefaultModel;

    /** 管理端动态通道（新增/修改后无需重启，options 与 resolve 实时读取） */
    @Autowired private com.zhixue.repository.AiChannelRepository channelRepo;

    /** 配置白名单里的识图模型（不查库） */
    private boolean staticVision(String model) {
        if (model == null || model.isBlank() || visionModels == null || visionModels.isBlank()) return false;
        for (String m : visionModels.split(",")) {
            if (m.trim().equalsIgnoreCase(model.trim())) return true;
        }
        return false;
    }

    public boolean supportsVision(String model) {
        if (staticVision(model)) return true;
        // 主通道（origin=PRIMARY）已从 dbChannels 里排除，单独检查它标记过的识图模型
        try {
            com.zhixue.entity.AiChannel p = primaryRow();
            if (p != null && splitModels(p.getVisionModels()).contains(model == null ? "" : model.trim())) return true;
        } catch (Exception ignored) {}
        // 管理端动态通道里标记为识图的模型
        try {
            for (Channel c : dbChannels()) {
                if (c.visionModels().contains(model == null ? "" : model.trim())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public String defaultVisionModel() { return visionDefaultModel; }

    /** 下拉里的模型项：附带是否支持识图，供前端禁用/提示 */
    private Map<String, Object> modelItem(String id, boolean primary) { return modelItem(id, primary, staticVision(id)); }
    private Map<String, Object> modelItem(String id, boolean primary, boolean vision) {
        return Map.of("id", id, "label", id, "primary", primary, "vision", vision);
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper om = new ObjectMapper();

    /** 通道（channelId 用于前端下拉分组与 resolve 回查；name 仅用于展示） */
    private record Channel(String channelId, String name, String baseUrl, String apiKey,
                           java.util.List<String> models, java.util.List<String> visionModels) {}

    /** 前端选定后的实际调用端点 */
    private record Endpoint(String baseUrl, String apiKey, String model, String channelName) {
        String label() { return channelName + " / " + model; }
    }

    /** 解析 ai.extra-channels（JSON 数组）；格式错误时返回空列表，不影响主流程 */
    private java.util.List<Channel> extraChannels() {
        if (extraChannels == null || extraChannels.isBlank()) return java.util.List.of();
        try {
            java.util.List<Channel> out = new java.util.ArrayList<>();
            int i = 0;
            for (JsonNode ch : om.readTree(extraChannels)) {
                java.util.List<String> ms = new java.util.ArrayList<>();
                for (JsonNode m : ch.path("models")) {
                    if (!m.asText().isBlank()) ms.add(m.asText().trim());
                }
                String baseUrl = ch.path("baseUrl").asText("").trim();
                if (!baseUrl.isEmpty() && !ms.isEmpty()) {
                    String name = ch.path("name").asText("").trim();
                    if (name.isEmpty()) {
                        try { name = URI.create(baseUrl).getHost(); } catch (Exception ignored) { name = "备用通道"; }
                    }
                    out.add(new Channel("extra-" + i++, name == null ? "备用通道" : name, baseUrl,
                            ch.path("apiKey").asText(), ms, java.util.List.of()));
                }
            }
            return out;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /** 管理端「AI 通道配置」里 origin=PRIMARY 的主通道记录；未导入/库异常时为 null */
    private com.zhixue.entity.AiChannel primaryRow() {
        try {
            for (com.zhixue.entity.AiChannel c : channelRepo.findAll()) {
                if ("PRIMARY".equals(c.getOrigin())) return c;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 主通道是否仍参与自动级联：页面停用后跳过，直接走备用网关 */
    private boolean primaryEnabled() {
        com.zhixue.entity.AiChannel row = primaryRow();
        return row == null || Boolean.TRUE.equals(row.getEnabled());
    }

    /** 主通道实际端点：页面导入后以数据库配置为准（改地址/密钥即时生效），否则退回 yml */
    private Endpoint primaryEndpoint(String m) {
        com.zhixue.entity.AiChannel row = primaryRow();
        if (row != null && row.getBaseUrl() != null && !row.getBaseUrl().isBlank()) {
            return new Endpoint(row.getBaseUrl().trim(), row.getApiKey() == null ? "" : row.getApiKey(),
                    m, row.getName() == null || row.getName().isBlank() ? channelName : row.getName());
        }
        return new Endpoint(baseUrl, apiKey, m, channelName);
    }

    /** 管理端动态配置的通道（数据库）；密钥只在服务端内部使用。主通道由 primary 分组代表，不在此重复 */
    private java.util.List<Channel> dbChannels() {
        java.util.List<Channel> out = new java.util.ArrayList<>();
        try {
            for (com.zhixue.entity.AiChannel c : channelRepo.findByEnabledTrueOrderBySortNoAscIdAsc()) {
                if ("PRIMARY".equals(c.getOrigin())) continue;
                java.util.List<String> ms = splitModels(c.getModels());
                if (c.getBaseUrl() == null || c.getBaseUrl().isBlank() || ms.isEmpty()) continue;
                out.add(new Channel("db-" + c.getId(), c.getName(), c.getBaseUrl().trim(),
                        c.getApiKey() == null ? "" : c.getApiKey(), ms, splitModels(c.getVisionModels())));
            }
        } catch (Exception ignored) {}   // 表尚未创建/数据库异常时不阻断静态通道
        return out;
    }

    private java.util.List<String> splitModels(String s) {
        if (s == null || s.isBlank()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String m : s.split("[,，\\n]")) if (!m.trim().isEmpty()) out.add(m.trim());
        return out;
    }

    /** 静态配置通道 + 管理端动态通道；已被管理页接管（同名同址已入库）的静态通道不再重复出现 */
    private java.util.List<Channel> allChannels() {
        java.util.List<Channel> out = new java.util.ArrayList<>();
        java.util.Set<String> managed = managedKeys();
        for (Channel c : extraChannels()) {
            if (!managed.contains(AiChannelSyncService.key(c.name(), c.baseUrl()))) out.add(c);
        }
        out.addAll(dbChannels());
        return out;
    }

    /** 库里所有通道的「名称+Base URL」判重键（含停用项），用于静态通道去重 */
    private java.util.Set<String> managedKeys() {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            for (com.zhixue.entity.AiChannel c : channelRepo.findAll()) {
                out.add(AiChannelSyncService.key(c.getName(), c.getBaseUrl()));
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ======== 自选通道 / 模型（前端下拉）========

    /** 可用通道与模型清单：只回显名称与模型 id，绝不返回 baseUrl / apiKey */
    public java.util.List<Map<String, Object>> options() {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.List<String> chain = modelChain();
        if (primaryEnabled()) {
            Endpoint pe = primaryEndpoint(chain.get(0));
            java.util.List<Map<String, Object>> pm = new java.util.ArrayList<>();
            pm.add(modelItem(chain.get(0), true));
            for (int j = 1; j < chain.size(); j++) pm.add(modelItem(chain.get(j), false));
            out.add(Map.of("id", "primary", "name", pe.channelName(), "models", pm));
        }
        for (Channel c : allChannels()) {
            java.util.List<Map<String, Object>> ms = new java.util.ArrayList<>();
            for (String m : c.models()) ms.add(modelItem(m, false, c.visionModels().contains(m) || staticVision(m)));
            out.add(Map.of("id", c.channelId(), "name", c.name(), "models", ms));
        }
        if (fallbackBaseUrl != null && !fallbackBaseUrl.isBlank()) {
            out.add(Map.of("id", "fallback", "name", "免费兜底（无密钥）",
                    "models", java.util.List.of(modelItem("openai", false))));
        }
        return out;
    }

    /** 主通道模型链（主模型 + 同网关备用模型）：页面导入后以数据库配置为准 */
    private java.util.List<String> modelChain() {
        com.zhixue.entity.AiChannel row = primaryRow();
        if (row != null) {
            java.util.List<String> db = splitModels(row.getModels());
            if (!db.isEmpty()) return db;
        }
        java.util.List<String> chain = new java.util.ArrayList<>();
        chain.add(model);
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String s : fallbackModels.split(",")) {
                String m = s.trim();
                if (!m.isEmpty() && !chain.contains(m)) chain.add(m);
            }
        }
        return chain;
    }

    /** 把前端的 channel/model 解析成真实端点；非法或空选择返回 null = 自动级联 */
    private Endpoint resolve(String channelId, String modelId) {
        if (modelId == null || modelId.isBlank()) return null;
        String mid = modelId.trim();
        String cid = channelId == null ? "" : channelId.trim();
        java.util.List<Channel> chs = allChannels();

        if ((cid.isEmpty() || "auto".equals(cid) || "primary".equals(cid))
                && primaryEnabled() && modelChain().contains(mid)) {
            return primaryEndpoint(mid);
        }
        if ("fallback".equals(cid) && "openai".equals(mid)
                && fallbackBaseUrl != null && !fallbackBaseUrl.isBlank()) {
            return new Endpoint(fallbackBaseUrl, "", mid, "免费兜底（无密钥）");
        }
        // 明确指定通道（静态 extra-N / 动态 db-N）：按 channelId 精确匹配，避免任意字符串透传
        for (Channel c : chs) {
            if (cid.equals(c.channelId()) && c.models().contains(mid)) {
                return new Endpoint(c.baseUrl(), c.apiKey(), mid, c.name());
            }
        }
        // 只传模型名、未指定通道：在备用网关中按模型名查找（方便直接调 API）
        if (cid.isEmpty() || "auto".equals(cid)) {
            for (Channel c : chs) {
                if (c.models().contains(mid)) return new Endpoint(c.baseUrl(), c.apiKey(), mid, c.name());
            }
            if ("openai".equals(mid) && fallbackBaseUrl != null && !fallbackBaseUrl.isBlank()) {
                return new Endpoint(fallbackBaseUrl, "", mid, "免费兜底（无密钥）");
            }
        }
        // 兼容旧 channelId（如 extra-N 已迁移成 db-N、或通道被删除重建）：模型名仍在我们清单里就照常调用
        for (Channel c : chs) {
            if (c.models().contains(mid)) return new Endpoint(c.baseUrl(), c.apiKey(), mid, c.name());
        }
        return null;   // 组合不合法 → 自动级联，避免把任意字符串当模型名透传
    }

    /** 本次实际生效的选择（供前端 meta 展示），不含密钥 */
    public Map<String, Object> selectionInfo(String channelId, String modelId) {
        Endpoint ep = resolve(channelId, modelId);
        if (ep == null) return Map.of("mode", "auto", "label", "自动 · 智能级联",
                "modelLabel", "自动 · 智能级联");
        return Map.of("mode", "manual", "channel", ep.channelName(), "model", ep.model(),
                "label", ep.label(), "modelLabel", ep.label());
    }

    /** 指定通道/模型调用；未指定或非法时走原来的自动级联 */
    public String chat(List<Map<String, Object>> messages, String channelId, String modelId) {
        Endpoint ep = resolve(channelId, modelId);
        if (ep == null) return chat(messages);
        try {
            return callWithRetry(messages, ep.baseUrl(), ep.apiKey(), ep.model());
        } catch (Exception e) {
            throw new IllegalStateException("所选模型「" + ep.label() + "」调用失败：" + String.valueOf(e.getMessage()));
        }
    }

    /** 指定通道/模型流式调用；未指定或非法时走原来的自动级联 */
    public String chatStream(List<Map<String, Object>> messages, DeltaListener listener,
                             String channelId, String modelId) throws Exception {
        Endpoint ep = resolve(channelId, modelId);
        if (ep == null) return chatStream(messages, listener);
        try {
            return streamOnce(messages, ep.baseUrl(), ep.apiKey(), ep.model(), listener);
        } catch (Exception e) {
            throw new IllegalStateException("所选模型「" + ep.label() + "」调用失败：" + String.valueOf(e.getMessage()));
        }
    }

    /** 仅对配置中列出的模型附加 thinking:disabled，避免其他网关因未知参数报错 */
    private void applyThinking(Map<String, Object> body, String modelName) {
        if (noThinkingModels == null || noThinkingModels.isBlank()) return;
        for (String n : noThinkingModels.split(",")) {
            if (n.trim().equals(modelName)) {
                body.put("thinking", java.util.Map.of("type", "disabled"));
                return;
            }
        }
    }

    /**
     * 发送一轮对话，返回助手回复文本。
     * messages: [{role: system|user|assistant, content: "..."}]
     * 先走主通道；失败时自动回退免费通道，保证「始终能聊」。
     */
    public String chat(List<Map<String, Object>> messages) {
        Exception lastErr = null;
        // 模型级联：主模型（含限流退避重试）→ 备用模型 → 免费兜底网关（主通道以管理页配置为准）
        List<String> models = primaryEnabled()
                ? new java.util.ArrayList<>(modelChain()) : new java.util.ArrayList<>();
        for (int mi = 0; mi < models.size(); mi++) {
            String m = models.get(mi);
            int tries = mi == 0 ? 3 : 1;
            for (int attempt = 1; attempt <= tries; attempt++) {
                try {
                    Endpoint pe = primaryEndpoint(m);
                    return callWithRetry(messages, pe.baseUrl(), pe.apiKey(), m);
                } catch (Exception e) {
                    lastErr = e;
                    // 平台过载/限流：不再重试本模型，直接切下一个
                    if (String.valueOf(e.getMessage()).startsWith("AI_OVERLOAD")) break;
                    if (attempt < tries) {
                        try { Thread.sleep(1500L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }
        }
        // 备用通道（不同网关，如七牛云 / DeepSeek 官方）：主通道全挂后按序切换
        for (Channel ch : allChannels()) {
            for (String t : ch.models()) {
                if (models.contains(t)) continue;
                try {
                    return callWithRetry(messages, ch.baseUrl(), ch.apiKey(), t);
                } catch (Exception e) { lastErr = e; }
            }
        }
        // 主通道全部失败 → 免费兜底通道（无 key，模型 openai）
        if (fallbackBaseUrl != null && !fallbackBaseUrl.isBlank()
                && !fallbackBaseUrl.equals(baseUrl)) {
            try {
                return callWithRetry(messages, fallbackBaseUrl, "", "openai");
            } catch (Exception ignored) {
                // 兜底也失败，抛出主通道错误
            }
        }
        if (lastErr instanceof RuntimeException re) throw re;
        throw new IllegalStateException("调用 AI 服务失败：" + (lastErr == null ? "未知" : lastErr.getMessage()));
    }

    /** 单通道调用，内置限流退避重试 */
    private String callWithRetry(List<Map<String, Object>> messages, String baseUrl, String apiKey, String model)
            throws java.io.IOException, InterruptedException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", messages);
        applyThinking(body, model);
        String req = om.writeValueAsString(body);

        int attempt = 0;
        final int maxAttempts = 3;
        while (true) {
            attempt++;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "zhixue-selection/1.0");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(req))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                JsonNode content = om.readTree(resp.body())
                        .path("choices").path(0).path("message").path("content");
                if (content.isMissingNode() || content.asText().isBlank()) {
                    throw new IllegalStateException("AI 返回内容为空");
                }
                return content.asText();
            }
            // 限流/服务端抖动（429/5xx/408）退避后重试，提升稳定性
            int sc = resp.statusCode();
            String errBody = truncate(resp.body());
            // 上游平台过载码（1302 账户限流 / 1305 平台过载）：重试同一个模型没意义，交给外层立刻换下一个模型
            if (sc == 429 && (errBody.contains("1302") || errBody.contains("1305"))) {
                throw new IllegalStateException("AI_OVERLOAD: " + errBody);
            }
            boolean retryable = sc == 429 || sc == 408 || (sc / 100 == 5);
            if (retryable && attempt < maxAttempts) {
                Thread.sleep(1500L * attempt);
                continue;
            }
            if (retryable) {
                throw new IllegalStateException("AI 服务繁忙，请稍后重试");
            }
            throw new IllegalStateException("AI 服务返回 " + sc + "：" + errBody);
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    /** delta 增量回调：每收到一段流式文本触发一次；
     *  onReasoning：思考模型（如 deepseek-flash 默认思考模式）的推理过程增量，可选实现 */
    public interface DeltaListener {
        void onDelta(String chunk);
        default void onReasoning(String chunk) {}
    }

    /**
     * 流式对话：模型级联 + 限流退避。
     * 顺序：主模型（2 次退避）→ 备用模型各 1 次 → 免费兜底网关。
     * STREAM_BROKEN（已发出 delta）直接抛出，避免前端收到重复内容。
     */
    public String chatStream(List<Map<String, Object>> messages, DeltaListener listener) throws Exception {
        Exception lastErr = null;

        List<String> models = primaryEnabled()
                ? new java.util.ArrayList<>(modelChain()) : new java.util.ArrayList<>();
        for (int mi = 0; mi < models.size(); mi++) {
            String m = models.get(mi);
            int tries = mi == 0 ? 2 : 1;   // 主模型给一次退避重试，备用模型一击即中
            for (int attempt = 1; attempt <= tries; attempt++) {
                try {
                    Endpoint pe = primaryEndpoint(m);
                    return streamOnce(messages, pe.baseUrl(), pe.apiKey(), m, listener);
                } catch (Exception e) {
                    lastErr = e;
                    String msg = String.valueOf(e.getMessage());
                    if (msg != null && msg.startsWith("STREAM_BROKEN")) throw e;   // 中途断流不可重试
                    if (msg != null && msg.startsWith("AI_OVERLOAD")) break;       // 平台过载，立刻换模型
                    if (attempt < tries) Thread.sleep(1200L * attempt);
                }
            }
        }

        // 备用通道（不同网关，如七牛云 / DeepSeek 官方）：主通道全挂后按序切换
        for (Channel ch : allChannels()) {
            for (String t : ch.models()) {
                if (models.contains(t)) continue;
                try {
                    return streamOnce(messages, ch.baseUrl(), ch.apiKey(), t, listener);
                } catch (Exception e) {
                    lastErr = e;
                    String msg = String.valueOf(e.getMessage());
                    if (msg != null && msg.startsWith("STREAM_BROKEN")) throw e;   // 已发 delta 不可重试
                }
            }
        }
        // 全部模型失败 → 免费兜底网关（无 key，模型 openai）
        if (fallbackBaseUrl != null && !fallbackBaseUrl.isBlank() && !fallbackBaseUrl.equals(baseUrl)) {
            try {
                return streamOnce(messages, fallbackBaseUrl, "", "openai", listener);
            } catch (Exception ignored) {
                // 兜底也失败，抛出主通道错误
            }
        }
        throw lastErr != null ? lastErr : new IllegalStateException("AI 服务不可用");
    }

    /** 单通道流式调用：解析 OpenAI 兼容 SSE（data: {...} 行，[DONE] 结束）。
     *  响应头阶段的错误（429/5xx/401）会正常抛出——可安全重试（尚未发出 delta）；
     *  流读取中途断开则抛出前缀 STREAM_BROKEN 异常——不可重试（已发出 delta，重试会重复）。 */
    private String streamOnce(List<Map<String, Object>> messages, String baseUrl, String apiKey,
                              String model, DeltaListener listener) throws java.io.IOException, InterruptedException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("messages", messages);
        applyThinking(body, model);
        String req = om.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("User-Agent", "zhixue-selection/1.0");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(req)).build();

        HttpResponse<java.io.InputStream> resp =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            String err = new String(resp.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (resp.statusCode() == 429 && (err.contains("1302") || err.contains("1305"))) {
                throw new IllegalStateException("AI_OVERLOAD: " + truncate(err));
            }
            throw new IllegalStateException("AI 服务返回 " + resp.statusCode() + "：" + truncate(err));
        }

        StringBuilder full = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                try {
                    JsonNode delta = om.readTree(payload)
                            .path("choices").path(0).path("delta");
                    // 思考模型的推理增量（delta.reasoning_content）：只回调展示，不计入正文
                    String reasoning = delta.path("reasoning_content").asText("");
                    if (!reasoning.isEmpty() && listener != null) listener.onReasoning(reasoning);
                    String content = delta.path("content").asText("");
                    if (!content.isEmpty()) {
                        full.append(content);
                        if (listener != null) listener.onDelta(content);
                    }
                } catch (Exception ignore) {
                    // 单帧解析失败不影响整体流
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("STREAM_BROKEN: " + e.getMessage(), e);
        }
        if (full.length() == 0) throw new IllegalStateException("AI 流式返回内容为空");
        return full.toString();
    }
}
