package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 個人偏好設定（泛用 key-value）：對應 user_preferences 表。
 * 函式級註解：本次用於 dashboard_layout（存可見區塊有序 id 陣列 JSON）；自行管理 updated_at。
 */
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬使用者 ID。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 偏好鍵，如 dashboard_layout。 */
    @Column(name = "pref_key", nullable = false, length = 64)
    private String prefKey;

    /** 偏好值（JSON 字串）。 */
    @Column(name = "pref_value", nullable = false, columnDefinition = "text")
    private String prefValue;

    /** 最後更新時間（自行管理）。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {
    }

    /**
     * 建立個人偏好。
     *
     * @param userId 使用者 ID
     * @param prefKey 偏好鍵
     * @param prefValue 偏好值 JSON 字串
     */
    public UserPreference(Long userId, String prefKey, String prefValue) {
        this.userId = userId;
        this.prefKey = prefKey;
        this.prefValue = prefValue;
        this.updatedAt = Instant.now();
    }

    /** 更新偏好值並刷新更新時間。 */
    public void updateValue(String prefValue) {
        this.prefValue = prefValue;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPrefKey() { return prefKey; }
    public String getPrefValue() { return prefValue; }
    public Instant getUpdatedAt() { return updatedAt; }
}
