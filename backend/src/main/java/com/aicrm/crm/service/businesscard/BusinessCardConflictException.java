package com.aicrm.crm.service.businesscard;
/** 名片狀態或冪等 payload 衝突。 */
public class BusinessCardConflictException extends RuntimeException { public BusinessCardConflictException(String message){super(message);} }
