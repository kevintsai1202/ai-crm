package com.aicrm.crm.domain;

/**
 * 互動意圖分類，供情緒意圖雷達聚合與優先關懷判斷使用。
 */
public enum Intent {
    /** 詢價。 */
    ASK_PRICING,
    /** 競品比較。 */
    COMPARE_COMPETITOR,
    /** 流失信號。 */
    CHURN_SIGNAL,
    /** 續約意願。 */
    RENEWAL_INTEREST,
    /** 加購 / 升級。 */
    UPSELL_SIGNAL,
    /** 客訴。 */
    COMPLAINT,
    /** 其他（無命中）。 */
    OTHER
}
