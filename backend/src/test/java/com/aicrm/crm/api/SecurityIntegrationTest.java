package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * RBAC / 認證整合測試：未登入 401、壞 token 401、登入後 200、SALES DELETE 403。
 * DB 與 context 由 PostgresTestBase（Testcontainers pgvector Postgres + 空金鑰）提供。
 */
class SecurityIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    /** 建立套用 security filter 的 MockMvc。 */
    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 用 seed 帳號登入取得 JWT。 */
    private String login(String username) throws Exception {
        var body = "{\"username\":\"" + username + "\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        return node.get("token").asText();
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc().perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401NotServerError() throws Exception {
        mockMvc().perform(get("/api/customers").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void delete_asSales_returns403() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(delete("/api/customers/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeReindex_asSales_returns403() throws Exception {
        // 知識庫重建索引限 ADMIN：SALES 應被擋（釘住 SecurityConfig 的 reindex 規則）
        var token = login("sales@aurora.local");
        mockMvc().perform(post("/api/ai/knowledge/reindex").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
