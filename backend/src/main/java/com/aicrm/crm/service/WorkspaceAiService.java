package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import static com.aicrm.crm.service.ai.AiResponseLanguage.directive;
import static com.aicrm.crm.service.ai.AiResponseLanguage.systemLanguage;
import static com.aicrm.crm.service.ai.AiResponseLanguage.isEnglish;
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
            "你是專業的 CRM 業務助理，協助業務規劃每日工作。"
            + "只能根據提供的資料庫事實作答，不可編造未提供的客戶、數字或商機。";

    /** 客戶資料存取。 */
    private final CustomerRepository customerRepository;

    /** ChatModel 提供者：無金鑰時為 null，走 deterministic fallback。 */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** AI 治理：記錄每次呼叫（含 fallback）。 */
    private final AiGovernanceService aiGovernance;

    /** 系統設定：解析 chat options（max_completion_tokens 等）。 */
    private final SystemSettingService systemSettings;

    /** 單客戶問答委派：深入單客戶時複用既有客戶對話（grounding/PII/治理皆沿用）。 */
    private final InsightService insightService;

    /** 是否啟用真實 LLM（金鑰非空）。 */
    private final boolean aiEnabled;

    public WorkspaceAiService(CustomerRepository customerRepository,
                              ObjectProvider<ChatModel> chatModelProvider,
                              AiGovernanceService aiGovernance,
                              SystemSettingService systemSettings,
                              InsightService insightService,
                              @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.customerRepository = customerRepository;
        this.chatModelProvider = chatModelProvider;
        this.aiGovernance = aiGovernance;
        this.systemSettings = systemSettings;
        this.insightService = insightService;
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
    public List<Dtos.WorkspaceTodoItem> computeTodos(AuthPrincipal principal, String scope, String lang) {
        return computeTodosFrom(loadScopedCustomers(principal, scope), lang);
    }

    /**
     * 以既載入的客戶清單計算待辦（避免重複查 DB）。
     *
     * @param customers 已載入的客戶清單
     * @return 待辦清單
     */
    List<Dtos.WorkspaceTodoItem> computeTodosFrom(List<Customer> customers, String lang) {
        boolean en = isEnglish(lang); // 待辦描述依語系產生（前端直接顯示 reason）
        var today = LocalDate.now();
        var todos = new ArrayList<Dtos.WorkspaceTodoItem>();
        for (var c : customers) {
            // 高風險客戶
            if ("HIGH".equalsIgnoreCase(c.getRiskLevel())) {
                todos.add(new Dtos.WorkspaceTodoItem("HIGH_RISK", c.getId(), c.getName(),
                        en ? "Customer risk level is HIGH — prioritize a proactive check-in."
                           : "客戶風險等級為高，建議優先聯繫關懷", "HIGH"));
            }
            // 即將續約（今日起 14 天內）
            var due = c.getRenewalDueDate();
            if (due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(RENEWAL_DUE_DAYS))) {
                todos.add(new Dtos.WorkspaceTodoItem("RENEWAL_DUE", c.getId(), c.getName(),
                        en ? "Renewal due on " + due + " — start the renewal process."
                           : "續約日 " + due + " 即將到期，建議啟動續約", "MEDIUM"));
            }
            // 逾期未結商機（開放中且預計成交日早於今日）
            for (var o : c.getOpportunities()) {
                boolean open = o.getStage() != OpportunityStage.CLOSED_WON
                        && o.getStage() != OpportunityStage.CLOSED_LOST;
                if (open && o.getExpectedCloseDate() != null && o.getExpectedCloseDate().isBefore(today)) {
                    todos.add(new Dtos.WorkspaceTodoItem("STALE_OPPORTUNITY", c.getId(), c.getName(),
                            en ? "Opportunity \"" + o.getName() + "\" is past its expected close date ("
                                    + o.getExpectedCloseDate() + ") — advance or close it."
                               : "商機「" + o.getName() + "」預計成交日已過（" + o.getExpectedCloseDate() + "），需推進或結案", "MEDIUM"));
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
    String buildRecommendationPrompt(AuthPrincipal principal, List<Dtos.WorkspaceTodoItem> todos, String lang) {
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
        sb.append(directive(lang));
        return sb.toString();
    }

    /**
     * deterministic fallback：無 AI 或失敗時的工作建議文字（接地於待辦）。
     *
     * @param principal 認證主體
     * @param todos 已算好的待辦
     * @return 保底建議文字
     */
    public String deterministicRecommendation(AuthPrincipal principal, List<Dtos.WorkspaceTodoItem> todos, String lang) {
        boolean en = isEnglish(lang);
        if (todos.isEmpty()) {
            return en ? "You have no pending tasks in the CRM database; keep your current follow-up cadence."
                      : "根據 CRM 資料庫，您目前沒有待辦事項，維持既有跟進節奏即可。";
        }
        var sb = new StringBuilder(en ? "Based on the CRM database, you have " + todos.size() + " task(s) to handle:\n"
                                      : "根據 CRM 資料庫，您有 " + todos.size() + " 項待辦需處理：\n");
        for (var t : todos) {
            sb.append("• ").append(t.customerName()).append(en ? " — " : "：").append(t.reason()).append("\n");
        }
        sb.append(en ? "Prioritize the items marked High." : "建議優先處理標記為高的項目。");
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
        return computeDraftsFrom(loadScopedCustomers(principal, scope));
    }

    /**
     * 規則式商機草稿（LLM 不可用時的 fallback），以既載入的客戶清單計算。
     *
     * @param customers 已載入的客戶清單
     * @return 規則式草稿清單
     */
    List<Dtos.SuggestedOpportunityDraft> computeDraftsFrom(List<Customer> customers) {
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

    /** LLM 回傳的商機建議原始結構（欄位名須與 prompt 範例一致，供 Spring AI .entity 反序列化）。 */
    record DraftSuggestion(Long customerId, String name, String suggestedStage,
                           java.math.BigDecimal amount, String rationale) {}

    /**
     * LLM 主導的商機建議：分析 scope 內客戶（風險/續約/互動/現有商機），產出具體商機建議與行動方向。
     * 接地防幻覺：候選客戶與 id 由 DB 提供，回傳後驗證 customerId 必須在範圍內、階段須為合法 enum；
     * 客戶名稱一律以 DB 為準（不採信 LLM）。LLM 不可用、失敗或驗證後為空 → 回退規則式 fallback。
     *
     * @param customers scope 內客戶（已載入）
     * @param chatModel ChatModel（null 表無金鑰）
     * @param fallback 規則式草稿（LLM 不可用時回退）
     * @return 商機建議草稿清單
     */
    List<Dtos.SuggestedOpportunityDraft> generateAiDrafts(List<Customer> customers, ChatModel chatModel,
                                                          List<Dtos.SuggestedOpportunityDraft> fallback, String lang) {
        if (chatModel == null || customers.isEmpty()) {
            return fallback;
        }
        // 建 id→客戶 對照，並組候選清單 grounding
        var byId = new java.util.HashMap<Long, Customer>();
        var today = LocalDate.now();
        var sb = new StringBuilder();
        sb.append("你是資深 B2B 業務教練。以下是某業務負責的客戶（資料庫事實，customerId 不可更改）：\n");
        for (var c : customers) {
            byId.put(c.getId(), c);
            long openOpps = c.getOpportunities().stream().filter(o ->
                    o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST).count();
            var due = c.getRenewalDueDate();
            sb.append("- customerId=").append(c.getId())
              .append("｜名稱=").append(c.getName())
              .append("｜產業=").append(c.getIndustry())
              .append("｜風險=").append(c.getRiskLevel() == null ? "未評" : c.getRiskLevel())
              .append("｜續約日=").append(due == null ? "未定" : due)
              .append("｜進行中商機數=").append(openOpps).append("\n");
        }
        sb.append("\n今日為 ").append(today)
          .append("。請挑出最值得「主動開立新商機」的客戶（至多 5 個，優先高風險、即將續約、或無進行中商機者），"
                + "為每個建議一筆商機，回傳 JSON 陣列，每個物件欄位："
                + "customerId(數字，必須取自上方清單)、"
                + "name(商機名稱，繁中)、"
                + "suggestedStage(僅能是 QUALIFICATION/PROPOSAL/NEGOTIATION 之一)、"
                + "amount(預估金額，整數元，依產業與客戶規模合理估算)、"
                + "rationale(繁中，說明『為何現在該推進』的行動方向，30-60 字)。"
                + "不要輸出清單以外的客戶，不要捏造 customerId。");
        sb.append(directive(lang));
        try {
            var draftSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT + systemLanguage(lang)).user(sb.toString());
            var draftOpts = systemSettings.resolveChatOptions();
            if (draftOpts != null) draftSpec = draftSpec.options(draftOpts);
            var raw = draftSpec
                    .call().entity(new org.springframework.core.ParameterizedTypeReference<List<DraftSuggestion>>() {});
            if (raw == null) {
                return fallback;
            }
            var result = new ArrayList<Dtos.SuggestedOpportunityDraft>();
            for (var s : raw) {
                if (s == null || s.customerId() == null) {
                    continue;
                }
                var c = byId.get(s.customerId());      // 驗證：customerId 必須在範圍內，否則丟棄（防幻覺/越權）
                if (c == null) {
                    continue;
                }
                var stage = normalizeStage(s.suggestedStage());
                var name = (s.name() == null || s.name().isBlank()) ? c.getName() + "跟進商機" : s.name().trim();
                var amount = (s.amount() != null && s.amount().signum() > 0) ? s.amount() : null;
                var rationale = s.rationale() == null ? "" : s.rationale().trim();
                result.add(new Dtos.SuggestedOpportunityDraft(c.getId(), c.getName(), name, stage, amount, rationale));
                if (result.size() >= 5) {
                    break;
                }
            }
            return result.isEmpty() ? fallback : result;
        } catch (Exception e) {
            log.warn("LLM 商機建議失敗，改用規則式 fallback：{}", e.getMessage());
            return fallback;
        }
    }

    /** 將 LLM 回傳的階段字串正規化為合法 enum 名（非法或空 → QUALIFICATION）。 */
    private String normalizeStage(String stage) {
        if (stage == null) {
            return "QUALIFICATION";
        }
        var s = stage.trim().toUpperCase();
        return switch (s) {
            case "QUALIFICATION", "PROPOSAL", "NEGOTIATION" -> s;
            default -> "QUALIFICATION";
        };
    }

    /**
     * 串流產生工作推薦：先送待辦與商機草稿，再串流 AI 總結；無金鑰/失敗走 deterministic fallback。
     * 串流回呼於另一執行緒、readOnly 交易結束後執行，故先在交易內把純值（todos/drafts/fallback）算好。
     *
     * @param principal 認證主體
     * @param scope 請求範圍（SALES 會被強制為自己）
     * @return SseEmitter
     */
    public SseEmitter streamRecommendation(AuthPrincipal principal, String scope, String lang) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // 交易內先載入客戶一次，算好純值（待辦、規則式 fallback、提示詞）
        var customers = loadScopedCustomers(principal, scope);
        var todos = computeTodosFrom(customers, lang);
        final String fallback = deterministicRecommendation(principal, todos, lang);
        final String userPrompt = buildRecommendationPrompt(principal, todos, lang);
        final String subject = principal.username();
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;

        // 先送待辦（前端即時呈現）
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "todos", "items", todos)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // LLM 主導的商機建議（失敗回退規則式）；於交易內呼叫，僅讀已載入客戶
        var ruleDrafts = computeDraftsFrom(customers);
        var drafts = generateAiDrafts(customers, chatModel, ruleDrafts, lang);
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "drafts", "items", drafts)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        streamLlmText(emitter, AiCallType.WORKSPACE_RECOMMENDATION, subject, userPrompt, fallback, chatModel, lang);
        return emitter;
    }

    /**
     * 共用串流核心：訂閱 {@code ChatClient.stream()} 逐塊送 SSE，完成/失敗/空白皆有 deterministic fallback，
     * 並以 {@code subject} 寫 ai_call_log（customerId 為 null）。回呼於另一執行緒執行，僅使用傳入純值。
     *
     * @param emitter SSE 發送器
     * @param type 呼叫類型（WORKSPACE_RECOMMENDATION / WORKSPACE_CHAT）
     * @param subject 分群鍵（username）
     * @param userPrompt 完整提示詞（已含 grounding）
     * @param fallback 預先算好的 deterministic 保底答案
     * @param chatModel ChatModel（null 表無金鑰，直接 fallback）
     */
    private void streamLlmText(SseEmitter emitter, AiCallType type, String subject,
                               String userPrompt, String fallback, ChatModel chatModel, String lang) {
        if (chatModel == null) {
            var saved = aiGovernance.record(type, null, subject, null, 0, 0, 0, false, false, fallback);
            SseHelper.sendContent(emitter, fallback);
            SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
            return;
        }
        var fullAnswer = new StringBuilder();
        var lastResponse = new AtomicReference<ChatResponse>();
        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT + systemLanguage(lang)).user(userPrompt);
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
                    log.warn("工作檯串流失敗，type={}，subject={}，改用 fallback：{}", type, subject, error.getMessage());
                    var partial = fullAnswer.toString().strip();
                    if (partial.isBlank()) {
                        var saved = aiGovernance.record(type, null, subject, null, 0, 0, 0, false, false, fallback);
                        SseHelper.sendContent(emitter, fallback);
                        SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                    } else {
                        var saved = aiGovernance.record(type, null, subject, null, 0, 0, 0, true, false, partial);
                        SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                    }
                },
                () -> {
                    var answer = fullAnswer.toString().strip();
                    if (answer.isBlank()) {
                        var saved = aiGovernance.record(type, null, subject, null, 0, 0, 0, false, false, fallback);
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
                    var saved = aiGovernance.record(type, null, subject, model, pt, ct, tt, true, false, answer);
                    SseHelper.sendSimpleTailAndComplete(emitter, saved.getId());
                }
        );
    }

    /**
     * 驗證 customerId 在呼叫者 scope 內，否則拋 EntityNotFoundException（不洩漏存在性）。
     *
     * @param principal 認證主體
     * @param scope 範圍
     * @param customerId 欲存取的客戶
     */
    public void assertCustomerVisible(AuthPrincipal principal, String scope, Long customerId) {
        boolean visible = loadScopedCustomers(principal, scope).stream()
                .anyMatch(c -> c.getId().equals(customerId));
        if (!visible) {
            throw new jakarta.persistence.EntityNotFoundException("查無此客戶資料：" + customerId);
        }
    }

    /**
     * 個人問答串流。有 customerId：驗證可見性後委派既有單客戶對話；無 customerId：對個人客戶組合做總覽問答。
     *
     * @param principal 認證主體
     * @param req 問答請求（scope / customerId / message）
     * @return SseEmitter
     */
    public SseEmitter streamChat(AuthPrincipal principal, Dtos.WorkspaceChatRequest req) {
        if (req.customerId() != null) {
            // 深入單客戶：先驗證可見性，再複用既有客戶對話（含 RAG/PII/治理）
            assertCustomerVisible(principal, req.scope(), req.customerId());
            return insightService.streamChat(new Dtos.ChatRequest(req.customerId(), req.message(), req.lang()));
        }
        // 總覽問答：以個人客戶組合摘要當 grounding
        SseEmitter emitter = new SseEmitter(300_000L);
        var customers = loadScopedCustomers(principal, req.scope());
        final String subject = principal.username();
        final String userPrompt = buildPortfolioPrompt(principal, customers, req.message(), req.lang());
        final String fallback = deterministicPortfolioAnswer(customers);
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        streamLlmText(emitter, AiCallType.WORKSPACE_CHAT, subject, userPrompt, fallback, chatModel, req.lang());
        return emitter;
    }

    /**
     * 組個人客戶組合的 grounding 提示詞（名稱 / 風險 / 續約 / 開放商機數），明示不可竄改。
     */
    String buildPortfolioPrompt(AuthPrincipal principal, List<Customer> customers, String question, String lang) {
        var today = LocalDate.now();
        var sb = new StringBuilder();
        sb.append("以下為業務「").append(principal.displayName()).append("」負責的客戶組合摘要（資料庫事實，請勿更改）：\n");
        for (var c : customers) {
            long openOpps = c.getOpportunities().stream().filter(o ->
                    o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST).count();
            sb.append("- ").append(c.getName())
              .append("｜風險:").append(c.getRiskLevel() == null ? "未評" : c.getRiskLevel())
              .append("｜續約日:").append(c.getRenewalDueDate() == null ? "未定" : c.getRenewalDueDate())
              .append("｜進行中商機:").append(openOpps).append("\n");
        }
        sb.append("\n（今日為 ").append(today).append("）\n# 業務提問\n").append(question)
          .append("\n請用繁體中文，只根據上述客戶資料回答，不要編造未列出的客戶或數字。");
        sb.append(directive(lang));
        return sb.toString();
    }

    /**
     * 讀取工作推薦：AI 總結取自最近一筆 WORKSPACE_RECOMMENDATION 紀錄（無則 null），待辦即時重算，drafts 不快取（重新產生才有）。
     *
     * @param principal 認證主體
     * @param scope 請求範圍
     * @return 工作推薦回應
     */
    public Dtos.WorkspaceRecommendationResponse getRecommendation(AuthPrincipal principal, String scope, String lang) {
        var todos = computeTodos(principal, scope, lang);
        var history = aiGovernance.workspaceHistory(principal.username());
        var last = history.stream()
                .filter(h -> AiCallType.WORKSPACE_RECOMMENDATION.name().equals(h.callType()))
                .findFirst()
                .orElse(null);
        String summary = last == null ? null : last.answer();
        String model = last == null ? null : last.model();
        String generatedAt = last == null || last.createdAt() == null ? null : last.createdAt().toString();
        return new Dtos.WorkspaceRecommendationResponse(summary, model, generatedAt, todos, List.of());
    }

    /**
     * 本人工作檯 AI 歷程（WORKSPACE_RECOMMENDATION + WORKSPACE_CHAT）。
     *
     * @param principal 認證主體
     * @return AI 呼叫歷程（新到舊）
     */
    public List<Dtos.AiCallHistoryItem> history(AuthPrincipal principal) {
        return aiGovernance.workspaceHistory(principal.username());
    }

    /** 總覽問答的 deterministic fallback。 */
    String deterministicPortfolioAnswer(List<Customer> customers) {
        long high = customers.stream().filter(c -> "HIGH".equalsIgnoreCase(c.getRiskLevel())).count();
        return "根據 CRM 資料庫，您目前負責 " + customers.size() + " 位客戶，其中高風險 " + high
                + " 位。建議優先關注高風險與近期續約客戶。（AI 服務暫不可用，以上為系統統計）";
    }
}
