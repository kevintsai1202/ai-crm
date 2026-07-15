package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * 商機智能 API 整合測試（V26）：驗證重算後 Opportunity stage/probability 不變、owner scope、
 * 歷史 snapshot 累積，以及回應含總分/可解釋分項/下一最佳行動。
 */
class OpportunityIntelligenceIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @Autowired AppUserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired OpportunityRepository opportunities;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private Long opportunityId;

    /** 建立真實 security MockMvc、屬於 sales@aurora.local 的客戶與商機（PROPOSAL、機率 50）。 */
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = new Customer("健康度測試客戶", "client@buyer.example", "0912345678", "12345678", "科技", "x");
        customer.assignOwner(owner);
        // 加入一位聯絡人與一則互動，讓評分有可引用的訊號。
        customer.getContacts().add(new Contact(customer, "王採購", "採購經理", "buyer@buyer.example"));
        customer.addInteraction(new Interaction(InteractionType.PHONE, LocalDateTime.now().minusDays(3), "討論導入時程"));
        var savedCustomer = customers.save(customer);
        var opportunity = new Opportunity(savedCustomer, "健康度商機", OpportunityStage.PROPOSAL,
                BigDecimal.valueOf(100000), LocalDate.now().plusDays(30), OpportunityType.NEW_BUSINESS,
                LeadSource.OUTBOUND, 50);
        opportunity.assignOwner(owner);
        opportunityId = opportunities.save(opportunity).getId();
    }

    /** 重算回總分/分項/下一最佳行動，且不改動 Opportunity 的 stage 與 probability。 */
    @Test
    void recalculate_returnsExplainableHealthAndDoesNotMutateOpportunity() throws Exception {
        String token = login("sales@aurora.local");
        var before = opportunities.findById(opportunityId).orElseThrow();
        var stageBefore = before.getStage();
        var probabilityBefore = before.getProbability();

        mvc.perform(post("/api/opportunities/{id}/health/recalculate", opportunityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opportunityId").value(opportunityId))
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.components[0].evidence").isNotEmpty())
                .andExpect(jsonPath("$.nextBestAction").isNotEmpty())
                .andExpect(jsonPath("$.ruleVersion").value("health-rules-v1"));

        // 重算後商機階段與機率必須維持不變。
        var after = opportunities.findById(opportunityId).orElseThrow();
        Assertions.assertThat(after.getStage()).isEqualTo(stageBefore);
        Assertions.assertThat(after.getProbability()).isEqualTo(probabilityBefore);
    }

    /** GET 回最新 snapshot；多次重算後歷史 snapshot 累積成趨勢。 */
    @Test
    void history_accumulatesAcrossRecalculations() throws Exception {
        String token = login("sales@aurora.local");
        mvc.perform(post("/api/opportunities/{id}/health/recalculate", opportunityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String body = mvc.perform(post("/api/opportunities/{id}/health/recalculate", opportunityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertThat(mapper.readTree(body).get("trend").size()).isEqualTo(2);

        // GET 回最新 snapshot，趨勢維持 2 筆（不再新增）。
        mvc.perform(get("/api/opportunities/{id}/health", opportunityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend.length()").value(2));
    }

    /** GET 首次無 snapshot 時即時計算並回結果（趨勢至少 1 筆）。 */
    @Test
    void getHealth_autoComputesFirstSnapshotWhenAbsent() throws Exception {
        String token = login("sales@aurora.local");
        mvc.perform(get("/api/opportunities/{id}/health", opportunityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.trend.length()").value(1));
    }

    /** owner scope：其他 SALES 不可查詢/重算非本人負責客戶的商機健康度（回 403）。 */
    @Test
    void crossOwnerAccessIsForbidden() throws Exception {
        String otherUsername = users.findByRole(Role.SALES).stream().map(AppUser::getUsername)
                .filter(u -> !u.equals("sales@aurora.local")).findFirst().orElseThrow();
        String otherToken = login(otherUsername);

        mvc.perform(get("/api/opportunities/{id}/health", opportunityId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/opportunities/{id}/health/recalculate", opportunityId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    /** 登入 seed 使用者並取得 JWT。 */
    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }
}
