package com.aicrm.crm.domain;

/** 外寄郵件狀態：排入佇列、已寄出、寄送失敗。 */
public enum OutboundEmailStatus {
    /** 已建立待寄送。 */
    QUEUED,
    /** 已成功透過寄信服務寄出。 */
    SENT,
    /** 寄送失敗，可重試。 */
    FAILED
}
