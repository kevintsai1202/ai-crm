package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.repository.AiCallLogRepository;
import com.aicrm.crm.repository.ManagerInsightRepository;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ManagerInsightService 測試：無金鑰時走 deterministic fallback、寫 ai_call_log、快取 upsert。
 */
class ManagerInsightServiceTest extends PostgresTestBase {

    @Autowired ManagerInsightService service;
    @Autowired ManagerInsightRepository insightRepo;
    @Autowired AiCallLogRepository callLogRepo;

    @Test
    void teamInsight_noKey_fallback_andCached() {
        assertThat(service.getTeamInsight()).isNull();

        var before = callLogRepo.count();
        var first = service.generateTeamInsight();

        assertThat(first.content()).isNotBlank();
        assertThat(first.model()).isNull();
        assertThat(first.scope()).isEqualTo("TEAM");
        assertThat(callLogRepo.count()).isEqualTo(before + 1);
        assertThat(insightRepo.findFirstByScope("TEAM")).isPresent();
        assertThat(service.getTeamInsight()).isNotNull();

        var firstGeneratedAt = first.generatedAt();
        var second = service.generateTeamInsight();
        assertThat(insightRepo.findAll().stream().filter(i -> "TEAM".equals(i.getScope())).count()).isEqualTo(1L);
        assertThat(second.generatedAt()).isAfterOrEqualTo(firstGeneratedAt);
    }

    @Test
    void ownerInsight_noKey_fallback_andCached() {
        var ownerName = service.firstOwnerNameForTest();
        assertThat(ownerName).isNotBlank();

        var result = service.generateOwnerInsight(ownerName);
        assertThat(result.content()).isNotBlank();
        assertThat(result.scope()).isEqualTo("OWNER");
        assertThat(result.ownerName()).isEqualTo(ownerName);
        assertThat(service.getOwnerInsight(ownerName)).isNotNull();
    }
}
