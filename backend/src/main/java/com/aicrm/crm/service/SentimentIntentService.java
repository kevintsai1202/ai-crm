package com.aicrm.crm.service;

import com.aicrm.crm.domain.Intent;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Sentiment;
import com.aicrm.crm.repository.InteractionInsightRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情緒意圖分類服務。
 *
 * <p>沿用專案「可切換 LLM + deterministic fallback」哲學：設定 OpenAI 金鑰時可用真實 LLM 要求嚴格 JSON
 * 分類，未設定或失敗時 fallback 至中文關鍵字 deterministic 規則。批次分析（analyzeMissing）預設走 deterministic，
 * 避免大量真 LLM 呼叫造成慢/貴。</p>
 *
 * <p>注意：本服務為分類器而非對話 LLM，<b>不寫入 ai_call_log</b>（不接 AiGovernanceService），
 * 以保持治理日誌專注於 chat / assessment（spec §1 非目標）。</p>
 */
@Service
@Transactional
public class SentimentIntentService {

    /** 記錄 LLM 分類失敗等事件。 */
    private static final Logger log = LoggerFactory.getLogger(SentimentIntentService.class);

    /** LLM 分類系統提示詞：約束僅回嚴格 JSON。 */
    private static final String SYSTEM_PROMPT = """
            你是一位 B2B CRM 互動分析器。請判斷下列互動內容的「情緒」與「業務意圖」，
            僅回傳單一 JSON 物件，不得有任何額外文字、解說或 Markdown 圍欄。
            格式：{"sentiment":"POSITIVE|NEUTRAL|NEGATIVE","score":-100..100,"intent":"ASK_PRICING|COMPARE_COMPETITOR|CHURN_SIGNAL|RENEWAL_INTEREST|UPSELL_SIGNAL|COMPLAINT|OTHER"}
            score 為情緒分數，負值越大越負面。""";

    /** 互動分析結果資料存取。 */
    private final InteractionInsightRepository insights;

    /**
     * OpenAI ChatModel 提供者。
     * 注意：Spring AI 2.0 即使 api-key 為空仍會建立此 bean，故不可僅以 bean 是否存在判斷是否啟用。
     */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** 解析 LLM 回傳 JSON 用。 */
    private final ObjectMapper objectMapper;

    /** 是否啟用真實 OpenAI：以 api-key 是否實際設定為準（與 InsightService 一致）。 */
    private final boolean aiEnabled;

    /** 系統設定服務：提供 AI 模型覆蓋（DB 設定優先於環境變數）。 */
    private final SystemSettingService systemSettings;

