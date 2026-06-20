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
 * AI 呼叫紀錄：每次 LLM 呼叫（含 fallback）的用量、答案與治理旗標。
 * 函式級註解：採簡化稽核欄位，自行管理 created_at（與 V6 migration 一致），不繼承 AuditableEntity。
 */
@Entity
@Table(name = "ai_call_log")
public class AiCallLog {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 關聯客戶 ID（Portfolio 評估可為 null）。 */
    @Column(name = "customer_id")
    private Long customerId;

    /** 呼叫類型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 40)
    private AiCallType callType;

    /** 使用的模型名稱（fallback 時可為 null）。 */
    @Column(length = 120)
    private String model;

    /** 提示 token 數。 */
    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    /** 完成 token 數。 */
    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    /** 總 token 數。 */
    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    /** 是否實際呼叫真實 LLM（fallback 為 false）。 */
    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    /** 送 LLM 的 grounding context 是否已做 PII 遮罩。 */
    @Column(name = "pii_masked", nullable = false)
    private boolean piiMasked;

    /** 回答內容。 */
    @Column(columnDefinition = "text")
    private String answer;

    /** 建立時間（自行管理）。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCallLog() {
    }

    /**
     * 建立 AI 呼叫紀錄。
     *
     * @param customerId 客戶 ID（可為 null）
     * @param callType 呼叫類型
     * @param model 模型名稱（可為 null）
     * @param promptTokens 提示 token 數
     * @param completionTokens 完成 token 數
     * @param totalTokens 總 token 數
     * @param aiEnabled 是否真實呼叫 LLM
     * @param piiMasked 是否已遮罩 PII
     * @param answer 回答內容
     */
    public AiCallLog(Long customerId, AiCallType callType, String model,
                     int promptTokens, int completionTokens, int totalTokens,
                     boolean aiEnabled, boolean piiMasked, String answer) {
        this.customerId = customerId;
        this.callType = callType;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.aiEnabled = aiEnabled;
        this.piiMasked = piiMasked;
        this.answer = answer;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public AiCallType getCallType() { return callType; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public boolean isAiEnabled() { return aiEnabled; }
    public boolean isPiiMasked() { return piiMasked; }
    public String getAnswer() { return answer; }
    public Instant getCreatedAt() { return createdAt; }
}
