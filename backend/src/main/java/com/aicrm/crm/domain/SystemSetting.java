package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 系統設定（全域 key-value）：對應 system_settings 表。
 * 函式級註解：以 setting_key 為主鍵，setting_value 存純字串或 JSON 字串；自行管理 updated_at / updated_by。
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    /** 設定鍵（主鍵），如 ai.chat.model。 */
    @Id
    @Column(name = "setting_key", length = 64)
    private String settingKey;

    /** 設定值（純字串或 JSON 字串）。 */
    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String settingValue;

    /** 最後更新時間（自行管理）。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 最後修改者帳號（可為 null）。 */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected SystemSetting() {
    }

    /**
     * 建立系統設定。
     *
     * @param settingKey 設定鍵
     * @param settingValue 設定值
     * @param updatedBy 修改者帳號
     */
    public SystemSetting(String settingKey, String settingValue, String updatedBy) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    /** 更新設定值與修改者並刷新更新時間。 */
    public void updateValue(String settingValue, String updatedBy) {
        this.settingValue = settingValue;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
