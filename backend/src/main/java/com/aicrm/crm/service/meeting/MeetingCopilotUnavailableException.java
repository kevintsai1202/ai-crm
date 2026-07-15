package com.aicrm.crm.service.meeting;

/** 轉錄 assignment 未設定或不支援 audio transcription。 */
public class MeetingCopilotUnavailableException extends RuntimeException {
    public MeetingCopilotUnavailableException(String message) { super(message); }
}
