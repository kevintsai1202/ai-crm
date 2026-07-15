package com.aicrm.crm.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.mail.ApprovedEmail;
import com.aicrm.crm.service.mail.DeliveryResult;
import com.aicrm.crm.service.mail.MailDeliveryClient;
import com.aicrm.crm.service.mail.MailDeliveryException;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 跟進信 API：草稿版本化、統一寄件者／Reply-To、冪等寄送、重試與水平權限整合測試。 */
class FollowUpIntegrationTest extends PostgresTestBase {
    @Autowired WebApplicationContext context; @Autowired FilterChainProxy filters;
    @Autowired AppUserRepository users; @Autowired CustomerRepository customers;
    @MockitoBean MailDeliveryClient mailClient;
    private MockMvc mvc; private final ObjectMapper mapper = new ObjectMapper(); private Long ownerCustomerId;

    /** 建立真實 security MockMvc、屬於業務的客戶並預設寄送成功。 */
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = new Customer("跟進信測試客戶", "client@buyer.example", "0912345678", "12345678", "科技", "x");
        customer.assignOwner(owner);
        ownerCustomerId = customers.save(customer).getId();
        when(mailClient.send(any())).thenReturn(new DeliveryResult("zbr-it-msg-1"));
    }

    /** 草稿 → 人工修改成新版本 → 冪等寄送：同 key 只寄一次、回同結果、不同 payload 回 409。 */
    @Test void draftEditApproveSendIsIdempotent() throws Exception {
        String token = login("sales@aurora.local");
        // 建立草稿（版本 1）
        String draftBody = mvc.perform(post("/api/customers/{id}/follow-ups/drafts", ownerCustomerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(1))
                .andReturn().getResponse().getContentAsString();
        long draftV1 = mapper.readTree(draftBody).get("id").asLong();

        // 人工修改 → 產生新版本（不覆寫）
        String editBody = mvc.perform(put("/api/follow-ups/drafts/{id}", draftV1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"人工主旨\",\"body\":\"人工內文\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.edited").value(true))
                .andReturn().getResponse().getContentAsString();
        long draftV2 = mapper.readTree(editBody).get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(draftV2).isNotEqualTo(draftV1);

        // 核准並寄送
        mvc.perform(post("/api/follow-ups/drafts/{id}/approve-and-send", draftV2)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", "send-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.replyTo").value("sales@aurora.local"))
                .andExpect(jsonPath("$.recipient").value("client@buyer.example"))
                .andExpect(jsonPath("$.messageId").value("zbr-it-msg-1"));

        // 相同 key 重送回原結果
        mvc.perform(post("/api/follow-ups/drafts/{id}/approve-and-send", draftV2)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", "send-key-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
        // 相同 key + 不同 payload（不同草稿）回 409
        mvc.perform(post("/api/follow-ups/drafts/{id}/approve-and-send", draftV1)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", "send-key-1"))
                .andExpect(status().isConflict());

        // 寄件者統一、Reply-To 為負責業務、且只寄一次
        var captor = org.mockito.ArgumentCaptor.forClass(ApprovedEmail.class);
        verify(mailClient, times(1)).send(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().replyTo()).isEqualTo("sales@aurora.local");
    }

    /** 其他 SALES 不可對非本人客戶產生草稿；對非本人草稿修改／寄送回 404。 */
    @Test void crossOwnerOperationsAreNotFound() throws Exception {
        String otherUsername = users.findByRole(Role.SALES).stream().map(AppUser::getUsername)
                .filter(u -> !u.equals("sales@aurora.local")).findFirst().orElseThrow();
        String ownerToken = login("sales@aurora.local"), otherToken = login(otherUsername);
        String draftBody = mvc.perform(post("/api/customers/{id}/follow-ups/drafts", ownerCustomerId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long draftId = mapper.readTree(draftBody).get("id").asLong();

        // 其他業務對非本人客戶建立草稿 → 404
        mvc.perform(post("/api/customers/{id}/follow-ups/drafts", ownerCustomerId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        // 其他業務對非本人草稿修改 → 404
        mvc.perform(put("/api/follow-ups/drafts/{id}", draftId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"x\",\"body\":\"y\"}"))
                .andExpect(status().isNotFound());
        // 其他業務對非本人草稿寄送 → 404
        mvc.perform(post("/api/follow-ups/drafts/{id}/approve-and-send", draftId)
                        .header("Authorization", "Bearer " + otherToken).header("Idempotency-Key", "x-key"))
                .andExpect(status().isNotFound());
    }

    /** 寄送失敗建立 FAILED；FAILED 可重試轉 SENT，SENT 再重試回 409。 */
    @Test void failedEmailCanRetryThenSentCannotRetry() throws Exception {
        String token = login("sales@aurora.local");
        String draftBody = mvc.perform(post("/api/customers/{id}/follow-ups/drafts", ownerCustomerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long draftId = mapper.readTree(draftBody).get("id").asLong();

        // 第一次寄送失敗 → FAILED（回應狀態欄位而非 HTTP 錯誤，以便保留可重試紀錄）
        // 使用 doThrow/doReturn 形式，避免對「已設定為丟例外」的 mock 重新 stub 時再次觸發例外。
        doThrow(new MailDeliveryException("Bearer token=SECRET-XYZ rejected")).when(mailClient).send(any());
        String sendBody = mvc.perform(post("/api/follow-ups/drafts/{id}/approve-and-send", draftId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", "send-key-fail"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();
        var sendJson = mapper.readTree(sendBody);
        long outboundId = sendJson.get("id").asLong();
        // 錯誤訊息不得包含憑證
        org.assertj.core.api.Assertions.assertThat(sendJson.get("errorSummary").asText()).doesNotContain("SECRET-XYZ");

        // 重試成功 → SENT
        doReturn(new DeliveryResult("zbr-retry-ok")).when(mailClient).send(any());
        mvc.perform(post("/api/outbound-emails/{id}/retry", outboundId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.retryCount").value(1));

        // 已 SENT 再重試 → 409
        mvc.perform(post("/api/outbound-emails/{id}/retry", outboundId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    /** 登入 seed 使用者並取得 JWT。 */
    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }
}
