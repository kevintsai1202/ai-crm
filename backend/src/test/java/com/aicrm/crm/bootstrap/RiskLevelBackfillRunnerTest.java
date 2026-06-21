package com.aicrm.crm.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 風險等級啟動補算 runner 測試：補上 risk_level 為 null 的客戶。 */
class RiskLevelBackfillRunnerTest extends PostgresTestBase {

    @Autowired RiskLevelBackfillRunner runner;
    @Autowired CustomerRepository customers;

    /** 以 repository 直接灌入未回填客戶，runner 應補上 risk_level。 */
    @Test
    void run_backfillsNullRiskLevel() {
        var c = new Customer("補算風險客戶", "riskbackfill@example.com", "0922333444", "66000001", "補算產業", "王小明");
        c.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(100), "久未聯絡"));
        var id = customers.saveAndFlush(c).getId();
        // 前置：新建客戶 risk_level 為 null（未經 service 寫入路徑）
        assertThat(customers.findById(id).orElseThrow().getRiskLevel()).isNull();

        runner.run(null);

        assertThat(customers.findById(id).orElseThrow().getRiskLevel()).isEqualTo("HIGH");
    }
}
