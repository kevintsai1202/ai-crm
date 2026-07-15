package com.aicrm.crm.service.vision;

import com.aicrm.crm.api.Dtos.RecognizedBusinessCard;

/** 可替換的名片 Vision 辨識邊界。 */
public interface BusinessCardRecognitionClient {
    /** 將名片圖片轉成嚴格結構化草稿。 */
    RecognizedBusinessCard recognize(byte[] image, String mimeType, AiModelAssignment assignment);
}
