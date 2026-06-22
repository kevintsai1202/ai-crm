package com.aicrm.crm.api;

import com.aicrm.crm.service.AiGovernanceService;
import com.aicrm.crm.service.ManagerInsightService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manager AI 分析 API（模組 C）：團隊整體診斷與個別業務 coaching。
 * GET 讀快取（未生成回 204）；POST 點按生成（呼叫 LLM，無金鑰走 fallback）。
 * 存取由 SecurityConfig 以 /api/manager/** → hasAnyRole("MANAGER","ADMIN") 保護。
 */
@RestController
@RequestMapping("/api/manager/insights")
public class ManagerInsightController {

    /** AI 分析服務。 */
    private final ManagerInsightService service;

    /** AI 治理服務：查詢 AI 呼叫歷程。 */
    private final AiGovernanceService governance;

    public ManagerInsightController(ManagerInsightService service, AiGovernanceService governance) {
        this.service = service;
        this.governance = governance;
    }

    /**
     * 讀團隊診斷快取；未生成回 204。
     *
     * @return 快取回應或 204
     */
    @GetMapping("/team")
    public ResponseEntity<Dtos.ManagerInsightResponse> getTeam() {
        var cached = service.getTeamInsight();
        return cached == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(cached);
    }

    /**
     * 生成團隊整體診斷（upsert 快取）。
     *
     * @return 生成後的回應
     */
    @PostMapping(value = "/team", produces = MediaType.APPLICATION_JSON_VALUE)
    public Dtos.ManagerInsightResponse generateTeam() {
        return service.generateTeamInsight();
    }

    /**
     * 以 SSE 串流推送團隊整體診斷（邊產生邊送，完成後 upsert 快取）。
     *
     * @return SseEmitter 串流發送器
     */
    @PostMapping(value = "/team", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTeam() {
        return service.streamTeamInsight();
    }

    /**
     * 讀個別業務 coaching 快取；未生成回 204。
     *
     * @param owner 業務顯示名稱
     * @return 快取回應或 204
     */
    @GetMapping("/owner")
    public ResponseEntity<Dtos.ManagerInsightResponse> getOwner(@RequestParam String owner) {
        var cached = service.getOwnerInsight(owner);
        return cached == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(cached);
    }

    /**
     * 生成個別業務 coaching（upsert 快取）。
     *
     * @param owner 業務顯示名稱
     * @return 生成後的回應
     */
    @PostMapping(value = "/owner", produces = MediaType.APPLICATION_JSON_VALUE)
    public Dtos.ManagerInsightResponse generateOwner(@RequestParam String owner) {
        return service.generateOwnerInsight(owner);
    }

    /**
     * 以 SSE 串流推送個別業務 coaching（邊產生邊送，完成後 upsert 快取）。
     *
     * @param owner 業務顯示名稱
     * @return SseEmitter 串流發送器
     */
    @PostMapping(value = "/owner", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOwner(@RequestParam String owner) {
        return service.streamOwnerInsight(owner);
    }

    /**
     * 列出團隊診斷的 AI 歷程（TEAM_ANALYSIS）。
     *
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/team/calls")
    public java.util.List<Dtos.AiCallHistoryItem> teamCalls() {
        return governance.historyByType(com.aicrm.crm.domain.AiCallType.TEAM_ANALYSIS);
    }

    /**
     * 列出指定業務的 coaching AI 歷程（OWNER_COACHING）。
     *
     * @param owner 業務顯示名稱
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/owner/calls")
    public java.util.List<Dtos.AiCallHistoryItem> ownerCalls(@RequestParam String owner) {
        return governance.historyByOwner(owner);
    }
}
