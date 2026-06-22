package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * AI 供應商設定 Entity：儲存 provider 名稱、API endpoint 與金鑰。
 * apiKey 欄位僅供後端讀取，永不序列化回前端。
 */
@Entity
@Table(name = "ai_providers")
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 供應商識別名稱（唯一），如 "OpenAI"、"Anthropic"。 */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    /** API base URL；null 或空字串代表使用 OpenAI 預設（https://api.openai.com）。 */
    @Column(name = "base_url")
    private String baseUrl;

    /** API 金鑰（敏感欄位，GET API 絕對不回傳原文）。 */
    @Column(name = "api_key")
    private String apiKey;

    /** 建立時間。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最後更新時間。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 最後修改者帳號。 */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected AiProvider() {}

    /** 新增 provider 建構子。 */
    public AiProvider(String name, String baseUrl, String apiKey, String updatedBy) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.updatedBy = updatedBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** 更新 provider 設定；apiKey 為 null 時保留現有金鑰不異動。 */
    public void update(String name, String baseUrl, String apiKey, String updatedBy) {
        this.name = name;
        this.baseUrl = baseUrl;
        if (apiKey != null) this.apiKey = apiKey;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    /** apiKey 是否已設定（非空）。 */
    public boolean isApiKeySet() { return apiKey != null && !apiKey.isBlank(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
