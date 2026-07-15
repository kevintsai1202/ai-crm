package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 保存會議音訊轉錄、AI 摘要、結構化草稿與人工確認結果的 session。 */
@Entity
@Table(name = "meeting_copilot_sessions")
public class MeetingCopilotSession extends AuditableEntity {
    /** 樂觀鎖版本，避免非確認路徑遺失更新。 */
    @Version @Column(nullable = false) private long version;
    /** session 主鍵。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** 暫存音訊 metadata。 */
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "media_id", nullable = false, unique = true) private TemporaryMedia media;
    /** 上傳者帳號，亦為水平權限範圍。 */
    @Column(name = "creator_username", nullable = false) private String creatorUsername;
    /** 會議所屬客戶。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id", nullable = false) private Customer customer;
    /** 選填關聯商機。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "opportunity_id") private Opportunity opportunity;
    /** 流程狀態。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MeetingCopilotStatus status;
    /** 實際轉錄模型與 Provider pair。 */
    @Column(name = "transcription_model", nullable = false) private String transcriptionModel;
    @Column(name = "transcription_provider_id", nullable = false) private Long transcriptionProviderId;
    /** 逐字稿，確認後仍保留作為正式互動依據。 */
    @Column(columnDefinition = "text") private String transcript;
    /** AI 摘要。 */
    @Column(columnDefinition = "text") private String summary;
    /** 結構化草稿（含穩定 changeId）。 */
    @Column(name = "draft_json", columnDefinition = "text") private String draftJson;
    /** 去敏錯誤摘要。 */
    @Column(name = "error_summary", length = 1000) private String errorSummary;
    /** 確認 audit。 */
    @Column(name = "confirmed_by") private String confirmedBy;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    /** 確認請求冪等資訊。 */
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "idempotency_payload_hash", length = 64) private String idempotencyPayloadHash;
    /** 確認結果 JSON，供相同冪等鍵重送時回傳原結果。 */
    @Column(name = "confirm_result_json", columnDefinition = "text") private String confirmResultJson;

    protected MeetingCopilotSession() {}

    /** 建立剛上傳完成、尚未轉錄的 session。 */
    public MeetingCopilotSession(TemporaryMedia media, String creatorUsername, Customer customer,
            Opportunity opportunity, String transcriptionModel, Long transcriptionProviderId) {
        this.media = media; this.creatorUsername = creatorUsername; this.customer = customer;
        this.opportunity = opportunity; this.transcriptionModel = transcriptionModel;
        this.transcriptionProviderId = transcriptionProviderId; this.status = MeetingCopilotStatus.UPLOADED;
    }

    /** 進入處理中狀態。 */
    public void startProcessing() { this.status = MeetingCopilotStatus.PROCESSING; }

    /** 保存可供人工審核的逐字稿、摘要與草稿。 */
    public void review(String transcript, String summary, String draftJson) {
        this.transcript = transcript; this.summary = summary; this.draftJson = draftJson;
        this.status = MeetingCopilotStatus.REVIEW_PENDING;
    }

    /** 將轉錄或草稿錯誤轉成安全失敗狀態。 */
    public void fail(String summary) { this.errorSummary = summary; this.status = MeetingCopilotStatus.FAILED; }

    /** 記錄單一成功確認結果與冪等 payload；transcript 與 draft 保留不清除。 */
    public void confirm(String username, String key, String hash, String confirmResultJson) {
        this.confirmedBy = username; this.confirmedAt = Instant.now(); this.idempotencyKey = key;
        this.idempotencyPayloadHash = hash; this.confirmResultJson = confirmResultJson;
        this.status = MeetingCopilotStatus.CONFIRMED;
    }

    public Long getId() { return id; } public TemporaryMedia getMedia() { return media; } public String getCreatorUsername() { return creatorUsername; }
    public Customer getCustomer() { return customer; } public Opportunity getOpportunity() { return opportunity; }
    public MeetingCopilotStatus getStatus() { return status; } public String getTranscriptionModel() { return transcriptionModel; }
    public Long getTranscriptionProviderId() { return transcriptionProviderId; } public String getTranscript() { return transcript; }
    public String getSummary() { return summary; } public String getDraftJson() { return draftJson; } public String getErrorSummary() { return errorSummary; }
    public String getIdempotencyKey() { return idempotencyKey; } public String getIdempotencyPayloadHash() { return idempotencyPayloadHash; }
    public String getConfirmResultJson() { return confirmResultJson; }
}
