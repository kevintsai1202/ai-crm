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

/** Forecast 加權邏輯單元測試（SP8）：總額含失單、加權排除失單並依成交機率折算。 */
class DashboardForecastTest {

    /** 同月一筆進行中（機率 75）+ 一筆已失單：總額含兩者，加權僅算進行中 × 機率。 */
    @Test
    void monthlyForecast_totalIncludesLost_weightedExcludesLostAndScalesByProbability() {
        var customer = new Customer("測試客戶", "a@b.c", "0912345678", "12345678", "軟體", "業務A");
        var inProgress = new Opportunity(customer, "進行中", OpportunityStage.NEGOTIATION, new BigDecimal("1000"),
                LocalDate.of(2026, 5, 10), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 75);
        var lost = new Opportunity(customer, "失單", OpportunityStage.CLOSED_LOST, new BigDecimal("400"),
                LocalDate.of(2026, 5, 20), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 0);
        customer.getOpportunities().add(inProgress);
        customer.getOpportunities().add(lost);

        var repo = Mockito.mock(CustomerRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(customer));
        var stageHistory = org.mockito.Mockito.mock(OpportunityStageHistoryService.class);
        org.mockito.Mockito.when(stageHistory.computeDwellStats(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of());
        var service = new DashboardService(repo, stageHistory);

        var point = service.dashboardReports().monthlyForecast().stream()
                .filter(p -> p.label().equals("2026-05")).findFirst().orElseThrow();
        assertThat(point.totalAmount()).isEqualByComparingTo("1400");   // 含失單
        assertThat(point.weightedAmount()).isEqualByComparingTo("750"); // 1000 × 0.75，排除失單
        assertThat(point.count()).isEqualTo(2);
    }
}
