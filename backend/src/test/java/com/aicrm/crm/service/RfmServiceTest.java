package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RfmService 純邏輯測試（Mockito，不啟 Spring）。
 * 以反射建構 Opportunity（無 public 建構子）並自組 Customer fixture，
 * 驗證 R/F/M 分數與分群標籤，含邊界（無互動、無商機）。
 */
class RfmServiceTest {

    /** 以 mock 的 CustomerService 建立受測服務。 */
    private RfmService newService(List<Customer> customers) {
        var customerService = mock(CustomerService.class);
        when(customerService.findAllWithDetail()).thenReturn(customers);
        return new RfmService(customerService);
    }

    /** 建立空客戶（指定名稱）。 */
    private Customer customer(String name) {
        return new Customer(name, "a@b.c", "0912345678", "12345678", "雲端服務", "業務A");
    }

    /** 以反射建構商機並設定階段與金額（Opportunity 無 public 建構子）。 */
    private Opportunity opportunity(OpportunityStage stage, String amount) {
        try {
            var ctor = Opportunity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            var opp = ctor.newInstance();
            ReflectionTestUtils.setField(opp, "stage", stage);
            ReflectionTestUtils.setField(opp, "amount", new BigDecimal(amount));
            return opp;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("建構 Opportunity fixture 失敗", e);
        }
    }

    /** 將商機塞入客戶的 opportunities 清單。 */
    @SuppressWarnings("unchecked")
    private void addOpportunity(Customer c, Opportunity o) {
        ((List<Opportunity>) ReflectionTestUtils.getField(c, "opportunities")).add(o);
    }

    @Test
    void champion_highRfm_getsTopScoresAndChampionSegment() {
        var c = customer("冠軍");
        // 近期互動（高 R）
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(2), "近期會議"));
        // 高頻互動（共 10 次以上 → fScore 5）
        for (int i = 0; i < 11; i++) {
            c.addInteraction(new Interaction(InteractionType.PHONE, LocalDateTime.now().minusDays(3), "頻繁聯繫"));
        }
        // 高額有效商機（≥100 萬 → mScore 5）
        addOpportunity(c, opportunity(OpportunityStage.NEGOTIATION, "1200000"));

        var result = newService(List.of(c)).computeRfm();

        assertThat(result).hasSize(1);
        var rfm = result.get(0);
        assertThat(rfm.rScore()).isEqualTo(5);
        assertThat(rfm.fScore()).isEqualTo(5);
        assertThat(rfm.mScore()).isEqualTo(5);
        assertThat(rfm.frequency()).isEqualTo(12);
        assertThat(rfm.monetary()).isEqualByComparingTo("1200000");
        assertThat(rfm.segment()).isEqualTo("冠軍客戶");
    }

    @Test
    void noInteraction_givesLargeRecencyAndLowestScoresAndNeedAttention() {
        var c = customer("無互動");
        // 無互動、無商機 → 邊界
        var result = newService(List.of(c)).computeRfm();

        var rfm = result.get(0);
        assertThat(rfm.recencyDays()).isEqualTo(9999); // 無互動給極大值
        assertThat(rfm.frequency()).isEqualTo(0);
        assertThat(rfm.monetary()).isEqualByComparingTo("0"); // 無商機 → 0
        assertThat(rfm.rScore()).isEqualTo(1);
        assertThat(rfm.fScore()).isEqualTo(1);
        assertThat(rfm.mScore()).isEqualTo(1);
        assertThat(rfm.segment()).isEqualTo("需關注");
    }

    @Test
    void closedLostExcludedFromMonetary() {
        var c = customer("含流失商機");
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(1), "近期"));
        // CLOSED_LOST 不計入 monetary；只有有效商機 30 萬計入
        addOpportunity(c, opportunity(OpportunityStage.CLOSED_LOST, "9999999"));
        addOpportunity(c, opportunity(OpportunityStage.PROPOSAL, "300000"));

        var rfm = newService(List.of(c)).computeRfm().get(0);

        assertThat(rfm.monetary()).isEqualByComparingTo("300000");
        assertThat(rfm.mScore()).isEqualTo(3); // 30 萬 → 落在 ≥10 萬 區間
    }

    @Test
    void recentButLowValue_isPotential() {
        var c = customer("具潛力");
        // 近期互動（高 R）但金額低、頻率低
        c.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(3), "剛接觸"));
        addOpportunity(c, opportunity(OpportunityStage.QUALIFICATION, "5000"));

        var rfm = newService(List.of(c)).computeRfm().get(0);

        assertThat(rfm.rScore()).isEqualTo(5);
        assertThat(rfm.mScore()).isLessThan(4);
        assertThat(rfm.segment()).isEqualTo("具潛力");
    }

    @Test
    void staleButHighValue_isAtRisk() {
        var c = customer("瀕危");
        // 久未互動（120 天 → rScore 1）但高額商機（mScore 5）→ 瀕危流失
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(120), "久未聯繫"));
        addOpportunity(c, opportunity(OpportunityStage.NEGOTIATION, "1500000"));

        var rfm = newService(List.of(c)).computeRfm().get(0);

        assertThat(rfm.rScore()).isEqualTo(1);
        assertThat(rfm.mScore()).isEqualTo(5);
        assertThat(rfm.segment()).isEqualTo("瀕危流失");
    }
}
