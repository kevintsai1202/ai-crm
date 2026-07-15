package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 商機健康度 snapshot（V26）。
 *
 * <p>保存單次重算的總分、各分項明細（含分數/可解釋 reason/evidence，序列化為 JSON 文字）、
 * 下一最佳行動、規則版本、（選配）AI 模型與計算時間。歷史 snapshot 保留（同商機多筆），
 * 供健康度趨勢呈現。本實體<b>不</b>持有商機/機率等可變欄位，確保重算不影響 Opportunity 本體。</p>
 */
@Entity
@Table(name = "opportunity_health_snapshots")
public class OpportunityHealthSnapshot extends AuditableEntity {

    /** snapshot 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬商機 id（正式 FK，欄位形式避免載入整個商機 entity）。 */
    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    /** 總分（0–100，恆等於各分項分數總和）。 */
    @Column(name = "total_score", nullable = false)
    private int totalScore;

    /** 各分項明細的 JSON 文字（由服務層以 ObjectMapper 序列化）。 */
    @Column(nullable = false, columnDefinition = "text")
    private String components;

    /** 下一最佳行動文案。 */
    @Column(name = "next_best_action", columnDefinition = "text")
    private String nextBestAction;

    /** 規則版本，供歷史 snapshot 追溯評分規則。 */
    @Column(name = "rule_version", nullable = false, length = 32)
    private String ruleVersion;

    /** 產生下一最佳行動的 AI 模型；deterministic 產生時為 null。 */
    @Column(name = "model")
    private String model;

    /** 計算時間（趨勢排序用）。 */
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected OpportunityHealthSnapshot() {
    }

    /**
     * 建立一筆健康度 snapshot。
     *
     * @param opportunityId 所屬商機 id
     * @param totalScore 總分（0–100）
     * @param components 各分項明細的 JSON 文字
     * @param nextBestAction 下一最佳行動文案
     * @param ruleVersion 規則版本
     * @param model 產生下一最佳行動的 AI 模型（deterministic 時 null）
     * @param calculatedAt 計算時間
     */
    public OpportunityHealthSnapshot(Long opportunityId, int totalScore, String components, String nextBestAction,
                                     String ruleVersion, String model, Instant calculatedAt) {
        this.opportunityId = opportunityId;
        this.totalScore = totalScore;
        this.components = components;
        this.nextBestAction = nextBestAction;
        this.ruleVersion = ruleVersion;
        this.model = model;
        this.calculatedAt = calculatedAt;
    }

    public Long getId() { return id; }
    public Long getOpportunityId() { return opportunityId; }
    public int getTotalScore() { return totalScore; }
    public String getComponents() { return components; }
    public String getNextBestAction() { return nextBestAction; }
    public String getRuleVersion() { return ruleVersion; }
    public String getModel() { return model; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
