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

    public InsightService(CustomerService customers,
                          KnowledgeDocumentRepository knowledgeDocuments,
                          EmbeddingClient embeddingClient,
                          KnowledgeVectorRepository vectorRepo,
                          ObjectProvider<ChatModel> chatModelProvider,
                          AiGovernanceService aiGovernance,
                          ChatMemoryService chatMemory,
                          @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.customers = customers;
        this.knowledgeDocuments = knowledgeDocuments;
        this.embeddingClient = embeddingClient;
        this.vectorRepo = vectorRepo;
        this.chatModelProvider = chatModelProvider;
        this.aiGovernance = aiGovernance;
        this.chatMemory = chatMemory;
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
     * 以 SSE 串流推送 AI 聊天回答（打字機效果），answer 先產生再逐字送出。
     *
     * @param request 聊天請求
     * @return SseEmitter 串流發送器
     */
    public SseEmitter streamChat(Dtos.ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L);

        var customer = customers.findDetail(request.customerId());
        var risk = calculateOpportunityRisk(customer);
        var citations = loadCitations(request.message());
        // 順序：先 recall（不含本則）→ 產生 answer → 再 save 本輪，避免把本則當記憶
        var memory = chatMemory.recall(request.customerId(), request.message());
        // 先完整產生回答（真 LLM 或 fallback），再以既有打字機機制推送，保持 SSE 協定不變
        // buildAnswer 內部已透過 callLlm 寫一次 ai_call_log（含 fallback），故串流不再重複記錄
        var result = buildAnswer(AiCallType.CHAT, customer, risk, citations, memory, request.message());
        var answer = result.answer();
        chatMemory.save(request.customerId(), ChatRole.USER, request.message());
        chatMemory.save(request.customerId(), ChatRole.ASSISTANT, answer);

        // 啟動非同步執行緒進行打字機效果推送
        new Thread(() -> {
            try {
                // 1. 推送 answer (逐字發送)
                for (int i = 0; i < answer.length(); i++) {
                    String delta = String.valueOf(answer.charAt(i));
                    emitter.send(SseEmitter.event().data(Map.of("type", "content", "delta", delta)));
                    Thread.sleep(40);
                }

                // 2. 推送 citations 引用
                emitter.send(SseEmitter.event().data(Map.of("type", "citations", "citations", citations)));
                Thread.sleep(100);

                // 3. 推送 risk 風險分析
                emitter.send(SseEmitter.event().data(Map.of("type", "risk", "risk", risk)));
                Thread.sleep(100);

                // 4. 推送 callId（供前端對此筆 AI 回答送出採納/拒絕回饋）
                if (result.callId() != null) {
                    emitter.send(SseEmitter.event().data(Map.of("type", "callId", "callId", result.callId())));
                    Thread.sleep(50);
                }

                // 5. 發送完成標記 [DONE]
                emitter.send(SseEmitter.event().data("[DONE]"));

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
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
        var instruction = """

                # 任務
                請對此客戶產出「360 度整體評估報告」（Markdown 格式），務必涵蓋：
                1. **客戶健康度總評**（一句話定性 + 依據）
                2. **商機 / Pipeline 分析**（金額、階段分布、最該推進的商機）
                3. **風險與預警**（解讀流失與續約延遲分數、互動訊號）
                4. **下一步建議行動**（具體、可執行，依知識庫條款，勿編造價格）
                """;
        var result = callLlm(AiCallType.ASSESSMENT, customer, risk, buildGroundingContext(customer, risk, citations, "") + instruction);
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
            var chatResponse = ChatClient.create(chatModel)
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
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
}
