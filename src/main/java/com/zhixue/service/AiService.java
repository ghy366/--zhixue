package com.zhixue.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper om = new ObjectMapper();

    /** 备用通道 */
    private record Channel(String baseUrl, String apiKey, java.util.List<String> models) {}

    /** 解析 ai.extra-channels（JSON 数组）；格式错误时返回空列表，不影响主流程 */
    private java.util.List<Channel> extraChannels() {
        if (extraChannels == null || extraChannels.isBlank()) return java.util.List.of();
        try {
            java.util.List<Channel> out = new java.util.ArrayList<>();
            for (JsonNode ch : om.readTree(extraChannels)) {
                java.util.List<String> ms = new java.util.ArrayList<>();
                for (JsonNode m : ch.path("models")) {
                    if (!m.asText().isBlank()) ms.add(m.asText().trim());
                }
                String baseUrl = ch.path("baseUrl").asText("").trim();
                if (!baseUrl.isEmpty() && !ms.isEmpty()) {
                    out.add(new Channel(baseUrl, ch.path("apiKey").asText(), ms));
                }
            }
            return out;
        } catch (Exception e) {
            return java.util.List.of();
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
    public String chat(List<Map<String, String>> messages) {
        Exception lastErr = null;
        // 模型级联：主模型（含限流退避重试）→ 备用模型 → 免费兜底网关
        List<String> models = new java.util.ArrayList<>();
        models.add(model);
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String m : fallbackModels.split(",")) {
                String t = m.trim();
                if (!t.isEmpty() && !models.contains(t)) models.add(t);
            }
        }
        for (int mi = 0; mi < models.size(); mi++) {
            String m = models.get(mi);
            int tries = mi == 0 ? 3 : 1;
            for (int attempt = 1; attempt <= tries; attempt++) {
                try {
                    return callWithRetry(messages, baseUrl, apiKey, m);
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
        for (Channel ch : extraChannels()) {
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
    private String callWithRetry(List<Map<String, String>> messages, String baseUrl, String apiKey, String model)
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
    public String chatStream(List<Map<String, String>> messages, DeltaListener listener) throws Exception {
        Exception lastErr = null;

        List<String> models = new java.util.ArrayList<>();
        models.add(model);
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String m : fallbackModels.split(",")) {
                String t = m.trim();
                if (!t.isEmpty() && !models.contains(t)) models.add(t);
            }
        }
        for (int mi = 0; mi < models.size(); mi++) {
            String m = models.get(mi);
            int tries = mi == 0 ? 2 : 1;   // 主模型给一次退避重试，备用模型一击即中
            for (int attempt = 1; attempt <= tries; attempt++) {
                try {
                    return streamOnce(messages, baseUrl, apiKey, m, listener);
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
        for (Channel ch : extraChannels()) {
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
    private String streamOnce(List<Map<String, String>> messages, String baseUrl, String apiKey,
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
