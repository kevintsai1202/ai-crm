package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 風險等級重算服務整合測試：重算後欄位被正確寫入 DB。 */
class RiskLevelMaintenanceServiceTest extends PostgresTestBase {

    @Autowired RiskLevelMaintenanceService maintenance;
    @Autowired CustomerRepository customers;

    /** 久未互動的客戶重算後應為 HIGH 並寫回欄位。 */
    @Test
    void recompute_writesHighForStaleCustomer() {
        var customer = new Customer("重算測試客戶", "recompute@example.com", "0911222333", "88000001", "製造業", "王小明");
        customer.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(90), "很久沒聯絡了"));
        var id = customers.saveAndFlush(customer).getId();

        maintenance.recompute(id);

        var reloaded = customers.findById(id).orElseThrow();
        assertThat(reloaded.getRiskLevel()).isEqualTo("HIGH");
        assertThat(reloaded.getRiskComputedAt()).isNotNull();
    }
}
