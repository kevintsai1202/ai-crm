package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.ChatRole;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.repository.KnowledgeDocumentRepository;
import com.aicrm.crm.repository.KnowledgeVectorRepository;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 洞察服務：串接 Spring AI + OpenAI 產生客戶分析回答。
 *
 * <p>採可切換策略：當設定了 OpenAI api-key（{@link ChatModel} bean 存在）時呼叫真實 LLM；
 * 未設定或呼叫失敗時，fallback 回 deterministic 教學流程，確保本機驗收不依賴外部金鑰。
 * 風險分數與 RAG 引用一律由資料庫與 Java 計算後餵給 LLM 當作 grounding context，避免幻覺。</p>
 */
@Service
@Transactional(readOnly = true)
public class InsightService {

    /** 記錄 OpenAI 呼叫失敗等事件。 */
    private static final Logger log = LoggerFactory.getLogger(InsightService.class);

    /**
     * AI 助理的系統提示詞，約束角色、語言與防幻覺邊界。
     * 此處集中定義業務語氣與安全規則，可依需求調整。
     */
    private static final String SYSTEM_PROMPT = """
            你是一位專業的 B2B CRM 業務助理。請僅根據提供的「客戶資料」「風險評分」「知識庫引用」回答，
            不得自行編造價格、折扣或任何未經確認的承諾。回答需簡潔、條理清楚、可執行，並使用繁體中文。
            若資料不足，請明確指出需要補齊哪些資訊，而非臆測。""";

    /**
     * 客戶 360 度整體評估的任務指令（接在 grounding context 之後）。
     * 抽成常數供同步 {@link #customerAssessment} 與串流 {@link #streamCustomerAssessment} 共用，避免兩邊走鐘。
     */
    private static final String ASSESSMENT_INSTRUCTION = """

            # 任務
            請對此客戶產出「360 度整體評估報告」（Markdown 格式），務必涵蓋：
            1. **客戶健康度總評**（一句話定性 + 依據）
            2. **商機 / Pipeline 分析**（金額、階段分布、最該推進的商機）
            3. **風險與預警**（解讀流失與續約延遲分數、互動訊號）
            4. **下一步建議行動**（具體、可執行，依知識庫條款，勿編造價格）
            """;

    /** 客戶查詢服務。 */
    private final CustomerService customers;

    /** 知識文件資料來源（fallback 用，依 similarityHint 排序）。 */
    private final KnowledgeDocumentRepository knowledgeDocuments;

    /** 向量嵌入用戶端：將查詢文字嵌入為向量供檢索。 */
    private final EmbeddingClient embeddingClient;

    /** 知識文件向量存取：pgvector cosine 近鄰檢索。 */
    private final KnowledgeVectorRepository vectorRepo;

    /**
     * OpenAI ChatModel 提供者。
     * 注意：Spring AI 2.0 即使 api-key 為空仍會建立此 bean，故不可僅以 bean 是否存在判斷是否啟用。
     */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** 是否啟用真實 OpenAI：以 api-key 是否實際設定為準。 */
    private final boolean aiEnabled;

    /** AI 治理服務：記錄每次 LLM 呼叫的用量與答案。 */
    private final AiGovernanceService aiGovernance;

    /** 對話記憶服務：時序 + 語意向量雙路召回與保存（可寫交易，與本服務 readOnly 隔離）。 */
    private final ChatMemoryService chatMemory;

    /** 系統設定服務：提供 AI 模型覆蓋（DB 設定優先於環境變數）。 */
    private final SystemSettingService systemSettings;

