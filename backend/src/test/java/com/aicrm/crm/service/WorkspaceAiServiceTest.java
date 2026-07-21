package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * WorkspaceAiService 待辦計算與 scope 隔離測試。
 * 以 @Transactional rollback 避免污染共用 Testcontainers DB 的其他測試。
 */
@Transactional
class WorkspaceAiServiceTest extends com.aicrm.crm.support.PostgresTestBase {

    @Autowired WorkspaceAiService workspaceAiService;
    @Autowired CustomerRepository customerRepository;

    /** 測試業務「艾美」。 */
    private final AuthPrincipal sales = new AuthPrincipal("amy@aurora.local", "艾美", Role.SALES);

    @BeforeEach
    void setup() {
        // 艾美的高風險客戶（且續約日在 5 天後 → 同時命中 HIGH_RISK 與 RENEWAL_DUE）
        var high = new Customer("艾美高風險客", "h@x.com", "0900000001", "11110001", "金融", "艾美");
        high.applyRiskLevel("HIGH");
        high.updateContractDates(LocalDate.now().minusYears(1), LocalDate.now().plusMonths(6), LocalDate.now().plusDays(5));

        // 艾美的逾期商機客戶（預計成交日已過 → 命中 STALE_OPPORTUNITY）
        var stale = new Customer("艾美逾期客", "s@x.com", "0900000002", "11110002", "製造", "艾美");
        var opp = new Opportunity(stale, "逾期案", OpportunityStage.PROPOSAL, BigDecimal.valueOf(500000),
                LocalDate.now().minusDays(10), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 50);
        stale.getOpportunities().add(opp);

        // 別的業務的高風險客戶（不應出現在艾美的待辦）
        var other = new Customer("他人高風險客", "o@x.com", "0900000003", "11110003", "零售", "別的業務");
        other.applyRiskLevel("HIGH");

        customerRepository.saveAll(List.of(high, stale, other));
        customerRepository.flush();
    }

    @Test
    void computeTodos_salesScope_onlyOwnCustomers() {
        // SALES 即使帶 scope=all 仍被強制只看自己
        var todos = workspaceAiService.computeTodos(sales, "all", "zh-TW");

        assertThat(todos).extracting(Dtos.WorkspaceTodoItem::customerName)
                .doesNotContain("他人高風險客");
        assertThat(todos).anyMatch(t -> "HIGH_RISK".equals(t.type()) && "艾美高風險客".equals(t.customerName()));
        assertThat(todos).anyMatch(t -> "RENEWAL_DUE".equals(t.type()) && "艾美高風險客".equals(t.customerName()));
        assertThat(todos).anyMatch(t -> "STALE_OPPORTUNITY".equals(t.type()) && "艾美逾期客".equals(t.customerName()));
    }

    @Test
    void recommendationFallback_containsTodoCustomers() {
        var todos = workspaceAiService.computeTodos(sales, "self", "zh-TW");
        String fallback = workspaceAiService.deterministicRecommendation(sales, todos, "zh-TW");
        // 接地：fallback 文字應列出待辦客戶，且提到「待辦」
        assertThat(fallback).contains("艾美高風險客");
        assertThat(fallback).contains("待辦");
    }

    @Test
    void computeTodos_english_producesEnglishReason() {
        // lang=en 時待辦描述應為英文（不含中文語系關鍵字），客戶名仍為 DB 原值
        var todos = workspaceAiService.computeTodos(sales, "self", "en");
        assertThat(todos).isNotEmpty();
        assertThat(todos).allMatch(t -> !t.reason().contains("風險")
                && !t.reason().contains("續約") && !t.reason().contains("商機"));
    }

    @Test
    void generateAiDrafts_whenNoAi_returnsRuleFallback() {
        var customers = customerRepository.findByOwnerName("艾美");
        var fallback = workspaceAiService.computeDraftsFrom(customers);
        // 無金鑰（chatModel=null）時應原樣回退規則式草稿
        var drafts = workspaceAiService.generateAiDrafts(customers, null, fallback, null);
        assertThat(drafts).isEqualTo(fallback);
    }

    @Test
    void chatDrilldown_foreignCustomer_isRejected() {
        var foreign = customerRepository.findByOwnerName("別的業務").get(0);
        // 艾美不可深入問「別的業務」的客戶
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> workspaceAiService.assertCustomerVisible(sales, "self", foreign.getId()));
    }

    @Test
    void chatDrilldown_ownCustomer_isAllowed() {
        var own = customerRepository.findByOwnerName("艾美").get(0);
        // 自己的客戶不應拋例外
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> workspaceAiService.assertCustomerVisible(sales, "self", own.getId()));
    }
}
