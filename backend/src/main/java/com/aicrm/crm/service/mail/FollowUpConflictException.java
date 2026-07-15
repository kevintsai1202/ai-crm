package com.aicrm.crm.service.mail;

/** 跟進信冪等或狀態衝突（如相同鍵不同 payload、已寄出不可重試）；對應 HTTP 409。 */
public class FollowUpConflictException extends RuntimeException {
    public FollowUpConflictException(String message) { super(message); }
}
