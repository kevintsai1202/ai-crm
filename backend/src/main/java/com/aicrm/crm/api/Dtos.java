package com.aicrm.crm.api;

import com.aicrm.crm.domain.CloseReason;
import com.aicrm.crm.domain.CustomerStatus;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API DTO 集合，集中定義 Controller 的輸入與輸出契約。
 */
public final class Dtos {
    private Dtos() {
    }

    /** serverStartTime：後端 bean 建立時的 Unix 毫秒，用於前端偵測後端重新部署。 */
    public record HealthResponse(String status, Instant timestamp, Map<String, String> features, long serverStartTime) {}

    /** 儀表板版面回應（SP7）：可見區塊有序 id 陣列；尚未設定時 visibleOrder 為 null。 */
    public record DashboardLayoutResponse(List<String> visibleOrder) {}

    /** 儀表板版面請求（SP7）：可見區塊有序 id 陣列。 */
    public record DashboardLayoutRequest(List<String> visibleOrder) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, UserResponse user) {}

    public record UserResponse(Long id, String username, String displayName, Role role) {}

    /** 管理員視角的帳號資料（含啟用狀態與建立時間）。 */
    public record AdminUserResponse(Long id, String username, String displayName, Role role, boolean enabled, Instant createdAt) {}

    /** 新增帳號請求。 */
    public record CreateUserRequest(
            @NotBlank @Email String username,
            @NotBlank String displayName,
            @NotNull Role role,
            @NotBlank String password
    ) {}

    /** 編輯帳號（顯示名稱與角色）請求。 */
    public record UpdateUserRequest(@NotBlank String displayName, @NotNull Role role) {}

    /** 重設密碼請求。 */
    public record ResetPasswordRequest(@NotBlank String password) {}

    /** 啟用/停用帳號請求。 */
    public record SetEnabledRequest(@NotNull Boolean enabled) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    public record CreateCustomerRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^09\\d{8}$", message = "phone 必須為台灣手機格式，例如 0912345678") String phone,
            @NotBlank @Pattern(regexp = "^\\d{8}$", message = "taxId 必須為 8 位數字") String taxId,
            @NotBlank String industry,
            @NotNull Long ownerId,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            LocalDate renewalDueDate
    ) {}

    /**
     * 完整編輯客戶請求。name/email/phone/taxId/industry/ownerId 必填，合約日期可空。
     *
     * @param name 客戶名稱
     * @param email 電子郵件
     * @param phone 電話
     * @param taxId 統一編號
     * @param industry 產業別
     * @param ownerId 負責業務帳號 ID
     * @param contractStartDate 合約起始日（可空）
     * @param contractEndDate 合約到期日（可空）
     * @param renewalDueDate 預計續約日（可空）
     */
    public record UpdateCustomerRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^09\\d{8}$", message = "phone 必須為台灣手機格式，例如 0912345678") String phone,
            @NotBlank @Pattern(regexp = "^\\d{8}$", message = "taxId 必須為 8 位數字") String taxId,
            @NotBlank String industry,
            @NotNull Long ownerId,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            LocalDate renewalDueDate
    ) {}

    /** 可指派的負責業務選項（帳號 id + 顯示名稱）。 */
    public record OwnerOption(Long id, String displayName) {}

    /** 新增客戶表單選項：現有的不重複產業與可指派的負責業務（SALES 帳號）清單，供前端下拉選單使用。 */
    public record CustomerOptionsResponse(List<String> industries, List<OwnerOption> owners) {}

    public record UpdateStatusRequest(@NotNull CustomerStatus status) {}

    public record CreateInteractionRequest(
            @NotNull InteractionType type,
            @NotNull LocalDateTime occurredAt,
            @NotBlank String content
    ) {}

    public record CustomerSummaryResponse(
            Long id,
            String name,
            String email,
            String phone,
            String taxId,
            String industry,
            String ownerName,
            CustomerStatus status,
            String riskLevel,
            LocalDate renewalDueDate,
            LocalDateTime lastInteractionAt,
            BigDecimal opportunityAmount
    ) {}

    public record CustomerDetailResponse(
            CustomerSummaryResponse customer,
            List<ContactResponse> contacts,
            List<InteractionResponse> interactions,
            List<OpportunityResponse> opportunities
    ) {}

    public record ContactResponse(Long id, String name, String title, String email) {}

    /**
     * 新增聯絡人請求。
     *
     * @param name 聯絡人姓名
     * @param title 聯絡人職稱
     * @param email 聯絡人 email
     */
    public record CreateContactRequest(
            @NotBlank String name,
            @NotBlank String title,
            @NotBlank @Email String email
    ) {}

    /**
     * 編輯聯絡人請求。
     *
     * @param name 聯絡人姓名
     * @param title 聯絡人職稱
     * @param email 聯絡人 email
     */
    public record UpdateContactRequest(
            @NotBlank String name,
            @NotBlank String title,
            @NotBlank @Email String email
    ) {}

    public record InteractionResponse(Long id, InteractionType type, LocalDateTime occurredAt, String content, String sentiment, String intent) {}

    /**
     * 編輯互動請求。
     *
     * @param type 互動類型
     * @param occurredAt 發生時間
     * @param content 內容
     */
    public record UpdateInteractionRequest(
            @NotNull InteractionType type,
            @NotNull LocalDateTime occurredAt,
            @NotBlank String content
    ) {}

    public record OpportunityResponse(
            Long id,
            String name,
            OpportunityStage stage,
            BigDecimal amount,
            LocalDate expectedCloseDate,
            OpportunityType type,
            Long ownerId,
            String ownerName,
            LeadSource leadSource,
            Integer probability,
            CloseReason closeReason,
            String closeReasonNote,
            LocalDate actualCloseDate
    ) {}

    /**
     * 編輯商機請求（不含階段；階段由 /{id}/stage 專屬端點處理）。
     *
     * @param name 商機名稱
     * @param amount 商機金額（非負）
     * @param expectedCloseDate 預計成交日（可空）
     * @param type 商機類型
     */
    public record UpdateOpportunityRequest(
            @NotBlank String name,
            @NotNull @PositiveOrZero BigDecimal amount,
            LocalDate expectedCloseDate,
            @NotNull OpportunityType type,
            Long ownerId,
            LeadSource leadSource,
            Integer probability
    ) {}

    public record DashboardSummary(long customerCount, long activeOpportunityCount, BigDecimal opportunityAmount, long highRiskCount) {}

    public record DashboardReports(
            List<StageReport> pipelineByStage,
            List<ForecastPoint> monthlyForecast,
            List<MoneyChartPoint> industryBreakdown,
            List<ChartPoint> riskBreakdown,
            List<OwnerReport> ownerLeaderboard,
            List<MoneyChartPoint> renewalForecast,
            List<ActivityReport> recentActivities
    ) {}

    /**
     * 漏斗階段報表。
     *
     * @param stage 階段代碼
     * @param count 筆數
     * @param amount 金額合計
     * @param avgDaysInStage 平均停留天數（無歷史時為 0）
     * @param overdueCount 超過 SLA 門檻的筆數
     */
    public record StageReport(String stage, long count, BigDecimal amount,
                              Double avgDaysInStage, long overdueCount) {}

    public record MoneyChartPoint(String label, BigDecimal amount, long count) {}

    /** 月營收預測點：總額 pipeline 與機率加權預測（SP8）。 */
    public record ForecastPoint(String label, BigDecimal totalAmount, BigDecimal weightedAmount, long count) {}

    public record ChartPoint(String label, long value) {}

    public record OwnerReport(String ownerName, long customerCount, BigDecimal opportunityAmount, long highRiskCount) {}

    /**
     * 單一業務的績效統計（模組 B）。
     *
     * @param ownerId 業務帳號 id（舊資料可能為 null）
     * @param ownerName 業務顯示名稱（分組鍵）
     * @param customerCount 負責客戶數
     * @param highRiskCount 高風險客戶數
     * @param pipelineAmount 進行中商機金額（非 CLOSED_WON/CLOSED_LOST）
     * @param activeOpportunityCount 進行中商機數
     * @param wonAmount 已成交金額（CLOSED_WON）
     * @param wonCount 已成交件數
     * @param winRate 成交率 = won /（won + lost），無已關閉商機時為 0
     * @param avgDaysSinceInteraction 客戶最後互動距今天數平均（無互動客戶不計；全無則 null）
     * @param avgSentimentScore 客戶情緒分數平均（無分析則 null）
     * @param renewalsThisMonth 本月續約到期客戶數
     * @param renewalsThisQuarter 本季續約到期客戶數
     */
    public record OwnerStats(
            Long ownerId,
            String ownerName,
            long customerCount,
            long highRiskCount,
            BigDecimal pipelineAmount,
            long activeOpportunityCount,
            BigDecimal wonAmount,
            long wonCount,
            double winRate,
            Double avgDaysSinceInteraction,
            Double avgSentimentScore,
            long renewalsThisMonth,
            long renewalsThisQuarter
    ) {}

    /**
     * 團隊總覽（模組 B），供統計頁頂部 KPI 列。
     *
     * @param totalCustomers 全部客戶數
     * @param totalWonAmount 全團隊成交金額
     * @param totalPipeline 全團隊進行中商機金額
     * @param totalHighRisk 全團隊高風險客戶數
     * @param avgWinRate 各業務成交率的平均
     * @param ownerCount 業務人數
     */
    public record TeamSummary(
            long totalCustomers,
            BigDecimal totalWonAmount,
            BigDecimal totalPipeline,
            long totalHighRisk,
            double avgWinRate,
            int ownerCount
    ) {}

    /** Manager 業務分析回應：團隊總覽 + 各業務統計（依成交金額降序）。 */
    public record ManagerAnalyticsResponse(TeamSummary team, List<OwnerStats> owners) {}

    /**
     * Manager AI 分析回應（模組 C）：團隊診斷或個別業務 coaching 的快取/生成結果。
     *
     * @param scope 範圍（TEAM / OWNER）
     * @param ownerName 業務名（TEAM 時為 null）
     * @param content Markdown 報告內容
     * @param model 產出模型名（deterministic fallback 時為 null）
     * @param generatedAt 產出時間
     */
    public record ManagerInsightResponse(
            String scope,
            String ownerName,
            String content,
            String model,
            Instant generatedAt
    ) {}

    public record ActivityReport(Long customerId, String customerName, InteractionType type, LocalDateTime occurredAt, String content) {}

    public record DrilldownItem(
            Long customerId,
            String customerName,
            String industry,
            String ownerName,
            String riskLevel,
            String title,
            String stage,
            BigDecimal amount,
            String date,
            String status
    ) {}

    public record DrilldownResponse(String type, String key, String label, BigDecimal totalAmount, int count, List<DrilldownItem> items) {}

    public record ChatRequest(@NotNull Long customerId, @NotBlank String message) {}

    public record ChatResponse(String answer, List<CitationResponse> citations, RiskResponse risk, Long callId) {}

    /**
     * 前端對話歷史單則（不含 embedding）。
     *
     * @param id 訊息 id
     * @param role USER 或 ASSISTANT
     * @param content 訊息內容
     * @param createdAt 建立時間
     */
    public record ChatMessageResponse(Long id, String role, String content, Instant createdAt) {}

    public record AiFeedbackRequest(@NotBlank String decision, String note) {}

    public record UsageSummaryResponse(long totalCalls, long totalTokens, long realCalls, long fallbackCalls, long adopted, long rejected) {}

    /**
     * 單一客戶的 AI 呼叫歷史項目（供「AI 歷程」Modal 列出歷次呼叫）。
     *
     * @param id 呼叫紀錄 id
     * @param callType 呼叫類型（CHAT / ASSESSMENT / PORTFOLIO）
     * @param model 模型名稱（fallback 時為 null）
     * @param aiEnabled 是否真實呼叫 LLM（false 為 deterministic fallback）
     * @param totalTokens 總 token 數
     * @param answer 回答內容
     * @param createdAt 呼叫時間
     */
    public record AiCallHistoryItem(Long id, String callType, String model, boolean aiEnabled,
                                    int totalTokens, String answer, Instant createdAt) {}

    public record PortfolioAssessmentResponse(
            String assessment,
            int customerCount,
            int highRiskCount,
            BigDecimal totalPipeline,
            long activeOpportunityCount,
            Long callId
    ) {}

    public record CitationResponse(String title, String docType, String content, BigDecimal similarity) {}

    public record RiskResponse(@PositiveOrZero int churnRisk, @PositiveOrZero int renewalDelayRisk, List<String> reasons) {}

    public record UpdateStageRequest(
            @NotNull OpportunityStage stage,
            CloseReason closeReason,
            String closeReasonNote,
            LocalDate actualCloseDate
    ) {}

    /**
     * 新增商機請求。
     *
     * @param customerId 所屬客戶 ID
     * @param name 商機名稱
     * @param stage 商機階段
     * @param amount 商機金額（非負）
     * @param expectedCloseDate 預計成交日（可空）
     * @param type 商機類型
     */
    public record CreateOpportunityRequest(
            @NotNull Long customerId,
            @NotBlank String name,
            @NotNull OpportunityStage stage,
            @NotNull @PositiveOrZero BigDecimal amount,
            LocalDate expectedCloseDate,
            @NotNull OpportunityType type,
            Long ownerId,
            LeadSource leadSource,
            Integer probability
    ) {}

    public record AgentTraceResponse(Long customerId, String route, String finalRecommendation, List<AgentStepResponse> steps) {}

    public record AgentStepResponse(int order, String action, String status, long durationMs, String input, String output) {}

    public record RfmResponse(
            Long customerId,
            String name,
            long recencyDays,
            long frequency,
            BigDecimal monetary,
            int rScore,
            int fScore,
            int mScore,
            String segment
    ) {}

    /**
     * 情緒意圖雷達聚合結果，含 5 區塊：意圖分布、情緒趨勢、高風險互動、流失雷達、優先關懷。
     */
    public record SentimentRadarResponse(
            List<IntentCount> intentDistribution,
            List<SentimentTrendPoint> sentimentTrend,
            List<HighRiskInteraction> highRiskInteractions,
            List<ChurnRadarItem> churnRadar,
            List<PriorityCareItem> priorityCare
    ) {}

    /** 意圖分布單筆：意圖名稱與筆數。 */
    public record IntentCount(String intent, long count) {}

    /** 月情緒趨勢單點：月份 yyyy-MM 與三種情緒筆數。 */
    public record SentimentTrendPoint(String month, long positive, long neutral, long negative) {}

    /** 高風險互動單筆（NEGATIVE 且意圖為流失 / 客訴）。 */
    public record HighRiskInteraction(
            Long customerId,
            String customerName,
            LocalDateTime occurredAt,
            String type,
            String intent,
            String sentiment,
            String content
    ) {}

    /** 流失雷達單筆：負面 / 流失訊號 / 客訴計數與加權分數。 */
    public record ChurnRadarItem(
            Long customerId,
            String name,
            long negativeCount,
            long churnSignalCount,
            long complaintCount,
            int score
    ) {}

    /** 優先關懷單筆：客戶與中文關懷理由。 */
    public record PriorityCareItem(Long customerId, String name, String reason) {}

    /** 建立 CRM 任務請求。 */
    public record CreateTaskRequest(
            @NotNull Long customerId,
            Long opportunityId,
            Long contactId,
            @NotNull com.aicrm.crm.domain.CrmTaskType type,
            @NotNull com.aicrm.crm.domain.CrmTaskPriority priority,
            @NotBlank String title,
            String description,
            @NotNull Long assigneeId,
            @NotNull LocalDateTime scheduledStart,
            @NotNull LocalDateTime scheduledEnd,
            @NotNull com.aicrm.crm.domain.CrmTaskSource source) {}

    /** 編輯 CRM 任務請求；version 用於拒絕過期畫面送出的更新。 */
    public record UpdateTaskRequest(
            @NotNull com.aicrm.crm.domain.CrmTaskType type,
            @NotNull com.aicrm.crm.domain.CrmTaskPriority priority,
            @NotBlank String title,
            String description,
            @NotNull Long assigneeId,
            @NotNull LocalDateTime scheduledStart,
            @NotNull LocalDateTime scheduledEnd,
            @NotNull Long version) {}

    /** CRM 任務延期請求。 */
    public record PostponeTaskRequest(@NotNull LocalDateTime scheduledStart,
                                      @NotNull LocalDateTime scheduledEnd,
                                      @NotNull Long version) {}

    /** 完成 CRM 任務請求，version 用來拒絕順序式舊畫面更新。 */
    public record CompleteTaskRequest(@NotNull Long version) {}

    /** CRM 任務 API 回應。 */
    public record TaskResponse(
            Long id, Long customerId, Long opportunityId, Long contactId,
            com.aicrm.crm.domain.CrmTaskType type,
            com.aicrm.crm.domain.CrmTaskStatus status,
            com.aicrm.crm.domain.CrmTaskPriority priority,
            String title, String description, Long assigneeId, String assigneeName,
            LocalDateTime scheduledStart, LocalDateTime scheduledEnd, LocalDateTime completedAt,
            int postponeCount, com.aicrm.crm.domain.CrmTaskSource source, long version,
            Instant revisionTimestamp) {}

    /** 模型設定項目（含供應商關聯、已確認能力及能力來源）。 */
    public record ModelOptionItem(
            String model,
            Long providerId,
            java.util.Set<com.aicrm.crm.domain.ModelCapability> capabilities,
            com.aicrm.crm.domain.CapabilitySource capabilitySource) {
        /** 正規化舊資料或缺漏欄位，禁止從模型名稱推測能力。 */
        public ModelOptionItem {
            capabilities = capabilities == null ? java.util.Set.of() : java.util.Set.copyOf(capabilities);
            capabilitySource = capabilitySource == null
                    ? com.aicrm.crm.domain.CapabilitySource.UNKNOWN
                    : capabilitySource;
        }
    }

    /** Chat、OCR 與轉錄三種用途的模型 assignment。 */
    public record AiModelAssignments(
            String chatModel,
            Long chatProviderId,
            String ocrModel,
            Long ocrProviderId,
            String transcriptionModel,
            Long transcriptionProviderId) {}

    /** Vision 模型辨識後的標準化名片欄位。 */
    public record RecognizedBusinessCard(String personName, String title, String email, String phone,
                                         String companyName, String website, Map<String, Double> confidence,
                                         List<String> warnings) {}

    /** 可能重複的客戶候選，不自動選擇合併。 */
    public record BusinessCardDuplicateCandidate(Long customerId, String customerName,
                                                  List<String> matchedBy) {}

    /** 名片 intake 查詢與建立回應。 */
    public record BusinessCardIntakeResponse(Long id, String status, Long mediaId,
                                              RecognizedBusinessCard recognized,
                                              List<BusinessCardDuplicateCandidate> duplicateCandidates,
                                              String errorSummary) {}

    /** 人工確認名片建檔請求；CREATE 與 MERGE 必須明確選擇。 */
    public record ConfirmBusinessCardRequest(
            @NotBlank String customerAction, Long customerId,
            @NotBlank String customerName, @NotBlank @Email String customerEmail,
            @NotBlank String customerPhone, @NotBlank String taxId, @NotBlank String industry,
            @NotBlank String contactName, @NotBlank String contactTitle, @NotBlank @Email String contactEmail,
            @NotBlank String opportunityName, @NotNull @PositiveOrZero BigDecimal opportunityAmount,
            LocalDate expectedCloseDate, @NotNull LocalDateTime callAt) {}

    /** 名片確認後所有正式 CRM 關聯 ID。 */
    public record BusinessCardConfirmResponse(Long intakeId, Long customerId, Long contactId,
                                               Long opportunityId, Long taskId) {}

    // ===== V24 AI 會議 Copilot =====

    /**
     * 會議 Copilot 草稿的單一變更項；每項有穩定 changeId，confirm 只套用被選定者。
     *
     * @param changeId 穩定變更識別碼
     * @param type INTERACTION / TASK / OPPORTUNITY_PATCH / STAKEHOLDER_SUGGESTION
     * @param description 人可讀描述
     * @param lowConfidence 是否為低信心推測
     * @param selectedByDefault 前端預設是否勾選（低信心預設不勾）
     * @param detail 套用所需的型別特定欄位
     */
    public record MeetingChange(String changeId, String type, String description,
                                boolean lowConfidence, boolean selectedByDefault, Map<String, String> detail) {
        /** 正規化 detail 為不可變 map，避免 null 與外部改動。 */
        public MeetingChange {
            detail = detail == null ? Map.of() : Map.copyOf(detail);
        }
    }

    /** 會議 Copilot 結構化草稿：AI 摘要與可勾選變更清單。 */
    public record MeetingDraft(String summary, List<MeetingChange> changes) {}

    /** 會議 Copilot session 建立與查詢回應。 */
    public record MeetingCopilotSessionResponse(Long id, String status, Long mediaId, Long customerId,
                                                Long opportunityId, String transcript, String summary,
                                                List<MeetingChange> changes, String errorSummary) {}

    /** 會議 Copilot 確認請求：選定要套用的 changeId 清單。 */
    public record ConfirmMeetingRequest(@NotNull List<String> selectedChangeIds) {}

    /** 會議 Copilot 確認結果：實際套用的變更與建立的正式 CRM 資料 ID。 */
    public record MeetingCopilotConfirmResponse(Long sessionId, List<String> appliedChangeIds, Long interactionId,
                                                List<Long> taskIds, Long opportunityId, int stakeholderSuggestionCount) {}

    /** Admin 人工覆寫指定 Provider 模型能力的請求。 */
    public record ModelCapabilitiesRequest(
            @jakarta.validation.constraints.NotNull Long providerId,
            @jakarta.validation.constraints.NotNull java.util.Set<com.aicrm.crm.domain.ModelCapability> capabilities) {}

    /** AI 供應商檢視（apiKey 永不回傳前端，以 apiKeySet 布林代替）。 */
    public record AiProviderItem(Long id, String name, String baseUrl, boolean apiKeySet) {}

    /** 新增/更新供應商請求；apiKey 為 null 代表不更換現有金鑰。 */
    public record AiProviderRequest(
        @jakarta.validation.constraints.NotBlank String name,
        String baseUrl,
        String apiKey
    ) {}

    /** AI 設定回應（含供應商清單與帶 providerId 的模型選項）。 */
    public record AiSettingsResponse(
        String currentModel,
        Long currentProviderId,
        List<ModelOptionItem> modelOptions,
        List<AiProviderItem> providers,
        String envDefaultModel,
        String source,
        // 可編輯模型參數（null/空 = 未設定，沿用預設）
        Double temperature,
        Integer maxCompletionTokens,
        String reasoningEffort,
        String ocrModel,
        Long ocrProviderId,
        String transcriptionModel,
        Long transcriptionProviderId
    ) {}

    /** AI 設定更新請求。 */
    public record AiSettingsRequest(String model, Long providerId, List<ModelOptionItem> modelOptions,
                                    Double temperature, Integer maxCompletionTokens, String reasoningEffort) {}

    /** 模型競速測試請求（providerId 指定使用哪組 API 憑證）。 */
    public record AiTestRequest(String message, String model, Long providerId) {}

    /** 單一模型的競速測試結果（供評分 API 傳入）。 */
    public record ModelResultItem(
            String model,
            long firstTokenMs,
            long totalMs,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            String content) {}

    /** 多模型評分請求：包含所有模型的測試結果與競速 session 識別碼。 */
    public record AiScoreRequest(List<ModelResultItem> results, String sessionId) {}

    /** 儲存單一模型競速測試結果的請求 DTO（前端在每個模型測試完成後呼叫）。 */
    public record AiTestLogRequest(
            @jakarta.validation.constraints.NotBlank String model,
            @jakarta.validation.constraints.NotBlank String sessionId,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            String answer
    ) {}

    // ===== 我的工作檯個人 AI (SP9-B) =====

    /** 工作檯待辦項目（純 DB 規則計算）。type: HIGH_RISK / RENEWAL_DUE / STALE_OPPORTUNITY。 */
    public record WorkspaceTodoItem(String type, Long customerId, String customerName,
                                    String reason, String severity) {}

    /** AI 建議商機草稿（使用者確認後才建立）。 */
    public record SuggestedOpportunityDraft(Long customerId, String customerName, String name,
                                            String suggestedStage, BigDecimal amount, String rationale) {}

    /** 工作檯問答請求（無 customerId 為總覽；有則深入單客戶）。scope: self / all。 */
    public record WorkspaceChatRequest(String scope, Long customerId, @NotBlank String message) {}

    /** 工作檯推薦的非串流回應（GET 讀快取用）。summary 可為 null（尚未產生）。 */
    public record WorkspaceRecommendationResponse(String summary, String model, String generatedAt,
                                                  List<WorkspaceTodoItem> todos,
                                                  List<SuggestedOpportunityDraft> drafts) {}

    // ===== V25 AI 跟進信與 Zeabur Sendmail =====

    /** 產生跟進信草稿請求；opportunityId 選填（帶入時 grounding 納入該商機）。 */
    public record CreateFollowUpDraftRequest(Long opportunityId) {}

    /** 人工修改草稿請求（產生新版本）。 */
    public record UpdateFollowUpDraftRequest(@NotBlank String subject, @NotBlank String body) {}

    /**
     * 跟進信草稿回應。
     *
     * @param id 草稿 id
     * @param customerId 所屬客戶 id
     * @param opportunityId 關聯商機 id（可空）
     * @param versionNumber 版本號（1 起算，人工修改遞增）
     * @param parentId 上一版本 id（第一版為 null）
     * @param model 產生模型（deterministic 時為 null）
     * @param grounding AI 引用依據（客戶／商機／近期互動）
     * @param subject 主旨
     * @param body 內文
     * @param edited 是否為人工修改版本
     * @param approvedBy 核准者（未核准為 null）
     * @param approvedAt 核准時間（未核准為 null）
     */
    public record FollowUpDraftResponse(Long id, Long customerId, Long opportunityId, int versionNumber, Long parentId,
                                        String model, String grounding, String subject, String body, boolean edited,
                                        String approvedBy, Instant approvedAt) {}

    /**
     * 外寄郵件回應。
     *
     * @param id 外寄郵件 id
     * @param draftId 來源草稿 id
     * @param from 寄件者（統一公司信箱）
     * @param replyTo Reply-To（負責業務 Email）
     * @param recipient 收件者
     * @param subject 主旨快照
     * @param body 內文快照
     * @param status QUEUED / SENT / FAILED
     * @param messageId Zeabur message id（未寄出為 null）
     * @param retryCount 重試次數
     * @param errorSummary 去敏錯誤摘要（絕不含憑證）
     * @param sentAt 寄出時間（未寄出為 null）
     */
    public record OutboundEmailResponse(Long id, Long draftId, String from, String replyTo, String recipient,
                                        String subject, String body, String status, String messageId, int retryCount,
                                        String errorSummary, Instant sentAt) {}
}
