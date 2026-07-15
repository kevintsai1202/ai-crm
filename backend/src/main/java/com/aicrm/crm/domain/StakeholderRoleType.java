package com.aicrm.crm.domain;

/**
 * Stakeholder 在採購決策中的角色類型。
 */
public enum StakeholderRoleType {
    /** 決策者（最終拍板）。 */
    DECISION_MAKER,
    /** 經濟買者（掌握預算 / 採購）。 */
    ECONOMIC_BUYER,
    /** 技術買者（評估技術與規格）。 */
    TECHNICAL_BUYER,
    /** 內部支持者（推動我方方案）。 */
    CHAMPION,
    /** 影響者（意見具份量但非決策者）。 */
    INFLUENCER,
    /** 守門人（控制資訊或聯繫管道）。 */
    GATEKEEPER,
    /** 最終使用者。 */
    END_USER,
    /** 角色未知 / 尚無足夠訊號判斷。 */
    UNKNOWN
}
