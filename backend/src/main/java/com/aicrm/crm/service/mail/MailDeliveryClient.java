package com.aicrm.crm.service.mail;

/**
 * 郵件寄送邊界。正式環境由 {@link ZeaburSendmailClient} 實作；
 * 自動測試與本機以 {@link FakeMailDeliveryClient} 提供 deterministic 結果。
 */
public interface MailDeliveryClient {
    /**
     * 寄送已核准郵件。
     *
     * @param email 郵件內容快照
     * @return 含 message id 的寄送結果
     * @throws MailDeliveryException 寄送失敗（訊息不得包含憑證）
     */
    DeliveryResult send(ApprovedEmail email);
}
