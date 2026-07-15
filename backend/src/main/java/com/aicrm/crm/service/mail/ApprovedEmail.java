package com.aicrm.crm.service.mail;

/**
 * 已核准、準備寄送的郵件內容快照。
 *
 * @param from 寄件者（統一已驗證公司信箱）
 * @param replyTo Reply-To（客戶／商機負責業務 Email）
 * @param recipient 收件者
 * @param subject 主旨
 * @param body 內文
 */
public record ApprovedEmail(String from, String replyTo, String recipient, String subject, String body) {}
