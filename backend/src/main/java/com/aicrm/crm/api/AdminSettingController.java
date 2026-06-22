package com.aicrm.crm.api;

import com.aicrm.crm.service.InsightService;
import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.SystemSettingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 系統設定 API（限 ADMIN）：目前提供 AI 對話模型設定的讀取與更新。
 * 函式級註解：路徑在 /api/admin/** 範圍內，沿用既有 SecurityConfig hasRole("ADMIN") 保護，無需額外設定。
 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingController {

    /** 系統設定服務。 */
    private final SystemSettingService systemSettings;
    /** AI 洞察服務（借用串流基礎設施執行模型測試）。 */
    private final InsightService insightService;

    public AdminSettingController(SystemSettingService systemSettings, InsightService insightService) {
        this.systemSettings = systemSettings;
        this.insightService = insightService;
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

    /**
     * 模型競速測試（SSE 串流）：以指定模型對測試問題發起呼叫，限 ADMIN 使用。
     * 函式級註解：model 直接覆蓋系統設定，無 grounding context，純粹比較裸 LLM 速度與品質。
     *
     * @param request 含 message（問題）與 model（要測試的模型名）
     * @return SseEmitter 串流發送器
     */
    @PostMapping(value = "/ai/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter testModel(@RequestBody Dtos.AiTestRequest request) {
        return insightService.streamModelTest(request.model(), request.message());
    }

    /** 從認證主體解析登入帳號；無法解析時回 "unknown"。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        return authentication != null ? authentication.getName() : "unknown";
    }
}
