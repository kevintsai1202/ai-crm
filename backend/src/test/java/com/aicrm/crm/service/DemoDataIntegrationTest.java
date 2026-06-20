package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Intent;
import com.aicrm.crm.repository.InteractionInsightRepository;
import com.aicrm.crm.repository.InteractionRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 示範資料生成器整合測試（Testcontainers pgvector + V8 套用）。
 *
 * <p>驗證 {@code DemoDataService.generate} 後：互動與 interaction_insights 筆數成長、
 * 每筆互動都有對應 insight（待分析互動數歸零）、且意圖分布含多類（≥3 種 intent 出現）。</p>
 */
class DemoDataIntegrationTest extends PostgresTestBase {

    @Autowired DemoDataService demoDataService;
    @Autowired InteractionRepository interactionRepository;
    @Autowired InteractionInsightRepository insightRepository;

    /**
     * 生成 5 個客戶後驗證資料量成長、insight 完整覆蓋、intent 分布多元。
     */
    @Test
    void generate_shouldGrowInteractionsAndInsightsWithDiverseIntents() {
        long interactionsBefore = interactionRepository.count();
        long insightsBefore = insightRepository.count();

        var stats = demoDataService.generate(5);

        // 統計結果與實際資料量
        assertThat(stats.customers()).isEqualTo(5);
        assertThat(stats.interactions()).isGreaterThanOrEqualTo(25); // 每客戶至少 5 則
        // 本次分析量至少涵蓋所有新互動（亦可能補算既有 seed 中尚未分析的互動）
        assertThat(stats.insights()).isGreaterThanOrEqualTo(stats.interactions());

        long interactionsAfter = interactionRepository.count();
        long insightsAfter = insightRepository.count();

        // 互動筆數成長量等於新增互動數；insight 筆數成長量等於本次分析數
        assertThat(interactionsAfter - interactionsBefore).isEqualTo(stats.interactions());
        assertThat(insightsAfter - insightsBefore).isEqualTo(stats.insights());

        // 每筆互動都有 insight：待分析互動歸零
        assertThat(insightRepository.findInteractionsWithoutInsight()).isEmpty();

        // intent 分布含多類（≥3 種 intent 出現）
        Set<String> distinctIntents = new HashSet<>();
        for (var row : insightRepository.countByIntent()) {
            distinctIntents.add(((Intent) row[0]).name());
        }
        assertThat(distinctIntents).hasSizeGreaterThanOrEqualTo(3);
    }
}
