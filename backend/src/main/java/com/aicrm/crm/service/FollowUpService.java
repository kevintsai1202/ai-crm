package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.FollowUpDraftRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import com.aicrm.crm.repository.OutboundEmailRepository;
import com.aicrm.crm.service.mail.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 跟進信服務：deterministic 草稿產生、人工修改版本化、核准後透過 {@link MailDeliveryClient}
 * 寄送並冪等去重，以及失敗郵件重試。寄件者統一使用已驗證公司信箱，Reply-To 使用客戶／商機
 * 負責業務 Email；負責業務缺少有效 Email 時禁止寄送。
 */
@Service
public class FollowUpService {
    private final FollowUpDraftRepository drafts;
    private final OutboundEmailRepository outboundEmails;
    private final CustomerRepository customers;
    private final OpportunityRepository opportunities;
    private final MailDeliveryClient mailClient;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    /** 統一已驗證公司寄件者，由環境設定注入。 */
    private final String fromAddress;
    /** 寄送失敗時保存的固定安全訊息，絕不含任何憑證或底層例外文字。 */
    private static final String SAFE_DELIVERY_ERROR = "跟進信寄送失敗，請稍後重試或聯絡系統管理員。";
    /** 基本 Email 格式驗證。 */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** 注入持久化、寄信邊界與寄件者設定。 */
    public FollowUpService(FollowUpDraftRepository drafts, OutboundEmailRepository outboundEmails,
            CustomerRepository customers, OpportunityRepository opportunities, MailDeliveryClient mailClient,
            ObjectMapper mapper, EntityManager entityManager,
            @Value("${app.mail.from:no-reply@ai-crm.local}") String fromAddress) {
        this.drafts = drafts; this.outboundEmails = outboundEmails; this.customers = customers;
        this.opportunities = opportunities; this.mailClient = mailClient; this.mapper = mapper;
        this.entityManager = entityManager; this.fromAddress = fromAddress;
    }

