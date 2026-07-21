package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.MediaPurpose;
import com.aicrm.crm.service.media.TemporaryMediaService;
import com.aicrm.crm.service.transcription.TranscriptionClient;
import com.aicrm.crm.service.vision.BusinessCardRecognitionClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 以實際上傳樣本驗證目前 OCR／語音轉錄 assignment 的用途級測試服務。 */
@Service
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class AiPurposeModelTestService {
    /** 用途 assignment 解析服務。 */
    private final SystemSettingService settings;
    /** 共用媒體格式與內容驗證服務。 */
    private final TemporaryMediaService media;
    /** 名片 Vision client（正式環境為真實 Provider，本機可由 fake 覆蓋）。 */
    private final BusinessCardRecognitionClient vision;
    /** 語音轉錄 client（正式環境為真實 Provider，本機可由 fake 覆蓋）。 */
    private final TranscriptionClient transcription;

    /** 建立用途模型測試服務。 */
    public AiPurposeModelTestService(SystemSettingService settings, TemporaryMediaService media,
                                     BusinessCardRecognitionClient vision, TranscriptionClient transcription) {
        this.settings = settings;
        this.media = media;
        this.vision = vision;
        this.transcription = transcription;
    }

    /** 驗證圖片後，以目前有效 OCR assignment 執行真實結構化辨識測試。 */
    public Dtos.AiPurposeModelTestResponse testOcr(MultipartFile file) {
        var assignment = settings.resolveOcrAssignment();
        if (assignment == null) {
            throw new AiPurposeModelUnavailableException("尚未設定可用的名片 OCR 模型");
        }
        var validated = media.validateOnly(file, MediaPurpose.BUSINESS_CARD);
        long startedAt = System.nanoTime();
        vision.recognize(validated.bytes(), validated.mimeType(), assignment);
        return response("BUSINESS_CARD_OCR", assignment.model(), assignment.providerId(), startedAt,
                "名片結構化辨識成功");
    }

    /** 驗證音訊後，以目前有效 Transcription assignment 執行真實語音轉錄測試。 */
    public Dtos.AiPurposeModelTestResponse testTranscription(MultipartFile file) {
        var assignment = settings.resolveTranscriptionAssignment();
        if (assignment == null) {
            throw new AiPurposeModelUnavailableException("尚未設定可用的會議語音轉錄模型");
        }
        var validated = media.validateOnly(file, MediaPurpose.MEETING_AUDIO);
        long startedAt = System.nanoTime();
        var transcript = transcription.transcribe(validated.bytes(), validated.mimeType(), assignment);
        int textLength = transcript.text() == null ? 0 : transcript.text().length();
        return response("MEETING_TRANSCRIPTION", assignment.model(), assignment.providerId(), startedAt,
                "逐字稿產生成功（" + textLength + " 字）");
    }

    /** 組裝不含名片個資或逐字稿內容的安全測試結果。 */
    private Dtos.AiPurposeModelTestResponse response(String purpose, String model, Long providerId,
                                                     long startedAt, String summary) {
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        return new Dtos.AiPurposeModelTestResponse(true, purpose, model, providerId, latencyMs, summary);
    }
}
