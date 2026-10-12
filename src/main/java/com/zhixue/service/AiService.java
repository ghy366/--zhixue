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

    /** 免费兜底通道：主通道失败时自动使用（无需任何密钥） */
    @Value("${ai.fallback-base-url:https://text.pollinations.ai/v1}")
    private String fallbackBaseUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper om = new ObjectMapper();

    /**
     * 发送一轮对话，返回助手回复文本。
     * messages: [{role: system|user|assistant, content: "..."}]
     * 先走主通道；失败时自动回退免费通道，保证「始终能聊」。
     */
    public String chat(List<Map<String, String>> messages) {
        Exception primaryErr;
        try {
            return callWithRetry(messages, baseUrl, apiKey, model);
        } catch (Exception e) {
            primaryErr = e;
        }
        // 主通道失败 → 免费兜底通道（无 key，模型 openai）
        if (fallbackBaseUrl != null && !fallbackBaseUrl.isBlank()
                && !fallbackBaseUrl.equals(baseUrl)) {
            try {
                return callWithRetry(messages, fallbackBaseUrl, "", "openai");
            } catch (Exception ignored) {
                // 兜底也失败，抛出主通道错误
            }
        }
        if (primaryErr instanceof RuntimeException re) throw re;
        throw new IllegalStateException("调用 AI 服务失败：" + primaryErr.getMessage());
    }

    /** 单通道调用，内置限流退避重试 */
    private String callWithRetry(List<Map<String, String>> messages, String baseUrl, String apiKey, String model)
            throws java.io.IOException, InterruptedException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", messages);
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
            boolean retryable = sc == 429 || sc == 408 || (sc / 100 == 5);
            if (retryable && attempt < maxAttempts) {
                Thread.sleep(1500L * attempt);
                continue;
            }
            if (retryable) {
                throw new IllegalStateException("AI 服务繁忙，请稍后重试");
            }
            throw new IllegalStateException("AI 服务返回 " + sc + "：" + truncate(resp.body()));
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
