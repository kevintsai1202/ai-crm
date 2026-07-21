package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiProvider;
import com.aicrm.crm.domain.CapabilitySource;
import com.aicrm.crm.domain.ModelCapability;
import com.aicrm.crm.domain.SystemSetting;
import com.aicrm.crm.repository.AiProviderRepository;
import com.aicrm.crm.repository.SystemSettingRepository;
import com.aicrm.crm.service.ai.ModelCatalogClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 模型能力與用途 assignment 的服務層規則測試。
 */
class SystemSettingModelCapabilityTest {

    /** 模擬持久化的系統設定，讓受測服務走真實序列化與驗證流程。 */
    private final Map<String, SystemSetting> storedSettings = new HashMap<>();
    private SystemSettingService settings;
    private AiProviderRepository providerRepository;

    /** 建立使用記憶體 map 的 repository mock。 */
    @BeforeEach
    void setUp() {
        var repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedSettings.get(invocation.getArgument(0, String.class))));
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> {
            var setting = invocation.getArgument(0, SystemSetting.class);
            storedSettings.put(setting.getSettingKey(), setting);
            return setting;
        });
        providerRepository = mock(AiProviderRepository.class);
        when(providerRepository.existsById(anyLong())).thenReturn(true);
        settings = new SystemSettingService(repository, providerRepository,
                mock(ModelCatalogClient.class), new ObjectMapper(), "", "", "", "", "");
    }

    /** OCR assignment 不得接受缺少 VISION 能力的模型。 */
    @Test
    void ocrAssignment_rejectsModelWithoutVisionCapability() {
        var textOnly = new Dtos.ModelOptionItem(
                "text-only", 7L, Set.of(), CapabilitySource.MANUAL);
        settings.updateAiSettings("text-only", 7L, List.of(textOnly), null, null, null, "admin");

        assertThatThrownBy(() -> settings.updateAssignments(
                new Dtos.AiModelAssignments("text-only", 7L, "text-only", 7L, "", null), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VISION");
    }

    /** Transcription assignment 可接受具 AUDIO_TRANSCRIPTION 能力的模型。 */
    @Test
    void transcriptionAssignment_acceptsAudioTranscriptionModel() {
        var audio = new Dtos.ModelOptionItem(
                "whisper-1", 9L, Set.of(ModelCapability.AUDIO_TRANSCRIPTION), CapabilitySource.MANUAL);
        settings.updateAiSettings("", null, List.of(audio), null, null, null, "admin");
        settings.updateModelCapabilities(
                "whisper-1", 9L, Set.of(ModelCapability.AUDIO_TRANSCRIPTION), "admin");

        settings.updateAssignments(
                new Dtos.AiModelAssignments("", null, "", null, "whisper-1", 9L), "admin");

        assertThat(settings.getAiSettingsView().transcriptionModel()).isEqualTo("whisper-1");
        assertThat(settings.getAiSettingsView().transcriptionProviderId()).isEqualTo(9L);
    }

    /** 同名模型分屬不同 provider 時，能力驗證必須以 model 與 providerId 成對比對。 */
    @Test
    void assignment_matchesModelAndProviderTogether() {
        var vision = new Dtos.ModelOptionItem(
                "shared-name", 11L, Set.of(ModelCapability.VISION), CapabilitySource.AUTO);
        var textOnly = new Dtos.ModelOptionItem(
                "shared-name", 12L, Set.of(), CapabilitySource.UNKNOWN);
        settings.updateAiSettings("", null, List.of(vision, textOnly), null, null, null, "admin");
        settings.updateModelCapabilities("shared-name", 11L, Set.of(ModelCapability.VISION), "admin");

        assertThatThrownBy(() -> settings.updateAssignments(
                new Dtos.AiModelAssignments("", null, "shared-name", 12L, "", null), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VISION");
    }

    /** 舊設定 API 不得讓外部 payload 偽造 AUTO 能力。 */
    @Test
    void updateAiSettings_downgradesUntrustedAutoCapabilities() {
        var forged = new Dtos.ModelOptionItem(
                "forged-auto", 21L, Set.of(ModelCapability.VISION), CapabilitySource.AUTO);

        settings.updateAiSettings("forged-auto", 21L, List.of(forged), null, null, null, "admin");

        assertThat(settings.getModelOptions()).singleElement().satisfies(option -> {
            assertThat(option.capabilities()).isEmpty();
            assertThat(option.capabilitySource()).isEqualTo(CapabilitySource.UNKNOWN);
        });
    }

    /** 舊設定 API 更新候選清單時必須保留伺服器既有的可信能力。 */
    @Test
    void updateAiSettings_preservesExistingTrustedCapabilities() {
        storedSettings.put(SystemSettingService.KEY_AI_CHAT_MODEL_OPTIONS, new SystemSetting(
                SystemSettingService.KEY_AI_CHAT_MODEL_OPTIONS,
                "[{\"model\":\"trusted\",\"providerId\":22,\"capabilities\":[\"VISION\"],"
                        + "\"capabilitySource\":\"AUTO\"}]", "server"));
        var attemptedOverwrite = new Dtos.ModelOptionItem(
                "trusted", 22L, Set.of(), CapabilitySource.UNKNOWN);

        settings.updateAiSettings("trusted", 22L, List.of(attemptedOverwrite), null, null, null, "admin");

        assertThat(settings.getModelOptions()).singleElement().satisfies(option -> {
            assertThat(option.capabilities()).containsExactly(ModelCapability.VISION);
            assertThat(option.capabilitySource()).isEqualTo(CapabilitySource.AUTO);
        });
    }

    /** 舊設定 API 的 chat model/provider 必須以 pair 驗證，不能只比模型名稱。 */
    @Test
    void updateAiSettings_rejectsChatModelProviderMismatch() {
        var candidate = new Dtos.ModelOptionItem(
                "same-name", 31L, Set.of(), CapabilitySource.UNKNOWN);

        assertThatThrownBy(() -> settings.updateAiSettings(
                "same-name", 32L, List.of(candidate), null, null, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("候選清單");
    }

    /** 人工能力設定不得接受已刪除或不存在的 Provider。 */
    @Test
    void updateModelCapabilities_rejectsMissingProvider() {
        var candidate = new Dtos.ModelOptionItem(
                "stale", 999L, Set.of(), CapabilitySource.UNKNOWN);
        settings.updateAiSettings("", null, List.of(candidate), null, null, null, "admin");
        when(providerRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> settings.updateModelCapabilities(
                "stale", 999L, Set.of(ModelCapability.VISION), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider");
    }

    /** Assignment 不得接受已刪除或不存在的 Provider。 */
    @Test
    void updateAssignments_rejectsMissingProvider() {
        var candidate = new Dtos.ModelOptionItem(
                "stale-vision", 998L, Set.of(ModelCapability.VISION), CapabilitySource.MANUAL);
        settings.updateAiSettings("", null, List.of(candidate), null, null, null, "admin");
        when(providerRepository.existsById(998L)).thenReturn(false);

        assertThatThrownBy(() -> settings.updateAssignments(
                new Dtos.AiModelAssignments("", null, "stale-vision", 998L, "", null), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider");
    }

    /** Null assignment request 必須轉成可預期的 400 邊界錯誤，而非 NPE。 */
    @Test
    void updateAssignments_rejectsNullRequest() {
        assertThatThrownBy(() -> settings.updateAssignments(null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignment");
    }

    /** DB 未指派時，OCR 與轉錄應從各自的部署預設解析，且不得借用 Chat 預設。 */
    @Test
    void mediaAssignments_fallBackToPurposeSpecificEnvironmentDefaults() {
        var repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(any())).thenReturn(Optional.empty());
        var provider = mock(AiProvider.class);
        when(provider.getId()).thenReturn(77L);
        when(provider.getBaseUrl()).thenReturn("https://api.example.test/v1");
        when(provider.getApiKey()).thenReturn("secret");
        when(provider.isApiKeySet()).thenReturn(true);
        when(providerRepository.findByName("Environment Provider")).thenReturn(Optional.of(provider));
        settings = new SystemSettingService(repository, providerRepository,
                mock(ModelCatalogClient.class), new ObjectMapper(), "chat-default",
                "Environment Provider", "vision-default",
                "Environment Provider", "audio-default");
        storedSettings.clear();
        when(repository.findBySettingKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedSettings.get(invocation.getArgument(0, String.class))));
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> {
            var setting = invocation.getArgument(0, SystemSetting.class);
            storedSettings.put(setting.getSettingKey(), setting);
            return setting;
        });
        settings.updateAiSettings("", null, List.of(
                new Dtos.ModelOptionItem("vision-default", 77L,
                        Set.of(ModelCapability.VISION), CapabilitySource.MANUAL),
                new Dtos.ModelOptionItem("audio-default", 77L,
                        Set.of(ModelCapability.AUDIO_TRANSCRIPTION), CapabilitySource.MANUAL)
        ), null, null, null, "admin");
        settings.updateModelCapabilities("vision-default", 77L,
                Set.of(ModelCapability.VISION), "admin");
        settings.updateModelCapabilities("audio-default", 77L,
                Set.of(ModelCapability.AUDIO_TRANSCRIPTION), "admin");

        assertThat(settings.resolveOcrAssignment().model()).isEqualTo("vision-default");
        assertThat(settings.resolveTranscriptionAssignment().model()).isEqualTo("audio-default");
        assertThat(settings.getAiSettingsView().ocrSource()).isEqualTo("ENV");
        assertThat(settings.getAiSettingsView().transcriptionSource()).isEqualTo("ENV");
    }

    /** 部署預設未通過 Provider、模型目錄及能力驗證時，不得宣稱已生效。 */
    @Test
    void mediaAssignmentSource_isUnsetWhenEnvironmentDefaultIsNotUsable() {
        var repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(any())).thenReturn(Optional.empty());
        settings = new SystemSettingService(repository, providerRepository,
                mock(ModelCatalogClient.class), new ObjectMapper(), "chat-default",
                "Missing Provider", "vision-default", "", "");

        assertThat(settings.getAiSettingsView().ocrSource()).isEqualTo("UNSET");
        assertThat(settings.resolveOcrAssignment()).isNull();
    }
}
