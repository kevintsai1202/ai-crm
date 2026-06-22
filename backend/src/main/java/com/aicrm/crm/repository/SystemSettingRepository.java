package com.aicrm.crm.repository;

import com.aicrm.crm.domain.SystemSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系統設定資料存取：以 setting_key 為主鍵查詢。
 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    /**
     * 依設定鍵查詢。
     *
     * @param settingKey 設定鍵
     * @return 設定（可能不存在）
     */
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