    public InsightService(CustomerService customers,
                          KnowledgeDocumentRepository knowledgeDocuments,
                          EmbeddingClient embeddingClient,
                          KnowledgeVectorRepository vectorRepo,
                          ObjectProvider<ChatModel> chatModelProvider,
                          AiGovernanceService aiGovernance,
                          ChatMemoryService chatMemory,
                          SystemSettingService systemSettings,
                          @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.customers = customers;
        this.knowledgeDocuments = knowledgeDocuments;
        this.embeddingClient = embeddingClient;
        this.vectorRepo = vectorRepo;
        this.chatModelProvider = chatModelProvider;
        this.aiGovernance = aiGovernance;
        this.chatMemory = chatMemory;
        this.systemSettings = systemSettings;
        this.aiEnabled = openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /**
     * callLlm 的回傳值：回答文字與其對應的 AI 呼叫紀錄 id。
     *
     * @param answer 回答文字
     * @param callId AI 呼叫紀錄 id（可為 null）
     */
    private record LlmResult(String answer, Long callId) {}

    /**
     * 產生 AI 聊天回答（非串流）。風險與引用為事實依據，answer 由 LLM 或 fallback 產生。
     *
     * @param request 聊天請求
     * @return 聊天回答
     */
    public Dtos.ChatResponse chat(Dtos.ChatRequest request) {
        var customer = customers.findDetail(request.customerId());
        var risk = calculateOpportunityRisk(customer);
        var citations = loadCitations(request.message());
        // 順序：先 recall（不含本則）→ 產生 answer → 再 save 本輪 user+assistant，避免把本則當記憶
        var memory = chatMemory.recall(request.customerId(), request.message());
        var result = buildAnswer(AiCallType.CHAT, customer, risk, citations, memory, request.message());
        chatMemory.save(request.customerId(), ChatRole.USER, request.message());
        chatMemory.save(request.customerId(), ChatRole.ASSISTANT, result.answer());
        return new Dtos.ChatResponse(result.answer(), citations, risk, result.callId());
    }

    /**
     * 以 SSE 真串流推送 AI 聊天回答：直接把 LLM 逐塊產生的 token 轉送前端，不再「先全產生再逐字慢吐」。
     *
     * <p>函式級註解：舊版用固定 60 秒 emitter + 每字 {@code sleep(40ms)} 的假打字機，回答一旦超過約 1500 字
     * 就會在吐到一半時逾時失敗。改為 Spring AI {@code ChatClient.stream()} 真串流後，token 邊產生邊送，
     * 回答越長反而越快吐完，徹底消除逾時。</p>
     *
     * <p><b>執行緒/交易注意</b>：Reactor 的 subscribe callback 會在另一條執行緒、且本方法的 readOnly 交易
     * 結束後才執行，故所有需要存取 entity LAZY 關聯的計算（grounding context、deterministic fallback 文字）
     * 都必須在 subscribe「之前」就在交易內算成純字串；callback 內只使用 String / record DTO / Long，
     * 避免 {@code LazyInitializationException}。{@link AiGovernanceService#record} 與
     * {@link ChatMemoryService#save} 皆為 {@code REQUIRES_NEW}，於 callback 執行緒呼叫安全。</p>
     *
     * @param request 聊天請求
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamChat(Dtos.ChatRequest request) {
        // 設 5 分鐘逾時作為「卡住保險絲」：真串流下 token 持續流動，正常回答遠在此之前完成
        SseEmitter emitter = new SseEmitter(300_000L);

        final Long customerId = request.customerId();
        final String userMessage = request.message();
        var customer = customers.findDetail(customerId);
        var risk = calculateOpportunityRisk(customer);
        var citations = loadCitations(userMessage);
        // 順序：先 recall（不含本則）→ 產生 answer → 再 save 本輪，避免把本則當記憶
        var memory = chatMemory.recall(customerId, userMessage);
        // 在交易內先把 deterministic fallback 文字算好（會讀 customer LAZY 關聯），供 callback 執行緒安全使用
        final String fallbackAnswer = deterministicAnswer(customer, risk);
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        var prompt = buildGroundingContext(customer, risk, citations, memory) + "\n# 業務提問\n" + userMessage + "\n";

        // 對話：最終答案出爐後才存「本輪 user + assistant」對話記憶（延後到串流完成，保持首字延遲低）
        streamAnswer(emitter, AiCallType.CHAT, customerId, chatModel, prompt, citations, risk, fallbackAnswer,
                answer -> {
                    chatMemory.save(customerId, ChatRole.USER, userMessage);
                    chatMemory.save(customerId, ChatRole.ASSISTANT, answer);
                });
        return emitter;
    }

    /**
     * 以 SSE 真串流推送客戶 360 度整體評估報告。與 {@link #streamChat} 共用串流核心，
     * 差別僅在 prompt 為評估指令、類型為 ASSESSMENT、且不寫對話記憶。
     *
     * <p>函式級註解：同步版 {@link #customerAssessment} 走 axios JSON、整份產生完才回傳，
     * 報告偏長時容易撞前端/閘道逾時；串流版邊產生邊送，連線持續有資料、首字 1~2 秒即到，結構上免疫逾時。</p>
     *
     * @param customerId 客戶 ID
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamCustomerAssessment(Long customerId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        var customer = customers.findDetail(customerId);
        var risk = calculateOpportunityRisk(customer);
        var citations = loadCitations(customer.getName() + " " + customer.getIndustry() + " 續約 風險 評估");
        // 在交易內先算好 fallback 文字（讀 LAZY 關聯），供 callback 執行緒安全使用
        final String fallbackAnswer = deterministicAnswer(customer, risk);
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        var prompt = buildGroundingContext(customer, risk, citations, "") + ASSESSMENT_INSTRUCTION;

        // 評估非對話，不寫對話記憶 → onFinalAnswer 傳 null
        streamAnswer(emitter, AiCallType.ASSESSMENT, customerId, chatModel, prompt, citations, risk, fallbackAnswer, null);
        return emitter;
    }

    /**
     * 串流核心：訂閱 Spring AI {@code ChatClient.stream()}，逐塊 token 即時送 SSE；完成後寫 ai_call_log、
     * 補送 citations/risk/callId/[DONE]。無金鑰、串流失敗或回空白皆 fallback 至 deterministic 完整答案。
     *
     * <p><b>執行緒/交易注意</b>：所有 callback 在另一執行緒、本方法呼叫端的 readOnly 交易結束後才執行，
     * 故僅可使用傳入的純值（String / record DTO / Long）；fallbackAnswer 須由呼叫端在交易內預先算好。
     * {@code aiGovernance.record} 與 {@code onFinalAnswer} 內的 {@code chatMemory.save} 皆 REQUIRES_NEW，
     * 於 callback 執行緒呼叫安全。</p>
     *
     * @param emitter SSE 發送器
     * @param type AI 呼叫類型（CHAT / ASSESSMENT）
     * @param customerId 客戶 ID（寫 ai_call_log 用）
     * @param chatModel ChatModel（null 表示無金鑰，直接走 fallback）
     * @param userPrompt 完整使用者提示詞（已含 grounding context）
     * @param citations RAG 引用（串流尾段送出）
     * @param risk 風險評分（串流尾段送出）
     * @param fallbackAnswer 預先算好的 deterministic 保底答案
     * @param onFinalAnswer 取得最終答案後的回呼（chat 用來存對話記憶；assessment 傳 null）
     */
    private void streamAnswer(SseEmitter emitter, AiCallType type, Long customerId, ChatModel chatModel,
                             String userPrompt, List<Dtos.CitationResponse> citations, Dtos.RiskResponse risk,
                             String fallbackAnswer, java.util.function.Consumer<String> onFinalAnswer) {
        // 無金鑰：直接 deterministic fallback，一次送出完整回答後結束
        if (chatModel == null) {
            finalizeFallback(emitter, type, customerId, citations, risk, fallbackAnswer, onFinalAnswer);
            return;
        }

        var fullAnswer = new StringBuilder();
        var lastResponse = new java.util.concurrent.atomic.AtomicReference<org.springframework.ai.chat.model.ChatResponse>();

        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
        var streamOpts = systemSettings.resolveChatOptions();
        if (streamOpts != null) streamSpec = streamSpec.options(streamOpts);
        streamSpec.stream().chatResponse()
                .subscribe(
                        chatResponse -> {
                            lastResponse.set(chatResponse);
                            var result = chatResponse.getResult();
                            var text = result == null ? null : result.getOutput().getText();
                            if (text != null && !text.isEmpty()) {
                                fullAnswer.append(text);
                                sendContent(emitter, text);
                            }
                        },
                        error -> {
                            var partial = fullAnswer.toString().strip();
                            log.warn("OpenAI 串流呼叫失敗，type={}，customerId={}，改用 fallback：{}", type, customerId, error.getMessage());
                            if (partial.isBlank()) {
                                // 尚未送出任何內容 → 送完整 deterministic fallback
                                finalizeFallback(emitter, type, customerId, citations, risk, fallbackAnswer, onFinalAnswer);
                            } else {
                                // 已串流部分內容後才中斷 → 用已收到內容收尾，避免再疊一份 fallback 造成重複
                                finalizeStreamed(emitter, type, customerId, citations, risk, partial, null, 0, 0, 0, onFinalAnswer);
                            }
                        },
                        () -> {
                            var answer = fullAnswer.toString().strip();
                            if (answer.isBlank()) {
                                // 串流回空白 → 補送完整 fallback
                                finalizeFallback(emitter, type, customerId, citations, risk, fallbackAnswer, onFinalAnswer);
                                return;
                            }
                            var metadata = lastResponse.get() == null ? null : lastResponse.get().getMetadata();
                            var usage = metadata == null ? null : metadata.getUsage();
                            var model = metadata == null ? null : metadata.getModel();
                            Integer pt = usage == null ? null : usage.getPromptTokens();
                            Integer ct = usage == null ? null : usage.getCompletionTokens();
                            Integer tt = usage == null ? null : usage.getTotalTokens();
                            finalizeStreamed(emitter, type, customerId, citations, risk, answer, model, pt, ct, tt, onFinalAnswer);
                        }
                );
    }

    /**
     * 收尾「已串流」的答案：內容已逐塊送出，這裡只寫 ai_call_log、跑最終答案回呼、補送尾段。
     *
     * @param onFinalAnswer 最終答案回呼（可為 null）
     */
    private void finalizeStreamed(SseEmitter emitter, AiCallType type, Long customerId,
                                  List<Dtos.CitationResponse> citations, Dtos.RiskResponse risk, String answer,
                                  String model, Integer pt, Integer ct, Integer tt,
                                  java.util.function.Consumer<String> onFinalAnswer) {
        var saved = aiGovernance.record(type, customerId, model, pt, ct, tt, true, true, answer);
        if (onFinalAnswer != null) {
            onFinalAnswer.accept(answer);
        }
        sendTailAndComplete(emitter, citations, risk, saved.getId());
    }

    /**
     * 收尾「fallback」答案：尚未送出任何內容，這裡一次送出完整 deterministic 答案、寫 ai_call_log、補送尾段。
     *
     * @param onFinalAnswer 最終答案回呼（可為 null）
     */
    private void finalizeFallback(SseEmitter emitter, AiCallType type, Long customerId,
                                  List<Dtos.CitationResponse> citations, Dtos.RiskResponse risk, String fallbackAnswer,
                                  java.util.function.Consumer<String> onFinalAnswer) {
        var saved = aiGovernance.record(type, customerId, null, 0, 0, 0, false, true, fallbackAnswer);
        if (onFinalAnswer != null) {
            onFinalAnswer.accept(fallbackAnswer);
        }
        sendContent(emitter, fallbackAnswer);
        sendTailAndComplete(emitter, citations, risk, saved.getId());
    }

    /**
     * 推送一段內容 delta（以 Map 送出，JSON 序列化會自動轉義換行，不破壞 SSE 協定）。
     * 傳送失敗（多半是 client 已斷線）即結束串流。
     *
     * @param emitter SSE 發送器
     * @param delta 內容片段
     */
    void sendContent(SseEmitter emitter, String delta) {
        SseHelper.sendContent(emitter, delta);
    }

    /**
     * 補送串流尾段：引用、風險、callId，最後送 [DONE] 並關閉串流。
     *
     * @param emitter SSE 發送器
     * @param citations RAG 引用
     * @param risk 風險評分
     * @param callId AI 呼叫紀錄 id（供前端送採納/拒絕回饋；可為 null）
     */
    private void sendTailAndComplete(SseEmitter emitter, List<Dtos.CitationResponse> citations, Dtos.RiskResponse risk, Long callId) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "citations", "citations", citations)));
            emitter.send(SseEmitter.event().data(Map.of("type", "risk", "risk", risk)));
            if (callId != null) {
                emitter.send(SseEmitter.event().data(Map.of("type", "callId", "callId", callId)));
            }
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 依查詢語意做向量檢索取回 top-3 引用；無任何向量時 graceful fallback 回相似度提示排序。
     *
     * @param query 查詢文字（使用者提問或評估指令）
     * @return 引用清單
     */
    private List<Dtos.CitationResponse> loadCitations(String query) {
        try {
            var vec = embeddingClient.embed(List.of(query), EmbeddingClient.InputType.QUERY).get(0);
            var hits = vectorRepo.searchTopK(vec, 3);
            if (!hits.isEmpty()) {
                return hits;
            }
        } catch (Exception e) {
            log.warn("向量檢索失敗，改用 similarityHint fallback：{}", e.getMessage());
        }
        return knowledgeDocuments.findTop3ByOrderBySimilarityHintDesc().stream()
                .map(doc -> new Dtos.CitationResponse(doc.getTitle(), doc.getDocType(), doc.getContent(), doc.getSimilarityHint()))
                .toList();
    }

    /**
     * 產生客戶 360 度整體評估報告（Markdown）。複用對話的完整 grounding context。
     *
     * @param customerId 客戶 ID
     * @return 含評估報告、引用與風險的回應
     */
    public Dtos.ChatResponse customerAssessment(Long customerId) {
        var customer = customers.findDetail(customerId);
        var risk = calculateOpportunityRisk(customer);
        var citations = loadCitations(customer.getName() + " " + customer.getIndustry() + " 續約 風險 評估");
        var result = callLlm(AiCallType.ASSESSMENT, customer, risk, buildGroundingContext(customer, risk, citations, "") + ASSESSMENT_INSTRUCTION);
        return new Dtos.ChatResponse(result.answer(), citations, risk, result.callId());
    }

    /**
     * 產生 Portfolio 跨客戶整體評估報告（Markdown）。彙總數字由 Java 計算後餵給 LLM。
     *
     * @return Portfolio 評估回應
     */
    public Dtos.PortfolioAssessmentResponse portfolioAssessment() {
        var all = customers.findAllWithDetail();
        var rows = new java.util.ArrayList<String>();
        var totalPipeline = BigDecimal.ZERO;
        long activeOpportunities = 0;
        int highRisk = 0;
        for (var c : all) {
            var risk = calculateOpportunityRisk(c);
            var amount = c.getOpportunities().stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var active = c.getOpportunities().stream().filter(o -> o.getStage() != OpportunityStage.CLOSED_LOST).count();
            var lastDate = c.getInteractions().stream().map(Interaction::getOccurredAt).max(LocalDateTime::compareTo)
                    .map(d -> d.toLocalDate().toString()).orElse("無");
            totalPipeline = totalPipeline.add(amount);
            activeOpportunities += active;
            if (risk.churnRisk() >= 50 || risk.renewalDelayRisk() >= 50) {
                highRisk++;
            }
            rows.add(c.getName() + "｜" + c.getIndustry() + "｜" + c.getOwnerName()
                    + "｜流失" + risk.churnRisk() + "/續約延遲" + risk.renewalDelayRisk()
                    + "｜商機" + c.getOpportunities().size() + "筆/" + amount
                    + "｜最近互動" + lastDate
                    + "｜續約日" + (c.getRenewalDueDate() == null ? "未定" : c.getRenewalDueDate()));
        }
        var result = buildPortfolioAnswer(rows, all.size(), highRisk, totalPipeline, activeOpportunities);
        return new Dtos.PortfolioAssessmentResponse(result.answer(), all.size(), highRisk, totalPipeline, activeOpportunities, result.callId());
    }

    /**
     * 呼叫 LLM 產生回答；無金鑰或失敗時 fallback 至 deterministic 文字。
     *
     * @param customer 客戶實體（供 fallback 與日誌使用）
     * @param risk 風險評分（供 fallback 使用）
     * @param userPrompt 完整使用者提示詞
     * @return 回答文字
     */
    private LlmResult callLlm(AiCallType type, Customer customer, Dtos.RiskResponse risk, String userPrompt) {
        // 未設定 api-key 時直接走 fallback，避免發出註定失敗的 API 請求
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        if (chatModel == null) {
            return recordFallback(type, customer, risk);
        }
        try {
            var spec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
            var opts = systemSettings.resolveChatOptions();
            if (opts != null) spec = spec.options(opts);
            var chatResponse = spec.call().chatResponse();
            var content = chatResponse == null ? null : chatResponse.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                log.warn("OpenAI 回傳空白內容，customerId={}，改用 deterministic fallback", customer.getId());
                return recordFallback(type, customer, risk);
            }
            // 成功：擷取用量與模型（取不到時記 0/null），寫入 ai_call_log
            var metadata = chatResponse.getMetadata();
            var usage = metadata == null ? null : metadata.getUsage();
            var model = metadata == null ? null : metadata.getModel();
            Integer pt = usage == null ? null : usage.getPromptTokens();
            Integer ct = usage == null ? null : usage.getCompletionTokens();
            Integer tt = usage == null ? null : usage.getTotalTokens();
            var answer = content.strip();
            var saved = aiGovernance.record(type, customer.getId(), model, pt, ct, tt, true, true, answer);
            return new LlmResult(answer, saved.getId());
        } catch (Exception e) {
            // 任何 API 錯誤（金鑰無效、額度、網路）都不應中斷服務，fallback 保底
            log.warn("OpenAI 呼叫失敗，customerId={}，改用 deterministic fallback：{}", customer.getId(), e.getMessage());
            return recordFallback(type, customer, risk);
        }
    }

    /**
     * 產生 deterministic fallback 回答並寫入 ai_call_log（ai_enabled=false、tokens=0、masked=true）。
     *
     * @param type 呼叫類型
     * @param customer 客戶實體
     * @param risk 風險評分
     * @return 含回答與 callId 的結果
     */
    private LlmResult recordFallback(AiCallType type, Customer customer, Dtos.RiskResponse risk) {
        var answer = deterministicAnswer(customer, risk);
        var saved = aiGovernance.record(type, customer.getId(), null, 0, 0, 0, false, true, answer);
        return new LlmResult(answer, saved.getId());
    }

    /**
     * 產生 AI 對話回答：以完整 grounding context + 使用者提問呼叫 LLM。
     *
     * @param customer 客戶實體
     * @param risk 風險評分
     * @param citations RAG 引用
     * @param userMessage 使用者問題
     * @return 回答文字
     */
    private LlmResult buildAnswer(AiCallType type, Customer customer, Dtos.RiskResponse risk, List<Dtos.CitationResponse> citations, String memory, String userMessage) {
        var prompt = buildGroundingContext(customer, risk, citations, memory) + "\n# 業務提問\n" + userMessage + "\n";
        return callLlm(type, customer, risk, prompt);
    }

    /**
     * 組裝餵給 LLM 的 grounding context：客戶完整資料、風險與知識庫引用。
     * 函式級註解：擴充為完整互動歷史（近 20 筆）、所有商機與聯絡人，讓 LLM 能做整體評估而非僅看單筆互動。
     *
     * @param customer 客戶實體
     * @param risk 風險評分
     * @param citations RAG 引用
     * @param memory 對話記憶區塊（可為空字串；本方法會過 PII 遮罩後併入）
     * @return grounding context 文字
     */
    private String buildGroundingContext(Customer customer, Dtos.RiskResponse risk, List<Dtos.CitationResponse> citations, String memory) {
        var knowledge = citations.stream()
                .map(c -> "- [" + c.docType() + "] " + c.title() + "：" + c.content())
                .collect(Collectors.joining("\n"));
        // 對話記憶送 LLM 前過 PII 遮罩；無記憶時不額外輸出區塊
        var maskedMemory = (memory == null || memory.isBlank()) ? "" : PiiMasker.mask(memory) + "\n\n";
        return """
                %s%s

                # 風險評分（系統計算，請勿更改數字）
                流失風險：%d 分
                續約延遲風險：%d 分
                風險原因：%s

                # 知識庫引用
                %s
                """.formatted(
                maskedMemory,
                // PII 遮罩只作用在「送 LLM 的 grounding context」：遮蔽客戶資料中的 email/電話/統編
                PiiMasker.mask(customerContext(customer)),
                risk.churnRisk(),
                risk.renewalDelayRisk(),
                risk.reasons().isEmpty() ? "無顯著風險訊號" : String.join("、", risk.reasons()),
                knowledge.isBlank() ? "（無可用引用）" : knowledge);
    }

    /**
     * 組裝單一客戶的完整資料區塊：基本資料、合約、聯絡人、商機、互動歷史。
     *
     * @param customer 客戶實體
     * @return 客戶資料 Markdown 區塊
     */
    private String customerContext(Customer customer) {
        var contacts = customer.getContacts().stream()
                .map(c -> "- " + c.getName() + "（" + c.getTitle() + " / " + c.getEmail() + "）")
                .collect(Collectors.joining("\n"));
        var opportunities = customer.getOpportunities().stream()
                .map(o -> "- " + o.getName() + "｜階段 " + o.getStage() + "｜金額 " + o.getAmount()
                        + "｜預計成交 " + (o.getExpectedCloseDate() == null ? "未定" : o.getExpectedCloseDate())
                        + "｜類型 " + o.getType())
                .collect(Collectors.joining("\n"));
        var interactions = customer.getInteractions().stream()
                .sorted(Comparator.comparing(Interaction::getOccurredAt).reversed())
                .limit(20)
                .map(i -> "- [" + i.getOccurredAt().toLocalDate() + "][" + i.getType() + "] " + i.getContent())
                .collect(Collectors.joining("\n"));
        return """
                # 客戶資料
                名稱：%s
                產業：%s
                負責業務：%s
                狀態：%s
                合約起訖：%s ~ %s
                預計續約日：%s

                ## 聯絡人
                %s

                ## 商機（Pipeline）
                %s

                ## 互動歷史（近 20 筆，新到舊）
                %s""".formatted(
                customer.getName(),
                customer.getIndustry(),
                customer.getOwnerName(),
                customer.getStatus(),
                customer.getContractStartDate() == null ? "未定" : customer.getContractStartDate(),
                customer.getContractEndDate() == null ? "未定" : customer.getContractEndDate(),
                customer.getRenewalDueDate() == null ? "未設定" : customer.getRenewalDueDate(),
                contacts.isBlank() ? "（無聯絡人資料）" : contacts,
                opportunities.isBlank() ? "（無商機）" : opportunities,
                interactions.isBlank() ? "（無互動紀錄）" : interactions);
    }

    /**
     * 產生 Portfolio 評估報告：有 LLM 走真實呼叫，否則 fallback。
     *
     * @param rows 各客戶摘要行
     * @param count 客戶總數
     * @param highRisk 高風險客戶數
     * @param pipeline 商機總額
     * @param activeOpportunities 活躍商機數
     * @return 報告 Markdown
     */
    private LlmResult buildPortfolioAnswer(List<String> rows, int count, int highRisk, BigDecimal pipeline, long activeOpportunities) {
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        if (chatModel == null) {
            // fallback 也要寫 ai_call_log（tokens=0、ai_enabled=false、masked=true）
            var answer = deterministicPortfolio(count, highRisk, pipeline, activeOpportunities);
            var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, false, true, answer);
            return new LlmResult(answer, saved.getId());
        }
        try {
            var prompt = """
                    以下是全公司 CRM 客戶組合資料，請產出「Portfolio 整體評估報告」（Markdown 格式）。

                    # 彙總統計（系統計算，請勿更改數字）
                    客戶總數：%d
                    高風險客戶數（流失或續約延遲 >= 50）：%d
                    商機總額：%s
                    活躍商機數：%d

                    # 客戶清單（名稱｜產業｜負責人｜風險｜商機｜最近互動｜續約日）
                    %s

                    # 報告要求
                    請涵蓋：①整體健康度總評 ②風險分布洞察與「最該優先處理的客戶 Top 3」（附理由） ③Pipeline 重點與機會 ④給銷售主管的具體建議行動。使用繁體中文、條理清楚、勿編造數字。
                    """.formatted(count, highRisk, pipeline, activeOpportunities,
                            // PII 遮罩只作用在送 LLM 的 grounding context
                            PiiMasker.mask(String.join("\n", rows)));
            var chatResponse = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(prompt).call().chatResponse();
            var content = chatResponse == null ? null : chatResponse.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                log.warn("OpenAI Portfolio 評估回傳空白，改用 deterministic fallback");
                var answer = deterministicPortfolio(count, highRisk, pipeline, activeOpportunities);
                var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, false, true, answer);
                return new LlmResult(answer, saved.getId());
            }
            // 成功：擷取用量與模型（取不到記 0/null），寫入 ai_call_log
            var metadata = chatResponse.getMetadata();
            var usage = metadata == null ? null : metadata.getUsage();
            var model = metadata == null ? null : metadata.getModel();
            Integer pt = usage == null ? null : usage.getPromptTokens();
            Integer ct = usage == null ? null : usage.getCompletionTokens();
            Integer tt = usage == null ? null : usage.getTotalTokens();
            var answer = content.strip();
            var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, model, pt, ct, tt, true, true, answer);
            return new LlmResult(answer, saved.getId());
        } catch (Exception e) {
            log.warn("OpenAI Portfolio 評估失敗，改用 deterministic fallback：{}", e.getMessage());
            var answer = deterministicPortfolio(count, highRisk, pipeline, activeOpportunities);
            var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, false, true, answer);
            return new LlmResult(answer, saved.getId());
        }
    }

    /**
     * Portfolio 評估的 deterministic fallback 報告。
     *
     * @param count 客戶總數
     * @param highRisk 高風險客戶數
     * @param pipeline 商機總額
     * @param activeOpportunities 活躍商機數
     * @return 報告 Markdown
     */
    private String deterministicPortfolio(int count, int highRisk, BigDecimal pipeline, long activeOpportunities) {
        return """
                ## Portfolio 整體評估（教學版摘要）

                - **客戶總數**：%d
                - **高風險客戶數**：%d（流失或續約延遲 >= 50）
                - **商機總額**：%s
                - **活躍商機數**：%d

                > 目前未設定 OpenAI 金鑰，顯示為系統彙總的 deterministic 摘要；設定金鑰後可取得 AI 綜合洞察與優先客戶建議。
                """.formatted(count, highRisk, pipeline, activeOpportunities);
    }

    /**
     * Deterministic 教學版回答，作為無 LLM 或呼叫失敗時的保底輸出。
     *
     * @param customer 客戶實體
     * @param risk 風險評分
     * @return 回答文字
     */
    private String deterministicAnswer(Customer customer, Dtos.RiskResponse risk) {
        return "根據 CRM 資料庫，" + customer.getName()
                + " 目前由 " + customer.getOwnerName()
                + " 負責。最近互動摘要：" + latestInteractionContent(customer)
                + "。流失風險 " + risk.churnRisk()
                + " 分，續約延遲風險 " + risk.renewalDelayRisk()
                + " 分。建議先依引用來源中的服務條款與續約話術安排下一步，不應自行編造價格或未確認承諾。";
    }

    /**
     * 取得客戶最近一次互動內容。
     *
     * @param customer 客戶實體
     * @return 最近互動內容，無則回傳提示字串
     */
    private String latestInteractionContent(Customer customer) {
        return customer.getInteractions().stream()
                .max((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
                .map(Interaction::getContent)
                .orElse("目前沒有互動紀錄");
    }

    /**
     * 依互動間隔、風險文字與續約日計算風險。
     *
     * @param customer 客戶實體
     * @return 風險 DTO
     */
    public Dtos.RiskResponse calculateOpportunityRisk(Customer customer) {
        var lastInteraction = customer.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        var reasons = new java.util.ArrayList<String>();
        var churn = 10;
        var renewal = 10;
        if (lastInteraction == null) {
            reasons.add("近期互動資料不足，無法可靠判斷");
            churn += 35;
        } else {
            var days = ChronoUnit.DAYS.between(lastInteraction.toLocalDate(), LocalDate.now());
            if (days > 60) {
                churn += 45;
                reasons.add("距上次互動 " + days + " 天");
            } else if (days > 30) {
                churn += 25;
                reasons.add("距上次互動超過 30 天");
            }
        }
        var riskyText = customer.getInteractions().stream()
                .map(Interaction::getContent)
                .anyMatch(text -> text.contains("客訴") || text.contains("預算凍結") || text.contains("競品") || text.contains("未收到回覆"));
        if (riskyText) {
            churn += 30;
            reasons.add("互動紀錄包含客訴、預算凍結或競品比較訊號");
        }
        if (customer.getRenewalDueDate() != null && customer.getRenewalDueDate().isBefore(LocalDate.now())) {
            var overdue = ChronoUnit.DAYS.between(customer.getRenewalDueDate(), LocalDate.now());
            renewal += 55;
            reasons.add("續約日已逾期 " + overdue + " 天");
        }
        return new Dtos.RiskResponse(Math.min(churn, 100), Math.min(renewal, 100), reasons);
    }

    /**
     * 模型競速測試：以「全公司客戶組合」為 grounding context，讓指定模型回答固定分析任務。
     * 函式級註解：所有模型收到完全相同的真實資料與任務，比較的是分析品質與速度，不是知識背景。
     * grounding context 在交易內同步建立（讀所有客戶 LAZY 關聯），callback 執行緒只使用純值。
     * 無 api-key 時送錯誤訊息後關閉；不走 deterministic fallback（測試目的不同）。
     *
     * @param model 要測試的模型名（空時沿用系統設定或環境變數預設）
     * @param ignored 保留參數（grounding context 由後端自行建構，前端傳入值不使用）
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamModelTest(String model, String ignored) {
        SseEmitter emitter = new SseEmitter(120_000L);
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        if (chatModel == null) {
            sendContent(emitter, "⚠️ 未設定 API 金鑰，無法執行模型測試。");
            sendSimpleTailAndComplete(emitter, null);
            return emitter;
        }

        // 在交易內建立全公司客戶 grounding context（與 streamPortfolioAssessment 一致）
        var all = customers.findAllWithDetail();
        var rows = new java.util.ArrayList<String>();
        var totalPipeline = BigDecimal.ZERO;
        long activeOpportunities = 0;
        int highRisk = 0;
        for (var c : all) {
            var risk = calculateOpportunityRisk(c);
            var amount = c.getOpportunities().stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var active = c.getOpportunities().stream().filter(o -> o.getStage() != OpportunityStage.CLOSED_LOST).count();
            var lastDate = c.getInteractions().stream().map(Interaction::getOccurredAt).max(LocalDateTime::compareTo)
                    .map(d -> d.toLocalDate().toString()).orElse("無");
            totalPipeline = totalPipeline.add(amount);
            activeOpportunities += active;
            if (risk.churnRisk() >= 50 || risk.renewalDelayRisk() >= 50) highRisk++;
            rows.add(c.getName() + "｜" + c.getIndustry() + "｜" + c.getOwnerName()
                    + "｜流失" + risk.churnRisk() + "/續約延遲" + risk.renewalDelayRisk()
                    + "｜商機" + c.getOpportunities().size() + "筆/" + amount
                    + "｜最近互動" + lastDate
                    + "｜續約日" + (c.getRenewalDueDate() == null ? "未定" : c.getRenewalDueDate()));
        }

        // 固定分析任務：所有模型相同，確保比較公平
        final String prompt = """
                以下是全公司 CRM 客戶組合資料（系統計算，請勿更改數字）。

                # 彙總統計
                客戶總數：%d｜高風險客戶數：%d｜商機總額：%s｜活躍商機數：%d

                # 客戶清單（名稱｜產業｜負責人｜流失/續約延遲風險｜商機｜最近互動｜續約日）
                %s

                # 分析任務
                請找出「最需要立即關注的前 3 名客戶」，每位附上：① 風險原因（依據上方數字）② 建議的具體下一步行動。
                格式要求：Markdown 清單，繁體中文，每位客戶說明不超過 60 字，不可編造任何未在資料中出現的數字或事件。
                """.formatted(all.size(), highRisk, totalPipeline, activeOpportunities,
                        PiiMasker.mask(String.join("\n", rows)));

        // 優先使用傳入的 model 參數；為空則沿用系統設定
        var spec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(prompt);
        var testModel = (model != null && !model.isBlank()) ? model : null;
        if (testModel != null) {
            spec = spec.options(org.springframework.ai.openai.OpenAiChatOptions.builder().model(testModel));
        } else {
            var opts = systemSettings.resolveChatOptions();
            if (opts != null) spec = spec.options(opts);
        }

        spec.stream().chatResponse().subscribe(
                cr -> {
                    var result = cr.getResult();
                    var text = result == null ? null : result.getOutput().getText();
                    if (text != null && !text.isEmpty()) sendContent(emitter, text);
                },
                error -> {
                    log.warn("模型測試串流失敗 model={}：{}", model, error.getMessage());
                    sendContent(emitter, "⚠️ 呼叫失敗：" + error.getMessage());
                    sendSimpleTailAndComplete(emitter, null);
                },
                () -> sendSimpleTailAndComplete(emitter, null));
        return emitter;
    }

    /**
     * 以 SSE 真串流推送 Portfolio 全公司整體評估報告。
     * 函式級註解：在交易內先算好所有 grounding data 與 fallback 文字，供 callback 執行緒安全使用。
     * Portfolio 無 per-customer citations/risk，串流尾段只送 callId 與 [DONE]。
     *
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamPortfolioAssessment() {
        SseEmitter emitter = new SseEmitter(300_000L);

        // 在交易內同步建立 grounding context（讀所有客戶 LAZY 關聯）
        var all = customers.findAllWithDetail();
        var rows = new java.util.ArrayList<String>();
        var totalPipeline = BigDecimal.ZERO;
        long activeOpportunities = 0;
        int highRisk = 0;
        for (var c : all) {
            var risk = calculateOpportunityRisk(c);
            var amount = c.getOpportunities().stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var active = c.getOpportunities().stream().filter(o -> o.getStage() != OpportunityStage.CLOSED_LOST).count();
            var lastDate = c.getInteractions().stream().map(Interaction::getOccurredAt).max(LocalDateTime::compareTo)
                    .map(d -> d.toLocalDate().toString()).orElse("無");
            totalPipeline = totalPipeline.add(amount);
            activeOpportunities += active;
            if (risk.churnRisk() >= 50 || risk.renewalDelayRisk() >= 50) highRisk++;
            rows.add(c.getName() + "｜" + c.getIndustry() + "｜" + c.getOwnerName()
                    + "｜流失" + risk.churnRisk() + "/續約延遲" + risk.renewalDelayRisk()
                    + "｜商機" + c.getOpportunities().size() + "筆/" + amount
                    + "｜最近互動" + lastDate
                    + "｜續約日" + (c.getRenewalDueDate() == null ? "未定" : c.getRenewalDueDate()));
        }

        final int finalHighRisk = highRisk;
        final BigDecimal finalPipeline = totalPipeline;
        final long finalActive = activeOpportunities;
        final String fallback = deterministicPortfolio(all.size(), highRisk, totalPipeline, activeOpportunities);
        final var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;

        // 無金鑰：直接 fallback，送完整報告後結束
        if (chatModel == null) {
            var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, false, true, fallback);
            sendContent(emitter, fallback);
            sendSimpleTailAndComplete(emitter, saved.getId());
            return emitter;
        }

        final String prompt = """
                以下是全公司 CRM 客戶組合資料，請產出「Portfolio 整體評估報告」（Markdown 格式）。

                # 彙總統計（系統計算，請勿更改數字）
                客戶總數：%d
                高風險客戶數（流失或續約延遲 >= 50）：%d
                商機總額：%s
                活躍商機數：%d

                # 客戶清單（名稱｜產業｜負責人｜風險｜商機｜最近互動｜續約日）
                %s

                # 報告要求
                請涵蓋：①整體健康度總評 ②風險分布洞察與「最該優先處理的客戶 Top 3」（附理由） ③Pipeline 重點與機會 ④給銷售主管的具體建議行動。使用繁體中文、條理清楚、勿編造數字。
                """.formatted(all.size(), finalHighRisk, finalPipeline, finalActive,
                        PiiMasker.mask(String.join("\n", rows)));

        var fullAnswer = new StringBuilder();
        var lastResponse = new java.util.concurrent.atomic.AtomicReference<org.springframework.ai.chat.model.ChatResponse>();

        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(prompt);
        var streamOpts = systemSettings.resolveChatOptions();
        if (streamOpts != null) streamSpec = streamSpec.options(streamOpts);
        streamSpec.stream().chatResponse()
                .subscribe(
                        chatResponse -> {
                            lastResponse.set(chatResponse);
                            var result = chatResponse.getResult();
                            var text = result == null ? null : result.getOutput().getText();
                            if (text != null && !text.isEmpty()) {
                                fullAnswer.append(text);
                                sendContent(emitter, text);
                            }
                        },
                        error -> {
                            log.warn("OpenAI Portfolio 串流失敗，改用 fallback：{}", error.getMessage());
                            if (fullAnswer.toString().isBlank()) {
                                var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, false, true, fallback);
                                sendContent(emitter, fallback);
                                sendSimpleTailAndComplete(emitter, saved.getId());
                            } else {
                                var partial = fullAnswer.toString().strip();
                                var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, true, true, partial);
                                sendSimpleTailAndComplete(emitter, saved.getId());
                            }
                        },
                        () -> {
                            var answer = fullAnswer.toString().strip();
                            if (answer.isBlank()) {
                                var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, null, 0, 0, 0, false, true, fallback);
                                sendContent(emitter, fallback);
                                sendSimpleTailAndComplete(emitter, saved.getId());
                                return;
                            }
                            var resp = lastResponse.get();
                            var metadata = resp == null ? null : resp.getMetadata();
                            var usage = metadata == null ? null : metadata.getUsage();
                            var model = metadata == null ? null : metadata.getModel();
                            Integer pt = usage == null ? null : usage.getPromptTokens();
                            Integer ct = usage == null ? null : usage.getCompletionTokens();
                            Integer tt = usage == null ? null : usage.getTotalTokens();
                            var saved = aiGovernance.record(AiCallType.PORTFOLIO, null, model, pt, ct, tt, true, true, answer);
                            sendSimpleTailAndComplete(emitter, saved.getId());
                        });
        return emitter;
    }

    /**
     * 串流尾段（無 citations/risk 版本）：只送 callId 與 [DONE]，用於 Portfolio/Team/Owner 等無 RAG 引用的串流。
     *
     * @param emitter SSE 發送器
     * @param callId AI 呼叫紀錄 id（可為 null）
     */
    void sendSimpleTailAndComplete(SseEmitter emitter, Long callId) {
        SseHelper.sendSimpleTailAndComplete(emitter, callId);
    }
}
