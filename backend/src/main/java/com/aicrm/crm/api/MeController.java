package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 個人化 API：目前提供儀表板版面偏好的讀取與儲存（限本人，任何已登入角色）。
 */
@RestController
@RequestMapping("/api/me/preferences")
public class MeController {

    /** 個人偏好服務。 */
    private final UserPreferenceService preferenceService;

    public MeController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * 取得本人儀表板版面（可見區塊有序 id 陣列）。
     *
     * @param authentication 登入認證（principal 為 JwtService.AuthPrincipal）
     * @return 版面回應；未設定時 visibleOrder 為 null
     */
    @GetMapping("/dashboard-layout")
    public Dtos.DashboardLayoutResponse getDashboardLayout(Authentication authentication) {
        var order = preferenceService.getDashboardLayout(resolveUsername(authentication));
        return new Dtos.DashboardLayoutResponse(order);
    }

    /**
     * 儲存本人儀表板版面（upsert）。
     *
     * @param request 含可見區塊有序 id 陣列
     * @param authentication 登入認證
     */
    @PutMapping("/dashboard-layout")
    public void saveDashboardLayout(@RequestBody Dtos.DashboardLayoutRequest request, Authentication authentication) {
        preferenceService.saveDashboardLayout(resolveUsername(authentication), request.visibleOrder());
    }

    /** 從認證主體解析登入帳號。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未取得登入身分");
    }
}
