package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Manager 業務分析聚合測試：以種子資料驗證回應結構與基本一致性。 */
class ManagerAnalyticsServiceTest extends PostgresTestBase {

    @Autowired ManagerAnalyticsService analyticsService;

    /** 聚合應回傳非空業務清單，且團隊客戶數等於各業務客戶數總和。 */
    @Test
    void analytics_returnsOwnersAndConsistentTeamTotals() {
        var result = analyticsService.analytics();

        assertThat(result.owners()).isNotEmpty();
        assertThat(result.team().ownerCount()).isEqualTo(result.owners().size());
        long sumCustomers = result.owners().stream().mapToLong(o -> o.customerCount()).sum();
        assertThat(result.team().totalCustomers()).isEqualTo(sumCustomers);
        // 成交率介於 0 與 1
        assertThat(result.owners()).allMatch(o -> o.winRate() >= 0.0 && o.winRate() <= 1.0);
    }
}
