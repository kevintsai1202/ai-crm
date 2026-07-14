package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.*;
import com.aicrm.crm.service.businesscard.*;
import com.aicrm.crm.service.media.TemporaryMediaService;
import com.aicrm.crm.service.vision.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** AI 名片辨識、重複候選與人工確認建檔服務。 */
@Service
@ConditionalOnProperty(name="app.media.enabled", havingValue="true", matchIfMissing=true)
public class BusinessCardIntakeService {
    private final BusinessCardIntakeRepository intakes; private final TemporaryMediaService mediaService;
    private final BusinessCardRecognitionClient recognition; private final SystemSettingService settings;
    private final CustomerRepository customers; private final ContactRepository contacts;
    private final OpportunityRepository opportunities; private final CrmTaskRepository tasks;
    private final AppUserRepository users; private final ObjectMapper mapper;
    private final EntityManager entityManager;

    /** 注入所有持久化與外部服務邊界。 */
    public BusinessCardIntakeService(BusinessCardIntakeRepository intakes, TemporaryMediaService mediaService,
            BusinessCardRecognitionClient recognition, SystemSettingService settings, CustomerRepository customers,
            ContactRepository contacts, OpportunityRepository opportunities, CrmTaskRepository tasks,
            AppUserRepository users, ObjectMapper mapper, EntityManager entityManager) {
        this.intakes=intakes; this.mediaService=mediaService; this.recognition=recognition; this.settings=settings;
        this.customers=customers; this.contacts=contacts; this.opportunities=opportunities; this.tasks=tasks;
        this.users=users; this.mapper=mapper; this.entityManager=entityManager;
    }

    /** 驗證 OCR assignment 後暫存圖片並執行 deterministic/production Vision client。 */
    @Transactional
    public Dtos.BusinessCardIntakeResponse create(MultipartFile file, JwtService.AuthPrincipal principal) {
        AiModelAssignment assignment = settings.resolveOcrAssignment();
        if (assignment == null) throw new BusinessCardUnavailableException("尚未設定支援 Vision 的 OCR 模型");
        TemporaryMedia media = mediaService.stage(file, MediaPurpose.BUSINESS_CARD, principal);
        media.transition(MediaStatus.PROCESSING);
        BusinessCardIntake intake = intakes.save(new BusinessCardIntake(media, principal.username(), assignment.model(), assignment.providerId()));
        try {
            Dtos.RecognizedBusinessCard card = recognition.recognize(mediaService.read(media), media.getMimeType(), assignment);
            validateRecognized(card);
            List<Dtos.BusinessCardDuplicateCandidate> candidates = duplicateCandidates(card, principal);
            intake.review(write(card), write(candidates)); media.transition(MediaStatus.REVIEW_PENDING);
        } catch (RuntimeException exception) {
            intake.fail("名片辨識失敗"); media.transition(MediaStatus.FAILED);
        }
        return response(intakes.save(intake));
    }

    /** 取得本人可見的 intake，避免 SALES 水平越權。 */
    @Transactional(readOnly = true)
    public Dtos.BusinessCardIntakeResponse get(Long id, JwtService.AuthPrincipal principal) {
        return response(visible(intakes.findById(id).orElseThrow(() -> new EntityNotFoundException("名片辨識工作不存在")), principal));
    }

