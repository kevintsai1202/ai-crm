package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import jakarta.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/** 會議 Copilot 轉錄、草稿、選擇性套用與冪等規則測試。 */
class MeetingCopilotServiceTest {
    private final MeetingCopilotSessionRepository sessions = mock(MeetingCopilotSessionRepository.class);
    private final TemporaryMediaService media = mock(TemporaryMediaService.class);
    private final TranscriptionClient transcription = mock(TranscriptionClient.class);
    private final SystemSettingService settings = mock(SystemSettingService.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OpportunityRepository opportunities = mock(OpportunityRepository.class);
    private final CrmTaskRepository tasks = mock(CrmTaskRepository.class);
    private final InteractionRepository interactions = mock(InteractionRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query advisoryQuery = mock(Query.class);
    private final JwtService.AuthPrincipal principal = new JwtService.AuthPrincipal("sales@example.com", "業務", Role.SALES);
    private MeetingCopilotService service;

    /** 每案建立乾淨 service 並準備 advisory lock stub。 */
    @BeforeEach void setUp() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryQuery);
        when(advisoryQuery.setParameter(anyInt(), any())).thenReturn(advisoryQuery);
        when(advisoryQuery.getSingleResult()).thenReturn(1L);
        service = new MeetingCopilotService(sessions, media, transcription, settings, customers,
                opportunities, tasks, interactions, users, mapper, entityManager);
    }

    /** 無 audio transcription assignment 時建立必須回不可用（對應 503），且不暫存或轉錄。 */
    @Test void create_withoutTranscriptionAssignment_isUnavailable() {
        when(settings.resolveTranscriptionAssignment()).thenReturn(null);
        assertThatThrownBy(() -> service.create(mock(MultipartFile.class), 1L, null, principal))
                .isInstanceOf(MeetingCopilotUnavailableException.class);
        verifyNoInteractions(media, transcription);
    }

    /** 轉錄後產生結構化草稿；低信心 stakeholder 建議預設未選，互動預設已選。 */
    @Test void create_buildsDraft_lowConfidenceStakeholderUnselected() {
        var assignment = new AiModelAssignment("whisper", 1L, "http://provider", "secret");
        when(settings.resolveTranscriptionAssignment()).thenReturn(assignment);
        Customer customer = mock(Customer.class); when(customer.getId()).thenReturn(4L);
        AppUser owner = mock(AppUser.class); when(owner.getUsername()).thenReturn(principal.username());
        when(customer.getOwner()).thenReturn(owner); when(customers.findById(4L)).thenReturn(Optional.of(customer));
        TemporaryMedia staged = mock(TemporaryMedia.class); when(staged.getMimeType()).thenReturn("audio/wav"); when(staged.getId()).thenReturn(9L);
        when(media.stage(any(), eq(MediaPurpose.MEETING_AUDIO), eq(principal))).thenReturn(staged);
        when(media.read(staged)).thenReturn(new byte[]{1});
        when(transcription.transcribe(any(), eq("audio/wav"), eq(assignment))).thenReturn(new Transcript("客戶關注導入時程與預算。"));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(mock(MultipartFile.class), 4L, null, principal);

        assertThat(result.status()).isEqualTo(MeetingCopilotStatus.REVIEW_PENDING.name());
        assertThat(result.transcript()).contains("客戶關注導入時程與預算");
        var stakeholder = result.changes().stream().filter(c -> c.type().equals("STAKEHOLDER_SUGGESTION")).findFirst().orElseThrow();
        assertThat(stakeholder.lowConfidence()).isTrue();
        assertThat(stakeholder.selectedByDefault()).isFalse();
        var interaction = result.changes().stream().filter(c -> c.type().equals("INTERACTION")).findFirst().orElseThrow();
        assertThat(interaction.selectedByDefault()).isTrue();
    }

