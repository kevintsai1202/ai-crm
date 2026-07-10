package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import com.aicrm.crm.repository.OpportunityStageHistoryRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 階段歷史寫入與停留統計整合測試。
 */
class OpportunityStageHistoryServiceTest extends PostgresTestBase {

    @Autowired OpportunityStageHistoryService stageHistory;
    @Autowired OpportunityStageHistoryRepository historyRepository;
    @Autowired OpportunityRepository opportunityRepository;
    @Autowired CustomerRepository customerRepository;

    @Test
    void recordAndDwellStats() {
        var customer = customerRepository.findAll().stream().findFirst().orElseThrow();
        var opp = new Opportunity(customer, "測試商機階段", OpportunityStage.QUALIFICATION,
                BigDecimal.valueOf(10000), LocalDate.now().plusDays(30), OpportunityType.NEW_BUSINESS,
                LeadSource.OUTBOUND, 20);
        opportunityRepository.saveAndFlush(opp);

        stageHistory.record(opp, null, OpportunityStage.QUALIFICATION);
        stageHistory.record(opp, OpportunityStage.QUALIFICATION, OpportunityStage.PROPOSAL);
        opp.updateStage(OpportunityStage.PROPOSAL);
        opportunityRepository.saveAndFlush(opp);

        assertThat(historyRepository.findByOpportunityIdIn(java.util.List.of(opp.getId())))
                .hasSizeGreaterThanOrEqualTo(2);

        var stats = stageHistory.computeDwellStats(java.util.List.of(opp));
        assertThat(stats.get(OpportunityStage.PROPOSAL).avgDaysInStage()).isGreaterThanOrEqualTo(0.0);
    }
}
