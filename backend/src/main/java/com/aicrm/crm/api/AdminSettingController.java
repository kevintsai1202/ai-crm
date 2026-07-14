package com.aicrm.crm.api;

import com.aicrm.crm.service.AiGovernanceService;
import com.aicrm.crm.service.InsightService;
import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.SystemSettingService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    /** AI 洞察服務（借用串流基礎設施執行模型測試與評分）。 */
    private final InsightService insightService;
    /** AI 治理服務（查詢評分 AI 歷程）。 */
    private final AiGovernanceService aiGovernance;

    public AdminSettingController(SystemSettingService systemSettings,
                                  InsightService insightService,
                                  AiGovernanceService aiGovernance) {
        this.systemSettings = systemSettings;
        this.insightService = insightService;
        this.aiGovernance = aiGovernance;
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
            systemSettings.updateAiSettings(request.model(), request.providerId(), request.modelOptions(),
                    request.temperature(), request.maxCompletionTokens(), request.reasoningEffort(), resolveUsername(authentication));
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
    public SseEmitter testModel(@RequestBody Dtos.AiTestRequest request, HttpServletResponse response) {
        // 禁止 HTTP cache 與 Nginx/Zeabur proxy buffer，確保每次測試均呼叫真實 LLM 並即時串流
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return insightService.streamModelTest(request.model(), request.providerId(), request.message());
    }

    /**
     * 多模型競速評分（SSE 串流）：以 claude-opus-4-8 評審各模型速度、token 效率與回答品質。
     *
     * @param request 各模型測試結果
     * @param response HTTP 回應（設 no-cache 標頭）
     * @return SseEmitter 串流發送器
     */
    @PostMapping(value = "/ai/score", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter scoreModels(@RequestBody Dtos.AiScoreRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        return insightService.streamModelScore(request);
    }

    /**
     * 取得所有 AI 供應商（不含 apiKey）。
     *
     * @return provider 清單
     */
    @GetMapping("/ai/providers")
    public java.util.List<Dtos.AiProviderItem> getProviders() {
        return systemSettings.getProviders();
    }

    /**
     * 新增 AI 供應商。
     *
     * @param request 供應商請求 DTO
     * @param authentication 登入認證
     * @return 新增後的 provider
     */
    @PostMapping("/ai/providers")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Dtos.AiProviderItem createProvider(@jakarta.validation.Valid @RequestBody Dtos.AiProviderRequest request,
                                               Authentication authentication) {
        try {
            return systemSettings.createProvider(request, resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 更新 AI 供應商；apiKey 為 null 或空字串時保留現有金鑰。
     *
     * @param id provider id
     * @param request 供應商請求 DTO
     * @param authentication 登入認證
     * @return 更新後的 provider
     */
    @PutMapping("/ai/providers/{id}")
    public Dtos.AiProviderItem updateProvider(@PathVariable Long id,
                                               @jakarta.validation.Valid @RequestBody Dtos.AiProviderRequest request,
                                               Authentication authentication) {
        try {
            return systemSettings.updateProvider(id, request, resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 刪除 AI 供應商。
     *
     * @param id provider id
     */
    @DeleteMapping("/ai/providers/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProvider(@PathVariable Long id) {
        try {
            systemSettings.deleteProvider(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 從 Provider 模型目錄 refresh 候選與可靠的 input modality metadata。
     *
     * @param id Provider ID
     * @param authentication 登入認證
     * @return refresh 後完整模型候選清單
     */
    @PostMapping("/ai/providers/{id}/models/refresh")
    public List<Dtos.ModelOptionItem> refreshProviderModels(@PathVariable Long id,
                                                            Authentication authentication) {
        try {
            return systemSettings.refreshProviderModels(id, resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (org.springframework.web.client.RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider 模型目錄查詢失敗");
        }
    }

    /**
     * 由 Admin 人工設定模型能力，能力來源固定記為 MANUAL。
     *
     * @param model 模型名稱
     * @param request Provider 與能力集合
     * @param authentication 登入認證
     * @return 更新後模型選項
     */
    @PutMapping("/ai/models/{model}/capabilities")
    public Dtos.ModelOptionItem updateModelCapabilities(@PathVariable String model,
                                                        @RequestBody Dtos.ModelCapabilitiesRequest request,
                                                        Authentication authentication) {
        try {
            return systemSettings.updateModelCapabilities(model, request.providerId(), request.capabilities(),
                    resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 更新 Chat、OCR 與語音轉錄用途 assignment，後端再次檢查能力。
     *
     * @param request 三種用途 assignment
     * @param authentication 登入認證
     * @return 更新後 AI 設定檢視
     */
    @PutMapping("/ai/assignments")
    public Dtos.AiSettingsResponse updateAssignments(@RequestBody Dtos.AiModelAssignments request,
                                                      Authentication authentication) {
        try {
            systemSettings.updateAssignments(request, resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return systemSettings.getAiSettingsView();
    }

    /**
     * 取得多模型評分的 AI 歷程（MODEL_EVAL 類型）。
     *
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/ai/score/calls")
    public java.util.List<Dtos.AiCallHistoryItem> scoreCalls() {
        return aiGovernance.historyByType(com.aicrm.crm.domain.AiCallType.MODEL_EVAL);
    }

    /**
     * 儲存單一模型競速測試結果（前端測試完成後非同步呼叫）。
     *
     * @param request 測試結果 DTO（model、sessionId 皆為必填）
     */
    @PostMapping("/ai/test/log")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logModelTest(@Valid @RequestBody Dtos.AiTestLogRequest request) {
        aiGovernance.recordModelTest(request);
    }

    /**
     * 取得指定模型的 MODEL_TEST 歷程（新到舊）。
     *
     * @param model 模型名稱（query param，必填）
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/ai/test/calls")
    public List<Dtos.AiCallHistoryItem> modelTestCalls(@RequestParam String model) {
        return aiGovernance.historyByModel(model);
    }

    /** 從認證主體解析登入帳號；無法解析時回 "unknown"。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        return authentication != null ? authentication.getName() : "unknown";
    }
}
