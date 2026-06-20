package com.aicrm.crm.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * AI fallback 整合測試：空金鑰下 /api/ai/chat 回 200 deterministic 答案（證實未打真 LLM）。
 * AiController 對 /api/ai/chat 提供兩種 mapping：produces=application/json 回 ChatResponse、
 * text/event-stream 回 SSE。本測試以 Accept=application/json 命中 JSON 版本並驗證 fallback 內容。
 * DB 與 context 由 PostgresTestBase（Testcontainers pgvector Postgres + 空金鑰）提供。
 */
class AiFallbackIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    private String token;

    /** 建立套用 security filter 的 MockMvc。 */
    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 每個測試前以 sales 帳號登入取得 token。 */
    @BeforeEach
    void loginFirst() throws Exception {
        var body = "{\"username\":\"sales@aurora.local\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void chat_withoutApiKey_returnsDeterministicAnswer() throws Exception {
        var body = "{\"customerId\":1,\"message\":\"請評估\"}";
        mockMvc().perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                // 驗證確實走 deterministic fallback（內容含風險敘述與建議），而非空白或 placeholder
                .andExpect(jsonPath("$.answer", containsString("風險")))
                .andExpect(jsonPath("$.answer", containsString("建議")))
                .andExpect(jsonPath("$.risk").exists());
    }
}
