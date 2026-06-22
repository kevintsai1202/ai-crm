package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.ManagerInsight;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.ManagerInsightRepository;
import static java.util.stream.Collectors.joining;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manager AI 分析服務（模組 C）：團隊整體診斷與個別業務 coaching。
 *
 * <p>採可切換策略，與 InsightService 一致：設定 OpenAI api-key 時呼叫真實 LLM；
 * 未設定或失敗時 fallback 回 deterministic 摘要。統計數字一律由 ManagerAnalyticsService
 * 以 Java/DB 計算後餵給 LLM 當 grounding context，防止幻覺。每次呼叫（含 fallback）寫一筆 ai_call_log。
 * 「點按生成」時 upsert manager_insight 快取，進頁先讀快取顯示上次分析時間。</p>
 */
@Service
@Transactional(readOnly = true)
public class ManagerInsightService {

    private static final Logger log = LoggerFactory.getLogger(ManagerInsightService.class);

    /** 系統提示詞：約束角色（銷售主管教練）、語言與防幻覺邊界。 */
    private static final String SYSTEM_PROMPT = """
            你是一位資深 B2B 銷售主管教練。請僅根據提供的「團隊/業務統計數字」與「客戶摘要」回答，
            不得自行編造數字、價格或未經確認的承諾。回答需簡潔、條理清楚、可執行，並使用繁體中文。""";

    /** 業務統計聚合（模組 B），作為團隊診斷的資料來源。 */
    private final ManagerAnalyticsService analytics;

    /** 快取存取。 */
    private final ManagerInsightRepository insights;

    /** 客戶存取（個別 coaching 需各客戶的商機/互動）。 */
    private final CustomerRepository customers;

    /** AI 治理：寫入每次呼叫的用量與答案。 */
    private final AiGovernanceService aiGovernance;

    /** OpenAI ChatModel 提供者（Spring AI 2.0 即使無金鑰仍建立 bean）。 */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** 是否啟用真實 OpenAI：以 api-key 是否實際設定為準。 */
    private final boolean aiEnabled;

    /** 系統設定服務：提供 AI 模型覆蓋（DB 設定優先於環境變數）。 */
    private final SystemSettingService systemSettings;

    /** InsightService：借用 sendSimpleTailAndComplete（無 citations/risk 的 SSE 尾段）。 */
    private final InsightService insightService;

