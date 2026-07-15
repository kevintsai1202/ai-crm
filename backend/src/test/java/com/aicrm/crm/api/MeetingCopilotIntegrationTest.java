package com.aicrm.crm.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.AiProviderRepository;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.SystemSettingService;
import com.aicrm.crm.service.media.*;
import com.aicrm.crm.service.transcription.Transcript;
import com.aicrm.crm.service.transcription.TranscriptionClient;
import com.aicrm.crm.support.PostgresTestBase;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 會議 Copilot API 的音訊上傳、轉錄 assignment、水平權限與冪等確認整合測試。 */
@org.springframework.boot.test.context.SpringBootTest(properties = {"app.media.enabled=true", "app.media.s3.endpoint=http://unused", "app.media.s3.access-key=x", "app.media.s3.secret-key=x"})
class MeetingCopilotIntegrationTest extends PostgresTestBase {
    @Autowired WebApplicationContext context; @Autowired FilterChainProxy filters;
    @Autowired SystemSettingService settings; @Autowired AiProviderRepository providers; @Autowired AppUserRepository users;
    @Autowired CustomerRepository customers;
    @MockitoBean TemporaryMediaStore store; @MockitoBean TranscriptionClient transcription;
    private MockMvc mvc; private final ObjectMapper mapper = new ObjectMapper(); private byte[] wav; private Long ownerCustomerId;

    /** 建立真實 security MockMvc、Transcription assignment、fake 轉錄與屬於業務的客戶。 */
    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        wav = pcmWav();
        var provider = providers.findAll().stream().filter(p -> p.getName().equals("MeetingTest")).findFirst()
                .orElseGet(() -> providers.save(new AiProvider("MeetingTest", "http://unused", "secret", "test")));
        var option = new Dtos.ModelOptionItem("transcribe-test", provider.getId(), Set.of(), CapabilitySource.UNKNOWN);
        settings.updateAiSettings("", null, List.of(option), null, null, null, "test");
        settings.updateModelCapabilities("transcribe-test", provider.getId(), Set.of(ModelCapability.AUDIO_TRANSCRIPTION), "test");
        settings.updateAssignments(new Dtos.AiModelAssignments("", null, "", null, "transcribe-test", provider.getId()), "test");
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = new Customer("會議測試客戶", "meet@example.com", "0912000222", "99887766", "科技", "x");
        customer.assignOwner(owner); ownerCustomerId = customers.save(customer).getId();
        when(store.put(any())).thenAnswer(inv -> { MediaUpload u = inv.getArgument(0); return new StoredMedia("temporary/" + UUID.randomUUID(), u.bytes().length, "0".repeat(64)); });
        when(store.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(wav));
        when(transcription.transcribe(any(), anyString(), any())).thenReturn(new Transcript("客戶關注導入時程與預算，會後需寄送報價。"));
    }

    /** 未設定 transcription assignment 時建立必須回 503，且不觸發轉錄。 */
    @Test void createWithoutTranscriptionAssignment_returnsServiceUnavailable() throws Exception {
        settings.updateAssignments(new Dtos.AiModelAssignments("", null, "", null, "", null), "test");
        String token = login("sales@aurora.local");
        mvc.perform(multipart("/api/meeting-copilot/sessions").file(audio()).param("customerId", ownerCustomerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(transcription);
    }

    /** 有效音訊建立 REVIEW_PENDING session；其他 SALES 讀取同 id 依防枚舉慣例回 404。 */
    @Test void uploadThenCrossOwnerGet_returnsCreatedAndNotFound() throws Exception {
        String otherUsername = users.findByRole(Role.SALES).stream().map(AppUser::getUsername).filter(u -> !u.equals("sales@aurora.local")).findFirst().orElseThrow();
        String ownerToken = login("sales@aurora.local"), otherToken = login(otherUsername);
        String body = mvc.perform(multipart("/api/meeting-copilot/sessions").file(audio()).param("customerId", ownerCustomerId.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REVIEW_PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        mvc.perform(get("/api/meeting-copilot/sessions/{id}", id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
    }

    /** 確認只套用選定變更、commit 後保留 transcript，並以相同 Idempotency-Key 回原結果、不同 payload 回 409。 */
    @Test void confirmAppliesSelectedRetainsTranscriptAndIsIdempotent() throws Exception {
        String token = login("sales@aurora.local");
        String createBody = mvc.perform(multipart("/api/meeting-copilot/sessions").file(audio()).param("customerId", ownerCustomerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var created = mapper.readTree(createBody); long id = created.get("id").asLong();
        // 低信心 stakeholder 建議預設未選
        boolean stakeholderUnselected = false; String interactionChangeId = null;
        for (var change : created.get("changes")) {
            if (change.get("type").asText().equals("STAKEHOLDER_SUGGESTION")) stakeholderUnselected = !change.get("selectedByDefault").asBoolean();
            if (change.get("type").asText().equals("INTERACTION")) interactionChangeId = change.get("changeId").asText();
        }
        org.assertj.core.api.Assertions.assertThat(stakeholderUnselected).isTrue();
        org.assertj.core.api.Assertions.assertThat(interactionChangeId).isNotNull();

        String confirmPayload = "{\"selectedChangeIds\":[\"" + interactionChangeId + "\"]}";
        String confirmBody = mvc.perform(post("/api/meeting-copilot/sessions/{id}/confirm", id).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "meet-key-1").contentType(MediaType.APPLICATION_JSON).content(confirmPayload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(confirmBody).get("interactionId").isNull()).isFalse();

        // 確認後 transcript 仍保留
        mvc.perform(get("/api/meeting-copilot/sessions/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.transcript").value("客戶關注導入時程與預算，會後需寄送報價。"));

        // 相同 key + payload 回原結果
        mvc.perform(post("/api/meeting-copilot/sessions/{id}/confirm", id).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "meet-key-1").contentType(MediaType.APPLICATION_JSON).content(confirmPayload))
                .andExpect(status().isOk());
        // 相同 key + 不同 payload 回 409
        mvc.perform(post("/api/meeting-copilot/sessions/{id}/confirm", id).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "meet-key-1").contentType(MediaType.APPLICATION_JSON).content("{\"selectedChangeIds\":[]}"))
                .andExpect(status().isConflict());
    }

    /** 產生固定 multipart 音訊檔。 */
    private MockMultipartFile audio() { return new MockMultipartFile("file", "meeting.wav", "audio/wav", wav); }

    /** 產生 jaudiotagger 可解析的最小 1 秒 44.1kHz 16-bit 單聲道 PCM WAV。 */
    private byte[] pcmWav() {
        int sampleRate = 44100, bits = 16, channels = 1, samples = sampleRate; // 1 秒
        int dataSize = samples * channels * bits / 8;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()); buffer.putInt(36 + dataSize); buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes()); buffer.putInt(16); buffer.putShort((short) 1); buffer.putShort((short) channels);
        buffer.putInt(sampleRate); buffer.putInt(sampleRate * channels * bits / 8); buffer.putShort((short) (channels * bits / 8)); buffer.putShort((short) bits);
        buffer.put("data".getBytes()); buffer.putInt(dataSize);
        for (int i = 0; i < samples; i++) buffer.putShort((short) (Math.sin(i * 0.05) * 1000)); // 非靜音樣本
        return buffer.array();
    }

    /** 登入 seed 使用者並取得 JWT。 */
    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }
}
