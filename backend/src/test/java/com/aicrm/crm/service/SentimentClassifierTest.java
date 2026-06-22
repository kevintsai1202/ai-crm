package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicrm.crm.domain.Intent;
import com.aicrm.crm.domain.Sentiment;
import com.aicrm.crm.repository.InteractionInsightRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * SentimentIntentService.classifyDeterministic 中文關鍵字分類器測試（純邏輯，不啟 Spring、不打 LLM）。
 */
class SentimentClassifierTest {

    /** 以空金鑰建立受測服務（deterministic 分類不需 AI）。 */
    @SuppressWarnings("unchecked")
    private SentimentIntentService newService() {
        var insights = mock(InteractionInsightRepository.class);
        var provider = (ObjectProvider<ChatModel>) mock(ObjectProvider.class);
        var objectMapper = new ObjectMapper();
        var systemSettings = mock(SystemSettingService.class);
        return new SentimentIntentService(insights, provider, objectMapper, systemSettings, "");
    }

    @Test
    void complaintKeyword_isNegativeComplaint() {
        var c = newService().classifyDeterministic("客戶針對服務品質提出客訴並表達不滿");
        assertThat(c.intent()).isEqualTo(Intent.COMPLAINT);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(c.score()).isBetween(-80, -40);
    }

    @Test
    void churnKeyword_isNegativeChurnSignal() {
        var c = newService().classifyDeterministic("客戶表示明年合約可能不續，考慮取消服務");
        assertThat(c.intent()).isEqualTo(Intent.CHURN_SIGNAL);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(c.score()).isBetween(-80, -40);
    }

    @Test
    void competitorKeyword_isCompareCompetitor() {
        var c = newService().classifyDeterministic("客戶拿我們和競品做比較，詢問差異");
        assertThat(c.intent()).isEqualTo(Intent.COMPARE_COMPETITOR);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(c.score()).isBetween(-80, 0);
    }

    @Test
    void pricingKeyword_isAskPricingNeutral() {
        var c = newService().classifyDeterministic("客戶想要一份正式報價單並詢問折扣空間");
        assertThat(c.intent()).isEqualTo(Intent.ASK_PRICING);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEUTRAL);
        assertThat(c.score()).isBetween(0, 20);
    }

    @Test
    void renewalKeyword_isRenewalInterestPositive() {
        var c = newService().classifyDeterministic("客戶確認願意續約，並希望延長合約年限");
        assertThat(c.intent()).isEqualTo(Intent.RENEWAL_INTEREST);
        assertThat(c.sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(c.score()).isBetween(40, 70);
    }

    @Test
    void upsellKeyword_isUpsellSignalPositive() {
        var c = newService().classifyDeterministic("客戶有意加購進階模組並升級方案");
        assertThat(c.intent()).isEqualTo(Intent.UPSELL_SIGNAL);
        assertThat(c.sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(c.score()).isBetween(40, 70);
    }

    @Test
    void noKeyword_isOtherNeutral() {
        var c = newService().classifyDeterministic("今天與客戶確認下次拜訪時間");
        assertThat(c.intent()).isEqualTo(Intent.OTHER);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEUTRAL);
        assertThat(c.score()).isEqualTo(0);
    }

    @Test
    void blankContent_isOtherNeutral() {
        var c = newService().classifyDeterministic("");
        assertThat(c.intent()).isEqualTo(Intent.OTHER);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEUTRAL);
    }

    @Test
    void nullContent_doesNotThrowAndIsOther() {
        var c = newService().classifyDeterministic(null);
        assertThat(c.intent()).isEqualTo(Intent.OTHER);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEUTRAL);
    }

    @Test
    void complaintTakesPriorityOverPricing() {
        // 同時含「客訴」與「報價」時，負面信號優先（規則順序）
        var c = newService().classifyDeterministic("客戶客訴上次報價過高");
        assertThat(c.intent()).isEqualTo(Intent.COMPLAINT);
        assertThat(c.sentiment()).isEqualTo(Sentiment.NEGATIVE);
    }

    @Test
    void scoreAlwaysWithinValidRange() {
        // 各分類 score 皆應落在 -100..100
        String[] samples = {"客訴", "不續", "競品", "報價", "續約", "加購", "一般拜訪"};
        var service = newService();
        for (var s : samples) {
            assertThat(service.classifyDeterministic(s).score()).isBetween(-100, 100);
        }
    }
}
