package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 示範資料「清除重建 + 漏斗遞減分布」整合測試。
 *
 * <p>以 {@code app.demo.reset-enabled=true} 啟用清除重建，驗證兩件事：</p>
 * <ul>
 *   <li>reset 模式會先清掉既有業務資料再重建（客戶數等於本次生成數，不累積）。</li>
 *   <li>商機階段呈「越前面越多」的自然漏斗（資格評估顯著多於已成交、前段總量大於後段）。</li>
 * </ul>
 */
@TestPropertySource(properties = "app.demo.reset-enabled=true")
@Transactional // 破壞性清除操作於測試結束 rollback，避免污染共用 Testcontainers DB 的其他測試
class DemoDataResetAndFunnelTest extends PostgresTestBase {

    @Autowired DemoDataService demoDataService;
    @Autowired CustomerRepository customerRepository;
    @Autowired OpportunityRepository opportunityRepository;

    /**
     * reset 模式：連續生成兩次，第二次應先清除，使客戶數等於最後一次生成數（不累積）。
     */
    @Test
    void generateWithReset_shouldClearPreviousBusinessData() {
        demoDataService.generate(8, true);
        var stats = demoDataService.generate(6, true);

        assertThat(stats.customers()).isEqualTo(6);
        // 第二次先清除，客戶總數應只剩本次的 6（而非 8+6）
        assertThat(customerRepository.count()).isEqualTo(6);
    }

    /**
     * 漏斗遞減：大量生成後，商機階段分布應呈現「越前面越多」的自然漏斗形狀。
     */
    @Test
    void generateWithReset_shouldProduceDecreasingFunnel() {
        demoDataService.generate(200, true);

        Map<OpportunityStage, Long> countByStage = new EnumMap<>(OpportunityStage.class);
        for (var stage : OpportunityStage.values()) {
            countByStage.put(stage, 0L);
        }
        for (var opp : opportunityRepository.findAll()) {
            countByStage.merge(opp.getStage(), 1L, Long::sum);
        }

        long qualification = countByStage.get(OpportunityStage.QUALIFICATION);
        long proposal = countByStage.get(OpportunityStage.PROPOSAL);
        long negotiation = countByStage.get(OpportunityStage.NEGOTIATION);
        long closedWon = countByStage.get(OpportunityStage.CLOSED_WON);

        // 至少要有資料可判斷
        assertThat(qualification).isGreaterThan(0);
        // 自然漏斗：最前段（資格評估）顯著多於成交段
        assertThat(qualification).isGreaterThan(closedWon);
        // 前段（資格評估 + 提案）總量大於後段（議價 + 成交）
        assertThat(qualification + proposal).isGreaterThan(negotiation + closedWon);
        // 資格評估為最大宗
        assertThat(qualification).isGreaterThanOrEqualTo(proposal);
    }
}
