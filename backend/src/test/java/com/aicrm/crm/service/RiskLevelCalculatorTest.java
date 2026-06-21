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

    @Test
    void exactlyAt60Days_returnsMedium() {
        // 邊界測試：days == 60 應為 MEDIUM（not HIGH），因為條件是 days > 60
        var last = today.minusDays(60).atStartOfDay(); // 2026-04-22 00:00
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("MEDIUM");
    }

    @Test
    void exactlyAt30Days_returnsLow() {
        // 邊界測試：days == 30 應為 LOW（not MEDIUM），因為條件是 days > 30
        var last = today.minusDays(30).atStartOfDay(); // 2026-05-22 00:00
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("LOW");
    }

    @Test
    void renewalDueEqualsToday_returnsLow() {
        // 邊界測試：renewalDueDate == today 應為 LOW（not HIGH），因為條件是 renewalDueDate.isBefore(today)
        var last = LocalDateTime.of(2026, 6, 20, 0, 0); // 近期互動
        var renewalDue = today; // 2026-06-21，等於今天（非逾期）
        assertThat(RiskLevelCalculator.calculate(last, renewalDue, today)).isEqualTo("LOW");
    }
}
