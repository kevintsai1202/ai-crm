package com.aicrm.crm.service.transcription;

import com.aicrm.crm.service.vision.AiModelAssignment;

/** 可替換的語音轉錄邊界。 */
public interface TranscriptionClient {
    /** 將音訊轉成逐字稿；未設定或失敗時拋 exception。 */
    Transcript transcribe(byte[] audio, String mimeType, AiModelAssignment assignment);
}
