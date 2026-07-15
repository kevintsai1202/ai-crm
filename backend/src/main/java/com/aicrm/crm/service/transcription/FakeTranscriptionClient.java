package com.aicrm.crm.service.transcription;

import com.aicrm.crm.service.vision.AiModelAssignment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 供 E2E / 本機驗收使用的 deterministic 轉錄 fake。
 * 僅在設定 {@code app.transcription.fake.enabled=true} 時啟用，並以 {@link Primary} 覆蓋
 * 正式 {@link OpenAiCompatibleTranscriptionClient}，讓 live 後端無需真實轉錄金鑰即可跑完整流程。
 *
 * <p>回傳固定逐字稿以確保可重現：內容刻意包含跟進、預算與關鍵人資訊，
 * 供服務端 deterministic 草稿產生器生成互動、任務、商機與 stakeholder 建議。
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.transcription.fake.enabled", havingValue = "true")
public class FakeTranscriptionClient implements TranscriptionClient {

    /** 忽略音訊與 assignment，回傳固定逐字稿。 */
    @Override
    public Transcript transcribe(byte[] audio, String mimeType, AiModelAssignment assignment) {
        return new Transcript("客戶表示對方案有高度興趣，關注導入時程與預算上限，"
                + "會後需寄送正式報價；決策由採購主管陳經理拍板。");
    }
}