    /** confirm 只套用 selectedChangeIds：只選互動時建立 Interaction，不建立 Task；commit 後刪音訊。 */
    @Test void confirm_appliesOnlySelectedChanges() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            String draftJson = mapper.writeValueAsString(draft());
            MeetingCopilotSession session = mock(MeetingCopilotSession.class); TemporaryMedia staged = mock(TemporaryMedia.class);
            when(session.getId()).thenReturn(5L); when(session.getCreatorUsername()).thenReturn(principal.username());
            when(session.getStatus()).thenReturn(MeetingCopilotStatus.REVIEW_PENDING); when(session.getDraftJson()).thenReturn(draftJson);
            when(session.getTranscript()).thenReturn("會議逐字稿內容"); when(session.getMedia()).thenReturn(staged);
            Customer customer = mock(Customer.class); when(session.getCustomer()).thenReturn(customer); when(session.getOpportunity()).thenReturn(null);
            when(sessions.findByIdForUpdate(5L)).thenReturn(Optional.of(session));
            AppUser owner = mock(AppUser.class); when(owner.getUsername()).thenReturn(principal.username()); when(users.findByUsername(principal.username())).thenReturn(Optional.of(owner));
            Interaction interaction = mock(Interaction.class); when(interaction.getId()).thenReturn(20L); when(interactions.save(any())).thenReturn(interaction);

            var result = service.confirm(5L, new Dtos.ConfirmMeetingRequest(List.of("interaction-1")), principal, "key-1");

            assertThat(result.appliedChangeIds()).containsExactly("interaction-1");
            assertThat(result.interactionId()).isEqualTo(20L);
            assertThat(result.taskIds()).isEmpty();
            verify(interactions, times(1)).save(any(Interaction.class));
            verify(tasks, never()).save(any());
            verify(media, never()).deleteConfirmed(any());
            var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCommit();
            verify(media).deleteConfirmed(staged);
        } catch (Exception e) { throw new RuntimeException(e); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 同 key 同 payload replay 原結果；同 key 不同 payload 必須衝突且零寫入。 */
    @Test void confirm_idempotencyReplayAndConflict() {
        var stored = new Dtos.MeetingCopilotConfirmResponse(5L, List.of("interaction-1"), 20L, List.of(), null, 0);
        MeetingCopilotSession session = mock(MeetingCopilotSession.class);
        when(session.getIdempotencyPayloadHash()).thenReturn(hash(write(List.of("interaction-1"))));
        when(session.getConfirmResultJson()).thenReturn(write(stored));
        when(sessions.findByCreatorUsernameAndIdempotencyKey(principal.username(), "key-1")).thenReturn(Optional.of(session));

        assertThat(service.confirm(5L, new Dtos.ConfirmMeetingRequest(List.of("interaction-1")), principal, "key-1").interactionId()).isEqualTo(20L);
        assertThatThrownBy(() -> service.confirm(5L, new Dtos.ConfirmMeetingRequest(List.of("task-1")), principal, "key-1"))
                .isInstanceOf(MeetingCopilotConflictException.class);
        verifyNoInteractions(interactions, tasks);
    }

    /** 建立測試用草稿：互動、任務與低信心 stakeholder 建議。 */
    private Dtos.MeetingDraft draft() {
        return new Dtos.MeetingDraft("會議摘要", List.of(
                new Dtos.MeetingChange("interaction-1", "INTERACTION", "記錄會議互動", false, true, Map.of()),
                new Dtos.MeetingChange("task-1", "TASK", "安排跟進電話", false, true, Map.of("title", "會議後續跟進")),
                new Dtos.MeetingChange("stakeholder-1", "STAKEHOLDER_SUGGESTION", "建議關鍵決策者", true, false, Map.of("name", "陳經理"))));
    }
    /** JSON 序列化。 */ private String write(Object o) { try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new RuntimeException(e); } }
    /** SHA-256。 */ private String hash(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new RuntimeException(e); } }
}
