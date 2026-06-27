package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 我的工作檯個人 AI 服務：計算個人待辦、解析資料 scope。
 *
 * <p>串流推薦與問答方法於後續任務補上。資料隔離原則：SALES 一律強制只看自己負責的客戶；
 * MANAGER/ADMIN 預設自己、可切換看全部。所有 scope 解析在後端，前端參數不可信任。</p>
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceAiService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceAiService.class);

    /** 即將續約的判定天數。 */
    private static final int RENEWAL_DUE_DAYS = 14;

    /** 工作檯 AI 系統提示：強調接地、不可竄改數字。 */
    private static final String SYSTEM_PROMPT =
            "你是專業的 CRM 業務助理，協助業務規劃每日工作。回答務必使用繁體中文，"
            + "且只能根據提供的資料庫事實作答，不可編造未提供的客戶、數字或商機。";

    /** 客戶資料存取。 */
    private final CustomerRepository customerRepository;

    /** ChatModel 提供者：無金鑰時為 null，走 deterministic fallback。 */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** AI 治理：記錄每次呼叫（含 fallback）。 */
    private final AiGovernanceService aiGovernance;

    /** 系統設定：解析 chat options（max_completion_tokens 等）。 */
    private final SystemSettingService systemSettings;

    /** 是否啟用真實 LLM（金鑰非空）。 */
    private final boolean aiEnabled;

    public WorkspaceAiService(CustomerRepository customerRepository,
                              ObjectProvider<ChatModel> chatModelProvider,
                              AiGovernanceService aiGovernance,
                              SystemSettingService systemSettings,
                              @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.customerRepository = customerRepository;
        this.chatModelProvider = chatModelProvider;
        this.aiGovernance = aiGovernance;
        this.systemSettings = systemSettings;
        this.aiEnabled = openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /**
     * 載入呼叫者 scope 內的客戶。
     * SALES 一律強制自己（忽略 requestedScope）；MANAGER/ADMIN：requestedScope="all" 回全部，否則回自己。
     *
     * @param principal 認證主體
     * @param requestedScope 前端請求的範圍（self / all）
     * @return scope 內客戶清單
     */
    List<Customer> loadScopedCustomers(AuthPrincipal principal, String requestedScope) {
        boolean all = principal.role() != Role.SALES && "all".equalsIgnoreCase(requestedScope);
        return all ? customerRepository.findAll()
                   : customerRepository.findByOwnerName(principal.displayName());
    }

    /**
     * 以純 DB 規則計算個人待辦：高風險、即將續約（14 天內）、逾期未結商機。
     * 不依賴 AI，永遠可用，並作為 AI 總結的 grounding。
     *
     * @param principal 認證主體
     * @param scope 請求範圍（SALES 會被強制為自己）
     * @return 待辦清單
     */
    public List<Dtos.WorkspaceTodoItem> computeTodos(AuthPrincipal principal, String scope) {
        var customers = loadScopedCustomers(principal, scope);
        var today = LocalDate.now();
        var todos = new ArrayList<Dtos.WorkspaceTodoItem>();
        for (var c : customers) {
            // 高風險客戶
            if ("HIGH".equalsIgnoreCase(c.getRiskLevel())) {
                todos.add(new Dtos.WorkspaceTodoItem("HIGH_RISK", c.getId(), c.getName(),
                        "客戶風險等級為高，建議優先聯繫關懷", "HIGH"));
            }
            // 即將續約（今日起 14 天內）
            var due = c.getRenewalDueDate();
            if (due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(RENEWAL_DUE_DAYS))) {
                todos.add(new Dtos.WorkspaceTodoItem("RENEWAL_DUE", c.getId(), c.getName(),
                        "續約日 " + due + " 即將到期，建議啟動續約", "MEDIUM"));
            }
            // 逾期未結商機（開放中且預計成交日早於今日）
            for (var o : c.getOpportunities()) {
                boolean open = o.getStage() != OpportunityStage.CLOSED_WON
                        && o.getStage() != OpportunityStage.CLOSED_LOST;
                if (open && o.getExpectedCloseDate() != null && o.getExpectedCloseDate().isBefore(today)) {
                    todos.add(new Dtos.WorkspaceTodoItem("STALE_OPPORTUNITY", c.getId(), c.getName(),
                            "商機「" + o.getName() + "」預計成交日已過（" + o.getExpectedCloseDate() + "），需推進或結案", "MEDIUM"));
                }
            }
        }
        return todos;
    }

    /**
     * 把待辦組成 grounding context 文字餵給 LLM，明示不可竄改數字。
     *
     * @param principal 認證主體
     * @param todos 已算好的待辦
     * @return 提示詞
     */
    String buildRecommendationPrompt(AuthPrincipal principal, List<Dtos.WorkspaceTodoItem> todos) {
        var sb = new StringBuilder();
        sb.append("你是 CRM 業務助理。以下為業務「").append(principal.displayName())
          .append("」目前由系統計算出的待辦（數字與對象已由資料庫確認，請勿更改）：\n");
        if (todos.isEmpty()) {
            sb.append("（目前無待辦）\n");
        } else {
            for (var t : todos) {
                sb.append("- [").append(t.type()).append("] ").append(t.customerName())
                  .append("：").append(t.reason()).append("\n");
            }
        }
        sb.append("\n請用繁體中文，依急迫性排序，給出今天的工作優先建議（150 字內），不要編造未列出的客戶或數字。");
        return sb.toString();
    }

    /**
     * deterministic fallback：無 AI 或失敗時的工作建議文字（接地於待辦）。
     *
     * @param principal 認證主體
     * @param todos 已算好的待辦
     * @return 保底建議文字
     */
    public String deterministicRecommendation(AuthPrincipal principal, List<Dtos.WorkspaceTodoItem> todos) {
        if (todos.isEmpty()) {
            return "根據 CRM 資料庫，您目前沒有待辦事項，維持既有跟進節奏即可。";
        }
        var sb = new StringBuilder("根據 CRM 資料庫，您有 ").append(todos.size()).append(" 項待辦需處理：\n");
        for (var t : todos) {
            sb.append("• ").append(t.customerName()).append("：").append(t.reason()).append("\n");
        }
        sb.append("建議優先處理標記為高的項目。");
        return sb.toString();
    }

    /**
     * 以規則產生 AI 建議商機草稿：對「高風險或即將續約、但目前無開放商機」的客戶，建議開立一筆跟進商機。
     * 草稿僅供建議，使用者確認後才透過既有建立流程真正建立。
     *
     * @param principal 認證主體
     * @param scope 範圍
     * @return 商機草稿清單
     */
    public List<Dtos.SuggestedOpportunityDraft> computeDrafts(AuthPrincipal principal, String scope) {
        var customers = loadScopedCustomers(principal, scope);
        var today = LocalDate.now();
        var drafts = new ArrayList<Dtos.SuggestedOpportunityDraft>();
        for (var c : customers) {
            boolean hasOpenOpp = c.getOpportunities().stream().anyMatch(o ->
                    o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST);
            if (hasOpenOpp) {
                continue;
            }
            boolean highRisk = "HIGH".equalsIgnoreCase(c.getRiskLevel());
            var due = c.getRenewalDueDate();
            boolean renewalDue = due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(RENEWAL_DUE_DAYS));
            if (renewalDue) {
                drafts.add(new Dtos.SuggestedOpportunityDraft(c.getId(), c.getName(), c.getName() + "續約案",
                        "QUALIFICATION", null, "續約日將至且無進行中商機，建議開立續約商機跟進"));
            } else if (highRisk) {
                drafts.add(new Dtos.SuggestedOpportunityDraft(c.getId(), c.getName(), c.getName() + "關懷專案",
                        "QUALIFICATION", null, "客戶高風險且無進行中商機，建議建立關懷/挽留商機"));
            }
        }
        return drafts;
    }

    /**
     * 串流產生工作推薦：先送待辦與商機草稿，再串流 AI 總結；無金鑰/失敗走 deterministic fallback。
     * 串流回呼於另一執行緒、readOnly 交易結束後執行，故先在交易內把純值（todos/drafts/fallback）算好。
     *
     * @param principal 認證主體
     * @param scope 請求範圍（SALES 會被強制為自己）
     * @return SseEmitter
     */
    public SseEmitter streamRecommendation(AuthPrincipal principal, String scope) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // 交易內先算好純值
        var todos = computeTodos(principal, scope);
        var drafts = computeDrafts(principal, scope);
        final String fallback = deterministicRecommendation(principal, todos);
        final String userPrompt = buildRecommendationPrompt(principal, todos);
        final String subject = principal.username();
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;

        // 先送結構化待辦與草稿（前端即時呈現）
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "todos", "items", todos)));
            emitter.send(SseEmitter.event().data(Map.of("type", "drafts", "items", drafts)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // 無金鑰：直接送 deterministic fallback 後收尾
        if (chatModel == null) {
            var saved = aiGovernance.record(AiCallType.WORKSPACE_RECOMMENDATION, null, subject, null,
                    0, 0, 0, false, false, fallback);
            SseHelper.sendContent(emitter, fallback);
            SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
            return emitter;
        }

        // 真串流
        var fullAnswer = new StringBuilder();
        var lastResponse = new AtomicReference<ChatResponse>();
        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
        var opts = systemSettings.resolveChatOptions();
        if (opts != null) {
            streamSpec = streamSpec.options(opts);
        }
        streamSpec.stream().chatResponse().subscribe(
                resp -> {
                    lastResponse.set(resp);
                    var result = resp.getResult();
                    var text = result == null ? null : result.getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullAnswer.append(text);
                        SseHelper.sendContent(emitter, text);
                    }
                },
                error -> {
                    log.warn("工作檯推薦串流失敗，subject={}，改用 fallback：{}", subject, error.getMessage());
                    var partial = fullAnswer.toString().strip();
                    if (partial.isBlank()) {
                        var saved = aiGovernance.record(AiCallType.WORKSPACE_RECOMMENDATION, null, subject, null,
                                0, 0, 0, false, false, fallback);
                        SseHelper.sendContent(emitter, fallback);
                        SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                    } else {
                        var saved = aiGovernance.record(AiCallType.WORKSPACE_RECOMMENDATION, null, subject, null,
                                0, 0, 0, true, false, partial);
                        SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                    }
                },
                () -> {
                    var answer = fullAnswer.toString().strip();
                    if (answer.isBlank()) {
                        var saved = aiGovernance.record(AiCallType.WORKSPACE_RECOMMENDATION, null, subject, null,
                                0, 0, 0, false, false, fallback);
                        SseHelper.sendContent(emitter, fallback);
                        SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                        return;
                    }
                    var metadata = lastResponse.get() == null ? null : lastResponse.get().getMetadata();
                    var usage = metadata == null ? null : metadata.getUsage();
                    var model = metadata == null ? null : metadata.getModel();
                    Integer pt = usage == null ? null : usage.getPromptTokens();
                    Integer ct = usage == null ? null : usage.getCompletionTokens();
                    Integer tt = usage == null ? null : usage.getTotalTokens();
                    var saved = aiGovernance.record(AiCallType.WORKSPACE_RECOMMENDATION, null, subject, model,
                            pt, ct, tt, true, false, answer);
                    SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                }
        );
        return emitter;
    }
}
