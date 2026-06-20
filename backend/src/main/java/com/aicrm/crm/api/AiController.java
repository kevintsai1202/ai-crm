package com.aicrm.crm.api;

import com.aicrm.crm.domain.FeedbackDecision;
import com.aicrm.crm.service.AiGovernanceService;
import com.aicrm.crm.service.InsightService;
import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.KnowledgeIndexer;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 助理 API，教學版以 deterministic service 模擬 Spring AI Tool Calling。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    /** AI 洞察服務。 */
    private final InsightService insightService;

    /** 知識庫索引器。 */
    private final KnowledgeIndexer knowledgeIndexer;

    /** AI 治理服務：採納/拒絕回饋與用量彙總。 */
    private final AiGovernanceService aiGovernanceService;

    public AiController(InsightService insightService, KnowledgeIndexer knowledgeIndexer,
                        AiGovernanceService aiGovernanceService) {
        this.insightService = insightService;
        this.knowledgeIndexer = knowledgeIndexer;
        this.aiGovernanceService = aiGovernanceService;
    }

    /**
     * 產生 AI CRM 助理回答，提供一般 JSON API 給驗證腳本與非串流用戶端使用。
     *
     * @param request 聊天請求
     * @return 聊天回答
     */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public Dtos.ChatResponse chat(@Valid @RequestBody Dtos.ChatRequest request) {
        return insightService.chat(request);
    }

    /**
     * 產生 AI CRM 助理回答，提供 SSE 串流給前端打字機體驗使用。
     *
     * @param request 聊天請求
     * @return SseEmitter 串流發送器
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody Dtos.ChatRequest request) {
        return insightService.streamChat(request);
    }

    /**
     * 產生指定客戶的 360 度整體評估報告（Markdown）。
     *
     * @param id 客戶 ID
     * @return 含評估報告、引用與風險的回應
     */
    @GetMapping(value = "/customers/{id}/assessment", produces = MediaType.APPLICATION_JSON_VALUE)
    public Dtos.ChatResponse customerAssessment(@PathVariable Long id) {
        return insightService.customerAssessment(id);
    }

    /**
     * 產生指定客戶的 360 度整體評估報告（SSE 串流版）：邊產生邊送，避免長報告撞前端/閘道逾時。
     *
     * @param id 客戶 ID
     * @return SseEmitter 串流發送器
     */
    @GetMapping(value = "/customers/{id}/assessment", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCustomerAssessment(@PathVariable Long id) {
        return insightService.streamCustomerAssessment(id);
    }

    /**
     * 產生 Portfolio 跨客戶整體評估報告（Markdown）。
     *
     * @return Portfolio 評估回應
     */
    @GetMapping("/portfolio/assessment")
    public Dtos.PortfolioAssessmentResponse portfolioAssessment() {
        return insightService.portfolioAssessment();
    }

    /**
     * 重建知識庫向量索引（限 ADMIN）。
     *
     * @return 重建筆數
     */
    @PostMapping("/knowledge/reindex")
    public Map<String, Integer> reindex() {
        return Map.of("reindexed", knowledgeIndexer.reindexAll());
    }

    /**
     * 對某筆 AI 呼叫紀錄送出採納/拒絕回饋。
     *
     * @param id AI 呼叫紀錄 ID
     * @param request 回饋內容（decision: ADOPTED/REJECTED, note 選填）
     * @param authentication 登入認證資訊，用於取得回饋者帳號
     */
    @PostMapping("/calls/{id}/feedback")
    public void feedback(@PathVariable Long id, @Valid @RequestBody Dtos.AiFeedbackRequest request,
                         Authentication authentication) {
        aiGovernanceService.feedback(id,
                FeedbackDecision.valueOf(request.decision()),
                request.note(),
                resolveUsername(authentication));
    }

    /**
     * 取得 AI 用量彙總統計（限 MANAGER / ADMIN）。
     *
     * @return 用量彙總回應
     */
    @GetMapping("/usage")
    public Dtos.UsageSummaryResponse usage() {
        return aiGovernanceService.usage();
    }

    /**
     * 列出指定客戶的歷次 AI 呼叫紀錄（新到舊），供前端「AI 歷程」Modal 呈現。
     *
     * @param id 客戶 ID
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/customers/{id}/calls")
    public java.util.List<Dtos.AiCallHistoryItem> customerCalls(@PathVariable Long id) {
        return aiGovernanceService.customerCallHistory(id);
    }

    /**
     * 從認證主體解析回饋者帳號；principal 為 {@link JwtService.AuthPrincipal}。
     *
     * @param authentication 認證資訊（可為 null）
     * @return 回饋者帳號，無法解析時回傳 unknown
     */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        return "unknown";
    }
}
