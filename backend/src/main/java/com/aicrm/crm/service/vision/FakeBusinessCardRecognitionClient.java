package com.aicrm.crm.service.vision;

import com.aicrm.crm.api.Dtos.RecognizedBusinessCard;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 供 E2E / 本機驗收使用的 deterministic 名片辨識 fake。
 * 僅在設定 {@code app.vision.fake.enabled=true} 時啟用，並以 {@link Primary} 覆蓋
 * 正式 {@link OpenAiBusinessCardRecognitionClient}，讓 live 後端無需真實 Vision 金鑰即可跑完整流程。
 *
 * <p>回傳固定欄位以確保可重現：{@code title} 刻意給低信心以驗證 UI 校正提示；
 * email/phone/company 為固定值，E2E 可預建符合條件的客戶來觸發重複合併路徑。
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.vision.fake.enabled", havingValue = "true")
public class FakeBusinessCardRecognitionClient implements BusinessCardRecognitionClient {

    /** 忽略圖片與 assignment，回傳固定且結構合法的名片草稿。 */
    @Override
    public RecognizedBusinessCard recognize(byte[] image, String mimeType, AiModelAssignment assignment) {
        // 各欄位信心：title 低於 0.6 門檻，用於驗證前端低信心標示。
        Map<String, Double> confidence = Map.of(
                "personName", 0.96,
                "title", 0.42,
                "email", 0.93,
                "phone", 0.91,
                "companyName", 0.9);
        return new RecognizedBusinessCard(
                "名片測試員",
                "業務代表",
                "card-fake@example.com",
                "0912000111",
                "名片測試公司",
                null,
                confidence,
                List.of());
    }
}