    public ManagerInsightService(ManagerAnalyticsService analytics,
                                 ManagerInsightRepository insights,
                                 CustomerRepository customers,
                                 AiGovernanceService aiGovernance,
                                 ObjectProvider<ChatModel> chatModelProvider,
                                 SystemSettingService systemSettings,
                                 InsightService insightService,
                                 @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.analytics = analytics;
        this.insights = insights;
        this.customers = customers;
        this.aiGovernance = aiGovernance;
        this.chatModelProvider = chatModelProvider;
        this.systemSettings = systemSettings;
        this.insightService = insightService;
        this.aiEnabled = openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /** callLlm 回傳：答案與模型名（fallback 時 model 為 null）。 */
    private record LlmResult(String answer, String model) {}

    /**
     * 讀團隊診斷快取（未生成回 null）。
     *
     * @return 快取回應或 null
     */
    @Transactional(readOnly = true)
    public Dtos.ManagerInsightResponse getTeamInsight() {
        return insights.findFirstByScope("TEAM").map(this::toResponse).orElse(null);
    }

    /**
     * 讀個別業務 coaching 快取（未生成回 null）。
     *
     * @param owner 業務顯示名稱
     * @return 快取回應或 null
     */
    @Transactional(readOnly = true)
    public Dtos.ManagerInsightResponse getOwnerInsight(String owner) {
        return insights.findFirstByScopeAndOwnerName("OWNER", owner).map(this::toResponse).orElse(null);
    }

    /**
     * 生成團隊整體診斷並 upsert 快取（呼叫 LLM，無金鑰走 fallback）。
     *
     * @return 生成後的回應
     */
    @Transactional
    public Dtos.ManagerInsightResponse generateTeamInsight() {
        var data = analytics.analytics();
        var prompt = buildTeamPrompt(data);
        var fallback = deterministicTeam(data);
        var result = callLlm(AiCallType.TEAM_ANALYSIS, null, prompt, fallback);
        var saved = upsert("TEAM", null, result.answer(), result.model());
        return toResponse(saved);
    }

    /**
     * 生成個別業務 coaching 並 upsert 快取。
     *
     * @param owner 業務顯示名稱
     * @return 生成後的回應
     */
    @Transactional
    public Dtos.ManagerInsightResponse generateOwnerInsight(String owner) {
        var ownerCustomers = customers.findAll().stream()
                .filter(c -> owner.equals(c.getOwnerName()))
                .toList();
        if (ownerCustomers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此業務：" + owner);
        }
        var prompt = buildOwnerPrompt(owner, ownerCustomers);
        var fallback = deterministicOwner(owner, ownerCustomers);
        var result = callLlm(AiCallType.OWNER_COACHING, owner, prompt, fallback);
        var saved = upsert("OWNER", owner, result.answer(), result.model());
        return toResponse(saved);
    }

    /** 測試輔助：取任一存在的業務名（避免測試硬編種子名稱）。 */
    @Transactional(readOnly = true)
    public String firstOwnerNameForTest() {
        return customers.findDistinctOwners().stream().findFirst().orElse("");
    }

    /**
     * 呼叫 LLM；無金鑰、回空白或例外皆 fallback。每次（含 fallback）寫 ai_call_log（customerId 為 null）。
     *
     * @param type 呼叫類型
     * @param subject 分群鍵（OWNER_COACHING 存 ownerName；TEAM_ANALYSIS 傳 null）
     * @param userPrompt 使用者提示詞（已含 grounding context）
     * @param fallbackAnswer deterministic 保底答案
     * @return 答案與模型名
     */
    private LlmResult callLlm(AiCallType type, String subject, String userPrompt, String fallbackAnswer) {
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        if (chatModel == null) {
            aiGovernance.record(type, null, subject, null, 0, 0, 0, false, true, fallbackAnswer);
            return new LlmResult(fallbackAnswer, null);
        }
        try {
            var spec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
            var opts = systemSettings.resolveChatOptions();
            if (opts != null) spec = spec.options(opts);
            var chatResponse = spec.call().chatResponse();
            var content = chatResponse == null ? null : chatResponse.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                log.warn("OpenAI {} 回傳空白，改用 fallback", type);
                aiGovernance.record(type, null, subject, null, 0, 0, 0, false, true, fallbackAnswer);
                return new LlmResult(fallbackAnswer, null);
            }
            var metadata = chatResponse.getMetadata();
            var usage = metadata == null ? null : metadata.getUsage();
            var model = metadata == null ? null : metadata.getModel();
            Integer pt = usage == null ? null : usage.getPromptTokens();
            Integer ct = usage == null ? null : usage.getCompletionTokens();
            Integer tt = usage == null ? null : usage.getTotalTokens();
            var answer = content.strip();
            aiGovernance.record(type, null, subject, model, pt, ct, tt, true, true, answer);
            return new LlmResult(answer, model);
        } catch (Exception e) {
            log.warn("OpenAI {} 呼叫失敗，改用 fallback：{}", type, e.getMessage());
            aiGovernance.record(type, null, subject, null, 0, 0, 0, false, true, fallbackAnswer);
            return new LlmResult(fallbackAnswer, null);
        }
    }

    /**
     * upsert 快取：存在則更新同列，否則新增。
     *
     * @param scope TEAM / OWNER
     * @param ownerName 業務名（TEAM 傳 null）
     * @param content Markdown
     * @param model 模型名（fallback 為 null）
     * @return 已存的快取列
     */
    private ManagerInsight upsert(String scope, String ownerName, String content, String model) {
        var now = Instant.now();
        var existing = ownerName == null
                ? insights.findFirstByScope(scope)
                : insights.findFirstByScopeAndOwnerName(scope, ownerName);
        var entity = existing.orElseGet(() -> new ManagerInsight(scope, ownerName, content, model, now));
        if (existing.isPresent()) {
            entity.update(content, model, now);
        }
        return insights.save(entity);
    }

