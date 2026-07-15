package com.aicrm.crm.service.meeting;

/** 會議 Copilot session 狀態或冪等 payload 衝突。 */
public class MeetingCopilotConflictException extends RuntimeException {
    public MeetingCopilotConflictException(String message) { super(message); }
}
