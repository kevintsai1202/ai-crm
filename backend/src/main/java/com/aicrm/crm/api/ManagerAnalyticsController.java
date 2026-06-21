package com.aicrm.crm.api;

import com.aicrm.crm.service.ManagerAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager 業務分析 API：提供各業務績效統計與團隊總覽。
 * 端點存取由 SecurityConfig 以 hasAnyRole("MANAGER","ADMIN") 限制。
 */
@RestController
@RequestMapping("/api/manager")
public class ManagerAnalyticsController {

    /** 業務分析聚合服務。 */
    private final ManagerAnalyticsService analyticsService;

    public ManagerAnalyticsController(ManagerAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 回傳各業務績效統計與團隊總覽。
     *
     * @return Manager 業務分析回應
     */
    @GetMapping("/analytics")
    public Dtos.ManagerAnalyticsResponse analytics() {
        return analyticsService.analytics();
    }
}
