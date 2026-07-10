package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 商機階段轉換歷史：記錄每次進入新階段的時間，供漏斗停留天數與超時示警。
 */
@Entity
@Table(name = "opportunity_stage_history")
public class OpportunityStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 32)
    private OpportunityStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 32)
    private OpportunityStage toStage;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    protected OpportunityStageHistory() {
    }

    /**
     * 建立一筆階段轉換紀錄。
     *
     * @param opportunityId 商機 id
     * @param fromStage 原階段（建立時可 null）
     * @param toStage 新階段
     * @param changedAt 轉換時間
     * @param changedByUserId 操作者（可 null）
     */
    public OpportunityStageHistory(Long opportunityId, OpportunityStage fromStage, OpportunityStage toStage,
                                   Instant changedAt, Long changedByUserId) {
        this.opportunityId = opportunityId;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.changedAt = changedAt;
        this.changedByUserId = changedByUserId;
    }

    public Long getId() { return id; }
    public Long getOpportunityId() { return opportunityId; }
    public OpportunityStage getFromStage() { return fromStage; }
    public OpportunityStage getToStage() { return toStage; }
    public Instant getChangedAt() { return changedAt; }
    public Long getChangedByUserId() { return changedByUserId; }
}
