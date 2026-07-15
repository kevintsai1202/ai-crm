package com.aicrm.crm.service.task;

/** CRM 任務版本衝突；代表用戶端畫面已過期。 */
public class TaskConflictException extends RuntimeException {
    /** 建立版本衝突例外，HTTP 層一律回固定安全訊息。 */
    public TaskConflictException() { super("CRM task version conflict"); }
}