    /** entity → DTO。 */
    private Dtos.ManagerInsightResponse toResponse(ManagerInsight m) {
        return new Dtos.ManagerInsightResponse(m.getScope(), m.getOwnerName(), m.getContent(), m.getModel(), m.getGeneratedAt());
    }

    /**
     * 組裝團隊診斷 prompt：餵全體 OwnerStats 表 + 團隊總覽。
     *
     * @param data 模組 B 分析結果
     * @return prompt 文字
     */
    private String buildTeamPrompt(Dtos.ManagerAnalyticsResponse data) {
        var rows = data.owners().stream().map(o -> String.format(
                "%s｜客戶%d｜高風險%d｜進行中商機%s(%d筆)｜已成交%s(%d筆)｜成交率%.0f%%｜平均互動間隔%s天｜情緒%s｜本月續約%d｜本季續約%d",
                o.ownerName(), o.customerCount(), o.highRiskCount(),
                o.pipelineAmount(), o.activeOpportunityCount(), o.wonAmount(), o.wonCount(), o.winRate() * 100,
                o.avgDaysSinceInteraction() == null ? "—" : String.format("%.0f", o.avgDaysSinceInteraction()),
                o.avgSentimentScore() == null ? "—" : String.format("%.1f", o.avgSentimentScore()),
                o.renewalsThisMonth(), o.renewalsThisQuarter()))
                .collect(joining("\n"));
        var t = data.team();
        return """
                以下是全公司各業務的績效統計（系統計算，請勿更改數字）。

                # 團隊總覽
                客戶總數：%d｜全團隊成交金額：%s｜進行中商機總額：%s｜高風險客戶數：%d｜平均成交率：%.0f%%｜業務人數：%d

                # 各業務統計（依成交金額降序）
                %s

                # 任務
                請產出「團隊業務分析報告」（Markdown 格式），務必涵蓋：
                1. **團隊整體診斷**：top 表現者、落後者、共通問題與建議。
                2. **逐業務點評**：依排行榜對每位業務各給一段「優勢 + 加強建議」。
                使用繁體中文、條理清楚、勿編造數字。
                """.formatted(t.totalCustomers(), t.totalWonAmount(), t.totalPipeline(), t.totalHighRisk(),
                t.avgWinRate() * 100, t.ownerCount(), rows.isBlank() ? "（無業務資料）" : rows);
    }

