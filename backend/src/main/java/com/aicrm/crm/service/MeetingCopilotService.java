package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.*;
import com.aicrm.crm.service.media.TemporaryMediaService;
import com.aicrm.crm.service.meeting.MeetingCopilotConflictException;
import com.aicrm.crm.service.meeting.MeetingCopilotUnavailableException;
import com.aicrm.crm.service.transcription.Transcript;
import com.aicrm.crm.service.transcription.TranscriptionClient;
import com.aicrm.crm.service.vision.AiModelAssignment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/** AI 會議 Copilot：音訊轉錄、結構化草稿與人工選擇性確認建檔服務。 */
@Service
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class MeetingCopilotService {
    private final MeetingCopilotSessionRepository sessions; private final TemporaryMediaService mediaService;
    private final TranscriptionClient transcription; private final SystemSettingService settings;
    private final CustomerRepository customers; private final OpportunityRepository opportunities;
    private final CrmTaskRepository tasks; private final InteractionRepository interactions;
    private final AppUserRepository users; private final ObjectMapper mapper; private final EntityManager entityManager;

    /** 注入所有持久化與外部服務邊界。 */
    public MeetingCopilotService(MeetingCopilotSessionRepository sessions, TemporaryMediaService mediaService,
            TranscriptionClient transcription, SystemSettingService settings, CustomerRepository customers,
            OpportunityRepository opportunities, CrmTaskRepository tasks, InteractionRepository interactions,
            AppUserRepository users, ObjectMapper mapper, EntityManager entityManager) {
        this.sessions = sessions; this.mediaService = mediaService; this.transcription = transcription;
        this.settings = settings; this.customers = customers; this.opportunities = opportunities;
        this.tasks = tasks; this.interactions = interactions; this.users = users; this.mapper = mapper;
        this.entityManager = entityManager;
    }

    /** 驗證轉錄 assignment 後暫存音訊、轉錄並生成結構化草稿。 */
    @Transactional
    public Dtos.MeetingCopilotSessionResponse create(MultipartFile file, Long customerId, Long opportunityId,
            JwtService.AuthPrincipal principal) {
        AiModelAssignment assignment = settings.resolveTranscriptionAssignment();
        if (assignment == null) throw new MeetingCopilotUnavailableException("尚未設定支援語音轉錄的模型");
        Customer customer = visibleCustomer(customerId, principal);
        Opportunity opportunity = resolveOpportunity(opportunityId, customer, principal);
        TemporaryMedia media = mediaService.stage(file, MediaPurpose.MEETING_AUDIO, principal);
        media.transition(MediaStatus.PROCESSING);
        MeetingCopilotSession session = sessions.save(new MeetingCopilotSession(media, principal.username(),
                customer, opportunity, assignment.model(), assignment.providerId()));
        session.startProcessing();
        try {
            Transcript transcript = transcription.transcribe(mediaService.read(media), media.getMimeType(), assignment);
            Dtos.MeetingDraft draft = buildDraft(transcript, customer, opportunity);
            session.review(transcript.text(), draft.summary(), write(draft));
            media.transition(MediaStatus.REVIEW_PENDING);
        } catch (RuntimeException exception) {
            session.fail("會議轉錄或草稿生成失敗"); media.transition(MediaStatus.FAILED);
        }
        return response(sessions.save(session));
    }

    /** 取得本人可見的 session，避免 SALES 水平越權。 */
    @Transactional(readOnly = true)
    public Dtos.MeetingCopilotSessionResponse get(Long id, JwtService.AuthPrincipal principal) {
        return response(visible(sessions.findById(id).orElseThrow(() -> new EntityNotFoundException("會議 Copilot session 不存在")), principal));
    }

    /** 在單一交易套用選定變更；DB commit 後刪音訊，transcript 保留。 */
    @Transactional
    public Dtos.MeetingCopilotConfirmResponse confirm(Long id, Dtos.ConfirmMeetingRequest request,
            JwtService.AuthPrincipal principal, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key 為必填");
        List<String> selected = canonicalIds(request == null ? null : request.selectedChangeIds());
        String key = idempotencyKey.trim(); String hash = sha256(write(selected));
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, principal.username() + ":" + key).getSingleResult();
        Optional<MeetingCopilotSession> prior = sessions.findByCreatorUsernameAndIdempotencyKey(principal.username(), key);
        if (prior.isPresent()) {
            if (hash.equals(prior.get().getIdempotencyPayloadHash())) return confirmedResponse(prior.get());
            throw new MeetingCopilotConflictException("Idempotency-Key 已由不同請求使用");
        }
        MeetingCopilotSession session = visible(sessions.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("會議 Copilot session 不存在")), principal);
        if (session.getStatus() == MeetingCopilotStatus.CONFIRMED) {
            if (key.equals(session.getIdempotencyKey()) && hash.equals(session.getIdempotencyPayloadHash())) return confirmedResponse(session);
            throw new MeetingCopilotConflictException("會議已由不同請求確認");
        }
        if (session.getStatus() != MeetingCopilotStatus.REVIEW_PENDING) throw new MeetingCopilotConflictException("會議目前不可確認");
        AppUser owner = users.findByUsername(principal.username()).orElseThrow(() -> new AccessDeniedException("找不到登入帳號"));
        Dtos.MeetingCopilotConfirmResponse result = applySelected(session, new LinkedHashSet<>(selected), owner);
        session.confirm(principal.username(), key, hash, write(result));
        sessions.save(session); session.getMedia().transition(MediaStatus.CONFIRMED);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 僅在資料庫 commit 成功後刪除會議音訊；transcript 已保留於 session。 */
            @Override public void afterCommit() { mediaService.deleteConfirmed(session.getMedia()); }
        });
        return result;
    }

    /** 僅套用被選定且存在於草稿的變更，未選項目不套用。 */
    private Dtos.MeetingCopilotConfirmResponse applySelected(MeetingCopilotSession session,
            Set<String> selected, AppUser owner) {
        Dtos.MeetingDraft draft = parseDraft(session.getDraftJson());
        List<String> applied = new ArrayList<>(); List<Long> taskIds = new ArrayList<>();
        Long interactionId = null; Long opportunityId = null; int stakeholders = 0;
        for (Dtos.MeetingChange change : draft.changes()) {
            if (!selected.contains(change.changeId())) continue;
            switch (change.type()) {
                case "INTERACTION" -> {
                    Interaction interaction = new Interaction(InteractionType.MEETING, LocalDateTime.now(), interactionContent(session));
                    interaction.attachCustomer(session.getCustomer());
                    interactionId = interactions.save(interaction).getId(); applied.add(change.changeId());
                }
                case "TASK" -> {
                    String title = change.detail().getOrDefault("title", change.description());
                    LocalDateTime start = LocalDateTime.now().plusDays(2);
                    CrmTask task = new CrmTask(session.getCustomer(), session.getOpportunity(), null, CrmTaskType.PHONE_CALL,
                            CrmTaskPriority.NORMAL, title, session.getSummary(), owner, start, start.plusMinutes(30), CrmTaskSource.MEETING_AI);
                    taskIds.add(tasks.save(task).getId()); applied.add(change.changeId());
                }
                case "OPPORTUNITY_PATCH" -> {
                    Opportunity opportunity = session.getOpportunity();
                    if (opportunity != null) {
                        opportunity.updateDetails(opportunity.getName(), opportunity.getAmount(),
                                parseDate(change.detail().get("expectedCloseDate")), opportunity.getType());
                        opportunityId = opportunities.save(opportunity).getId(); applied.add(change.changeId());
                    }
                }
                case "STAKEHOLDER_SUGGESTION" -> { stakeholders++; applied.add(change.changeId()); }
                default -> { /* 未知型別忽略，維持前後端相容 */ }
            }
        }
        return new Dtos.MeetingCopilotConfirmResponse(session.getId(), applied, interactionId, taskIds, opportunityId, stakeholders);
    }

    /** 以逐字稿為互動內容依據，並限制於 Interaction 欄位長度。 */
    private String interactionContent(MeetingCopilotSession session) {
        String summary = session.getSummary() == null ? "" : session.getSummary();
        String transcript = session.getTranscript() == null ? "" : session.getTranscript();
        String content = "【AI 會議摘要】" + summary + System.lineSeparator() + "【逐字稿】" + transcript;
        return content.length() <= 2000 ? content : content.substring(0, 2000);
    }

    /** 依逐字稿與情境 deterministic 產生結構化草稿；低信心 stakeholder 建議預設不選。 */
    private Dtos.MeetingDraft buildDraft(Transcript transcript, Customer customer, Opportunity opportunity) {
        String text = transcript == null || transcript.text() == null ? "" : transcript.text();
        String customerName = customer.getName() == null ? "" : customer.getName();
        String summary = "會議摘要：與客戶「" + customerName + "」的會議紀錄，逐字稿共 " + text.length() + " 字，"
                + "包含需求確認、後續跟進與關鍵人資訊。";
        List<Dtos.MeetingChange> changes = new ArrayList<>();
        changes.add(new Dtos.MeetingChange("interaction-1", "INTERACTION",
                "將本次會議逐字稿記錄為 MEETING 互動", false, true, Map.of()));
        changes.add(new Dtos.MeetingChange("task-1", "TASK",
                "安排會議後續跟進電話", false, true, Map.of("title", "會議後續跟進：" + customerName)));
        if (opportunity != null) {
            changes.add(new Dtos.MeetingChange("opportunity-patch-1", "OPPORTUNITY_PATCH",
                    "依會議進度更新商機預計成交日", false, true,
                    Map.of("expectedCloseDate", LocalDate.now().plusMonths(1).toString())));
        }
        changes.add(new Dtos.MeetingChange("stakeholder-1", "STAKEHOLDER_SUGGESTION",
                "建議新增會議中提及的關鍵決策者", true, false, Map.of("role", "決策者")));
        return new Dtos.MeetingDraft(summary, changes);
    }

    /** 載入並驗證客戶 owner scope。 */
    private Customer visibleCustomer(Long customerId, JwtService.AuthPrincipal principal) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new EntityNotFoundException("客戶不存在"));
        if (!isCustomerVisible(customer, principal)) throw new EntityNotFoundException("客戶不存在");
        return customer;
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

    /** 驗證 session owner scope；管理角色沿用全域可見。 */
    private MeetingCopilotSession visible(MeetingCopilotSession session, JwtService.AuthPrincipal principal) {
        if (principal.role() == Role.SALES && !session.getCreatorUsername().equals(principal.username())) {
            throw new EntityNotFoundException("會議 Copilot session 不存在");
        }
        return session;
    }

    /** 判斷客戶是否可見。 */
    private boolean isCustomerVisible(Customer customer, JwtService.AuthPrincipal principal) {
        return principal.role() != Role.SALES || customer.getOwner() != null
                && principal.username().equals(customer.getOwner().getUsername());
    }

    /** 組裝 session 查詢/建立回應。 */
    private Dtos.MeetingCopilotSessionResponse response(MeetingCopilotSession session) {
        return new Dtos.MeetingCopilotSessionResponse(session.getId(), session.getStatus().name(),
                session.getMedia().getId(), session.getCustomer().getId(),
                session.getOpportunity() == null ? null : session.getOpportunity().getId(),
                session.getTranscript(), session.getSummary(), readChanges(session.getDraftJson()), session.getErrorSummary());
    }

    /** 組裝穩定冪等確認回應（重送時以儲存結果還原）。 */
    private Dtos.MeetingCopilotConfirmResponse confirmedResponse(MeetingCopilotSession session) {
        try { return mapper.readValue(session.getConfirmResultJson(), Dtos.MeetingCopilotConfirmResponse.class); }
        catch (Exception e) { throw new IllegalStateException("確認結果 JSON 解析失敗", e); }
    }

    /** 正規化選定 changeId：去空白、去重、排序，作為穩定冪等 payload。 */
    private List<String> canonicalIds(List<String> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).map(String::trim).filter(id -> !id.isBlank())
                .distinct().sorted().toList();
    }

    /** 解析草稿 JSON。 */
    private Dtos.MeetingDraft parseDraft(String json) {
        if (json == null) return new Dtos.MeetingDraft(null, List.of());
        try { return mapper.readValue(json, Dtos.MeetingDraft.class); }
        catch (Exception e) { throw new IllegalStateException("會議草稿 JSON 解析失敗", e); }
    }

    /** 解析草稿變更清單。 */
    private List<Dtos.MeetingChange> readChanges(String json) {
        if (json == null) return List.of();
        return parseDraft(json).changes();
    }

    /** 解析日期字串；空值時以一個月後為預設。 */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now().plusMonths(1);
        try { return LocalDate.parse(value.trim()); } catch (RuntimeException e) { return LocalDate.now().plusMonths(1); }
    }

    /** JSON 序列化失敗視為程式狀態錯誤。 */
    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("JSON 序列化失敗", e); } }

    /** SHA-256 保護冪等 payload 比對。 */
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
