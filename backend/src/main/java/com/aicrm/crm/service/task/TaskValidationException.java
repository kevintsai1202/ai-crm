package com.aicrm.crm.service.task;

/** CRM 任務輸入或狀態規則不合法；HTTP 層只回固定安全訊息。 */
public class TaskValidationException extends RuntimeException {
    /** 建立任務驗證例外，message 僅供 server-side 診斷。 */
    public TaskValidationException(String message) { super(message); }
}
