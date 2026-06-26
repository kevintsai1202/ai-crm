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

    public record StageReport(String stage, long count, BigDecimal amount) {}

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

    /** 模型設定項目（含供應商關聯）。 */
    public record ModelOptionItem(String model, Long providerId) {}

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
        String source
    ) {}

    /** AI 設定更新請求。 */
    public record AiSettingsRequest(String model, Long providerId, List<ModelOptionItem> modelOptions) {}

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
}
