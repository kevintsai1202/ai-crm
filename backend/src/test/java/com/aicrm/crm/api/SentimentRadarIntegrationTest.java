package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.DemoDataService;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 情緒意圖雷達整合測試（Testcontainers pgvector + V8 套用）。
 *
 * <p>以 {@code DemoDataService.generate(8)} 灌資料後驗證：</p>
 * <ul>
 *   <li>{@code GET /api/dashboard/sentiment} 帶 token 回 200、5 區塊存在、intentDistribution 非空（≥2 類）。</li>
 *   <li>{@code GET /api/customers/{id}} 某客戶互動帶 sentiment / intent（至少部分非空）。</li>
 * </ul>
 */
class SentimentRadarIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired DemoDataService demoDataService;
    @Autowired CustomerRepository customerRepository;

    private String token;

    /** 建立套用 security filter 的 MockMvc。 */
    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 每個測試前以 manager 帳號登入取得 token，並灌入 8 客戶示範資料。 */
    @BeforeEach
    void setUp() throws Exception {
        var body = "{\"username\":\"manager@aurora.local\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
        demoDataService.generate(8);
    }

    /**
     * 雷達端點回 200，5 區塊皆存在，且意圖分布非空（≥2 類）。
     */
    @Test
    void sentimentRadar_returnsFiveSections() throws Exception {
        mockMvc().perform(get("/api/dashboard/sentiment").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentDistribution").isArray())
                .andExpect(jsonPath("$.intentDistribution[1]").exists()) // ≥2 類
                .andExpect(jsonPath("$.sentimentTrend").isArray())
                .andExpect(jsonPath("$.sentimentTrend.length()").value(12)) // 補滿近 12 月
                .andExpect(jsonPath("$.highRiskInteractions").isArray())
                .andExpect(jsonPath("$.churnRadar").isArray())
                .andExpect(jsonPath("$.priorityCare").isArray());
    }

    /**
     * 客戶詳情每則互動帶 sentiment / intent，至少部分互動非空。
     */
    @Test
    void customerDetail_interactionsCarrySentimentAndIntent() throws Exception {
        // 取任一已生成客戶
        var customerId = customerRepository.findAll().getFirst().getId();
        mockMvc().perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactions").isArray())
                // 至少有一則互動的 intent 非 null（deterministic 分類必有結果）
                .andExpect(jsonPath("$.interactions[?(@.intent)]").exists())
                .andExpect(jsonPath("$.interactions[?(@.sentiment)]").exists());
    }
}
