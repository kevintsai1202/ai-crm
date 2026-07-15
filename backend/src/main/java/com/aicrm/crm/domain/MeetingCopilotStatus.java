package com.aicrm.crm.domain;

/** 會議 Copilot session 生命週期：上傳 → 處理 → 待審／失敗 → 已確認。 */
public enum MeetingCopilotStatus { UPLOADED, PROCESSING, REVIEW_PENDING, FAILED, CONFIRMED }
