package com.aicrm.crm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 商機健康度純函式評分器（V26）。
 *
 * <p>本類別為 deterministic 純函式：只依 {@link OpportunityContext} 內的已知訊號計算，
 * <b>絕不呼叫 LLM、不查資料庫、不讀時鐘</b>（評估時間由 context.evaluatedAt 帶入），
 * 因此在固定 clock 下結果可重現，可被單元測試完整覆蓋。</p>
 *
 * <p>總分為六個分項分數之總和，保證 {@code sum(components) == total} 且 {@code total ∈ [0,100]}。
 * 每個分項各有固定上限、實得分數、可解釋的中文 reason 與 evidence reference（引用哪筆資料來源）。</p>
 */
@Component
public class OpportunityHealthCalculator {

    /** 規則版本；規則調整時遞增，snapshot 會記錄以利歷史比對。 */
    public static final String RULE_VERSION = "health-rules-v1";

    /** 台北時區：預計成交日等 LocalDate 比較的基準時區。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    // 各分項上限（總和 = 100，確保 total 上限為 100）。
    private static final int MAX_STAGE = 20;
    private static final int MAX_CLOSE = 15;
    private static final int MAX_HEAT = 20;
    private static final int MAX_SENTIMENT = 20;
    private static final int MAX_TASK = 15;
    private static final int MAX_CHAIN = 10;

    /**
     * 商機健康度評分所需的純資料輸入（不含任何 JPA entity，確保純函式可測）。
     *
     * @param closed 是否已結案（CLOSED_WON/CLOSED_LOST）
     * @param daysInCurrentStage 目前階段已停留天數；無階段歷史時為 null
     * @param currentStageName 目前階段名稱（供 evidence 文字）
     * @param expectedCloseDate 預計成交日；未設定時為 null
     * @param lastInteractionAt 最近一次互動時間；無互動時為 null
     * @param interactionsLast30Days 近 30 天互動次數
     * @param negativeSentimentCount 負面情緒互動筆數（SP6 interaction_insights）
     * @param riskIntentCount 流失/客訴等高風險意圖筆數（SP6 interaction_insights）
     * @param openTaskCount 未完成任務數（OPEN/IN_PROGRESS）
     * @param overdueTaskCount 逾期未完成任務數
     * @param contactCount 關聯客戶聯絡人數（決策鏈完整度 proxy）
     * @param evaluatedAt 評估時間（固定 clock 用；所有時間計算皆以此為基準）
     */
    public record OpportunityContext(
            boolean closed,
            Long daysInCurrentStage,
            String currentStageName,
            LocalDate expectedCloseDate,
            LocalDateTime lastInteractionAt,
            int interactionsLast30Days,
            int negativeSentimentCount,
            int riskIntentCount,
            int openTaskCount,
            int overdueTaskCount,
            int contactCount,
            Instant evaluatedAt) {
    }

    /**
     * 健康度評分結果。
     *
     * @param total 總分（0–100，恆等於各分項分數總和）
     * @param components 各分項明細
     */
    public record HealthScore(int total, List<HealthComponent> components) {
    }

    /**
     * 單一評分分項（可解釋）。
     *
     * @param key 分項代碼（穩定識別，如 STAGE_DWELL）
     * @param label 分項中文標籤
     * @param score 實得分數（0–maxScore）
     * @param maxScore 分項上限
     * @param reason 中文加/扣分理由
     * @param evidence 佐證來源引用（引用哪筆互動/任務/階段停留天數等）
     */
    public record HealthComponent(String key, String label, int score, int maxScore, String reason, String evidence) {
    }

    /**
     * 計算商機健康度總分與各分項（純函式）。
     *
     * @param context 評分所需的純資料輸入
     * @return 健康度評分結果，保證 sum(components)==total 且 total∈[0,100]
     */
    public HealthScore calculate(OpportunityContext context) {
        LocalDate today = LocalDate.ofInstant(context.evaluatedAt(), ZONE);
        List<HealthComponent> components = List.of(
                scoreStageDwell(context),
                scoreExpectedClose(context, today),
                scoreInteractionHeat(context, today),
                scoreSentimentIntent(context),
                scoreTaskStatus(context),
                scoreDecisionChain(context));
        int total = components.stream().mapToInt(HealthComponent::score).sum();
        return new HealthScore(total, components);
    }

