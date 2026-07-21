package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicrm.crm.domain.MediaPurpose;
import com.aicrm.crm.service.media.TemporaryMediaService;
import com.aicrm.crm.service.media.ValidatedMedia;
import com.aicrm.crm.service.transcription.Transcript;
import com.aicrm.crm.service.transcription.TranscriptionClient;
import com.aicrm.crm.service.vision.AiModelAssignment;
import com.aicrm.crm.service.vision.BusinessCardRecognitionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** OCR／語音轉錄用途級模型測試服務規則。 */
class AiPurposeModelTestServiceTest {
    private SystemSettingService settings;
    private TemporaryMediaService media;
    private BusinessCardRecognitionClient vision;
    private TranscriptionClient transcription;
    private AiPurposeModelTestService service;

    /** 建立不接觸外部服務與儲存體的 mock 邊界。 */
    @BeforeEach
    void setUp() {
        settings = mock(SystemSettingService.class);
        media = mock(TemporaryMediaService.class);
        vision = mock(BusinessCardRecognitionClient.class);
        transcription = mock(TranscriptionClient.class);
        service = new AiPurposeModelTestService(settings, media, vision, transcription);
    }

    /** OCR 測試應驗證圖片並呼叫目前 OCR assignment，不建立暫存媒體。 */
    @Test
    void testOcr_usesValidatedImageAndCurrentAssignment() {
        var file = new MockMultipartFile("file", "card.png", "image/png", new byte[]{1, 2, 3});
        var assignment = new AiModelAssignment("vision-model", 1L, "https://example.test", "secret");
        when(settings.resolveOcrAssignment()).thenReturn(assignment);
        when(media.validateOnly(file, MediaPurpose.BUSINESS_CARD))
                .thenReturn(new ValidatedMedia(new byte[]{9, 8, 7}, "image/png"));
        when(vision.recognize(any(), any(), any())).thenReturn(null);

        var result = service.testOcr(file);

        assertThat(result.success()).isTrue();
        assertThat(result.purpose()).isEqualTo("BUSINESS_CARD_OCR");
        assertThat(result.model()).isEqualTo("vision-model");
        verify(vision).recognize(new byte[]{9, 8, 7}, "image/png", assignment);
    }

    /** 語音測試應驗證音訊並回報逐字稿字數，不保存逐字稿內容。 */
    @Test
    void testTranscription_reportsLengthWithoutReturningTranscript() {
        var file = new MockMultipartFile("file", "meeting.wav", "audio/wav", new byte[]{1, 2, 3});
        var assignment = new AiModelAssignment("audio-model", 2L, "https://example.test", "secret");
        when(settings.resolveTranscriptionAssignment()).thenReturn(assignment);
        when(media.validateOnly(file, MediaPurpose.MEETING_AUDIO))
                .thenReturn(new ValidatedMedia(new byte[]{6, 5, 4}, "audio/wav"));
        when(transcription.transcribe(any(), any(), any())).thenReturn(new Transcript("測試逐字稿"));

        var result = service.testTranscription(file);

        assertThat(result.success()).isTrue();
        assertThat(result.purpose()).isEqualTo("MEETING_TRANSCRIPTION");
        assertThat(result.summary()).contains("5");
        assertThat(result.summary()).doesNotContain("測試逐字稿");
    }

    /** 未設定相容 assignment 時必須 fail closed。 */
    @Test
    void testOcr_withoutAssignment_isUnavailable() {
        var file = new MockMultipartFile("file", "card.png", "image/png", new byte[]{1});
        when(settings.resolveOcrAssignment()).thenReturn(null);

        assertThatThrownBy(() -> service.testOcr(file))
                .isInstanceOf(AiPurposeModelUnavailableException.class);
    }
}
