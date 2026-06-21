package com.aicrm.crm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 風險等級計算純函式：自 CustomerMapper 抽出，today 改為參數注入以利測試與排程共用。
 * 規則維持原行為：無互動→MEDIUM；逾 60 天未互動或續約逾期→HIGH；逾 30 天→MEDIUM；其餘→LOW。
 */
public final class RiskLevelCalculator {

    private RiskLevelCalculator() {
    }

    /**
     * 計算風險等級。
     *
     * @param lastInteractionAt 最後互動時間（可為 null）
     * @param renewalDueDate 預計續約日（可為 null）
     * @param today 作為基準的今天日期
     * @return HIGH / MEDIUM / LOW
     */
    public static String calculate(LocalDateTime lastInteractionAt, LocalDate renewalDueDate, LocalDate today) {
        if (lastInteractionAt == null) {
            return "MEDIUM";
        }
        var days = ChronoUnit.DAYS.between(lastInteractionAt.toLocalDate(), today);
        if (days > 60 || (renewalDueDate != null && renewalDueDate.isBefore(today))) {
            return "HIGH";
        }
        if (days > 30) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