    /** 階段停留：停留越久扣越多，反映漏斗推進停滯風險（evidence 引用 V20 階段歷史天數）。 */
    private HealthComponent scoreStageDwell(OpportunityContext c) {
        String stage = c.currentStageName() == null ? "-" : c.currentStageName();
        if (c.daysInCurrentStage() == null) {
            // 無階段歷史時以中性分計，並明述資料缺口，避免無資料就重扣。
            return new HealthComponent("STAGE_DWELL", "階段停留", 15, MAX_STAGE,
                    "無階段停留歷史資料，暫以中性分計算。",
                    "opportunity_stage_history：查無「" + stage + "」階段的進入紀錄");
        }
        long days = c.daysInCurrentStage();
        int score;
        String reason;
        if (days <= 14) {
            score = MAX_STAGE;
            reason = "目前於「" + stage + "」階段停留 " + days + " 天，推進節奏健康。";
        } else if (days <= 30) {
            score = 14;
            reason = "目前於「" + stage + "」階段停留 " + days + " 天，稍嫌偏長，建議推進。";
        } else if (days <= 60) {
            score = 7;
            reason = "目前於「" + stage + "」階段停留 " + days + " 天，明顯偏長，有停滯風險。";
        } else {
            score = 0;
            reason = "目前於「" + stage + "」階段停留 " + days + " 天，嚴重停滯，需立即介入。";
        }
        return new HealthComponent("STAGE_DWELL", "階段停留", score, MAX_STAGE, reason,
                "opportunity_stage_history：「" + stage + "」階段已停留 " + days + " 天");
    }

    /** 預計成交日：逾期歸零、接近到期示警、時程充裕給滿分。 */
    private HealthComponent scoreExpectedClose(OpportunityContext c, LocalDate today) {
        LocalDate close = c.expectedCloseDate();
        if (close == null) {
            return new HealthComponent("EXPECTED_CLOSE", "預計成交日", 7, MAX_CLOSE,
                    "尚未設定預計成交日，無法評估時程健康度。",
                    "opportunities.expected_close_date=null");
        }
        if (close.isBefore(today)) {
            long overdue = ChronoUnit.DAYS.between(close, today);
            return new HealthComponent("EXPECTED_CLOSE", "預計成交日", 0, MAX_CLOSE,
                    "預計成交日已逾期 " + overdue + " 天仍未結案，時程失控。",
                    "opportunities.expected_close_date=" + close + "（逾期 " + overdue + " 天）");
        }
        long daysUntil = ChronoUnit.DAYS.between(today, close);
        if (daysUntil <= 7) {
            return new HealthComponent("EXPECTED_CLOSE", "預計成交日", 8, MAX_CLOSE,
                    "距預計成交日僅 " + daysUntil + " 天，需加速推進以如期成交。",
                    "opportunities.expected_close_date=" + close + "（剩 " + daysUntil + " 天）");
        }
        return new HealthComponent("EXPECTED_CLOSE", "預計成交日", MAX_CLOSE, MAX_CLOSE,
                "距預計成交日 " + daysUntil + " 天，時程充裕。",
                "opportunities.expected_close_date=" + close + "（剩 " + daysUntil + " 天）");
    }

    /** 互動熱度：以距最近互動天數為主，越久未互動扣越多。 */
    private HealthComponent scoreInteractionHeat(OpportunityContext c, LocalDate today) {
        if (c.lastInteractionAt() == null) {
            return new HealthComponent("INTERACTION_HEAT", "互動熱度", 0, MAX_HEAT,
                    "近期無任何互動紀錄，關係熱度過低。",
                    "interactions：查無互動紀錄");
        }
        long days = ChronoUnit.DAYS.between(c.lastInteractionAt().toLocalDate(), today);
        int score;
        String reason;
        if (days <= 7) {
            score = MAX_HEAT;
            reason = "距最近互動僅 " + days + " 天（近30天 " + c.interactionsLast30Days() + " 次），互動熱度高。";
        } else if (days <= 14) {
            score = 15;
            reason = "距最近互動 " + days + " 天（近30天 " + c.interactionsLast30Days() + " 次），互動仍算活躍。";
        } else if (days <= 30) {
            score = 10;
            reason = "距最近互動 " + days + " 天（近30天 " + c.interactionsLast30Days() + " 次），互動趨緩。";
        } else if (days <= 60) {
            score = 5;
            reason = "距最近互動 " + days + " 天，互動明顯冷卻。";
        } else {
            score = 0;
            reason = "距最近互動 " + days + " 天，關係嚴重冷卻。";
        }
        return new HealthComponent("INTERACTION_HEAT", "互動熱度", score, MAX_HEAT, reason,
                "interactions：最近互動 " + c.lastInteractionAt().toLocalDate() + "，近30天 " + c.interactionsLast30Days() + " 次");
    }

