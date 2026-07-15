package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.*;
import com.aicrm.crm.service.mail.ApprovedEmail;
import com.aicrm.crm.service.mail.DeliveryResult;
import com.aicrm.crm.service.mail.MailDeliveryClient;
import com.aicrm.crm.service.mail.MailDeliveryException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/** 跟進信草稿版本化、統一寄件者／Reply-To、冪等寄送與重試規則測試。 */
class FollowUpServiceTest {
    private final FollowUpDraftRepository drafts = mock(FollowUpDraftRepository.class);
    private final OutboundEmailRepository outboundEmails = mock(OutboundEmailRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OpportunityRepository opportunities = mock(OpportunityRepository.class);
    private final MailDeliveryClient mailClient = mock(MailDeliveryClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
    private final jakarta.persistence.Query advisoryQuery = mock(jakarta.persistence.Query.class);
    private final JwtService.AuthPrincipal principal = new JwtService.AuthPrincipal("sales@example.com", "業務", Role.SALES);
    /** 統一已驗證公司寄件者。 */
    private static final String FROM = "no-reply@company.example";
    private FollowUpService service;

    /** 每案建立乾淨 service 並準備 advisory lock stub。 */
    @BeforeEach void setUp() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryQuery);
        when(advisoryQuery.setParameter(anyInt(), any())).thenReturn(advisoryQuery);
        when(advisoryQuery.getSingleResult()).thenReturn(1L);
        when(drafts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboundEmails.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new FollowUpService(drafts, outboundEmails, customers, opportunities, mailClient, mapper, entityManager, FROM);
    }

    /** 建立客戶（含負責業務 email）；owner scope 屬於登入業務。 */
    private Customer ownedCustomer(String ownerEmail, String customerEmail) {
        AppUser owner = mock(AppUser.class);
        when(owner.getUsername()).thenReturn(ownerEmail);
        when(owner.getDisplayName()).thenReturn("業務員");
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(7L);
        when(customer.getName()).thenReturn("測試客戶");
        when(customer.getEmail()).thenReturn(customerEmail);
        when(customer.getOwner()).thenReturn(owner);
        when(customer.getInteractions()).thenReturn(List.of());
        return customer;
    }

    /** 產生草稿：寄送時寄件者統一、Reply-To 為負責業務、收件者為客戶。 */
    @Test void approveAndSend_usesUnifiedFromAndOwnerReplyTo() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        when(drafts.findById(3L)).thenReturn(Optional.of(draft));
        when(outboundEmails.findByCreatorUsernameAndIdempotencyKey(principal.username(), "send-1")).thenReturn(Optional.empty());
        when(mailClient.send(any())).thenReturn(new DeliveryResult("zbr-msg-1"));

        var result = service.approveAndSend(3L, principal, "send-1");

        ArgumentCaptor<ApprovedEmail> captor = ArgumentCaptor.forClass(ApprovedEmail.class);
        verify(mailClient, times(1)).send(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(FROM);
        assertThat(captor.getValue().replyTo()).isEqualTo("owner@company.example");
        assertThat(captor.getValue().recipient()).isEqualTo("client@buyer.example");
        assertThat(result.status()).isEqualTo(OutboundEmailStatus.SENT.name());
        assertThat(result.messageId()).isEqualTo("zbr-msg-1");
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.replyTo()).isEqualTo("owner@company.example");
    }

