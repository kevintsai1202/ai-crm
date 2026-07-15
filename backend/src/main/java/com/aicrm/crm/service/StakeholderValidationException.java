package com.aicrm.crm.service;

/**
 * Stakeholder 決策鏈輸入驗證失敗（例如跨 customer 的關係）；由 GlobalExceptionHandler 轉為 400。
 */
public class StakeholderValidationException extends RuntimeException {

    /**
     * 建立驗證例外。
     *
     * @param message 錯誤訊息
     */
    public StakeholderValidationException(String message) {
        super(message);
    }
}
