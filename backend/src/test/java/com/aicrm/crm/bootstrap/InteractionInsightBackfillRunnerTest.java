package com.aicrm.crm.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.domain.Intent;
import com.aicrm.crm.domain.Sentiment;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 啟動補算 runner 整合測試。
 *
 * <p>模擬 Flyway 種子情境：直接以 repository 寫入互動（繞過 {@code CustomerService.addInteraction}
 * 的即時分析路徑），驗證 {@link InteractionInsightBackfillRunner} 啟動時能把缺漏的 insight 補上，
 * 且分類結果符合 deterministic 規則；再次執行驗證冪等（不重複新增）。</p>
 */
class InteractionInsightBackfillRunnerTest extends PostgresTestBase {

    @Autowired InteractionInsightBackfillRunner runner;
    @Autowired CustomerRepository customerRepository;
    @Autowired InteractionInsightRepository insightRepository;

    /**
     * 直接以 repository 灌入「無 insight」的互動後，runner 應補算出對應分析；重跑為冪等。
     */
    @Test
    void run_backfillsMissingInsights_andIsIdempotent() {
        // 以 repository 直接建客戶＋互動（cascade 寫入），模擬種子繞過分析層
        var customer = new Customer("補算測試客戶", "backfill@example.com", "0912345678", "99000001", "雲端服務", "王小明");
        customer.addInteraction(new Interaction(InteractionType.PHONE, LocalDateTime.now().minusDays(1),
                "客戶來電客訴系統當機影響營運，要求退費。"));
        var saved = customerRepository.saveAndFlush(customer);
        var customerId = saved.getId();

        // 前置條件：該客戶尚無任何 insight
        assertThat(insightRepository.findByCustomerId(customerId)).isEmpty();

        // 執行補算
        runner.run(null);

        // 應補出一筆，且符合 deterministic 規則（客訴 → NEGATIVE / COMPLAINT）
        List<InteractionInsight> after = insightRepository.findByCustomerId(customerId);
        assertThat(after).hasSize(1);
        assertThat(after.getFirst().getSentiment()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(after.getFirst().getIntent()).isEqualTo(Intent.COMPLAINT);

        // 冪等：再次執行不應重複新增
        runner.run(null);
        assertThat(insightRepository.findByCustomerId(customerId)).hasSize(1);
    }
}