    /** 負責業務沒有有效 Email 時禁止寄送，且不觸發寄信。 */
    @Test void approveAndSend_ownerWithoutValidEmail_forbidden() {
        AppUser owner = mock(AppUser.class);
        when(owner.getUsername()).thenReturn("not-an-email");
        Customer customer = mock(Customer.class);
        when(customer.getOwner()).thenReturn(owner);
        when(customer.getEmail()).thenReturn("client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        when(drafts.findById(3L)).thenReturn(Optional.of(draft));
        when(outboundEmails.findByCreatorUsernameAndIdempotencyKey(principal.username(), "send-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveAndSend(3L, principal, "send-1"))
                .isInstanceOf(com.aicrm.crm.service.mail.FollowUpPreconditionException.class);
        verifyNoInteractions(mailClient);
    }

    /** 人工修改草稿必須形成新版本（parentId 鏈、versionNumber+1），不覆寫舊版。 */
    @Test void updateDraft_createsNewVersionInsteadOfOverwrite() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft original = new FollowUpDraft(customer, null, principal.username(), "gpt", 1L, "依據", "舊主旨", "舊內文", 1, null, false);
        when(drafts.findById(9L)).thenReturn(Optional.of(original));

        var result = service.updateDraft(9L, new Dtos.UpdateFollowUpDraftRequest("新主旨", "新內文"), principal);

        ArgumentCaptor<FollowUpDraft> captor = ArgumentCaptor.forClass(FollowUpDraft.class);
        verify(drafts, times(1)).save(captor.capture());
        FollowUpDraft saved = captor.getValue();
        assertThat(saved).isNotSameAs(original);
        assertThat(saved.getVersionNumber()).isEqualTo(2);
        assertThat(saved.isEdited()).isTrue();
        assertThat(saved.getSubject()).isEqualTo("新主旨");
        assertThat(saved.getBody()).isEqualTo("新內文");
        // 舊版本內容維持不變（不覆寫）
        assertThat(original.getSubject()).isEqualTo("舊主旨");
        assertThat(result.subject()).isEqualTo("新主旨");
        assertThat(result.versionNumber()).isEqualTo(2);
    }

    /** 相同 Idempotency-Key 重送只回原寄送結果，不再呼叫 MailDeliveryClient.send。 */
    @Test void approveAndSend_replaySameKey_returnsStoredWithoutResending() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        // 首次寄送已建立的 outbound（payload hash 對應 draftId=3）
        OutboundEmail stored = new OutboundEmail(draft, principal.username(), FROM, "owner@company.example",
                "client@buyer.example", "主旨", "內文", "send-1", service.payloadHash(3L));
        stored.markSent("zbr-msg-1");
        when(outboundEmails.findByCreatorUsernameAndIdempotencyKey(principal.username(), "send-1")).thenReturn(Optional.of(stored));

        var result = service.approveAndSend(3L, principal, "send-1");

        assertThat(result.status()).isEqualTo(OutboundEmailStatus.SENT.name());
        assertThat(result.messageId()).isEqualTo("zbr-msg-1");
        verifyNoInteractions(mailClient);
        verify(outboundEmails, never()).save(any());
    }

    /** 相同 key 但不同 payload（不同草稿）必須衝突且零寄送。 */
    @Test void approveAndSend_differentPayloadSameKey_conflict() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        OutboundEmail stored = new OutboundEmail(draft, principal.username(), FROM, "owner@company.example",
                "client@buyer.example", "主旨", "內文", "send-1", service.payloadHash(999L));
        stored.markSent("zbr-msg-1");
        when(outboundEmails.findByCreatorUsernameAndIdempotencyKey(principal.username(), "send-1")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.approveAndSend(3L, principal, "send-1"))
                .isInstanceOf(com.aicrm.crm.service.mail.FollowUpConflictException.class);
        verifyNoInteractions(mailClient);
    }

    /** 寄送失敗建立 FAILED outbound，錯誤訊息不得包含憑證。 */
    @Test void approveAndSend_deliveryFailure_recordsFailedWithoutCredential() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        when(drafts.findById(3L)).thenReturn(Optional.of(draft));
        when(outboundEmails.findByCreatorUsernameAndIdempotencyKey(principal.username(), "send-1")).thenReturn(Optional.empty());
        when(mailClient.send(any())).thenThrow(new MailDeliveryException("Bearer token=SECRET-CREDENTIAL-123 rejected"));

        var result = service.approveAndSend(3L, principal, "send-1");

        assertThat(result.status()).isEqualTo(OutboundEmailStatus.FAILED.name());
        assertThat(result.errorSummary()).doesNotContain("SECRET-CREDENTIAL-123");
        assertThat(result.errorSummary()).doesNotContainIgnoringCase("token");
    }

    /** FAILED 可重試：再次寄送成功轉 SENT 並累加重試次數。 */
    @Test void retry_failedCanBeResent() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        OutboundEmail failed = new OutboundEmail(draft, principal.username(), FROM, "owner@company.example",
                "client@buyer.example", "主旨", "內文", "send-1", "hash");
        failed.markFailed("寄送失敗");
        when(outboundEmails.findByIdForUpdate(11L)).thenReturn(Optional.of(failed));
        when(mailClient.send(any())).thenReturn(new DeliveryResult("zbr-msg-retry"));

        var result = service.retry(11L, principal);

        assertThat(result.status()).isEqualTo(OutboundEmailStatus.SENT.name());
        assertThat(result.messageId()).isEqualTo("zbr-msg-retry");
        assertThat(result.retryCount()).isEqualTo(1);
        verify(mailClient, times(1)).send(any());
    }

    /** SENT 不可重試：回衝突且不再寄送。 */
    @Test void retry_sentCannotBeResent() {
        Customer customer = ownedCustomer("owner@company.example", "client@buyer.example");
        FollowUpDraft draft = new FollowUpDraft(customer, null, principal.username(), null, null, "依據", "主旨", "內文", 1, null, false);
        OutboundEmail sent = new OutboundEmail(draft, principal.username(), FROM, "owner@company.example",
                "client@buyer.example", "主旨", "內文", "send-1", "hash");
        sent.markSent("zbr-msg-1");
        when(outboundEmails.findByIdForUpdate(11L)).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> service.retry(11L, principal))
                .isInstanceOf(com.aicrm.crm.service.mail.FollowUpConflictException.class);
        verifyNoInteractions(mailClient);
    }

    /** 產生草稿：deterministic 內容含客戶名稱與引用依據，版本為 1。 */
    @Test void createDraft_deterministicWithGrounding() {
        // 客戶負責業務即為登入業務本人，通過 owner scope 檢查。
        Customer customer = ownedCustomer(principal.username(), "client@buyer.example");
        when(customers.findById(7L)).thenReturn(Optional.of(customer));

        var result = service.createDraft(7L, new Dtos.CreateFollowUpDraftRequest(null), principal);

        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.subject()).isNotBlank();
        assertThat(result.body()).contains("測試客戶");
        assertThat(result.grounding()).isNotBlank();
    }
}
