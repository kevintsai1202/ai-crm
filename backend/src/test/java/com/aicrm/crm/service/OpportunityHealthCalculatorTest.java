package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.service.OpportunityHealthCalculator.HealthComponent;
import com.aicrm.crm.service.OpportunityHealthCalculator.HealthScore;
import com.aicrm.crm.service.OpportunityHealthCalculator.OpportunityContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * 商機健康度純函式評分測試：以固定 clock（evaluatedAt）覆蓋各分項邊界，
 * 斷言 sum(components)==total、total 介於 0–100，且每個分項都有中文 reason 與 evidence。
 */
class OpportunityHealthCalculatorTest {

    /** 受測純函式計算器。 */
    private final OpportunityHealthCalculator calculator = new OpportunityHealthCalculator();

    /** 固定評估時間（UTC），Asia/Taipei 對應日期為 2026-07-15。 */
    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    /** 固定評估基準日（Asia/Taipei）。 */
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneId.of("Asia/Taipei"));

    /**
     * 建立一個「健康商機」的預設 context，各分項皆為最佳值，可在單一測試中覆寫。
     *
     * @return 預設健康 context
     */
    private OpportunityContext healthyContext() {
        return new OpportunityContext(
                false,                                   // 未結案
                5L,                                      // 階段停留 5 天
                "PROPOSAL",                              // 目前階段
                TODAY.plusDays(45),                      // 預計成交日充裕
                TODAY.atStartOfDay().minusDays(2),       // 最近互動 2 天前
                6,                                       // 近30天互動 6 次
                0,                                       // 無負面情緒
                0,                                       // 無流失/客訴訊號
                2,                                       // 待辦任務 2 筆
                0,                                       // 無逾期任務
                3,                                       // 3 位聯絡人
                NOW);
    }

    /** 健康商機：總分接近滿分且 sum==total==100。 */
    @Test
    void healthyOpportunity_scoresHighAndSumEqualsTotal() {
        HealthScore score = calculator.calculate(healthyContext());

        assertThat(score.total()).isEqualTo(100);
        assertSumEqualsTotalAndExplained(score);
    }

    /** 停滯 + 逾期 + 冷卻 + 負面 + 逾期任務 + 無決策鏈：各項觸底，總分 0。 */
    @Test
    void stagnantOverdueOpportunity_scoresZero() {
        OpportunityContext context = new OpportunityContext(
                false, 90L, "NEGOTIATION",
                TODAY.minusDays(10),                     // 預計成交日逾期
                TODAY.atStartOfDay().minusDays(80),      // 80 天無互動
                0, 3, 2, 0, 2, 0, NOW);

        HealthScore score = calculator.calculate(context);

        assertThat(score.total()).isEqualTo(0);
        assertSumEqualsTotalAndExplained(score);
    }

    /** 任意混合輸入：sum 恆等於 total 且落在 0–100。 */
    @Test
    void mixedInputs_sumAlwaysEqualsTotalWithinBounds() {
        OpportunityContext context = new OpportunityContext(
                false, 40L, "QUALIFICATION",
                TODAY.plusDays(4),                       // 接近成交日
                TODAY.atStartOfDay().minusDays(20),      // 20 天前互動
                1, 1, 0, 1, 0, 1, NOW);

        HealthScore score = calculator.calculate(context);

        assertThat(score.total()).isBetween(0, 100);
        assertSumEqualsTotalAndExplained(score);
    }

    /** 無階段歷史（daysInCurrentStage=null）：階段分項以中性計分並說明原因。 */
    @Test
    void nullStageHistory_usesNeutralStageScore() {
        OpportunityContext context = new OpportunityContext(
                false, null, "PROPOSAL", TODAY.plusDays(45),
                TODAY.atStartOfDay().minusDays(2), 6, 0, 0, 2, 0, 3, NOW);

        HealthComponent stage = component(calculator.calculate(context), "STAGE_DWELL");
        assertThat(stage.score()).isBetween(1, stage.maxScore() - 1);
        assertThat(stage.reason()).contains("無階段停留歷史");
    }

    /** 負面情緒與流失訊號會降低情緒/意圖分項。 */
    @Test
    void negativeSignals_reduceSentimentComponent() {
        OpportunityContext base = healthyContext();
        OpportunityContext withNegative = new OpportunityContext(
                base.closed(), base.daysInCurrentStage(), base.currentStageName(), base.expectedCloseDate(),
                base.lastInteractionAt(), base.interactionsLast30Days(), 2, 0,
                base.openTaskCount(), base.overdueTaskCount(), base.contactCount(), base.evaluatedAt());

        HealthComponent sentiment = component(calculator.calculate(withNegative), "SENTIMENT_INTENT");
        assertThat(sentiment.score()).isLessThan(sentiment.maxScore());
        assertThat(sentiment.reason()).contains("負面");
        assertThat(sentiment.evidence()).isNotBlank();
    }

    /** 逾期任務會降低任務狀態分項。 */
    @Test
    void overdueTasks_reduceTaskComponent() {
        OpportunityContext base = healthyContext();
        OpportunityContext withOverdue = new OpportunityContext(
                base.closed(), base.daysInCurrentStage(), base.currentStageName(), base.expectedCloseDate(),
                base.lastInteractionAt(), base.interactionsLast30Days(), 0, 0, 1, 2, base.contactCount(), base.evaluatedAt());

        HealthComponent task = component(calculator.calculate(withOverdue), "TASK_STATUS");
        assertThat(task.score()).isLessThan(task.maxScore());
        assertThat(task.reason()).contains("逾期");
    }

    /** 決策鏈分項必須明述「決策鏈資料有限」以優雅降級（V27 尚未建置）。 */
    @Test
    void decisionChainComponent_alwaysStatesLimitedData() {
        HealthComponent chain = component(calculator.calculate(healthyContext()), "DECISION_CHAIN");
        assertThat(chain.reason()).contains("決策鏈資料有限");

        OpportunityContext noContacts = new OpportunityContext(
                false, 5L, "PROPOSAL", TODAY.plusDays(45), TODAY.atStartOfDay().minusDays(2),
                6, 0, 0, 2, 0, 0, NOW);
        HealthComponent emptyChain = component(calculator.calculate(noContacts), "DECISION_CHAIN");
        assertThat(emptyChain.score()).isZero();
        assertThat(emptyChain.reason()).contains("決策鏈資料有限");
    }

    /** 逾期預計成交日會使成交日分項歸零並說明逾期天數。 */
    @Test
    void overdueExpectedCloseDate_zeroesCloseComponent() {
        OpportunityContext base = healthyContext();
        OpportunityContext overdue = new OpportunityContext(
                base.closed(), base.daysInCurrentStage(), base.currentStageName(), TODAY.minusDays(10),
                base.lastInteractionAt(), base.interactionsLast30Days(), 0, 0,
                base.openTaskCount(), base.overdueTaskCount(), base.contactCount(), base.evaluatedAt());

        HealthComponent close = component(calculator.calculate(overdue), "EXPECTED_CLOSE");
        assertThat(close.score()).isZero();
        assertThat(close.reason()).contains("逾期");
    }

    /** 共用斷言：sum(components)==total、total 0–100，且每分項皆有中文 reason 與 evidence。 */
    private void assertSumEqualsTotalAndExplained(HealthScore score) {
        int sum = score.components().stream().mapToInt(HealthComponent::score).sum();
        assertThat(sum).isEqualTo(score.total());
        assertThat(score.total()).isBetween(0, 100);
        assertThat(score.components()).isNotEmpty();
        for (HealthComponent c : score.components()) {
            assertThat(c.score()).isBetween(0, c.maxScore());
            assertThat(c.reason()).as("分項 %s 需有中文 reason", c.key()).isNotBlank();
            assertThat(c.evidence()).as("分項 %s 需有 evidence reference", c.key()).isNotBlank();
        }
    }

    /** 取出指定 key 的分項。 */
    private HealthComponent component(HealthScore score, String key) {
        return score.components().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }
}
