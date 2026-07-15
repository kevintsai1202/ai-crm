package com.aicrm.crm.domain;

/**
 * Stakeholder 決策鏈項目的資料來源，用於區分 AI 推測與人工輸入。
 */
public enum StakeholderSource {
    /** 由 AI（deterministic 推論）產生。 */
    AI,
    /** 由使用者手動輸入。 */
    MANUAL
}
