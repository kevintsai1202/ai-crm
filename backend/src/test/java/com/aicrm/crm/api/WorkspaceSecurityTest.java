package com.aicrm.crm.api;

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

/** 工作檯個人 AI 端點權限測試：未登入 401、任何登入角色可讀。 */
class WorkspaceSecurityTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

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
    void recommendation_requiresAuth() throws Exception {
        mockMvc().perform(get("/api/workspace/recommendation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sales_canReadOwnRecommendation() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/workspace/recommendation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
