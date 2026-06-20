package com.aicrm.crm.repository;

import com.aicrm.crm.domain.UserPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 個人偏好資料存取：依 (userId, prefKey) 查詢唯一偏好。
 */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * 依使用者與偏好鍵查詢。
     *
     * @param userId 使用者 ID
     * @param prefKey 偏好鍵
     * @return 偏好（可能不存在）
     */
    Optional<UserPreference> findByUserIdAndPrefKey(Long userId, String prefKey);
}
