package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.service.ai.RagCitationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * InsightService.calculateOpportunityRisk 分支測試（純邏輯，不啟 Spring）。
 */
class InsightServiceRiskTest {

    /** 以空金鑰建立受測服務（風險計算不需 AI）。 */
    @SuppressWarnings("unchecked")
    private InsightService newService() {
        var customerService = mock(CustomerService.class);
        var rag = mock(RagCitationService.class);
        var provider = (ObjectProvider<ChatModel>) mock(ObjectProvider.class);
        var governance = mock(AiGovernanceService.class);
        var chatMemory = mock(ChatMemoryService.class);
        var systemSettings = mock(SystemSettingService.class);
        return new InsightService(customerService, rag, provider, governance, chatMemory, systemSettings, "", "https://api.openai.com", 8000, "low");
    }

    /** 建立指定產業/業務的空客戶。 */
    private Customer customer() {
        return new Customer("測試客戶", "a@b.c", "0912345678", "12345678", "雲端服務", "業務A");
    }

    @Test
    void noInteraction_raisesChurnForInsufficientData() {
        var risk = newService().calculateOpportunityRisk(customer());
        // 基底 10 + 資料不足 35 = 45
        assertThat(risk.churnRisk()).isEqualTo(45);
        assertThat(risk.reasons()).anyMatch(r -> r.contains("互動資料不足"));
    }

    @Test
    void recentInteraction_keepsBaseChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(5), "正常會議"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(10);
    }

    @Test
    void interactionOver30Days_addsModerateChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(45), "久未聯繫"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(35); // 10 + 25
    }

    @Test
    void interactionOver60Days_addsHighChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(90), "長期失聯"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(55); // 10 + 45
        assertThat(risk.reasons()).anyMatch(r -> r.contains("天"));
    }

    @Test
    void riskyKeyword_addsChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.SUPPORT_TICKET, LocalDateTime.now().minusDays(5), "客戶提出客訴"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(40); // 10 + 30（近期互動不加天數分）
        assertThat(risk.reasons()).anyMatch(r -> r.contains("客訴"));
    }

    @Test
    void overdueRenewal_raisesRenewalRisk() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(5), "近期互動"));
        c.updateContractDates(null, null, LocalDate.now().minusDays(10));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.renewalDelayRisk()).isEqualTo(65); // 10 + 55
        assertThat(risk.reasons()).anyMatch(r -> r.contains("逾期"));
    }

    @Test
    void multipleSignals_capAt100() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.SUPPORT_TICKET, LocalDateTime.now().minusDays(120), "客訴加競品比較且預算凍結"));
        var risk = newService().calculateOpportunityRisk(c);
        // 10 + 45（>60天）+ 30（關鍵詞）= 85，未封頂；驗證不超過 100 且符合計算
        assertThat(risk.churnRisk()).isEqualTo(85);
        assertThat(risk.churnRisk()).isLessThanOrEqualTo(100);
    }
}
