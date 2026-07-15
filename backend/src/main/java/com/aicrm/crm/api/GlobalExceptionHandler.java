package com.aicrm.crm.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全域例外處理器，統一回傳 RFC 7807 ProblemDetail。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 處理 Bean Validation 失敗。
     *
     * @param ex validation 例外
     * @param request HTTP request
     * @return ProblemDetail response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", String.join("; ", message), request);
    }

    /**
     * 處理查無資料。
     *
     * @param ex 查無資料例外
     * @param request HTTP request
     * @return ProblemDetail response
     */
    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request);
    }

    /**
     * 處理登入失敗。
     *
     * @param ex 登入失敗例外
     * @param request HTTP request
     * @return ProblemDetail response
     */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    /**
     * 處理權限不足。
     *
     * @param ex 權限例外
     * @param request HTTP request
     * @return ProblemDetail response
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "目前角色沒有執行此操作的權限", request);
    }

    /** CRM 任務規則驗證失敗只回固定安全訊息。 */
    @ExceptionHandler(com.aicrm.crm.service.task.TaskValidationException.class)
    ResponseEntity<ProblemDetail> handleTaskValidation(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", "CRM 任務資料不符合規則", request);
    }

    /** 將過期版本或資料庫樂觀鎖衝突轉為 409。 */
    @ExceptionHandler(com.aicrm.crm.service.task.TaskConflictException.class)
    ResponseEntity<ProblemDetail> handleTaskConflict(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", "CRM 任務已被其他使用者更新，請重新載入", request);
    }

    /** OCR assignment 缺少或不相容時回 503。 */
    @ExceptionHandler(com.aicrm.crm.service.businesscard.BusinessCardUnavailableException.class)
    ResponseEntity<ProblemDetail> handleBusinessCardUnavailable(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "尚未設定可用的名片 Vision 模型", request);
    }

    /** 名片確認狀態或冪等 payload 衝突回 409。 */
    @ExceptionHandler(com.aicrm.crm.service.businesscard.BusinessCardConflictException.class)
    ResponseEntity<ProblemDetail> handleBusinessCardConflict(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", "名片確認請求與目前狀態衝突", request);
    }

    /** 轉錄 assignment 缺少或不相容時回 503。 */
    @ExceptionHandler(com.aicrm.crm.service.meeting.MeetingCopilotUnavailableException.class)
    ResponseEntity<ProblemDetail> handleMeetingUnavailable(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "尚未設定可用的語音轉錄模型", request);
    }

    /** 會議 Copilot 確認狀態或冪等 payload 衝突回 409。 */
    @ExceptionHandler(com.aicrm.crm.service.meeting.MeetingCopilotConflictException.class)
    ResponseEntity<ProblemDetail> handleMeetingConflict(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", "會議確認請求與目前狀態衝突", request);
    }

    /** multipart 內容超過伺服器限制時回 413。 */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadTooLarge(HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", "上傳檔案超過大小限制", request);
    }

    /**
     * 建立 ProblemDetail 回應。
     *
     * @param status HTTP 狀態
     * @param title 錯誤標題
     * @param detail 錯誤細節
     * @param request HTTP request
     * @return response entity
     */
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}

