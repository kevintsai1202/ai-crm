package com.aicrm.crm.domain;

/**
 * AI 呼叫類型：對話、單客戶評估、Portfolio 評估。
 */
public enum AiCallType {
    /** 一般對話（/api/ai/chat）。 */
    CHAT,
    /** 單一客戶 360 度評估。 */
    ASSESSMENT,
    /** 跨客戶 Portfolio 評估。 */
    PORTFOLIO
}
