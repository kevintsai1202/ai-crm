package com.aicrm.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Opportunity 新欄位行為單元測試（SP8）。 */
class OpportunityTest {

    /** 建立測試用商機（含 leadSource 與 probability 的完整建構子）。 */
    private Opportunity newOpp(OpportunityStage stage) {
        return new Opportunity(null, "案子", stage, new BigDecimal("1000"),
                LocalDate.of(2026, 1, 1), OpportunityType.NEW_BUSINESS,
                LeadSource.OUTBOUND, 50);
    }

    /** 測試指派負責業務後，owner 與 ownerName 均同步。 */
    @Test
    void assignOwner_syncsOwnerName() {
        var user = new AppUser("sales@a.local", "hash", "王小明", Role.SALES);
        var opp = newOpp(OpportunityStage.PROPOSAL);
        opp.assignOwner(user);
        assertThat(opp.getOwner()).isSameAs(user);
        assertThat(opp.getOwnerName()).isEqualTo("王小明");
    }

    /** 測試 closeWith 正確設定階段、原因、備註與實際成交日。 */
    @Test
    void closeWith_setsReasonAndDate() {
        var opp = newOpp(OpportunityStage.NEGOTIATION);
        opp.closeWith(OpportunityStage.CLOSED_WON, CloseReason.WON_PRICE, "價格有競爭力",
                LocalDate.of(2026, 3, 1));
        assertThat(opp.getStage()).isEqualTo(OpportunityStage.CLOSED_WON);
        assertThat(opp.getCloseReason()).isEqualTo(CloseReason.WON_PRICE);
        assertThat(opp.getCloseReasonNote()).isEqualTo("價格有競爭力");
        assertThat(opp.getActualCloseDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    /** 測試建構子正確保存 probability 值。 */
    @Test
    void constructor_keepsProbability() {
        assertThat(newOpp(OpportunityStage.PROPOSAL).getProbability()).isEqualTo(50);
    }

    /** 測試結案後切回非結案階段（重開）會清除 stale 結案欄位。 */
    @Test
    void updateStage_reopenClearsCloseFields() {
        var opp = newOpp(OpportunityStage.NEGOTIATION);
        opp.closeWith(OpportunityStage.CLOSED_WON, CloseReason.WON_PRICE, "備註", LocalDate.of(2026, 3, 1));
        opp.updateStage(OpportunityStage.NEGOTIATION);
        assertThat(opp.getStage()).isEqualTo(OpportunityStage.NEGOTIATION);
        assertThat(opp.getCloseReason()).isNull();
        assertThat(opp.getCloseReasonNote()).isNull();
        assertThat(opp.getActualCloseDate()).isNull();
    }
}
