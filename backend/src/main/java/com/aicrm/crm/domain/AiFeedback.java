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
 * AI 回答的人工回饋紀錄：對應某筆 AiCallLog 的採納/拒絕與備註。
 * 函式級註解：採簡化稽核欄位，自行管理 created_at / created_by（與 V6 migration 一致）。
 */
@Entity
@Table(name = "ai_feedback")
public class AiFeedback {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 對應的 AI 呼叫紀錄 ID。 */
    @Column(name = "call_log_id", nullable = false)
    private Long callLogId;

    /** 採納或拒絕。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackDecision decision;

    /** 回饋備註（可為 null）。 */
    @Column(length = 1000)
    private String note;

    /** 建立時間（自行管理）。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 回饋者（登入帳號）。 */
    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    protected AiFeedback() {
    }

    /**
     * 建立 AI 回饋紀錄。
     *
     * @param callLogId 對應的 AI 呼叫紀錄 ID
     * @param decision 採納或拒絕
     * @param note 備註（可為 null）
     * @param createdBy 回饋者帳號
     */
    public AiFeedback(Long callLogId, FeedbackDecision decision, String note, String createdBy) {
        this.callLogId = callLogId;
        this.decision = decision;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCallLogId() { return callLogId; }
    public FeedbackDecision getDecision() { return decision; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