    public SentimentIntentService(InteractionInsightRepository insights,
                                  ObjectProvider<ChatModel> chatModelProvider,
                                  ObjectMapper objectMapper,
                                  SystemSettingService systemSettings,
                                  @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.insights = insights;
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
        this.systemSettings = systemSettings;
        this.aiEnabled = openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /**
     * 分類結果：情緒、分數與意圖。
     *
     * @param sentiment 情緒
     * @param score 情緒分數（-100..100）
     * @param intent 意圖
     */
    public record Classification(Sentiment sentiment, int score, Intent intent) {}

    /**
     * Deterministic 中文關鍵字分類器。
     *
     * <p>規則依嚴重度與特異性由前往後比對，命中即回（客訴/流失 等負面信號優先）：</p>
     * <ul>
     *   <li>客訴/不滿/退費/抱怨/投訴 → NEGATIVE + COMPLAINT</li>
     *   <li>取消/不續/解約/轉單/流失/換供應商 → NEGATIVE + CHURN_SIGNAL</li>
     *   <li>競品/他牌/別家/比較/對手 → NEGATIVE + COMPARE_COMPETITOR</li>
     *   <li>報價/價格/折扣/費用/報價單/多少錢 → NEUTRAL + ASK_PRICING</li>
     *   <li>續約/續訂/延長/續簽 → POSITIVE + RENEWAL_INTEREST</li>
     *   <li>加購/升級/擴充/加值/增購 → POSITIVE + UPSELL_SIGNAL</li>
     *   <li>其餘 → NEUTRAL + OTHER</li>
     * </ul>
     *
     * @param content 互動內容
     * @return 分類結果
     */
    public Classification classifyDeterministic(String content) {
        var text = content == null ? "" : content;
        if (containsAny(text, "客訴", "不滿", "退費", "抱怨", "投訴")) {
            return new Classification(Sentiment.NEGATIVE, -70, Intent.COMPLAINT);
        }
        if (containsAny(text, "取消", "不續", "解約", "轉單", "流失", "換供應商", "考慮取消")) {
            return new Classification(Sentiment.NEGATIVE, -60, Intent.CHURN_SIGNAL);
        }
        if (containsAny(text, "競品", "他牌", "別家", "比較", "對手")) {
            return new Classification(Sentiment.NEGATIVE, -40, Intent.COMPARE_COMPETITOR);
        }
        if (containsAny(text, "報價", "價格", "折扣", "費用", "報價單", "多少錢", "價錢")) {
            return new Classification(Sentiment.NEUTRAL, 10, Intent.ASK_PRICING);
        }
        if (containsAny(text, "續約", "續訂", "延長", "續簽")) {
            return new Classification(Sentiment.POSITIVE, 55, Intent.RENEWAL_INTEREST);
        }
        if (containsAny(text, "加購", "升級", "擴充", "加值", "增購")) {
            return new Classification(Sentiment.POSITIVE, 60, Intent.UPSELL_SIGNAL);
        }
        return new Classification(Sentiment.NEUTRAL, 0, Intent.OTHER);
    }

    /**
     * LLM 分類：有金鑰時要求嚴格 JSON 解析，任何失敗（無金鑰、空白、解析錯誤）皆 fallback 至 deterministic。
     *
     * @param content 互動內容
     * @return 分類結果
     */
    public Classification classifyWithLlm(String content) {
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        if (chatModel == null) {
            return classifyDeterministic(content);
        }
        try {
            var spec = ChatClient.create(chatModel).prompt()
                    .system(SYSTEM_PROMPT).user(content == null ? "" : content);
            var opts = systemSettings.resolveChatOptions();
            if (opts != null) spec = spec.options(opts);
            var chatResponse = spec.call().chatResponse();
            var json = chatResponse == null ? null : chatResponse.getResult().getOutput().getText();
            if (json == null || json.isBlank()) {
                return classifyDeterministic(content);
            }
            return parseJson(json.strip(), content);
        } catch (Exception e) {
            log.warn("LLM 情緒意圖分類失敗，改用 deterministic fallback：{}", e.getMessage());
            return classifyDeterministic(content);
        }
    }

    /**
     * 解析 LLM 回傳 JSON；任何欄位缺失或值非法皆 fallback 至 deterministic。
     *
     * @param json LLM 回傳之 JSON 字串
     * @param content 原始內容（fallback 用）
     * @return 分類結果
     */
    private Classification parseJson(String json, String content) {
        try {
            // 去除可能的 Markdown 圍欄殘留，只取第一個 { 到最後一個 } 之間
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return classifyDeterministic(content);
            }
            var node = objectMapper.readTree(json.substring(start, end + 1));
            var sentiment = Sentiment.valueOf(node.get("sentiment").asString().strip().toUpperCase());
            var intent = Intent.valueOf(node.get("intent").asString().strip().toUpperCase());
            int score = node.has("score") ? node.get("score").asInt() : 0;
            // 夾限至 -100..100，避免模型回超界值
            score = Math.max(-100, Math.min(100, score));
            return new Classification(sentiment, score, intent);
        } catch (Exception e) {
            log.warn("LLM 回傳 JSON 解析失敗，改用 deterministic fallback：{}", e.getMessage());
            return classifyDeterministic(content);
        }
    }

    /**
     * 分析單筆互動並 upsert（已存在則更新）。
     *
     * @param interactionId 互動 ID
     * @param customerId 客戶 ID
     * @param content 互動內容
     * @param useLlm 是否允許走真實 LLM（無金鑰時自動 fallback）
     * @return 已儲存的分析結果
     */
    public InteractionInsight analyzeAndSave(Long interactionId, Long customerId, String content, boolean useLlm) {
        var c = useLlm ? classifyWithLlm(content) : classifyDeterministic(content);
        var existing = insights.findByInteractionId(interactionId).orElse(null);
        if (existing != null) {
            existing.update(c.sentiment(), c.score(), c.intent());
            return existing;
        }
        return insights.save(new InteractionInsight(interactionId, customerId, c.sentiment(), c.score(), c.intent()));
    }

    /**
     * 批次分析尚無 insight 的互動（預設 deterministic，供生成器與重建）。
     *
     * @param useLlm 是否允許走真實 LLM（批次預設應傳 false）
     * @return 本次分析筆數
     */
    public int analyzeMissing(boolean useLlm) {
        // 一次撈出待分析互動的 [id, customerId, content]，避免逐筆查詢與 LAZY 關聯
        var rows = insights.findInteractionsWithoutInsight();
        int analyzed = 0;
        for (var row : rows) {
            Long interactionId = (Long) row[0];
            Long customerId = (Long) row[1];
            String content = (String) row[2];
            analyzeAndSave(interactionId, customerId, content, useLlm);
            analyzed++;
        }
        return analyzed;
    }

    /**
     * 內容是否包含任一關鍵字。
     *
     * @param text 互動內容
     * @param keywords 關鍵字
     * @return 命中任一則為 true
     */
    private boolean containsAny(String text, String... keywords) {
        return List.of(keywords).stream().anyMatch(text::contains);
    }
}
