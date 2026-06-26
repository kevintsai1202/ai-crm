package com.aicrm.crm.domain;

/**
 * 商機來源，用於漏斗切片與通路分析。
 * INBOUND=主動上門/進線、OUTBOUND=業務開發、REFERRAL=推薦轉介。
 */
public enum LeadSource {
    INBOUND,
    OUTBOUND,
    REFERRAL
}
