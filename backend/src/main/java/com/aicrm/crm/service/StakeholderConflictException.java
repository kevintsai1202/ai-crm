package com.aicrm.crm.service;

/**
 * Stakeholder 建議狀態衝突（例如對已確認 / 已拒絕的建議再次 confirm / reject）；由 GlobalExceptionHandler 轉為 409。
 */
public class StakeholderConflictException extends RuntimeException {

    /**
     * 建立衝突例外。
     *
     * @param message 錯誤訊息
     */
    public StakeholderConflictException(String message) {
        super(message);
    }
}
