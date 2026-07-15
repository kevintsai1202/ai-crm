package com.aicrm.crm.service.mail;

/**
 * 寄信服務的寄送結果。
 *
 * @param messageId 寄信服務回傳的訊息識別碼
 */
public record DeliveryResult(String messageId) {}
