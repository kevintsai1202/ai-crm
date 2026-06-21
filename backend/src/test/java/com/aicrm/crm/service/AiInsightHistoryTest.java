package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AI 歷程查詢測試：團隊診斷依類型、業務 coaching 依 subject(ownerName) 隔離。
 */
class AiInsightHistoryTest extends PostgresTestBase {

    @Autowired ManagerInsightService insightService;
    @Autowired AiGovernanceService governance;
    @Autowired CustomerRepository customers;

    @Test
    void teamHistory_byType_andOwnerHistory_bySubject() {
        var owners = customers.findDistinctOwners();
        assertThat(owners.size()).isGreaterThanOrEqualTo(2);
        var ownerA = owners.get(0);
        var ownerB = owners.get(1);

        insightService.generateTeamInsight();
        insightService.generateOwnerInsight(ownerA);
        insightService.generateOwnerInsight(ownerB);

        var teamCalls = governance.historyByType(AiCallType.TEAM_ANALYSIS);
        assertThat(teamCalls).isNotEmpty();
        assertThat(teamCalls).allMatch(c -> c.callType().equals("TEAM_ANALYSIS"));

        List<?> aCalls = governance.historyByOwner(ownerA);
        List<?> bCalls = governance.historyByOwner(ownerB);
        assertThat(aCalls).hasSize(1);
        assertThat(bCalls).hasSize(1);
    }
}
