package com.aicrm.crm.service;

import com.aicrm.crm.domain.UserPreference;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.UserPreferenceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 個人偏好服務：本次提供儀表板版面（可見區塊有序 id 陣列）的讀取與 upsert。
 * 函式級註解：對外以登入 username 操作，內部解析為 userId；layout 以 JSON 字串存 pref_value。
 */
@Service
public class UserPreferenceService {

    /** 儀表板版面偏好鍵。 */
    private static final String KEY_DASHBOARD_LAYOUT = "dashboard_layout";

    /** 偏好資料存取。 */
    private final UserPreferenceRepository preferenceRepository;
    /** 使用者查詢（username → id）。 */
    private final AppUserRepository userRepository;
    /** Jackson 3 ObjectMapper（Spring Boot 4.1 受管 bean）。 */
    private final ObjectMapper objectMapper;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository, AppUserRepository userRepository, ObjectMapper objectMapper) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 讀取使用者的儀表板版面（可見區塊有序 id 陣列）。
     *
     * @param username 登入帳號
     * @return 區塊 id 陣列；尚未設定時回 null
     */
    @Transactional(readOnly = true)
    public List<String> getDashboardLayout(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在: " + username));
        return preferenceRepository.findByUserIdAndPrefKey(user.getId(), KEY_DASHBOARD_LAYOUT)
                .map(pref -> deserialize(pref.getPrefValue()))
                .orElse(null);
    }

    /**
     * upsert 使用者的儀表板版面。
     *
     * @param username 登入帳號
     * @param visibleOrder 可見區塊有序 id 陣列
     */
    @Transactional
    public void saveDashboardLayout(String username, List<String> visibleOrder) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在: " + username));
        var json = serialize(visibleOrder);
        preferenceRepository.findByUserIdAndPrefKey(user.getId(), KEY_DASHBOARD_LAYOUT)
                .ifPresentOrElse(
                        existing -> { existing.updateValue(json); preferenceRepository.save(existing); },
                        () -> preferenceRepository.save(new UserPreference(user.getId(), KEY_DASHBOARD_LAYOUT, json))
                );
    }

    /** 將區塊 id 陣列序列化為 JSON 字串。 */
    private String serialize(List<String> visibleOrder) {
        return objectMapper.writeValueAsString(visibleOrder);
    }

    /** 將 JSON 字串反序列化為區塊 id 陣列。 */
    private List<String> deserialize(String json) {
        return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    }
}
