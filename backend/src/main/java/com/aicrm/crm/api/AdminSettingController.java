package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.SystemSettingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 系統設定 API（限 ADMIN）：目前提供 AI 對話模型設定的讀取與更新。
 * 函式級註解：路徑在 /api/admin/** 範圍內，沿用既有 SecurityConfig hasRole("ADMIN") 保護，無需額外設定。
 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingController {

    /** 系統設定服務。 */
    private final SystemSettingService systemSettings;

    public AdminSettingController(SystemSettingService systemSettings) {
        this.systemSettings = systemSettings;
    }

    /**
     * 取得 AI 設定（目前模型、候選清單、環境變數預設、來源）。
     *
     * @return AI 設定回應
     */
    @GetMapping("/ai")
    public Dtos.AiSettingsResponse getAiSettings() {
        return systemSettings.getAiSettingsView();
    }

    /**
     * 更新 AI 設定（model 與候選清單）；model 不在清單內回 400。
     *
     * @param request 設定更新請求
     * @param authentication 登入認證（記錄修改者）
     * @return 更新後的設定檢視
     */
    @PutMapping("/ai")
    public Dtos.AiSettingsResponse updateAiSettings(@RequestBody Dtos.AiSettingsRequest request,
                                                     Authentication authentication) {
        try {
            systemSettings.updateAiSettings(request.model(), request.modelOptions(), resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return systemSettings.getAiSettingsView();
    }

    /** 從認證主體解析登入帳號；無法解析時回 "unknown"。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        return authentication != null ? authentication.getName() : "unknown";
    }
}
