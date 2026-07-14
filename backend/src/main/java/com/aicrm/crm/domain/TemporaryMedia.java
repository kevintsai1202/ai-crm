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

/** S3 暫存物件的 PostgreSQL metadata，不保存媒體內容。 */
@Entity
@Table(name = "temporary_media")
public class TemporaryMedia extends AuditableEntity {
    /** 暫存媒體主鍵。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 不可推知使用者或原始檔名的隨機 object key。 */
    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;
    /** 僅供 UI 顯示的原始檔名。 */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    /** 驗證後的 MIME 類型。 */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;
    /** 檔案大小（bytes）。 */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    /** 檔案內容 SHA-256（hex）。 */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
    /** 媒體用途。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private MediaPurpose purpose;
    /** 媒體生命週期狀態。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private MediaStatus status;
    /** 建立媒體的登入帳號。 */
    @Column(name = "creator_username", nullable = false, length = 255)
    private String creatorUsername;
    /** 尚未確認媒體的清理期限。 */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    /** 物件實際刪除時間。 */
    @Column(name = "deleted_at")
    private Instant deletedAt;
    /** 不含敏感內容的錯誤摘要。 */
    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    protected TemporaryMedia() {}

    /** 建立剛上傳完成的暫存媒體 metadata。 */
    public TemporaryMedia(String objectKey, String originalFilename, String mimeType, long sizeBytes,
                          String sha256, MediaPurpose purpose, String creatorUsername, Instant expiresAt) {
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.purpose = purpose;
        this.status = MediaStatus.UPLOADED;
        this.creatorUsername = creatorUsername;
        this.expiresAt = expiresAt;
    }

    /** 僅供不啟動 JPA 的 cleanup 單元測試重建候選資料。 */
    public static TemporaryMedia restoreForCleanup(Long id, String objectKey, MediaStatus status, Instant expiresAt) {
        TemporaryMedia media = new TemporaryMedia();
        media.id = id;
        media.objectKey = objectKey;
        media.status = status;
        media.expiresAt = expiresAt;
        return media;
    }

    /** 更新處理狀態。 */
    public void transition(MediaStatus status) { this.status = status; }

    /** 記錄物件已刪除；metadata 保留供 audit。 */
    public void markDeleted() { this.status = MediaStatus.DELETED; this.deletedAt = Instant.now(); this.errorSummary = null; }

    /** 記錄刪除失敗的安全摘要供 cleanup retry。 */
    public void deletionFailed(String summary) { this.status = MediaStatus.FAILED; this.errorSummary = summary; }

    public Long getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public MediaPurpose getPurpose() { return purpose; }
    public MediaStatus getStatus() { return status; }
    public String getCreatorUsername() { return creatorUsername; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getErrorSummary() { return errorSummary; }
}