    /** 依客戶／商機／近期互動 deterministic 產生第一版跟進信草稿。 */
    @Transactional
    public Dtos.FollowUpDraftResponse createDraft(Long customerId, Dtos.CreateFollowUpDraftRequest request,
            JwtService.AuthPrincipal principal) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new EntityNotFoundException("客戶不存在"));
        assertCustomerVisible(customer, principal);
        Opportunity opportunity = resolveOpportunity(request == null ? null : request.opportunityId(), customer, principal);
        String grounding = buildGrounding(customer, opportunity);
        String subject = buildSubject(customer, opportunity);
        String body = buildBody(customer, opportunity);
        // 第一版：deterministic 產生（model/provider 為 null），版本 1、無 parent、非人工修改。
        FollowUpDraft draft = new FollowUpDraft(customer, opportunity, principal.username(), null, null,
                grounding, subject, body, 1, null, false);
        return draftResponse(drafts.save(draft));
    }

    /** 人工修改草稿：以遞增版本號與 parentId 鏈建立新版本，不覆寫舊版。 */
    @Transactional
    public Dtos.FollowUpDraftResponse updateDraft(Long draftId, Dtos.UpdateFollowUpDraftRequest request,
            JwtService.AuthPrincipal principal) {
        FollowUpDraft prior = visibleDraft(draftId, principal);
        FollowUpDraft next = new FollowUpDraft(prior.getCustomer(), prior.getOpportunity(), prior.getCreatorUsername(),
                prior.getModel(), prior.getAiProviderId(), prior.getGrounding(),
                request.subject(), request.body(), prior.getVersionNumber() + 1, prior.getId(), true);
        return draftResponse(drafts.save(next));
    }

    /** 核准草稿並透過寄信服務寄送；同一 Idempotency-Key 只寄一次並回相同結果。 */
    @Transactional
    public Dtos.OutboundEmailResponse approveAndSend(Long draftId, JwtService.AuthPrincipal principal, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key 為必填");
        String key = idempotencyKey.trim();
        String hash = payloadHash(draftId);
        // 以 advisory lock 序列化同一使用者同一鍵的併發寄送，避免重複寄出。
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, principal.username() + ":" + key).getSingleResult();
        Optional<OutboundEmail> prior = outboundEmails.findByCreatorUsernameAndIdempotencyKey(principal.username(), key);
        if (prior.isPresent()) {
            if (hash.equals(prior.get().getIdempotencyPayloadHash())) return outboundResponse(prior.get());
            throw new FollowUpConflictException("Idempotency-Key 已由不同請求使用");
        }
        FollowUpDraft draft = visibleDraft(draftId, principal);
        Customer customer = draft.getCustomer();
        Opportunity opportunity = draft.getOpportunity();
        // Reply-To 用商機負責業務優先、否則客戶負責業務；無有效 Email 一律禁止寄送。
        String replyTo = ownerEmail(opportunity, customer);
        if (!isValidEmail(replyTo)) {
            throw new FollowUpPreconditionException("負責業務缺少有效 Email，無法寄送跟進信。");
        }
        String recipient = customer.getEmail();
        OutboundEmail email = new OutboundEmail(draft, principal.username(), fromAddress, replyTo, recipient,
                draft.getSubject(), draft.getBody(), key, hash);
        // 先持久化 QUEUED 並記錄冪等鍵，確保寄送結果（含 FAILED）可被重送查回與重試。
        dispatch(email);
        draft.approve(principal.username());
        drafts.save(draft);
        return outboundResponse(outboundEmails.save(email));
    }

    /** 重試 FAILED 郵件；SENT 不可重試（衝突）。 */
    @Transactional
    public Dtos.OutboundEmailResponse retry(Long outboundId, JwtService.AuthPrincipal principal) {
        OutboundEmail email = outboundEmails.findByIdForUpdate(outboundId)
                .orElseThrow(() -> new EntityNotFoundException("外寄郵件不存在"));
        if (principal.role() == Role.SALES && !email.getCreatorUsername().equals(principal.username())) {
            throw new EntityNotFoundException("外寄郵件不存在");
        }
        if (email.getStatus() == OutboundEmailStatus.SENT) {
            throw new FollowUpConflictException("郵件已寄出，不可重試。");
        }
        if (email.getStatus() != OutboundEmailStatus.FAILED) {
            throw new FollowUpConflictException("郵件目前狀態不可重試。");
        }
        email.incrementRetry();
        dispatch(email);
        return outboundResponse(outboundEmails.save(email));
    }

    /** 呼叫寄信服務並依結果轉為 SENT／FAILED；寄送失敗只保存固定安全訊息。 */
    private void dispatch(OutboundEmail email) {
        try {
            DeliveryResult result = mailClient.send(new ApprovedEmail(email.getFromAddress(), email.getReplyTo(),
                    email.getRecipient(), email.getSubject(), email.getBody()));
            email.markSent(result.messageId());
        } catch (MailDeliveryException exception) {
            // 刻意不轉述例外訊息，避免任何憑證外洩至 audit / 前端。
            email.markFailed(SAFE_DELIVERY_ERROR);
        }
    }

    /** 取得商機（優先）或客戶負責業務的 Email。 */
    private String ownerEmail(Opportunity opportunity, Customer customer) {
        AppUser owner = opportunity != null && opportunity.getOwner() != null ? opportunity.getOwner() : customer.getOwner();
        return owner == null ? null : owner.getUsername();
    }

    /** 載入草稿並驗證 owner scope；SALES 僅能存取本人建立者的草稿。 */
    private FollowUpDraft visibleDraft(Long draftId, JwtService.AuthPrincipal principal) {
        FollowUpDraft draft = drafts.findById(draftId).orElseThrow(() -> new EntityNotFoundException("跟進信草稿不存在"));
        if (principal.role() == Role.SALES && !draft.getCreatorUsername().equals(principal.username())) {
            throw new EntityNotFoundException("跟進信草稿不存在");
        }
        return draft;
    }

    /** 載入選填商機並驗證屬於客戶且可見。 */
    private Opportunity resolveOpportunity(Long opportunityId, Customer customer, JwtService.AuthPrincipal principal) {
        if (opportunityId == null) return null;
        Opportunity opportunity = opportunities.findById(opportunityId).orElseThrow(() -> new EntityNotFoundException("商機不存在"));
        if (opportunity.getCustomer() == null || !Objects.equals(opportunity.getCustomer().getId(), customer.getId())) {
            throw new EntityNotFoundException("商機不存在");
        }
        return opportunity;
    }

    /** 驗證客戶 owner scope；管理角色沿用全域可見。 */
    private void assertCustomerVisible(Customer customer, JwtService.AuthPrincipal principal) {
        boolean visible = principal.role() != Role.SALES
                || customer.getOwner() != null && principal.username().equals(customer.getOwner().getUsername());
        if (!visible) throw new EntityNotFoundException("客戶不存在");
    }

    /** 組裝 grounding 引用依據：客戶、商機與近三筆互動摘要。 */
    private String buildGrounding(Customer customer, Opportunity opportunity) {
        StringBuilder sb = new StringBuilder();
        sb.append("客戶：").append(nullToDash(customer.getName()))
                .append("（產業：").append(nullToDash(customer.getIndustry())).append("）").append(System.lineSeparator());
        if (opportunity != null) {
            sb.append("商機：").append(nullToDash(opportunity.getName()))
                    .append("（階段：").append(opportunity.getStage() == null ? "-" : opportunity.getStage().name()).append("）")
                    .append(System.lineSeparator());
        }
        List<Interaction> recent = recentInteractions(customer);
        if (recent.isEmpty()) {
            sb.append("近期互動：無");
        } else {
            sb.append("近期互動：").append(System.lineSeparator());
            for (Interaction i : recent) {
                String content = i.getContent() == null ? "" : i.getContent();
                if (content.length() > 60) content = content.substring(0, 60) + "…";
                sb.append("- [").append(i.getType() == null ? "-" : i.getType().name()).append("] ").append(content)
                        .append(System.lineSeparator());
            }
        }
        return sb.toString().trim();
    }

    /** deterministic 主旨。 */
    private String buildSubject(Customer customer, Opportunity opportunity) {
        if (opportunity != null) return "關於「" + nullToDash(opportunity.getName()) + "」的後續跟進";
        return "與 " + nullToDash(customer.getName()) + " 的後續跟進";
    }

    /** deterministic 內文（含客戶名稱與 grounding 摘要，供人工調整）。 */
    private String buildBody(Customer customer, Opportunity opportunity) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToDash(customer.getName())).append(" 您好，").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("感謝您撥冗與我們交流。").append(System.lineSeparator());
        if (opportunity != null) {
            sb.append("針對「").append(nullToDash(opportunity.getName())).append("」，我們整理了後續建議，並希望確認接下來的合作時程。")
                    .append(System.lineSeparator());
        } else {
            sb.append("我們整理了後續建議，並希望了解目前的需求與時程規劃。").append(System.lineSeparator());
        }
        List<Interaction> recent = recentInteractions(customer);
        if (!recent.isEmpty()) {
            sb.append(System.lineSeparator()).append("承接先前的交流重點，我們會盡快提供相關資訊。").append(System.lineSeparator());
        }
        sb.append(System.lineSeparator()).append("若有任何問題，歡迎直接回覆本信與負責業務聯繫。").append(System.lineSeparator());
        sb.append(System.lineSeparator()).append("敬祝 商祺");
        return sb.toString();
    }

    /** 取客戶近三筆互動（依發生時間新到舊）。 */
    private List<Interaction> recentInteractions(Customer customer) {
        List<Interaction> all = new ArrayList<>(customer.getInteractions());
        all.sort(Comparator.comparing(Interaction::getOccurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all.size() > 3 ? all.subList(0, 3) : all;
    }

    /** 空值以 "-" 呈現。 */
    private String nullToDash(String value) { return value == null || value.isBlank() ? "-" : value; }

    /** 基本 Email 有效性驗證。 */
    private boolean isValidEmail(String value) { return value != null && EMAIL.matcher(value.trim()).matches(); }

    /** 組裝草稿回應。 */
    private Dtos.FollowUpDraftResponse draftResponse(FollowUpDraft d) {
        return new Dtos.FollowUpDraftResponse(d.getId(), d.getCustomer().getId(),
                d.getOpportunity() == null ? null : d.getOpportunity().getId(), d.getVersionNumber(), d.getParentId(),
                d.getModel(), d.getGrounding(), d.getSubject(), d.getBody(), d.isEdited(), d.getApprovedBy(), d.getApprovedAt());
    }

    /** 組裝外寄郵件回應。 */
    private Dtos.OutboundEmailResponse outboundResponse(OutboundEmail e) {
        return new Dtos.OutboundEmailResponse(e.getId(), e.getDraft() == null ? null : e.getDraft().getId(),
                e.getFromAddress(), e.getReplyTo(), e.getRecipient(), e.getSubject(), e.getBody(), e.getStatus().name(),
                e.getMessageId(), e.getRetryCount(), e.getErrorSummary(), e.getSentAt());
    }

    /** 依 draftId 產生冪等 payload 雜湊；相同 key 但不同草稿會因此不同而視為衝突。 */
    public String payloadHash(Long draftId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("draft:" + draftId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("無法計算冪等雜湊", e);
        }
    }
}
