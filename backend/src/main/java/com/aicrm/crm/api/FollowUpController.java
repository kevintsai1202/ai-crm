package com.aicrm.crm.api;

import com.aicrm.crm.service.FollowUpService;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 跟進信草稿、版本化、冪等寄送與重試的 HTTP 邊界。 */
@RestController
public class FollowUpController {
    private final FollowUpService service;

    /** 建立 controller。 */
    public FollowUpController(FollowUpService service) { this.service = service; }

    /** 產生客戶跟進信草稿（grounding：客戶／商機／近期互動）。 */
    @PostMapping("/api/customers/{customerId}/follow-ups/drafts")
    public ResponseEntity<Dtos.FollowUpDraftResponse> createDraft(@PathVariable Long customerId,
            @RequestBody(required = false) Dtos.CreateFollowUpDraftRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Dtos.CreateFollowUpDraftRequest body = request == null ? new Dtos.CreateFollowUpDraftRequest(null) : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDraft(customerId, body, principal));
    }

    /** 人工修改草稿 → 產生新版本（不覆寫舊版）。 */
    @PutMapping("/api/follow-ups/drafts/{id}")
    public Dtos.FollowUpDraftResponse updateDraft(@PathVariable Long id,
            @Valid @RequestBody Dtos.UpdateFollowUpDraftRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.updateDraft(id, request, principal);
    }

    /** 核准草稿並以 Idempotency-Key 冪等寄送。 */
    @PostMapping("/api/follow-ups/drafts/{id}/approve-and-send")
    public Dtos.OutboundEmailResponse approveAndSend(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.approveAndSend(id, principal, key);
    }

    /** 重試 FAILED 外寄郵件（SENT 不可重試）。 */
    @PostMapping("/api/outbound-emails/{id}/retry")
    public Dtos.OutboundEmailResponse retry(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return service.retry(id, principal);
    }
}
