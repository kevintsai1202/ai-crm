package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UserPreferenceService 整合測試：dashboard layout 的 upsert 與讀取。
 * 以 seed 帳號驗證；DB 由 PostgresTestBase 提供。admin 帳號用於「無偏好」案例，
 * 避免與 MeControllerIntegrationTest（寫 manager）、本類別（寫 sales）在共用容器下互相污染。
 */
class UserPreferenceIntegrationTest extends PostgresTestBase {

    @Autowired UserPreferenceService service;

    @Test
    void getDashboardLayout_whenNoPreference_returnsNull() {
        // admin@aurora.local 未被任何測試寫入 layout，應回 null（讓前端套預設）
        assertThat(service.getDashboardLayout("admin@aurora.local")).isNull();
    }

    @Test
    void saveThenGet_returnsSameOrder() {
        var order = List.of("metrics", "rfm", "reports");
        service.saveDashboardLayout("sales@aurora.local", order);
        assertThat(service.getDashboardLayout("sales@aurora.local")).containsExactly("metrics", "rfm", "reports");
    }

    @Test
    void save_isUpsert_overwritesPrevious() {
        service.saveDashboardLayout("sales@aurora.local", List.of("metrics"));
        service.saveDashboardLayout("sales@aurora.local", List.of("reports", "metrics"));
        assertThat(service.getDashboardLayout("sales@aurora.local")).containsExactly("reports", "metrics");
    }
}
