package com.aicrm.crm.service.mail;

/** 跟進信寄送前置條件不符（如負責業務缺少有效 Email）；對應 HTTP 400。 */
public class FollowUpPreconditionException extends RuntimeException {
    public FollowUpPreconditionException(String message) { super(message); }
}
