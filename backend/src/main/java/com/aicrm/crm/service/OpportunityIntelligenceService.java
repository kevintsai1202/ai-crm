package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.CrmTask;
import com.aicrm.crm.domain.CrmTaskStatus;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityHealthSnapshot;
import com.aicrm.crm.domain.Intent;
import com.aicrm.crm.domain.Sentiment;
import com.aicrm.crm.repository.CrmTaskRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import com.aicrm.crm.repository.OpportunityHealthSnapshotRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import com.aicrm.crm.repository.OpportunityStageHistoryRepository;
import com.aicrm.crm.security.OwnershipGuard;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.service.OpportunityHealthCalculator.HealthComponent;
import com.aicrm.crm.service.OpportunityHealthCalculator.HealthScore;
import com.aicrm.crm.service.OpportunityHealthCalculator.OpportunityContext;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 商機智能應用服務（V26）：組裝評分所需訊號 → 呼叫 {@link OpportunityHealthCalculator} 純函式評分
 * → 依評分結果 deterministic 產生「下一最佳行動」→ 另存健康度 snapshot（保留歷史供趨勢）。
 *
 * <p>重要邊界：本服務只讀商機/互動/情緒/任務/聯絡人訊號後另存 snapshot，
 * <b>絕不修改 Opportunity 的 stage 或 probability</b>（不對 Opportunity 呼叫任何 setter 或 save）。
 * 評分為純函式且不呼叫 LLM；下一最佳行動目前一律走 deterministic 產生（AI 為選配，未設定時即為 fallback）。</p>
 */
@Service
public class OpportunityIntelligenceService {

    /** 台北時區：預計成交日等 LocalDate 比較與互動天數計算的基準時區。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    private final OpportunityRepository opportunities;
    private final OpportunityStageHistoryRepository stageHistories;
    private final InteractionInsightRepository insights;
    private final CrmTaskRepository tasks;
    private final OpportunityHealthSnapshotRepository snapshots;
    private final OpportunityHealthCalculator calculator;
    private final OwnershipGuard ownershipGuard;
    private final ObjectMapper mapper;

    /** 注入評分所需的 repository、純函式計算器、擁有權守衛與 JSON 序列化工具。 */
    public OpportunityIntelligenceService(OpportunityRepository opportunities,
                                          OpportunityStageHistoryRepository stageHistories,
                                          InteractionInsightRepository insights,
                                          CrmTaskRepository tasks,
                                          OpportunityHealthSnapshotRepository snapshots,
                                          OpportunityHealthCalculator calculator,
                                          OwnershipGuard ownershipGuard,
                                          ObjectMapper mapper) {
        this.opportunities = opportunities;
        this.stageHistories = stageHistories;
        this.insights = insights;
        this.tasks = tasks;
        this.snapshots = snapshots;
        this.calculator = calculator;
        this.ownershipGuard = ownershipGuard;
        this.mapper = mapper;
    }

    /**
     * 取得商機最新健康度 snapshot；若尚無任何 snapshot 則即時計算並保存第一筆，確保永遠有可呈現的結果。
     *
     * @param opportunityId 商機 id
     * @param principal 登入主體（owner scope 驗證）
     * @return 健康度回應（含最新總分、分項、下一最佳行動與趨勢）
     */
    @Transactional
    public Dtos.OpportunityHealthResponse getHealth(Long opportunityId, AuthPrincipal principal) {
        Opportunity opportunity = loadVisible(opportunityId);
        return snapshots.findFirstByOpportunityIdOrderByCalculatedAtDescIdDesc(opportunityId)
                .map(latest -> toResponse(opportunityId, latest))
                .orElseGet(() -> toResponse(opportunityId, computeAndSave(opportunity)));
    }

    /**
     * 重算健康度並保存新的 snapshot（保留歷史）；回傳最新結果與趨勢。
     * 重算過程只讀取商機相關訊號，不修改 Opportunity 的 stage/probability。
     *
     * @param opportunityId 商機 id
     * @param principal 登入主體（owner scope 驗證）
     * @return 重算後的健康度回應
     */
    @Transactional
    public Dtos.OpportunityHealthResponse recalculate(Long opportunityId, AuthPrincipal principal) {
        Opportunity opportunity = loadVisible(opportunityId);
        return toResponse(opportunityId, computeAndSave(opportunity));
    }

