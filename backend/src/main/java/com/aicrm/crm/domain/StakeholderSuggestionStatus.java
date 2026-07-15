package com.aicrm.crm.domain;

/**
 * Stakeholder 決策鏈項目（角色 / 關係）的生命週期狀態。
 *
 * <p>用來區分「AI 建議」與「人工確認事實」：只有 {@link #CONFIRMED} 會顯示為已確認圖，
 * {@link #SUGGESTED} 為待確認建議，{@link #REJECTED} 保留 audit 但不顯示為事實、也不出現在待確認清單。</p>
 */
public enum StakeholderSuggestionStatus {
    /** AI 或人工提出、尚未確認的建議。 */
    SUGGESTED,
    /** 已由人工確認、視為事實並顯示於已確認圖。 */
    CONFIRMED,
    /** 已被人工拒絕，保留稽核紀錄但不顯示為事實。 */
    REJECTED
}
