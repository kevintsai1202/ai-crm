package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** RiskLevelCalculator 純函式測試：各風險分支與邊界。 */
class RiskLevelCalculatorTest {

    private final LocalDate today = LocalDate.of(2026, 6, 21);

    @Test
    void nullLastInteraction_returnsMedium() {
        assertThat(RiskLevelCalculator.calculate(null, null, today)).isEqualTo("MEDIUM");
    }

    @Test
    void over60DaysSinceInteraction_returnsHigh() {
        var last = LocalDateTime.of(2026, 4, 1, 0, 0); // 距 6/21 > 60 天
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("HIGH");
    }

    @Test
    void renewalOverdue_returnsHigh() {
        var last = LocalDateTime.of(2026, 6, 20, 0, 0); // 近期互動
        var overdueRenewal = LocalDate.of(2026, 6, 1);  // 已逾期
        assertThat(RiskLevelCalculator.calculate(last, overdueRenewal, today)).isEqualTo("HIGH");
    }

    @Test
    void between31And60Days_returnsMedium() {
        var last = LocalDateTime.of(2026, 5, 10, 0, 0); // 距 6/21 約 42 天
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("MEDIUM");
    }

    @Test
    void recentInteraction_returnsLow() {
        var last = LocalDateTime.of(2026, 6, 15, 0, 0); // 距 6/21 = 6 天
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("LOW");
    }
}
