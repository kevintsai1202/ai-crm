package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 績效混合口徑單元測試（SP8）：商機指標按商機 owner、客戶指標按客戶 owner。 */
class ManagerAnalyticsMixedOwnerTest {

    /** 客戶屬 Alice、商機改派 Bob：成交金額歸 Bob，客戶數歸 Alice。 */
    @Test
    void wonAmount_attributedToOpportunityOwner_customerCountToCustomerOwner() {
        var alice = new AppUser("alice@a.local", "h", "Alice", Role.SALES);
        var bob = new AppUser("bob@a.local", "h", "Bob", Role.SALES);
        var customer = new Customer("客戶", "a@b.c", "0912345678", "12345678", "軟體", "Alice");
        customer.assignOwner(alice);
        var won = new Opportunity(customer, "deal", OpportunityStage.CLOSED_WON, new BigDecimal("900"),
                LocalDate.of(2026, 4, 1), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 100);
        won.assignOwner(bob);
        customer.getOpportunities().add(won);

        var repo = Mockito.mock(CustomerRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(customer));
        var insights = Mockito.mock(InteractionInsightRepository.class);
        Mockito.when(insights.findAll()).thenReturn(List.of());
        var service = new ManagerAnalyticsService(repo, insights);

        var owners = service.analytics().owners();
        var bobStats = owners.stream().filter(o -> o.ownerName().equals("Bob")).findFirst().orElseThrow();
        var aliceStats = owners.stream().filter(o -> o.ownerName().equals("Alice")).findFirst().orElseThrow();
        assertThat(bobStats.wonAmount()).isEqualByComparingTo("900");  // 商機歸商機 owner Bob
        assertThat(bobStats.customerCount()).isEqualTo(0);             // Bob 無負責客戶
        assertThat(aliceStats.customerCount()).isEqualTo(1);          // 客戶歸客戶 owner Alice
        assertThat(aliceStats.wonAmount()).isEqualByComparingTo("0"); // Alice 無經手商機
    }
}
