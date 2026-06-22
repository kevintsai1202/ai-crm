package com.aicrm.crm.domain;

/**
 * AI 呼叫類型：對話、單客戶評估、Portfolio 評估、團隊分析、業務 coaching。
 */
public enum AiCallType {
    /** 一般對話（/api/ai/chat）。 */
    CHAT,
    /** 單一客戶 360 度評估。 */
    ASSESSMENT,
    /** 跨客戶 Portfolio 評估。 */
    PORTFOLIO,
    /** 團隊整體診斷（模組 C，scope=TEAM）。 */
    TEAM_ANALYSIS,
    /** 個別業務 coaching（模組 C，scope=OWNER）。 */
    OWNER_COACHING,
    /** 多模型競速測試評分（claude-opus-4-8 評審）。 */
    MODEL_EVAL
}
