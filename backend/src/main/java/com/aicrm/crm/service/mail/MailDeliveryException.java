package com.aicrm.crm.service.mail;

/** 郵件寄送失敗。訊息只描述失敗性質，絕不包含 token／密碼等憑證。 */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(String message) { super(message); }
    public MailDeliveryException(String message, Throwable cause) { super(message, cause); }
}
