package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.service.MeetingCopilotService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 會議 Copilot 音訊上傳、輪詢與選擇性確認 HTTP 邊界。 */
@RestController
@RequestMapping("/api/meeting-copilot/sessions")
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class MeetingCopilotController {
    private final MeetingCopilotService service;

    /** 建立 controller。 */
    public MeetingCopilotController(MeetingCopilotService service) { this.service = service; }

    /** 上傳會議音訊並建立轉錄/草稿 session。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dtos.MeetingCopilotSessionResponse> create(@RequestPart("file") MultipartFile file,
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "opportunityId", required = false) Long opportunityId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(file, customerId, opportunityId, principal));
    }

    /** 輪詢本人可見的轉錄結果、摘要與結構化草稿。 */
    @GetMapping("/{id}")
    public Dtos.MeetingCopilotSessionResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return service.get(id, principal);
    }

    /** 人工選擇性確認並以 Idempotency-Key 原子套用選定的 CRM 變更。 */
    @PostMapping("/{id}/confirm")
    public Dtos.MeetingCopilotConfirmResponse confirm(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Dtos.ConfirmMeetingRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.confirm(id, request, principal, key);
    }
}