    /** 情緒/意圖訊號：以 SP6 interaction_insights 的負面情緒與高風險意圖扣分。 */
    private HealthComponent scoreSentimentIntent(OpportunityContext c) {
        int deduct = Math.min(MAX_SENTIMENT, c.negativeSentimentCount() * 5 + c.riskIntentCount() * 7);
        int score = MAX_SENTIMENT - deduct;
        String reason;
        if (deduct == 0) {
            reason = "近期互動情緒正向/中性，且無流失或客訴訊號。";
        } else {
            reason = "偵測到 " + c.negativeSentimentCount() + " 則負面情緒、"
                    + c.riskIntentCount() + " 則流失/客訴訊號，扣減 " + deduct + " 分。";
        }
        return new HealthComponent("SENTIMENT_INTENT", "情緒/意圖訊號", score, MAX_SENTIMENT, reason,
                "interaction_insights：negative=" + c.negativeSentimentCount() + "、churn/complaint=" + c.riskIntentCount());
    }

    /** Task 狀態：逾期任務扣分；完全無待辦視為缺乏後續規劃。 */
    private HealthComponent scoreTaskStatus(OpportunityContext c) {
        int score;
        String reason;
        if (c.overdueTaskCount() > 0) {
            score = Math.max(0, MAX_TASK - c.overdueTaskCount() * 8);
            reason = "有 " + c.overdueTaskCount() + " 筆逾期未完成任務，後續執行落後。";
        } else if (c.openTaskCount() == 0) {
            score = 8;
            reason = "目前無任何待辦任務，缺乏明確的下一步規劃。";
        } else {
            score = MAX_TASK;
            reason = "有 " + c.openTaskCount() + " 筆待辦任務且無逾期，後續規劃明確。";
        }
        return new HealthComponent("TASK_STATUS", "任務狀態", score, MAX_TASK, reason,
                "crm_tasks：open=" + c.openTaskCount() + "、overdue=" + c.overdueTaskCount());
    }

    /**
     * 決策鏈完整度（優雅降級）。
     *
     * <p>V27 的 stakeholder_roles/stakeholder_relations 尚未建置，本分項<b>不得</b>依賴其資料，
     * 改以「客戶關聯聯絡人數」作為決策鏈涵蓋度的 proxy，並在 reason 一律明述「決策鏈資料有限」，
     * 待 V27 落地後可再強化。</p>
     */
    private HealthComponent scoreDecisionChain(OpportunityContext c) {
        int count = c.contactCount();
        int score;
        String coverage;
        if (count >= 3) {
            score = MAX_CHAIN;
            coverage = "已掌握 " + count + " 位聯絡人，決策鏈涵蓋度佳";
        } else if (count == 2) {
            score = 7;
            coverage = "已掌握 2 位聯絡人，決策鏈涵蓋度尚可";
        } else if (count == 1) {
            score = 4;
            coverage = "僅掌握 1 位聯絡人，決策鏈涵蓋度不足";
        } else {
            score = 0;
            coverage = "尚未建立任何聯絡人";
        }
        // reason 必含「決策鏈資料有限」以標示 V27 尚未建置的降級處理。
        String reason = coverage + "（決策鏈資料有限：V27 決策角色/關係尚未建置，暫以聯絡人數估算）。";
        return new HealthComponent("DECISION_CHAIN", "決策鏈完整度", score, MAX_CHAIN, reason,
                "contacts：" + count + " 位（V27 stakeholder_roles 未建置）");
    }
}
