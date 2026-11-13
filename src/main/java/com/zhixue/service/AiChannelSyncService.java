package com.zhixue.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.entity.AiChannel;
import com.zhixue.repository.AiChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把配置里的 AI 通道同步进 ai_channels 表，让它们在「AI 通道配置」页里可查看 / 编辑 / 测试 / 停用。
 * 来源两类：
 *  - PRIMARY：ai.base-url / ai.api-key / ai.model / ai.fallback-models（主通道，按 origin 匹配，改名不断链）
 *  - CONFIG ：ai.extra-channels（备用网关，按「名称 + 归一化 Base URL」判重）
 * 启动时只补缺失，不覆盖后台改动；页面点「同步配置文件」才会用配置值覆盖这两类通道。
 * ai.import-static-channels=false 可关闭启动自动导入。
 */
@Service
public class AiChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(AiChannelSyncService.class);

    @Value("${ai.extra-channels:}")
    private String extraChannels;

    @Value("${ai.import-static-channels:true}")
    private boolean importOnStart;

    // 主通道（ai.base-url / ai.api-key / ai.model / ai.fallback-models）
    @Value("${ai.channel-name:主通道}")
    private String primaryName;
    @Value("${ai.base-url:}")
    private String primaryBaseUrl;
    @Value("${ai.api-key:}")
    private String primaryApiKey;
    @Value("${ai.model:}")
    private String primaryModel;
    @Value("${ai.fallback-models:}")
    private String primaryFallbackModels;
    @Value("${ai.vision-models:}")
    private String primaryVisionModels;

    @Autowired private AiChannelRepository repo;

    private final ObjectMapper om = new ObjectMapper();

    /** 配置里解析出来的静态通道（只读，不含数据库记录） */
    public record ConfigChannel(String name, String baseUrl, String apiKey,
                                List<String> models, List<String> visionModels) {}

    /** 启动后补导入：只新增配置里有、库里没有的通道 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!importOnStart) return;
        try {
            Map<String, Object> r = sync(false);
            int n = (int) r.getOrDefault("imported", 0);
            if (n > 0) log.info("[AI通道] 已从配置文件导入 {} 个静态通道到管理页；另有 {} 个已存在",
                    n, r.get("skipped"));
        } catch (Exception e) {
            log.warn("[AI通道] 静态通道导入失败（不影响静态通道直接使用）：{}", e.getMessage());
        }
    }

    /** 读取 ai.extra-channels（JSON 数组）；格式错误返回空列表 */
    public List<ConfigChannel> configChannels() {
        List<ConfigChannel> out = new ArrayList<>();
        if (extraChannels == null || extraChannels.isBlank()) return out;
        try {
            for (JsonNode ch : om.readTree(extraChannels)) {
                List<String> ms = new ArrayList<>();
                for (JsonNode m : ch.path("models")) {
                    if (!m.asText().isBlank()) ms.add(m.asText().trim());
                }
                List<String> vs = new ArrayList<>();
                for (JsonNode m : ch.path("visionModels")) {
                    if (!m.asText().isBlank()) vs.add(m.asText().trim());
                }
                String baseUrl = ch.path("baseUrl").asText("").trim();
                if (baseUrl.isEmpty() || ms.isEmpty()) continue;
                String name = ch.path("name").asText("").trim();
                if (name.isEmpty()) {
                    try { name = URI.create(baseUrl).getHost(); } catch (Exception ignored) { name = "备用通道"; }
                }
                out.add(new ConfigChannel(name, baseUrl, ch.path("apiKey").asText(""), ms, vs));
            }
        } catch (Exception e) {
            log.warn("[AI通道] ai.extra-channels 解析失败：{}", e.getMessage());
        }
        return out;
    }

    /** 主通道（阿里百炼等）：由 ai.base-url / ai.api-key / ai.model / ai.fallback-models 组成 */
    public ConfigChannel primaryChannel() {
        if (primaryBaseUrl == null || primaryBaseUrl.isBlank()
                || primaryModel == null || primaryModel.isBlank()) return null;
        List<String> ms = new ArrayList<>();
        ms.add(primaryModel.trim());
        if (primaryFallbackModels != null && !primaryFallbackModels.isBlank()) {
            for (String s : primaryFallbackModels.split(",")) {
                String m = s.trim();
                if (!m.isEmpty() && !ms.contains(m)) ms.add(m);
            }
        }
        String name = primaryName == null || primaryName.isBlank() ? "主通道" : primaryName.trim();
        List<String> vs = new ArrayList<>();
        if (primaryVisionModels != null && !primaryVisionModels.isBlank()) {
            for (String s : primaryVisionModels.split(",")) {
                String m = s.trim();
                if (ms.contains(m)) vs.add(m);   // 只标记该主通道上实际存在的识图模型
            }
        }
        return new ConfigChannel(name, primaryBaseUrl.trim(),
                primaryApiKey == null ? "" : primaryApiKey, ms, vs);
    }

    /**
     * 同步配置文件通道到数据库（含主通道）。
     * @param overwrite true = 用配置值覆盖已导入（CONFIG / PRIMARY）通道的地址/密钥/模型；false = 仅补新增
     */
    public Map<String, Object> sync(boolean overwrite) {
        List<ConfigChannel> conf = configChannels();
        ConfigChannel primary = primaryChannel();
        Map<String, Object> r = new LinkedHashMap<>();
        int imported = 0, updated = 0, skipped = 0;

        List<AiChannel> all = repo.findAll();
        Map<String, AiChannel> byKey = new LinkedHashMap<>();
        AiChannel primaryRow = null;
        int maxSort = 0;
        for (AiChannel c : all) {
            byKey.putIfAbsent(key(c.getName(), c.getBaseUrl()), c);
            if ("PRIMARY".equals(c.getOrigin()) && primaryRow == null) primaryRow = c;
            if (c.getSortNo() != null && c.getSortNo() > maxSort) maxSort = c.getSortNo();
        }

        // 主通道单独按 origin=PRIMARY 匹配：改名 / 改地址后仍与配置文件保持关联
        if (primary != null) {
            if (primaryRow == null) {
                AiChannel c = new AiChannel();
                c.setName(primary.name());
                c.setBaseUrl(primary.baseUrl());
                c.setApiKey(primary.apiKey());
                c.setModels(String.join(", ", primary.models()));
                c.setVisionModels(String.join(", ", primary.visionModels()));
                c.setEnabled(true);
                c.setOrigin("PRIMARY");
                c.setSortNo(-1);   // 管理页里主通道始终排第一
                c.setRemark("主通道（application.yml / application-local.yml）");
                AiChannel saved = repo.save(c);
                byKey.putIfAbsent(key(saved.getName(), saved.getBaseUrl()), saved);
                imported++;
            } else {
                // 排序是元数据，不覆盖用户改过的地址/密钥：始终把主通道固定在第一行
                if (primaryRow.getSortNo() == null || primaryRow.getSortNo() != -1) {
                    primaryRow.setSortNo(-1);
                }
                if (overwrite) {
                    primaryRow.setName(primary.name());
                    primaryRow.setBaseUrl(primary.baseUrl());
                    if (primary.apiKey() != null && !primary.apiKey().isBlank()) primaryRow.setApiKey(primary.apiKey());
                    primaryRow.setModels(String.join(", ", primary.models()));
                    primaryRow.setVisionModels(String.join(", ", primary.visionModels()));
                    updated++;
                } else {
                    skipped++;
                }
                repo.save(primaryRow);
            }
        }

        for (ConfigChannel cc : conf) {
            String k = key(cc.name(), cc.baseUrl());
            AiChannel exist = byKey.get(k);
            if (exist == null) {
                AiChannel c = new AiChannel();
                c.setName(cc.name());
                c.setBaseUrl(cc.baseUrl());
                c.setApiKey(cc.apiKey());
                c.setModels(String.join(", ", cc.models()));
                c.setVisionModels(String.join(", ", cc.visionModels()));
                c.setEnabled(true);
                c.setOrigin("CONFIG");
                c.setSortNo(++maxSort);
                c.setRemark("由 application-local.yml 导入");
                AiChannel saved = repo.save(c);
                byKey.put(k, saved);
                imported++;
            } else if (overwrite && "CONFIG".equals(exist.getOrigin())) {
                exist.setName(cc.name());
                exist.setBaseUrl(cc.baseUrl());
                if (cc.apiKey() != null && !cc.apiKey().isBlank()) exist.setApiKey(cc.apiKey());
                exist.setModels(String.join(", ", cc.models()));
                exist.setVisionModels(String.join(", ", cc.visionModels()));
                repo.save(exist);
                updated++;
            } else {
                skipped++;
            }
        }

        r.put("imported", imported);
        r.put("updated", updated);
        r.put("skipped", skipped);
        r.put("total", conf.size() + (primary == null ? 0 : 1));
        return r;
    }

    /** 判重键：通道名（忽略大小写/空白）+ 归一化 Base URL；同名同址视为同一条 */
    public static String key(String name, String baseUrl) {
        String n = name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
        return n + "|" + norm(baseUrl);
    }

    /** Base URL 归一化：去空白与尾斜杠、域名小写，仅用于判重 */
    public static String norm(String baseUrl) {
        if (baseUrl == null) return "";
        String s = baseUrl.trim().replaceAll("/+$", "");
        try {
            URI u = URI.create(s);
            if (u.getHost() != null) {
                return (u.getScheme() + "://" + u.getHost().toLowerCase()
                        + (u.getPort() > 0 ? ":" + u.getPort() : "")
                        + (u.getPath() == null ? "" : u.getPath())).replaceAll("/+$", "");
            }
        } catch (Exception ignored) {}
        return s.toLowerCase();
    }
}
