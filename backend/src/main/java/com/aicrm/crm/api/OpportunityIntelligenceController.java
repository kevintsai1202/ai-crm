package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.service.OpportunityIntelligenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商機智能 HTTP 邊界（V26）：健康度查詢與重算。owner scope 與評分規則由服務層統一執行。
 * 掛在 {@code /api/opportunities} 之下，沿用既有商機端點的認證與擁有權慣例。
 */
@RestController
@RequestMapping("/api/opportunities")
public class OpportunityIntelligenceController {

    /** 商機智能應用服務。 */
    private final OpportunityIntelligenceService service;

    public OpportunityIntelligenceController(OpportunityIntelligenceService service) {
        this.service = service;
    }

    /**
     * 取得商機最新健康度 snapshot（含總分、分項可解釋依據、趨勢與下一最佳行動）；owner scope。
     *
     * @param id 商機 id
     * @param principal 登入主體
     * @return 健康度回應
     */
    @GetMapping("/{id}/health")
    public Dtos.OpportunityHealthResponse getHealth(@PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getHealth(id, principal);
    }

    /**
     * 重算健康度並保存新 snapshot（保留歷史）；不修改商機 stage/probability。owner scope。
     *
     * @param id 商機 id
     * @param principal 登入主體
     * @return 重算後的健康度回應
     */
    @PostMapping("/{id}/health/recalculate")
    public Dtos.OpportunityHealthResponse recalculate(@PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.recalculate(id, principal);
    }
}
