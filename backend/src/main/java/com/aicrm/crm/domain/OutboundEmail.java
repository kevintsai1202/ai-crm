package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 外寄郵件紀錄。保存核准當下的內容快照（寄件者／Reply-To／收件者／主旨／內文），
 * 以及寄送狀態、Zeabur message id、重試次數與去敏錯誤摘要。憑證絕不寫入此表。
 */
@Entity
@Table(name = "outbound_emails")
public class OutboundEmail extends AuditableEntity {
    /** 樂觀鎖版本，避免併發重試遺失更新。 */
    @Version @Column(nullable = false) private long version;
    /** 外寄郵件主鍵。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** 來源草稿（內容快照已存於本列，草稿僅供追溯）。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "draft_id", nullable = false) private FollowUpDraft draft;
    /** 建立者帳號，亦為重試的水平權限範圍。 */
    @Column(name = "creator_username", nullable = false) private String creatorUsername;
    /** 寄件者（統一已驗證公司信箱）。 */
    @Column(name = "from_address", nullable = false) private String fromAddress;
    /** Reply-To（客戶／商機負責業務 Email）。 */
    @Column(name = "reply_to", nullable = false) private String replyTo;
    /** 收件者。 */
    @Column(nullable = false) private String recipient;
    /** 主旨快照。 */
    @Column(nullable = false) private String subject;
    /** 內文快照。 */
    @Column(columnDefinition = "text", nullable = false) private String body;
    /** 寄送狀態。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OutboundEmailStatus status;
    /** Zeabur Sendmail 回傳的 message id。 */
    @Column(name = "message_id") private String messageId;
    /** 重試次數。 */
    @Column(name = "retry_count", nullable = false) private int retryCount;
    /** 去敏錯誤摘要（固定安全訊息，絕不含憑證）。 */
    @Column(name = "error_summary", length = 1000) private String errorSummary;
    /** 寄送請求冪等鍵。 */
    @Column(name = "idempotency_key") private String idempotencyKey;
    /** 冪等 payload 雜湊，供相同鍵不同請求偵測衝突。 */
    @Column(name = "idempotency_payload_hash", length = 64) private String idempotencyPayloadHash;
    /** 成功寄出時間。 */
    @Column(name = "sent_at") private Instant sentAt;

    protected OutboundEmail() {}

    /** 建立 QUEUED 外寄郵件，帶入核准當下的內容快照與冪等資訊。 */
    public OutboundEmail(FollowUpDraft draft, String creatorUsername, String fromAddress, String replyTo,
            String recipient, String subject, String body, String idempotencyKey, String idempotencyPayloadHash) {
        this.draft = draft; this.creatorUsername = creatorUsername; this.fromAddress = fromAddress;
        this.replyTo = replyTo; this.recipient = recipient; this.subject = subject; this.body = body;
        this.idempotencyKey = idempotencyKey; this.idempotencyPayloadHash = idempotencyPayloadHash;
        this.status = OutboundEmailStatus.QUEUED; this.retryCount = 0;
    }

    /** 標記寄送成功，記錄 message id 與時間並清除錯誤。 */
    public void markSent(String messageId) {
        this.status = OutboundEmailStatus.SENT; this.messageId = messageId;
        this.sentAt = Instant.now(); this.errorSummary = null;
    }

    /** 標記寄送失敗；只保存固定安全訊息，不含任何憑證。 */
    public void markFailed(String safeErrorSummary) {
        this.status = OutboundEmailStatus.FAILED; this.errorSummary = safeErrorSummary;
    }

    /** 累加重試次數（每次重試前呼叫）。 */
    public void incrementRetry() { this.retryCount++; }

    public Long getId() { return id; }
    public FollowUpDraft getDraft() { return draft; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getFromAddress() { return fromAddress; }
    public String getReplyTo() { return replyTo; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public OutboundEmailStatus getStatus() { return status; }
    public String getMessageId() { return messageId; }
    public int getRetryCount() { return retryCount; }
    public String getErrorSummary() { return errorSummary; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getIdempotencyPayloadHash() { return idempotencyPayloadHash; }
    public Instant getSentAt() { return sentAt; }
}