    /**
     * 載入商機並套用 owner scope：SALES 僅能存取自己負責客戶的商機，越權由 OwnershipGuard 丟 403（沿用商機既有慣例）。
     *
     * @param opportunityId 商機 id
     * @return 可見的商機 entity
     */
    private Opportunity loadVisible(Long opportunityId) {
        Opportunity opportunity = opportunities.findById(opportunityId)
                .orElseThrow(() -> new EntityNotFoundException("查無此商機：" + opportunityId));
        // 以客戶負責業務名稱做擁有權驗證，與 OpportunityController 一致。
        ownershipGuard.assertCanAccessOwner(opportunity.getCustomer().getOwnerName());
        return opportunity;
    }

    /**
     * 組裝評分訊號 → 純函式評分 → deterministic 產生下一最佳行動 → 保存 snapshot。
     *
     * @param opportunity 商機 entity
     * @return 已保存的 snapshot
     */
    private OpportunityHealthSnapshot computeAndSave(Opportunity opportunity) {
        Instant now = Instant.now();
        OpportunityContext context = buildContext(opportunity, now);
        HealthScore score = calculator.calculate(context);
        String nextBestAction = buildNextBestAction(score);
        String componentsJson = writeComponents(score.components());
        // model=null 表示 deterministic 產生（AI 為選配，未啟用時即為 fallback）。
        OpportunityHealthSnapshot snapshot = new OpportunityHealthSnapshot(opportunity.getId(), score.total(),
                componentsJson, nextBestAction, OpportunityHealthCalculator.RULE_VERSION, null, now);
        return snapshots.save(snapshot);
    }

    /**
     * 由商機/客戶/互動/情緒/任務/聯絡人組裝純資料 context。
     *
     * @param opportunity 商機 entity
     * @param now 評估時間（固定 clock 基準）
     * @return 評分用 context
     */
    private OpportunityContext buildContext(Opportunity opportunity, Instant now) {
        Customer customer = opportunity.getCustomer();
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        boolean closed = opportunity.getStage() != null
                && opportunity.getStage().name().startsWith("CLOSED_");

        // 階段停留天數：取「進入目前階段」的最後一次歷史紀錄（V20），無紀錄則 null 交由計算器中性計分。
        Long daysInStage = opportunity.getStage() == null ? null
                : stageHistories.findLatestEntryToStage(opportunity.getId(), opportunity.getStage().name())
                        .map(h -> ChronoUnit.DAYS.between(h.getChangedAt(), now))
                        .orElse(null);

        // 互動熱度：以客戶互動（最近時間 + 近30天次數）作為商機互動熱度 proxy。
        List<Interaction> interactions = customer.getInteractions();
        LocalDateTime lastInteractionAt = interactions.stream()
                .map(Interaction::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime cutoff = today.atStartOfDay().minusDays(30);
        int interactionsLast30Days = (int) interactions.stream()
                .map(Interaction::getOccurredAt)
                .filter(t -> t != null && !t.isBefore(cutoff))
                .count();

        // 情緒/意圖訊號：讀 SP6 interaction_insights（客戶層彙總）。
        List<InteractionInsight> customerInsights = insights.findByCustomerId(customer.getId());
        int negativeSentimentCount = (int) customerInsights.stream()
                .filter(i -> i.getSentiment() == Sentiment.NEGATIVE).count();
        int riskIntentCount = (int) customerInsights.stream()
                .filter(i -> i.getIntent() == Intent.CHURN_SIGNAL || i.getIntent() == Intent.COMPLAINT).count();

        // Task 狀態：本商機的未完成/逾期任務。
        List<CrmTask> opportunityTasks = tasks.findByOpportunityId(opportunity.getId());
        LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ZONE);
        int openTaskCount = (int) opportunityTasks.stream().filter(this::isOpen).count();
        int overdueTaskCount = (int) opportunityTasks.stream()
                .filter(t -> isOpen(t) && t.getScheduledEnd() != null && t.getScheduledEnd().isBefore(nowLocal))
                .count();

        // 決策鏈完整度（優雅降級）：以客戶聯絡人數估算，V27 決策角色尚未建置。
        int contactCount = customer.getContacts().size();

        return new OpportunityContext(closed, daysInStage,
                opportunity.getStage() == null ? null : opportunity.getStage().name(),
                opportunity.getExpectedCloseDate(), lastInteractionAt, interactionsLast30Days,
                negativeSentimentCount, riskIntentCount, openTaskCount, overdueTaskCount, contactCount, now);
    }

