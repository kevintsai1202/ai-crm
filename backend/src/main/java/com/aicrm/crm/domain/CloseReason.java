package com.aicrm.crm.domain;

/**
 * 商機結案（輸/贏）原因。WON_* 用於 CLOSED_WON、LOST_* 用於 CLOSED_LOST。
 */
public enum CloseReason {
    WON_PRICE,
    WON_FEATURE,
    WON_RELATIONSHIP,
    WON_TIMING,
    LOST_PRICE,
    LOST_COMPETITOR,
    LOST_NO_BUDGET,
    LOST_NO_DECISION,
    LOST_NO_RESPONSE
}
