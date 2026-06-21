package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.repository.ManagerInsightRepository;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Manager 分析端點權限測試：MANAGER 可進、SALES 被擋。 */
class ManagerAnalyticsSecurityTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    /** 確保 insight 快取為空，防止跨測試類別的 DB 狀態污染 204 斷言。 */
    @Autowired ManagerInsightRepository insightRepo;

    @BeforeEach
    void clearInsightCache() {
        insightRepo.deleteAll();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 以指定帳號登入取得 token。 */
    private String login(String username) throws Exception {
        var body = "{\"username\":\"" + username + "\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void manager_canAccessAnalytics() throws Exception {
        var token = login("manager@aurora.local");
        mockMvc().perform(get("/api/manager/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void sales_isForbidden() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/manager/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void sales_isForbidden_onTeamInsight() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/manager/insights/team").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void manager_canAccessTeamInsight() throws Exception {
        var token = login("manager@aurora.local");
        // 未生成快取時回 204（仍代表「可存取」，非 403）
        mockMvc().perform(get("/api/manager/insights/team").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
