package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Manager AI 分析快取：團隊整體診斷（scope=TEAM）與個別業務 coaching（scope=OWNER）的最後一次產出。
 * 「點按生成」時 upsert 此表；進頁先讀此表顯示「上次分析時間」。
 */
@Entity
@Table(name = "manager_insight")
public class ManagerInsight {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 範圍：TEAM（團隊）或 OWNER（個別業務）。 */
    @Column(nullable = false, length = 16)
    private String scope;

    /** OWNER 時的業務顯示名稱；TEAM 時為 null。 */
    @Column(name = "owner_name")
    private String ownerName;

    /** 產出的 Markdown 報告。 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 產出模型名；deterministic fallback 時為 null。 */
    @Column(length = 128)
    private String model;

    /** 產出時間。 */
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ManagerInsight() {
    }

    /**
     * 建立快取列。
     *
     * @param scope 範圍（TEAM/OWNER）
     * @param ownerName 業務名（TEAM 傳 null）
     * @param content Markdown 報告
     * @param model 模型名（fallback 傳 null）
     * @param generatedAt 產出時間
     */
    public ManagerInsight(String scope, String ownerName, String content, String model, Instant generatedAt) {
        this.scope = scope;
        this.ownerName = ownerName;
        this.content = content;
        this.model = model;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getScope() {
        return scope;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /** 重新生成時更新內容、模型與時間（供 upsert 沿用同一列）。 */
    public void update(String content, String model, Instant generatedAt) {
        this.content = content;
        this.model = model;
        this.generatedAt = generatedAt;
    }
}
