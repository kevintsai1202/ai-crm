package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.support.PostgresTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 商機 API 整合測試（SP8）：建立帶新銷售欄位、未給機率時用階段預設。 */
class OpportunityApiTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    private final ObjectMapper om = new ObjectMapper();
    /** 登入後取得的 JWT token。 */
    private String token;
    /** 測試用客戶 ID（從資料庫第一筆取得）。 */
    private long customerId;

    /** 建立套用 security filter 的 MockMvc。 */
    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 每個測試前以 manager 帳號登入並取得第一筆客戶 ID。 */
    @BeforeEach
    void setup() throws Exception {
        var body = "{\"username\":\"manager@aurora.local\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = om.readTree(json).get("token").asText();
        var custJson = mockMvc().perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        customerId = om.readTree(custJson).get("items").get(0).get("id").asLong();
    }

    /** 建立商機時帶入 leadSource 與 probability，回應應正確回傳這兩個欄位及 ownerId。 */
    @Test
    void create_withSalesFields_returnsThem() throws Exception {
        var payload = String.format("{\"customerId\":%d,\"name\":\"測試案\",\"stage\":\"PROPOSAL\",\"amount\":500000,\"expectedCloseDate\":\"2026-09-01\",\"type\":\"NEW_BUSINESS\",\"leadSource\":\"INBOUND\",\"probability\":60}", customerId);
        mockMvc().perform(post("/api/opportunities").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leadSource").value("INBOUND"))
                .andExpect(jsonPath("$.probability").value(60))
                .andExpect(jsonPath("$.ownerId").exists());
    }

    /** 建立商機時未帶 probability，應依階段 NEGOTIATION 預設為 75。 */
    @Test
    void create_withoutProbability_usesStageDefault() throws Exception {
        var payload = String.format("{\"customerId\":%d,\"name\":\"預設機率\",\"stage\":\"NEGOTIATION\",\"amount\":100000,\"type\":\"NEW_BUSINESS\"}", customerId);
        mockMvc().perform(post("/api/opportunities").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.probability").value(75));
    }
}
