package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 對話訊息實體：落庫每輪 user/assistant 訊息供時序記憶查詢。
 * 函式級註解：採簡化稽核欄位，自行管理 created_at（與 V7 migration 一致），不繼承 AuditableEntity；
 * embedding 不映進此實體（同 KnowledgeDocument 做法），由 ChatMessageVectorRepository 用 JdbcTemplate 處理。
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    /** 訊息主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬客戶 id，用一般欄位（非關聯）以簡化。 */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 訊息角色（USER / ASSISTANT），以字串存。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    /** 訊息內容。 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 建立時間，自行管理（對齊 AiCallLog 做法，型別用 Instant 對應 timestamp with time zone）。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public ChatRole getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
