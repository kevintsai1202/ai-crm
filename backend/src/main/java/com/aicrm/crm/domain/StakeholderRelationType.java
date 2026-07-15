package com.aicrm.crm.domain;

/**
 * 兩位 Stakeholder（Contact）之間的關係類型。
 */
public enum StakeholderRelationType {
    /** 前者向後者匯報（上下級）。 */
    REPORTS_TO,
    /** 平行同僚。 */
    PEER,
    /** 前者影響後者。 */
    INFLUENCES,
    /** 盟友關係。 */
    ALLY,
    /** 對立 / 競爭關係。 */
    RIVAL
}
