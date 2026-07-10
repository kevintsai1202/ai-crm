package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 漏斗依來源過濾單元測試（SP8）：傳 leadSource 只計入該來源的商機。 */
class DashboardFunnelFilterTest {

    @Test
    void pipelineByStage_filtersByLeadSource() {
        var customer = new Customer("測試客戶", "a@b.c", "0912345678", "12345678", "軟體", "業務A");
        var inbound = new Opportunity(customer, "進線", OpportunityStage.PROPOSAL, new BigDecimal("100"),
                LocalDate.of(2026, 5, 1), OpportunityType.NEW_BUSINESS, LeadSource.INBOUND, 50);
        var outbound = new Opportunity(customer, "開發", OpportunityStage.PROPOSAL, new BigDecimal("200"),
                LocalDate.of(2026, 5, 1), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 50);
        customer.getOpportunities().add(inbound);
        customer.getOpportunities().add(outbound);

        var repo = Mockito.mock(CustomerRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(customer));
        var stageHistory = org.mockito.Mockito.mock(OpportunityStageHistoryService.class);
        org.mockito.Mockito.when(stageHistory.computeDwellStats(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of());
        var service = new DashboardService(repo, stageHistory);

        // 只取 INBOUND：PROPOSAL 階段應僅有 1 筆、金額 100
        var proposal = service.dashboardReports("INBOUND").pipelineByStage().stream()
                .filter(s -> s.stage().equals("PROPOSAL")).findFirst().orElseThrow();
        assertThat(proposal.count()).isEqualTo(1);
        assertThat(proposal.amount()).isEqualByComparingTo("100");

        // 不過濾（null）：PROPOSAL 應有 2 筆、金額 300
        var allProposal = service.dashboardReports().pipelineByStage().stream()
                .filter(s -> s.stage().equals("PROPOSAL")).findFirst().orElseThrow();
        assertThat(allProposal.count()).isEqualTo(2);
        assertThat(allProposal.amount()).isEqualByComparingTo("300");
    }
}
