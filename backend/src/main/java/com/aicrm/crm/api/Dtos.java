package com.aicrm.crm.api;

import com.aicrm.crm.domain.CustomerStatus;
import com.aicrm.crm.domain.InteractionType;
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

    public record HealthResponse(String status, Instant timestamp, Map<String, String> features) {}

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

    public record InteractionResponse(Long id, InteractionType type, LocalDateTime occurredAt, String content, String sentiment, String intent) {}

    public record OpportunityResponse(
            Long id,
            String name,
            OpportunityStage stage,
            BigDecimal amount,
            LocalDate expectedCloseDate,
            OpportunityType type
    ) {}

    public record DashboardSummary(long customerCount, long activeOpportunityCount, BigDecimal opportunityAmount, long highRiskCount) {}

    public record DashboardReports(
            List<StageReport> pipelineByStage,
            List<MoneyChartPoint> monthlyForecast,
            List<MoneyChartPoint> industryBreakdown,
            List<ChartPoint> riskBreakdown,
            List<OwnerReport> ownerLeaderboard,
            List<MoneyChartPoint> renewalForecast,
            List<ActivityReport> recentActivities
    ) {}

    public record StageReport(String stage, long count, BigDecimal amount) {}

    public record MoneyChartPoint(String label, BigDecimal amount, long count) {}

    public record ChartPoint(String label, long value) {}

    public record OwnerReport(String ownerName, long customerCount, BigDecimal opportunityAmount, long highRiskCount) {}

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

    public record UpdateStageRequest(@NotNull OpportunityStage stage) {}

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
            @NotNull OpportunityType type
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
}