    /**
     * 組裝個別 coaching prompt：餵該業務名下客戶的商機/風險/近期互動摘要（PII 遮罩）。
     *
     * @param owner 業務名
     * @param ownerCustomers 該業務客戶
     * @return prompt 文字
     */
    private String buildOwnerPrompt(String owner, List<Customer> ownerCustomers) {
        var rows = ownerCustomers.stream().map(c -> {
            var amount = c.getOpportunities().stream().map(Opportunity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var lastDate = c.getInteractions().stream().map(Interaction::getOccurredAt)
                    .max(Comparator.naturalOrder()).map(d -> d.toLocalDate().toString()).orElse("無");
            return "- " + c.getName() + "｜產業" + c.getIndustry() + "｜風險" + c.getRiskLevel()
                    + "｜商機" + c.getOpportunities().size() + "筆/" + amount
                    + "｜最近互動" + lastDate
                    + "｜續約日" + (c.getRenewalDueDate() == null ? "未定" : c.getRenewalDueDate());
        }).collect(joining("\n"));
        return """
                以下是業務「%s」名下所有客戶的摘要（系統計算，請勿更改數字）。

                # 客戶清單（名稱｜產業｜風險｜商機｜最近互動｜續約日）
                %s

                # 任務
                請以銷售主管視角產出對「%s」的「輔導報告」（Markdown 格式），務必涵蓋：
                1. **該優先跟進的客戶**（附理由）。
                2. **需要立即處理的風險**（高風險 / 續約逾期 / 久未互動）。
                3. **給該業務的具體輔導建議**。
                使用繁體中文、條理清楚、勿編造數字。
                """.formatted(owner, PiiMasker.mask(rows.isBlank() ? "（無客戶）" : rows), owner);
    }

    /**
     * 團隊診斷 deterministic fallback：由統計數字直接彙總。
     */
    private String deterministicTeam(Dtos.ManagerAnalyticsResponse data) {
        var t = data.team();
        var top = data.owners().isEmpty() ? "—" : data.owners().get(0).ownerName();
        return """
                ## 團隊業務分析（教學版摘要）

                - **業務人數**：%d
                - **客戶總數**：%d
                - **全團隊成交金額**：%s
                - **進行中商機總額**：%s
                - **高風險客戶數**：%d
                - **平均成交率**：%.0f%%
                - **成交金額最高**：%s

                > 目前未設定 OpenAI 金鑰，顯示為系統彙總的 deterministic 摘要；設定金鑰後可取得 AI 團隊診斷與逐業務點評。
                """.formatted(t.ownerCount(), t.totalCustomers(), t.totalWonAmount(), t.totalPipeline(),
                t.totalHighRisk(), t.avgWinRate() * 100, top);
    }

    /**
     * 個別 coaching deterministic fallback。
     */
    private String deterministicOwner(String owner, List<Customer> ownerCustomers) {
        long highRisk = ownerCustomers.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        return """
                ## %s 的輔導摘要（教學版）

                - **負責客戶數**：%d
                - **高風險客戶數**：%d

                > 目前未設定 OpenAI 金鑰，顯示為系統彙總的 deterministic 摘要；設定金鑰後可取得 AI 個別輔導建議。
                """.formatted(owner, ownerCustomers.size(), highRisk);
    }

    /**
     * 以 SSE 串流推送團隊整體診斷；完成後 upsert 快取。
     * 函式級註解：在方法開頭同步算好 prompt 與 fallback，callback 執行緒只使用純值；upsert 與 aiGovernance.record 各自有獨立交易，可於 callback 安全呼叫。
     *
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamTeamInsight() {
        var data = analytics.analytics();
        final String prompt = buildTeamPrompt(data);
        final String fallback = deterministicTeam(data);
        final var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        SseEmitter emitter = new SseEmitter(300_000L);

        if (chatModel == null) {
            aiGovernance.record(AiCallType.TEAM_ANALYSIS, null, null, null, 0, 0, 0, false, true, fallback);
            upsert("TEAM", null, fallback, null);
            insightService.sendContent(emitter, fallback);
            insightService.sendSimpleTailAndComplete(emitter, null);
            return emitter;
        }

        var fullAnswer = new StringBuilder();
        var lastResp = new java.util.concurrent.atomic.AtomicReference<org.springframework.ai.chat.model.ChatResponse>();
        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(prompt);
        var opts = systemSettings.resolveChatOptions();
        if (opts != null) streamSpec = streamSpec.options(opts);
        streamSpec.stream().chatResponse().subscribe(
                cr -> {
                    lastResp.set(cr);
                    var result = cr.getResult();
                    var text = result == null ? null : result.getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullAnswer.append(text);
                        insightService.sendContent(emitter, text);
                    }
                },
                error -> {
                    log.warn("OpenAI 團隊診斷串流失敗，改用 fallback：{}", error.getMessage());
                    aiGovernance.record(AiCallType.TEAM_ANALYSIS, null, null, null, 0, 0, 0, false, true, fallback);
                    upsert("TEAM", null, fallback, null);
                    if (fullAnswer.toString().isBlank()) insightService.sendContent(emitter, fallback);
                    insightService.sendSimpleTailAndComplete(emitter, null);
                },
                () -> {
                    var answer = fullAnswer.toString().strip();
                    if (answer.isBlank()) {
                        aiGovernance.record(AiCallType.TEAM_ANALYSIS, null, null, null, 0, 0, 0, false, true, fallback);
                        upsert("TEAM", null, fallback, null);
                        insightService.sendContent(emitter, fallback);
                        insightService.sendSimpleTailAndComplete(emitter, null);
                        return;
                    }
                    var meta = lastResp.get() == null ? null : lastResp.get().getMetadata();
                    var usage = meta == null ? null : meta.getUsage();
                    var model = meta == null ? null : meta.getModel();
                    Integer pt = usage == null ? null : usage.getPromptTokens();
                    Integer ct = usage == null ? null : usage.getCompletionTokens();
                    Integer tt = usage == null ? null : usage.getTotalTokens();
                    aiGovernance.record(AiCallType.TEAM_ANALYSIS, null, null, model, pt, ct, tt, true, true, answer);
                    upsert("TEAM", null, answer, model);
                    insightService.sendSimpleTailAndComplete(emitter, null);
                });
        return emitter;
    }

    /**
     * 以 SSE 串流推送個別業務 coaching；完成後 upsert 快取。
     *
     * @param owner 業務顯示名稱
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamOwnerInsight(String owner) {
        var ownerCustomers = customers.findAll().stream()
                .filter(c -> owner.equals(c.getOwnerName())).toList();
        if (ownerCustomers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此業務：" + owner);
        }
        final String prompt = buildOwnerPrompt(owner, ownerCustomers);
        final String fallback = deterministicOwner(owner, ownerCustomers);
        final var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        SseEmitter emitter = new SseEmitter(300_000L);

        if (chatModel == null) {
            aiGovernance.record(AiCallType.OWNER_COACHING, null, owner, null, 0, 0, 0, false, true, fallback);
            upsert("OWNER", owner, fallback, null);
            insightService.sendContent(emitter, fallback);
            insightService.sendSimpleTailAndComplete(emitter, null);
            return emitter;
        }

        var fullAnswer = new StringBuilder();
        var lastResp = new java.util.concurrent.atomic.AtomicReference<org.springframework.ai.chat.model.ChatResponse>();
        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(prompt);
        var opts = systemSettings.resolveChatOptions();
        if (opts != null) streamSpec = streamSpec.options(opts);
        streamSpec.stream().chatResponse().subscribe(
                cr -> {
                    lastResp.set(cr);
                    var result = cr.getResult();
                    var text = result == null ? null : result.getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullAnswer.append(text);
                        insightService.sendContent(emitter, text);
                    }
                },
                error -> {
                    log.warn("OpenAI 業務 coaching 串流失敗，改用 fallback：{}", error.getMessage());
                    aiGovernance.record(AiCallType.OWNER_COACHING, null, owner, null, 0, 0, 0, false, true, fallback);
                    upsert("OWNER", owner, fallback, null);
                    if (fullAnswer.toString().isBlank()) insightService.sendContent(emitter, fallback);
                    insightService.sendSimpleTailAndComplete(emitter, null);
                },
                () -> {
                    var answer = fullAnswer.toString().strip();
                    if (answer.isBlank()) {
                        aiGovernance.record(AiCallType.OWNER_COACHING, null, owner, null, 0, 0, 0, false, true, fallback);
                        upsert("OWNER", owner, fallback, null);
                        insightService.sendContent(emitter, fallback);
                        insightService.sendSimpleTailAndComplete(emitter, null);
                        return;
                    }
                    var meta = lastResp.get() == null ? null : lastResp.get().getMetadata();
                    var usage = meta == null ? null : meta.getUsage();
                    var model = meta == null ? null : meta.getModel();
                    Integer pt = usage == null ? null : usage.getPromptTokens();
                    Integer ct = usage == null ? null : usage.getCompletionTokens();
                    Integer tt = usage == null ? null : usage.getTotalTokens();
                    aiGovernance.record(AiCallType.OWNER_COACHING, null, owner, model, pt, ct, tt, true, true, answer);
                    upsert("OWNER", owner, answer, model);
                    insightService.sendSimpleTailAndComplete(emitter, null);
                });
        return emitter;
    }
}
