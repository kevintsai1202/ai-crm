package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 保存名片辨識草稿、重複候選與人工確認結果。 */
@Entity @Table(name = "business_card_intakes")
public class BusinessCardIntake extends AuditableEntity {
    /** 樂觀鎖版本，避免非確認路徑遺失更新。 */
    @Version @Column(nullable=false) private long version;
    /** 名片 intake 主鍵。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** 暫存原圖 metadata。 */
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "media_id", nullable = false, unique = true) private TemporaryMedia media;
    /** 上傳者帳號，亦為水平權限範圍。 */
    @Column(name = "creator_username", nullable = false) private String creatorUsername;
    /** 流程狀態。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BusinessCardStatus status;
    /** 實際 OCR 模型與 Provider pair。 */
    @Column(name = "ocr_model", nullable = false) private String ocrModel;
    @Column(name = "ocr_provider_id", nullable = false) private Long ocrProviderId;
    /** AI 結構化草稿與候選 JSON。 */
    @Column(name = "recognized_json", columnDefinition = "text") private String recognizedJson;
    @Column(name = "duplicate_candidates_json", columnDefinition = "text") private String duplicateCandidatesJson;
    /** 去敏錯誤摘要。 */
    @Column(name = "error_summary", length = 1000) private String errorSummary;
    /** 確認 audit 與最終 CRM IDs。 */
    @Column(name = "confirmed_by") private String confirmedBy;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "contact_id") private Long contactId;
    @Column(name = "opportunity_id") private Long opportunityId;
    @Column(name = "task_id") private Long taskId;
    /** 確認請求冪等資訊。 */
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "idempotency_payload_hash", length = 64) private String idempotencyPayloadHash;

    protected BusinessCardIntake() {}

    /** 建立處理中的名片辨識工作。 */
    public BusinessCardIntake(TemporaryMedia media, String creatorUsername, String model, Long providerId) {
        this.media = media; this.creatorUsername = creatorUsername; this.ocrModel = model;
        this.ocrProviderId = providerId; this.status = BusinessCardStatus.PROCESSING;
    }

    /** 保存可供人工檢查的辨識結果。 */
    public void review(String recognizedJson, String candidatesJson) {
        this.recognizedJson = recognizedJson; this.duplicateCandidatesJson = candidatesJson;
        this.status = BusinessCardStatus.REVIEW_PENDING;
    }

    /** 將外部辨識錯誤轉成安全失敗狀態。 */
    public void fail(String summary) { this.errorSummary = summary; this.status = BusinessCardStatus.FAILED; }

    /** 記錄單一成功確認結果與冪等 payload。 */
    public void confirm(String username, String key, String hash, Long customerId, Long contactId, Long opportunityId, Long taskId) {
        this.confirmedBy = username; this.confirmedAt = Instant.now(); this.idempotencyKey = key;
        this.idempotencyPayloadHash = hash; this.customerId = customerId; this.contactId = contactId;
        this.opportunityId = opportunityId; this.taskId = taskId; this.status = BusinessCardStatus.CONFIRMED;
    }

    public Long getId(){return id;} public TemporaryMedia getMedia(){return media;} public String getCreatorUsername(){return creatorUsername;}
    public BusinessCardStatus getStatus(){return status;} public String getOcrModel(){return ocrModel;} public Long getOcrProviderId(){return ocrProviderId;}
    public String getRecognizedJson(){return recognizedJson;} public String getDuplicateCandidatesJson(){return duplicateCandidatesJson;}
    public String getErrorSummary(){return errorSummary;} public String getIdempotencyKey(){return idempotencyKey;}
    public String getIdempotencyPayloadHash(){return idempotencyPayloadHash;} public Long getCustomerId(){return customerId;}
    public Long getContactId(){return contactId;} public Long getOpportunityId(){return opportunityId;} public Long getTaskId(){return taskId;}
}
