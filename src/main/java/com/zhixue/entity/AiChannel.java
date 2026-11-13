package com.zhixue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 管理端动态配置的 AI 通道（OpenAI 兼容网关）。
 * 与 application-local.yml 里的静态通道合并后进入 AI 助手的模型下拉；
 * apiKey 只存后端，接口一律以掩码回显。
 */
@Entity
@Table(name = "ai_channels")
public class AiChannel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key", length = 1000)
    private String apiKey;

    /** 模型 id，逗号分隔 */
    @Column(length = 2000)
    private String models;

    /** 支持识图的模型 id，逗号分隔；为空表示该通道不提供识图模型 */
    @Column(name = "vision_models", length = 2000)
    private String visionModels;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_no")
    private Integer sortNo = 0;

    @Column(length = 500)
    private String remark;

    /** 来源：CONFIG = 由 application-local.yml 的 ai.extra-channels 导入；ADMIN = 后台新增 */
    @Column(length = 20)
    private String origin = "ADMIN";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    public void onUpdate() { updatedAt = LocalDateTime.now(); }

    public AiChannel() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModels() { return models; }
    public void setModels(String models) { this.models = models; }
    public String getVisionModels() { return visionModels; }
    public void setVisionModels(String visionModels) { this.visionModels = visionModels; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
