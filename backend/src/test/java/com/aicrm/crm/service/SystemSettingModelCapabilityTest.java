package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aicrm.crm.api.Dtos;
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
        settings = new SystemSettingService(repository, mock(AiProviderRepository.class),
                mock(ModelCatalogClient.class), new ObjectMapper(), "");
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

        assertThatThrownBy(() -> settings.updateAssignments(
                new Dtos.AiModelAssignments("", null, "shared-name", 12L, "", null), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VISION");
    }
}
