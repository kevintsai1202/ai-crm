package com.aicrm.crm.service.transcription;

/** 轉錄邊界失敗，供服務判斷改記錄 session FAILED。 */
public class TranscriptionException extends RuntimeException {
    public TranscriptionException(String message) { super(message); }
    public TranscriptionException(String message, Throwable cause) { super(message, cause); }
}
