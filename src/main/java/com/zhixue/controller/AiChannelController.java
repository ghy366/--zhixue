package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.AiChannel;
import com.zhixue.repository.AiChannelRepository;
import com.zhixue.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端动态 AI 通道配置：增删改查 + 连通性测试。
 * 密钥只存后端，列表一律以掩码回显；新增/修改后无需重启，模型下拉与调用立即生效。
 */
@RestController
@RequestMapping("/api/ai/channels")
public class AiChannelController {

    @Autowired private AiChannelRepository repo;
    @Autowired private AiService aiService;
    @Autowired private com.zhixue.service.AiChannelSyncService syncService;

    /** 列表（不含明文密钥） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiChannel c : repo.findAllByOrderBySortNoAscIdAsc()) out.add(view(c));
        return ApiResponse.ok(out);
    }

    /** 把 application-local.yml 的 ai.extra-channels 同步进本页（overwrite=true 时用配置值覆盖已导入项） */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync(@RequestParam(defaultValue = "false") boolean overwrite) {
        Map<String, Object> r = syncService.sync(overwrite);
        int n = (int) r.getOrDefault("imported", 0) + (int) r.getOrDefault("updated", 0);
        String msg = n == 0
                ? "配置文件中的通道（含主通道）均已存在，无需导入"
                : "已导入 " + r.get("imported") + " 个、更新 " + r.get("updated") + " 个配置文件通道（含主通道）";
        return ApiResponse.ok(msg, r);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody AiChannel body) {
        String err = validate(body, true);
        if (err != null) return ApiResponse.fail(err);
        AiChannel c = new AiChannel();
        apply(c, body, true);
        c.setOrigin("ADMIN");
        c.setSortNo(body.getSortNo() == null ? (int) (repo.count() + 1) : body.getSortNo());
        return ApiResponse.ok("通道已新增，模型下拉已即时更新", view(repo.save(c)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody AiChannel body) {
        AiChannel c = repo.findById(id).orElse(null);
        if (c == null) return ApiResponse.fail("通道不存在");
        String err = validate(body, false);
        if (err != null) return ApiResponse.fail(err);
        apply(c, body, false);   // apiKey 留空表示不修改
        return ApiResponse.ok("通道已更新，模型下拉已即时更新", view(repo.save(c)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        AiChannel c = repo.findById(id).orElse(null);
        if (c == null) return ApiResponse.fail("通道不存在");
        if ("PRIMARY".equals(c.getOrigin())) return ApiResponse.fail("主通道不可删除，如需停用请关闭「启用」开关");
        repo.deleteById(id);
        return ApiResponse.ok("通道已删除", Map.of("id", id));
    }

    /** 用该通道的第一个模型发一条最小对话，验证 Key / Base URL / 模型名是否可用 */
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        AiChannel c = repo.findById(id).orElse(null);
        if (c == null) return ApiResponse.fail("通道不存在");
        if (!Boolean.TRUE.equals(c.getEnabled())) return ApiResponse.fail("通道已停用，请先启用再测试");
        List<String> models = split(c.getModels());
        if (models.isEmpty()) return ApiResponse.fail("请先配置模型名");

        long t0 = System.currentTimeMillis();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("channel", c.getName());
        r.put("model", models.get(0));
        try {
            // 主通道在下拉里是 primary 分组（不是 db-N），测试也按 primary 走，避免误落到别的网关
            String cid = "PRIMARY".equals(c.getOrigin()) ? "primary" : "db-" + id;
            String reply = aiService.chat(
                    List.of(Map.<String, Object>of("role", "user", "content", "ping")),
                    cid, models.get(0));
            r.put("ok", true);
            r.put("preview", reply == null ? "" : reply.substring(0, Math.min(80, reply.length())));
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", String.valueOf(e.getMessage()));
        }
        r.put("ms", System.currentTimeMillis() - t0);
        return ApiResponse.ok(r);
    }

    // ============ 内部工具 ============

    private void apply(AiChannel c, AiChannel b, boolean creating) {
        c.setName(b.getName().trim());
        c.setBaseUrl(b.getBaseUrl().trim());
        if (creating) c.setApiKey(b.getApiKey() == null ? "" : b.getApiKey().trim());
        else if (b.getApiKey() != null && !b.getApiKey().isBlank()) c.setApiKey(b.getApiKey().trim());
        c.setModels(b.getModels() == null ? "" : b.getModels().trim());
        c.setVisionModels(b.getVisionModels() == null ? "" : b.getVisionModels().trim());
        c.setEnabled(b.getEnabled() == null || b.getEnabled());
        c.setRemark(b.getRemark());
        if (b.getSortNo() != null) c.setSortNo(b.getSortNo());
    }

    /** 基本校验 + SSRF 防护：只允许 http(s)，拒绝本机与内网地址 */
    private String validate(AiChannel b, boolean creating) {
        if (b == null || b.getName() == null || b.getName().isBlank()) return "请填写通道名称";
        if (b.getBaseUrl() == null || b.getBaseUrl().isBlank()) return "请填写 Base URL";
        if (split(b.getModels()).isEmpty()) return "请至少填写一个模型名（逗号分隔）";
        if (creating && (b.getApiKey() == null || b.getApiKey().isBlank())) {
            // 允许无密钥通道（如免费网关），不强制
        }
        String url = b.getBaseUrl().trim();
        try {
            URI u = URI.create(url);
            String scheme = u.getScheme(), host = u.getHost();
            if (scheme == null || !(scheme.equals("https") || scheme.equals("http"))) return "Base URL 必须以 http(s):// 开头";
            if (host == null || host.isBlank()) return "Base URL 缺少主机名";
            String h = host.toLowerCase();
            if (h.equals("localhost") || h.startsWith("127.") || h.startsWith("10.")
                    || h.startsWith("192.168.") || h.equals("169.254.169.254")) {
                return "出于安全考虑，不允许填写本机或内网地址";
            }
            // 172.16.0.0 ~ 172.31.255.255
            if (h.startsWith("172.")) {
                String[] seg = h.split("\\.");
                if (seg.length > 1) {
                    try {
                        int n = Integer.parseInt(seg[1]);
                        if (n >= 16 && n <= 31) return "出于安全考虑，不允许填写本机或内网地址";
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            return "Base URL 格式不合法";
        }
        if (url.length() > 500) return "Base URL 过长";
        return null;
    }

    private List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String x : s.split("[,，\\n]")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    /** 密钥掩码：只保留末 4 位，绝不回传明文 */
    private String mask(String key) {
        if (key == null || key.isBlank()) return "未设置";
        if (key.length() <= 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }

    private Map<String, Object> view(AiChannel c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("baseUrl", c.getBaseUrl());
        m.put("apiKey", mask(c.getApiKey()));
        m.put("hasKey", c.getApiKey() != null && !c.getApiKey().isBlank());
        m.put("models", c.getModels());
        m.put("modelList", split(c.getModels()));
        m.put("visionModels", c.getVisionModels());
        m.put("enabled", Boolean.TRUE.equals(c.getEnabled()));
        m.put("sortNo", c.getSortNo());
        m.put("remark", c.getRemark());
        m.put("origin", c.getOrigin() == null ? "ADMIN" : c.getOrigin());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }
}
