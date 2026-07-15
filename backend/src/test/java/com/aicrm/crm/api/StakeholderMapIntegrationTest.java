package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Contact;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Stakeholder 決策鏈 API 整合測試（V27）：驗證 AI 建議 / 已確認事實分離、confirm 後成為事實、
 * owner scope 水平越權 403、跨 customer 關係 400。
 */
class StakeholderMapIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @Autowired AppUserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private Long customerId;
    private Long secondCustomerId;
    private Long firstContactId;
    private Long secondCustomerContactId;
    private String otherSalesUsername;

    /** 建立 security MockMvc、屬於 sales@aurora.local 的兩個客戶（各含聯絡人），以及一個獨立第二位 SALES 帳號。 */
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();

        var customer = new Customer("決策鏈客戶", "chain@buyer.example", "0912345678", "32345678", "科技", "x");
        customer.assignOwner(owner);
        customer.getContacts().add(new Contact(customer, "王大明", "總經理", "boss@buyer.example"));
        customer.getContacts().add(new Contact(customer, "李小華", "資訊部工程師", "eng@buyer.example"));
        customer.getContacts().add(new Contact(customer, "張美麗", "採購專員", "buyer@buyer.example"));
        var savedCustomer = customers.save(customer);
        customerId = savedCustomer.getId();
        // 由已保存實例的記憶體 contacts 取 id，避免對 detached entity 觸發 LazyInitialization。
        firstContactId = savedCustomer.getContacts().get(0).getId();

        var second = new Customer("另一決策鏈客戶", "chain2@buyer.example", "0987654321", "42345678", "科技", "x");
        second.assignOwner(owner);
        second.getContacts().add(new Contact(second, "陳經理", "業務經理", "sales@buyer.example"));
        var savedSecond = customers.save(second);
        secondCustomerId = savedSecond.getId();
        secondCustomerContactId = savedSecond.getContacts().get(0).getId();

        // 建立獨立的第二位 SALES 帳號，確保 owner scope 測試不受其他測試類別執行順序影響。
        otherSalesUsername = "other-sales@aurora.local";
        if (users.findByUsername(otherSalesUsername).isEmpty()) {
            users.save(new AppUser(otherSalesUsername, passwordEncoder.encode("password123"), "另一位業務", Role.SALES));
        }
    }

    /** suggest 產生 SUGGESTED 建議並出現在 GET 的 suggestions；未 confirm 前不進已確認圖。 */
    @Test
    void suggestThenGet_separatesSuggestionsFromConfirmed() throws Exception {
        String token = login("sales@aurora.local");

        mvc.perform(post("/api/customers/{id}/stakeholder-map/suggest", customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value("SUGGESTED"));

        mvc.perform(get("/api/customers/{id}/stakeholder-map", customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.confirmedRoles.length()").value(0))
                .andExpect(jsonPath("$.confirmedRelations.length()").value(0))
                .andExpect(jsonPath("$.suggestions.length()").value(5));
    }

    /** confirm 後建議成為已確認事實，出現在 confirmedRoles，並自待確認清單移除。 */
    @Test
    void confirm_promotesSuggestionToFact() throws Exception {
        String token = login("sales@aurora.local");
        String body = mvc.perform(post("/api/customers/{id}/stakeholder-map/suggest", customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var suggestions = mapper.readTree(body);
        String roleSuggestionId = null;
        for (var node : suggestions) {
            if ("ROLE".equals(node.get("kind").asText())) {
                roleSuggestionId = node.get("suggestionId").asText();
                break;
            }
        }

        mvc.perform(post("/api/stakeholder-suggestions/{id}/confirm", roleSuggestionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(get("/api/customers/{id}/stakeholder-map", customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedRoles.length()").value(1))
                .andExpect(jsonPath("$.suggestions.length()").value(4));
    }

    /** owner scope：其他 SALES 不可查詢 / 產生非本人負責客戶的決策鏈（回 403）。 */
    @Test
    void crossOwnerAccessIsForbidden() throws Exception {
        String otherToken = login(otherSalesUsername);

        mvc.perform(get("/api/customers/{id}/stakeholder-map", customerId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/customers/{id}/stakeholder-map/suggest", customerId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    /** 跨 customer 的手動關係被拒絕（兩位 Contact 屬不同客戶時回 400）。 */
    @Test
    void manualRelation_crossCustomerReturnsBadRequest() throws Exception {
        String token = login("sales@aurora.local");
        String payload = "{\"fromContactId\":" + firstContactId + ",\"toContactId\":" + secondCustomerContactId
                + ",\"relationType\":\"PEER\"}";
        mvc.perform(post("/api/customers/{id}/stakeholder-map/relations", customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    /** 登入 seed 使用者並取得 JWT。 */
    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }
}
