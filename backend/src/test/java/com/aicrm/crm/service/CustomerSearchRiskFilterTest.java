package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 風險篩選改走 SQL 後的分頁正確性測試。 */
class CustomerSearchRiskFilterTest extends PostgresTestBase {

    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customers;
    @Autowired RiskLevelMaintenanceService maintenance;

    /** 建立一個久未互動（HIGH）的客戶，並回填其風險等級。 */
    @BeforeEach
    void seedHighRiskCustomer() {
        var c = new Customer("風險篩選客戶", "riskfilter@example.com", "0900111222", "77000001", "風險篩選產業", "王小明");
        c.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(120), "久未聯絡"));
        var id = customers.saveAndFlush(c).getId();
        maintenance.recompute(id);
    }

    /** search 帶 riskLevel=HIGH 時，回傳項目應全為 HIGH（null principal 模擬 ADMIN/MANAGER 不限角色）。 */
    @Test
    void search_byHighRisk_returnsOnlyHigh() {
        var result = customerService.search(null, 0, 20, null, "風險篩選產業", null, null, "HIGH", null, null);
        assertThat(result.items()).isNotEmpty();
        assertThat(result.items()).allMatch(s -> "HIGH".equals(s.riskLevel()));
        assertThat(result.totalElements()).isEqualTo(result.items().size());
    }
}