    /** 未完成任務判斷：OPEN 或 IN_PROGRESS。 */
    private boolean isOpen(CrmTask task) {
        return task.getStatus() == CrmTaskStatus.OPEN || task.getStatus() == CrmTaskStatus.IN_PROGRESS;
    }

    /**
     * 依評分結果 deterministic 產生「下一最佳行動」：挑出扣分最重（相對缺口最大）的分項給出對應建議。
     *
     * <p>此為 deterministic fallback；LLM 只用來把此結果轉為更自然的文案（選配、失敗回退本文字）。
     * 目前一律使用 deterministic 版本以確保可重現。</p>
     *
     * @param score 健康度評分
     * @return 下一最佳行動文案
     */
    private String buildNextBestAction(HealthScore score) {
        // 找出相對缺口（maxScore-score）最大的分項；缺口相同時以固定分項順序決定，確保 deterministic。
        HealthComponent weakest = score.components().stream()
                .max(Comparator.comparingInt(c -> c.maxScore() - c.score()))
                .orElse(null);
        if (weakest == null || weakest.maxScore() - weakest.score() == 0) {
            return "各面向健康度良好，建議維持既有跟進節奏並把握預計成交日推進成交。";
        }
        String suggestion = switch (weakest.key()) {
            case "STAGE_DWELL" -> "此商機在目前階段停留過久，建議安排一次明確的推進會議，設定進入下一階段的具體條件與時程。";
            case "EXPECTED_CLOSE" -> "預計成交日時程已亮紅燈，建議與客戶重新確認決策時程並更新預計成交日，必要時升級處理。";
            case "INTERACTION_HEAT" -> "近期互動明顯冷卻，建議立即安排一次電話或會議重啟接觸，並建立後續固定跟進節奏。";
            case "SENTIMENT_INTENT" -> "偵測到負面情緒或流失/客訴訊號，建議優先安排關懷會議釐清疑慮，必要時由主管介入止血。";
            case "TASK_STATUS" -> "後續任務落後或缺乏規劃，建議立即建立明確的下一步任務並清理逾期項目。";
            case "DECISION_CHAIN" -> "決策鏈涵蓋度不足，建議補充關鍵決策者聯絡人並釐清採購決策流程（決策鏈資料有限）。";
            default -> "建議針對健康度較弱的面向安排具體跟進行動。";
        };
        return "健康度最弱面向為「" + weakest.label() + "」（" + weakest.score() + "/" + weakest.maxScore()
                + "）。" + suggestion;
    }

    /** 將分項明細序列化為 JSON 文字保存。 */
    private String writeComponents(List<HealthComponent> components) {
        List<Dtos.HealthComponentDto> dtos = components.stream()
                .map(c -> new Dtos.HealthComponentDto(c.key(), c.label(), c.score(), c.maxScore(), c.reason(), c.evidence()))
                .toList();
        return mapper.writeValueAsString(dtos);
    }

    /** 將分項 JSON 文字反序列化回 DTO。 */
    private List<Dtos.HealthComponentDto> readComponents(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return mapper.readValue(json, new TypeReference<List<Dtos.HealthComponentDto>>() {});
    }

    /**
     * 將最新 snapshot 組裝為回應，並附上該商機全部 snapshot 的趨勢（時間升冪）。
     *
     * @param opportunityId 商機 id
     * @param latest 最新 snapshot
     * @return 健康度回應
     */
    private Dtos.OpportunityHealthResponse toResponse(Long opportunityId, OpportunityHealthSnapshot latest) {
        List<Dtos.HealthTrendPoint> trend = snapshots.findByOpportunityIdOrderByCalculatedAtAscIdAsc(opportunityId).stream()
                .map(s -> new Dtos.HealthTrendPoint(s.getTotalScore(), s.getCalculatedAt()))
                .toList();
        return new Dtos.OpportunityHealthResponse(opportunityId, latest.getTotalScore(),
                readComponents(latest.getComponents()), latest.getNextBestAction(), latest.getRuleVersion(),
                latest.getModel(), latest.getCalculatedAt(), trend);
    }
}