    /** 在單一交易建立／合併 Customer、Contact、Opportunity 與 PHONE_CALL Task。 */
    @Transactional
    public Dtos.BusinessCardConfirmResponse confirm(Long id, Dtos.ConfirmBusinessCardRequest request,
            JwtService.AuthPrincipal principal, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key 為必填");
        CanonicalConfirmCommand command=CanonicalConfirmCommand.from(request);
        String key=idempotencyKey.trim(); String hash = sha256(write(command));
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, principal.username()+":"+key).getSingleResult();
        Optional<BusinessCardIntake> prior=intakes.findByCreatorUsernameAndIdempotencyKey(principal.username(),key);
        if(prior.isPresent()) {
            if(hash.equals(prior.get().getIdempotencyPayloadHash())) return confirmedResponse(prior.get());
            throw new BusinessCardConflictException("Idempotency-Key 已由不同請求使用");
        }
        BusinessCardIntake intake = visible(intakes.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("名片辨識工作不存在")), principal);
        if (intake.getStatus() == BusinessCardStatus.CONFIRMED) {
            if (key.equals(intake.getIdempotencyKey()) && hash.equals(intake.getIdempotencyPayloadHash())) return confirmedResponse(intake);
            throw new BusinessCardConflictException("名片已由不同請求確認");
        }
        if (intake.getStatus() != BusinessCardStatus.REVIEW_PENDING) throw new BusinessCardConflictException("名片目前不可確認");
        AppUser owner = users.findByUsername(principal.username()).orElseThrow(() -> new AccessDeniedException("找不到登入帳號"));
        Customer customer = resolveCustomer(command, principal, owner);
        Contact contact = contacts.save(new Contact(customer, command.contactName(), command.contactTitle(), command.contactEmail()));
        Opportunity opportunity = new Opportunity(customer, command.opportunityName(), OpportunityStage.QUALIFICATION,
                command.opportunityAmount(), command.expectedCloseDate(), OpportunityType.NEW_BUSINESS,
                LeadSource.REFERRAL, 10);
        opportunity.assignOwner(owner); opportunity = opportunities.save(opportunity);
        LocalDateTime callAt = command.callAt();
        CrmTask task = tasks.save(new CrmTask(customer, opportunity, contact, CrmTaskType.PHONE_CALL,
                CrmTaskPriority.NORMAL, "名片聯絡：" + contact.getName(), "安排電話訪問", owner,
                callAt, callAt.plusMinutes(30), CrmTaskSource.BUSINESS_CARD));
        intake.confirm(principal.username(), key, hash, customer.getId(), contact.getId(), opportunity.getId(), task.getId());
        intakes.save(intake); intake.getMedia().transition(MediaStatus.CONFIRMED);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 僅在資料庫 commit 成功後刪除名片原圖。 */
            @Override public void afterCommit() { mediaService.deleteConfirmed(intake.getMedia()); }
        });
        return confirmedResponse(intake);
    }

    /** 依人工選擇建立新客戶或合併既有客戶；候選永不自動合併。 */
    private Customer resolveCustomer(CanonicalConfirmCommand request, JwtService.AuthPrincipal principal, AppUser owner) {
        String action = request.customerAction();
        if ("CREATE".equals(action)) {
            Customer customer = new Customer(request.customerName(), request.customerEmail(), request.customerPhone(),
                    request.taxId(), request.industry(), owner.getDisplayName());
            customer.assignOwner(owner); return customers.save(customer);
        }
        if (!"MERGE".equals(action) || request.customerId() == null) throw new IllegalArgumentException("必須明確選擇 CREATE 或 MERGE 與 customerId");
        Customer customer = customers.findById(request.customerId()).orElseThrow(() -> new EntityNotFoundException("合併客戶不存在"));
        assertCustomerVisible(customer, principal); return customer;
    }

    /** Email exact、電話數字正規化與公司名稱模糊比對，僅回候選理由。 */
    private List<Dtos.BusinessCardDuplicateCandidate> duplicateCandidates(Dtos.RecognizedBusinessCard card, JwtService.AuthPrincipal principal) {
        String email = lower(card.email()), phone = digits(card.phone()), company = normalizeName(card.companyName());
        List<Dtos.BusinessCardDuplicateCandidate> result = new ArrayList<>();
        for (Customer customer : customers.findAll()) {
            if (!isCustomerVisible(customer, principal)) continue;
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            if (!email.isBlank() && (email.equals(lower(customer.getEmail())) || customer.getContacts().stream().anyMatch(c -> email.equals(lower(c.getEmail()))))) reasons.add("EMAIL_EXACT");
            if (!phone.isBlank() && phone.equals(digits(customer.getPhone()))) reasons.add("PHONE_NORMALIZED");
            String existing = normalizeName(customer.getName());
            if (!company.isBlank() && !existing.isBlank() && (company.contains(existing) || existing.contains(company) || similarity(company, existing) >= .72)) reasons.add("COMPANY_FUZZY");
            if (!reasons.isEmpty()) result.add(new Dtos.BusinessCardDuplicateCandidate(customer.getId(), customer.getName(), List.copyOf(reasons)));
        }
        return result;
    }

    /** 必要欄位不足視為不可解析，正式 CRM 不寫入。 */
    private void validateRecognized(Dtos.RecognizedBusinessCard card) {
        if (card == null || card.personName() == null || card.personName().isBlank() || card.companyName() == null || card.companyName().isBlank())
            throw new VisionServiceException("Vision 結構缺少必要欄位");
    }

    /** 驗證 intake owner scope；管理角色沿用全域可見。 */
    private BusinessCardIntake visible(BusinessCardIntake intake, JwtService.AuthPrincipal principal) {
        if (principal.role() == Role.SALES && !intake.getCreatorUsername().equals(principal.username())) throw new EntityNotFoundException("名片辨識工作不存在");
        return intake;
    }
    /** 驗證既有客戶 owner scope。 */
    private void assertCustomerVisible(Customer customer, JwtService.AuthPrincipal principal) { if (!isCustomerVisible(customer, principal)) throw new EntityNotFoundException("合併客戶不存在"); }
    /** 判斷客戶是否可見。 */
    private boolean isCustomerVisible(Customer customer, JwtService.AuthPrincipal principal) { return principal.role()!=Role.SALES || customer.getOwner()!=null && principal.username().equals(customer.getOwner().getUsername()); }

    /** 將 entity JSON 草稿轉為 API DTO。 */
    private Dtos.BusinessCardIntakeResponse response(BusinessCardIntake intake) {
        return new Dtos.BusinessCardIntakeResponse(intake.getId(), intake.getStatus().name(), intake.getMedia().getId(),
                read(intake.getRecognizedJson(), Dtos.RecognizedBusinessCard.class),
                readList(intake.getDuplicateCandidatesJson()), intake.getErrorSummary());
    }
    /** 組裝穩定冪等確認回應。 */
    private Dtos.BusinessCardConfirmResponse confirmedResponse(BusinessCardIntake i) { return new Dtos.BusinessCardConfirmResponse(i.getId(),i.getCustomerId(),i.getContactId(),i.getOpportunityId(),i.getTaskId()); }
    /** JSON 序列化失敗視為程式狀態錯誤。 */
    private String write(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("JSON 序列化失敗",e);} }
    /** JSON 物件解析。 */
    private <T>T read(String json,Class<T> type){if(json==null)return null;try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException("JSON 解析失敗",e);}}
    /** JSON 候選清單解析。 */
    private List<Dtos.BusinessCardDuplicateCandidate> readList(String json){if(json==null)return List.of();try{return mapper.readValue(json,new TypeReference<List<Dtos.BusinessCardDuplicateCandidate>>(){});}catch(Exception e){throw new IllegalStateException("JSON 解析失敗",e);}}
    /** SHA-256 保護冪等 payload 比對。 */
    private String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    /** Email 正規化。 */ private String lower(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
    /** 電話只保留數字，並將台灣國碼 886 正規化為國內 0 前綴。 */
    private String digits(String s){String value=s==null?"":s.replaceAll("\\D","");return value.startsWith("886")?"0"+value.substring(3):value;}
    /** 公司名稱移除常見符號與空白後比對。 */ private String normalizeName(String s){return lower(s).replaceAll("[\\p{Punct}\\s]","");}
    /** 以 bigram Dice 係數提供可重現的模糊公司候選。 */
    private double similarity(String a,String b){if(a.equals(b))return 1;Set<String>x=bigrams(a),y=bigrams(b);if(x.isEmpty()||y.isEmpty())return 0;Set<String>i=new HashSet<>(x);i.retainAll(y);return 2d*i.size()/(x.size()+y.size());}
    /** 產生字串 bigram 集合。 */ private Set<String>bigrams(String s){Set<String>r=new HashSet<>();for(int i=0;i<s.length()-1;i++)r.add(s.substring(i,i+2));return r;}
}
