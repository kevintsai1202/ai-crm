package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 共用稽核欄位，對齊教學提示詞中的 created_at / updated_at 要求。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    /** 建立時間，由 JPA Auditing 自動填入。 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最後更新時間，由 JPA Auditing 自動填入。 */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 建立者，教學版固定為 system 或登入使用者。 */
    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    /** 更新者，教學版固定為 system 或登入使用者。 */
    @Column(name = "updated_by", nullable = false)
    private String updatedBy = "system";

    /**
     * 取得建立時間。
     *
     * @return 建立時間
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 取得更新時間。
     *
     * @return 更新時間
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

