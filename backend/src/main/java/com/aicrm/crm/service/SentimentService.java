package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.config.CacheConfig;
import com.aicrm.crm.repository.InteractionInsightRepository;
import org.springframework.cache.annotation.Cacheable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情緒意圖雷達聚合服務。
 *
 * <p>以 {@link InteractionInsightRepository} 聚合產出儀表板 5 區塊：意圖分布、近 12 月情緒趨勢（補空月）、
 * 高風險互動（NEGATIVE 且意圖為流失 / 客訴，top 20）、流失雷達（加權排序 top 20）、優先關懷（top 10 附中文理由）。
 * 純讀取，不接 governance、不呼叫 LLM。</p>
 */
@Service
@Transactional(readOnly = true)
public class SentimentService {

    /** 情緒趨勢回溯月數。 */
    private static final int TREND_MONTHS = 12;

    /** 高風險互動取前 N 筆。 */
    private static final int HIGH_RISK_LIMIT = 20;

    /** 流失雷達取前 N 筆。 */
    private static final int CHURN_RADAR_LIMIT = 20;

    /** 優先關懷取前 N 筆。 */
    private static final int PRIORITY_CARE_LIMIT = 10;

    /** 月份格式 yyyy-MM。 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 互動情緒意圖分析結果存取。 */
    private final InteractionInsightRepository insights;

    public SentimentService(InteractionInsightRepository insights) {
        this.insights = insights;
    }

    /**
     * 產出情緒意圖雷達 5 區塊；churnRadar 先算一次，供 priorityCare 複用，避免重複查詢。
     *
     * @return 雷達聚合結果
     */
    @Cacheable(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
    public Dtos.SentimentRadarResponse radar() {
        var churn = churnRadar();
        return new Dtos.SentimentRadarResponse(
                intentDistribution(),
                sentimentTrend(),
                highRiskInteractions(),
                churn,
                priorityCareFrom(churn)
        );
    }

    /**
     * 意圖分布（筆數多者在前）。
     *
     * @return 意圖分布清單
     */
    private List<Dtos.IntentCount> intentDistribution() {
        var result = new ArrayList<Dtos.IntentCount>();
        for (var row : insights.countByIntent()) {
            // countByIntent 為 JPQL，intent 欄位為 enum 型別
            result.add(new Dtos.IntentCount(row[0].toString(), ((Number) row[1]).longValue()));
        }
        return result;
    }

    /**
     * 近 12 月情緒趨勢，補滿無資料的月份為 0。
     *
     * @return 月情緒趨勢清單（月份升冪）
     */
    private List<Dtos.SentimentTrendPoint> sentimentTrend() {
        var currentMonth = YearMonth.now();
        var startMonth = currentMonth.minusMonths(TREND_MONTHS - 1L);
        // 起算時間：回溯區間第一個月的第一天
        var since = startMonth.atDay(1).atStartOfDay();

        // 查詢結果以月份字串為鍵
        Map<String, Dtos.SentimentTrendPoint> byMonth = new HashMap<>();
        for (var row : insights.sentimentTrendSince(since)) {
            var month = (String) row[0];
            byMonth.put(month, new Dtos.SentimentTrendPoint(
                    month,
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()));
        }

        // 補滿近 12 月（含當月），缺月補 0
        var result = new ArrayList<Dtos.SentimentTrendPoint>(TREND_MONTHS);
        for (int i = 0; i < TREND_MONTHS; i++) {
            var ym = startMonth.plusMonths(i);
            var key = ym.format(MONTH_FORMAT);
            result.add(byMonth.getOrDefault(key, new Dtos.SentimentTrendPoint(key, 0, 0, 0)));
        }
        return result;
    }

    /**
     * 高風險互動 top 20（NEGATIVE 且意圖為流失 / 客訴，新到舊）。
     *
     * @return 高風險互動清單
     */
    private List<Dtos.HighRiskInteraction> highRiskInteractions() {
        var rows = insights.findHighRiskInteractions(PageRequest.of(0, HIGH_RISK_LIMIT));
        var result = new ArrayList<Dtos.HighRiskInteraction>(rows.size());
        for (var row : rows) {
            result.add(new Dtos.HighRiskInteraction(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    toLocalDateTime(row[2]),
                    row[3].toString(),
                    (String) row[4],
                    (String) row[5],
                    (String) row[6]));
        }
        return result;
    }

    /**
     * 流失雷達 top 20：依加權分數排序。
     * 加權：負面 *1 + 流失訊號 *3 + 客訴 *2（流失訊號權重最高）。
     *
     * @return 流失雷達清單（分數高者在前）
     */
    private List<Dtos.ChurnRadarItem> churnRadar() {
        var result = new ArrayList<Dtos.ChurnRadarItem>();
        for (var row : insights.aggregateCustomerRisk()) {
            long negative = ((Number) row[2]).longValue();
            long churn = ((Number) row[3]).longValue();
            long complaint = ((Number) row[4]).longValue();
            int score = (int) (negative + churn * 3 + complaint * 2);
            result.add(new Dtos.ChurnRadarItem(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    negative,
                    churn,
                    complaint,
                    score));
        }
        result.sort(Comparator.comparingInt(Dtos.ChurnRadarItem::score).reversed());
        return result.size() > CHURN_RADAR_LIMIT ? result.subList(0, CHURN_RADAR_LIMIT) : result;
    }

    /**
     * 優先關懷 top 10：取傳入的 churnRadar 結果（最高分者），附中文關懷理由。
     * 不重複呼叫 DB；由 radar() 傳入已算好的結果。
     *
     * @param radar 流失雷達清單（已依分數降冪排序）
     * @return 優先關懷清單
     */
    private List<Dtos.PriorityCareItem> priorityCareFrom(List<Dtos.ChurnRadarItem> radar) {
        var result = new ArrayList<Dtos.PriorityCareItem>();
        for (var item : radar) {
            if (result.size() >= PRIORITY_CARE_LIMIT) {
                break;
            }
            result.add(new Dtos.PriorityCareItem(item.customerId(), item.name(), buildReason(item)));
        }
        return result;
    }

    /**
     * 依風險計數組裝中文關懷理由，例如「3 則負面 + 1 則客訴」。
     *
     * @param item 流失雷達單筆
     * @return 中文理由
     */
    private String buildReason(Dtos.ChurnRadarItem item) {
        var parts = new ArrayList<String>();
        if (item.negativeCount() > 0) {
            parts.add(item.negativeCount() + " 則負面");
        }
        if (item.churnSignalCount() > 0) {
            parts.add(item.churnSignalCount() + " 則流失訊號");
        }
        if (item.complaintCount() > 0) {
            parts.add(item.complaintCount() + " 則客訴");
        }
        return parts.isEmpty() ? "需主動關懷" : String.join(" + ", parts);
    }

    /**
     * 將 native query 回傳的時間欄位轉為 LocalDateTime（JDBC 可能回 Timestamp 或 LocalDateTime）。
     *
     * @param value native query 時間欄位值
     * @return LocalDateTime
     */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toLocalDateTime();
        }
        if (value instanceof java.time.Instant instant) {
            return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        }
        // 其餘型別保守解析（不應發生）
        return LocalDate.parse(value.toString()).atStartOfDay();
    }
}
