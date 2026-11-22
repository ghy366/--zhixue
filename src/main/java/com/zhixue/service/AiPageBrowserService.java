package com.zhixue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 助手的「页面浏览器」：带着当前用户的 token 请求所选页面的真实接口，
 * 把页面此刻的数据（与用户在页面上看到的同源）放进提示词。
 *
 * 权限：请求走完整 MVC 拦截器，角色越权的页面会被 403 挡下，不会泄露数据。
 * 体积：字段裁剪 + 数组截断 + 每页限长，避免把整个课程表塞爆上下文。
 */
@Service
public class AiPageBrowserService {

    @Autowired private Environment env;

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    /** 长文本 / 敏感字段不进提示词 */
    private static final Set<String> SKIP = Set.of(
            "intro", "description", "content", "remark", "reason", "password", "token",
            "avatar", "images", "attachments", "files", "paperUrl", "fileUrl", "base64");

    public record BrowseResult(String block, List<String> visited) {}

    /** 逐个打开页面取数；空 api（如 chat 页）只作为入口，不请求 */
    public BrowseResult browse(List<AiPageCatalogService.Page> pages, String authHeader, Long userId) {
        StringBuilder sb = new StringBuilder();
        List<String> visited = new ArrayList<>();
        if (pages == null || pages.isEmpty()) return new BrowseResult("", visited);

        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String configured = env.getProperty("ai.page-base-url", "");
        String base = configured.isBlank() ? "http://127.0.0.1:" + port : configured;
        // 全览时页面多，逐页压缩体积，避免提示词过大
        int pageLimit = pages.size() > 12 ? 2000 : 4500;

        for (AiPageCatalogService.Page p : pages) {
            if (p.api() == null || p.api().isBlank()) continue;
            String api = p.api().replace("{uid}", String.valueOf(userId));
            sb.append("\n-- 【页面实况：").append(p.name()).append(" /").append(p.path()).append("】--\n")
              .append("数据来源：GET ").append(api).append("（以当前登录身份实时读取）\n");
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + api))
                        .timeout(Duration.ofSeconds(5)).GET();
                if (authHeader != null && !authHeader.isBlank()) b.header("Authorization", authHeader);
                HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                int st = resp.statusCode();
                if (st >= 400) {
                    sb.append("（该页面返回 HTTP ").append(st).append("，当前角色无权限或数据不可用）\n");
                    continue;
                }
                Object parsed = om.readValue(resp.body(), Object.class);
                Object data = parsed instanceof Map<?, ?> m ? m.get("data") : parsed;
                if (data == null) data = parsed;
                String text = om.writeValueAsString(slim(data, 0));
                if (text.length() > pageLimit) text = text.substring(0, pageLimit) + "…（已截断）";
                sb.append(text).append("\n");
                visited.add(p.name());
            } catch (Exception e) {
                sb.append("（页面数据读取失败：").append(e.getClass().getSimpleName()).append("）\n");
            }
        }
        if (visited.isEmpty()) return new BrowseResult("", visited);
        sb.append("\n【页面数据使用说明】以上【页面实况】是 AI 本轮实际打开页面后取得的实时数据，")
          .append("与【权威数据】同为事实来源；回答涉及这些页面上的数字与状态时以此为准，")
          .append("并可提示用户到 /xxx 页面查看详情。\n");
        return new BrowseResult(sb.toString(), visited);
    }

    /** 裁剪：丢长文本字段、数组只取前 12 条、嵌套最多 5 层 */
    private Object slim(Object v, int depth) {
        if (depth > 5) return "…";
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (SKIP.contains(k)) continue;
                Object val = e.getValue();
                if (val instanceof String s && s.length() > 120) {
                    out.put(k, s.substring(0, 120) + "…");
                } else out.put(k, slim(val, depth + 1));
            }
            return out;
        }
        if (v instanceof List<?> l) {
            int n = Math.min(l.size(), 12);
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < n; i++) out.add(slim(l.get(i), depth + 1));
            if (l.size() > n) out.add("…共 " + l.size() + " 条，仅取前 " + n + " 条");
            return out;
        }
        return v;
    }
}
