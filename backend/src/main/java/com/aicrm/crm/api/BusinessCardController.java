package com.aicrm.crm.api;

import com.aicrm.crm.service.BusinessCardIntakeService;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 名片上傳、輪詢與人工確認 HTTP 邊界。 */
@RestController @RequestMapping("/api/business-card-intakes")
@ConditionalOnProperty(name="app.media.enabled", havingValue="true", matchIfMissing=true)
public class BusinessCardController {
    private final BusinessCardIntakeService service;
    /** 建立 controller。 */ public BusinessCardController(BusinessCardIntakeService service){this.service=service;}

    /** 上傳名片並建立辨識工作。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dtos.BusinessCardIntakeResponse> create(@RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(file, principal));
    }

    /** 輪詢本人可見的辨識結果。 */
    @GetMapping("/{id}") public Dtos.BusinessCardIntakeResponse get(@PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal){return service.get(id,principal);}

    /** 人工確認並以 Idempotency-Key 原子寫入四類 CRM 正式資料。 */
    @PostMapping("/{id}/confirm") public Dtos.BusinessCardConfirmResponse confirm(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Dtos.ConfirmBusinessCardRequest request,
            @AuthenticationPrincipal AuthPrincipal principal){return service.confirm(id,request,principal,key);}
}
