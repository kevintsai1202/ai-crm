package com.aicrm.crm.service;

/** OCR 或語音轉錄沒有可用用途 assignment 時的 fail-closed 例外。 */
public class AiPurposeModelUnavailableException extends RuntimeException {
    /** 建立不含憑證或 Provider 細節的安全錯誤。 */
    public AiPurposeModelUnavailableException(String message) {
        super(message);
    }
}
