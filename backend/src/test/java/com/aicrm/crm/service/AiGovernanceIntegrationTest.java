package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.repository.AiCallLogRepository;
import com.aicrm.crm.repository.AiFeedbackRepository;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 治理整合測試（空金鑰 → fallback）：
 * 驗證 /api/ai/chat 回應帶 callId、ai_call_log 落地（fallback 旗標正確）、
 * 採納回饋寫入、/api/ai/usage 角色權限。
 * DB 與 context 由 PostgresTestBase（Testcontainers pgvector Postgres + 空金鑰）提供。
 */
class AiGovernanceIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired AiCallLogRepository callLogRepository;
    @Autowired AiFeedbackRepository feedbackRepository;

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
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void chatLogsCall_feedbackPersists_usageRbacEnforced() throws Exception {
        var salesToken = login("sales@aurora.local");
        // 客戶存取已加擁有權守衛（SALES 僅能存取自己負責客戶）；本測試聚焦治理記錄與用量 RBAC，
        // 與業務歸屬無關，故 chat 以 ADMIN 登入（可存取既有客戶，繞過歸屬限制）。
        var adminToken = login("admin@aurora.local");

        // 1. 呼叫 /api/ai/chat → 200 且回應含 callId（非 null）
        var chatBody = "{\"customerId\":1,\"message\":\"請評估\"}";
        var chatJson = mockMvc().perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").isNumber())
                .andReturn().getResponse().getContentAsString();
        var callId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(chatJson).get("callId").asLong();

        // 2. ai_call_log 至少 1 筆；該筆為 fallback（空金鑰）：ai_enabled=false、masked=true、answer 非空、tokens=0
        assertThat(callLogRepository.count()).isGreaterThanOrEqualTo(1);
        var saved = callLogRepository.findById(callId).orElseThrow();
        assertThat(saved.isAiEnabled()).isFalse();
        assertThat(saved.isPiiMasked()).isTrue();
        assertThat(saved.getAnswer()).isNotBlank();
        assertThat(saved.getTotalTokens()).isZero();

        // 3. 對該筆送出 ADOPTED 回饋 → 2xx；ai_feedback 落地
        var feedbackCountBefore = feedbackRepository.count();
        mockMvc().perform(post("/api/ai/calls/" + callId + "/feedback")
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ADOPTED\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(feedbackRepository.count()).isEqualTo(feedbackCountBefore + 1);

        // 4. /api/ai/usage：MANAGER 200、SALES 403
        var managerToken = login("manager@aurora.local");
        mockMvc().perform(get("/api/ai/usage").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                // 前面已至少觸發一次 chat → totalCalls 應 >= 1（避免 usage() 全零也過關）
                .andExpect(jsonPath("$.totalCalls").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc().perform(get("/api/ai/usage").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
    }
}
