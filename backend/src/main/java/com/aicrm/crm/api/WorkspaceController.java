package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.service.WorkspaceAiService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 我的工作檯個人 AI 端點（任何登入角色；資料 scope 由後端強制，SALES 一律自己）。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    /** 工作檯 AI 服務。 */
    private final WorkspaceAiService workspaceAiService;

    public WorkspaceController(WorkspaceAiService workspaceAiService) {
        this.workspaceAiService = workspaceAiService;
    }

    /**
     * 產生工作推薦（SSE：先送 todos / drafts，再串流 AI 總結）。
     *
     * @param principal 認證主體
     * @param scope 範圍（self / all；SALES 會被強制 self）
     * @return SSE 串流
     */
    @PostMapping("/recommendation")
    public SseEmitter streamRecommendation(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(defaultValue = "self") String scope) {
        return workspaceAiService.streamRecommendation(principal, scope);
    }

    /**
     * 讀上次快取的 AI 總結 + 即時重算待辦。
     *
     * @param principal 認證主體
     * @param scope 範圍
     * @return 工作推薦回應
     */
    @GetMapping("/recommendation")
    public Dtos.WorkspaceRecommendationResponse getRecommendation(@AuthenticationPrincipal AuthPrincipal principal,
                                                                  @RequestParam(defaultValue = "self") String scope) {
        return workspaceAiService.getRecommendation(principal, scope);
    }

    /**
     * 個人問答（SSE）。
     *
     * @param principal 認證主體
     * @param req 問答請求
     * @return SSE 串流
     */
    @PostMapping("/chat")
    public SseEmitter streamChat(@AuthenticationPrincipal AuthPrincipal principal,
                                 @RequestBody @Valid Dtos.WorkspaceChatRequest req) {
        return workspaceAiService.streamChat(principal, req);
    }

    /**
     * 本人工作檯 AI 歷程。
     *
     * @param principal 認證主體
     * @return AI 呼叫歷程
     */
    @GetMapping("/history")
    public List<Dtos.AiCallHistoryItem> history(@AuthenticationPrincipal AuthPrincipal principal) {
        return workspaceAiService.history(principal);
    }
}
