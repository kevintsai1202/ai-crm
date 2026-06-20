package com.aicrm.crm.domain;

/**
 * AI 回答的人工回饋決定：採納或拒絕。
 */
public enum FeedbackDecision {
    /** 採納此 AI 回答。 */
    ADOPTED,
    /** 拒絕此 AI 回答。 */
    REJECTED
}
