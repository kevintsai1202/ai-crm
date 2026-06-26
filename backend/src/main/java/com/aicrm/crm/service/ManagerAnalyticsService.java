package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager 業務分析聚合服務：以 ownerName 為單位彙總各業務績效（成交、活躍商機、情緒、續約），
 * 純 Java/DB 計算，不呼叫 LLM。供 /api/manager/analytics 與模組 C 的 AI 分析作為資料來源。
 */
@Service
@Transactional(readOnly = true)
public class ManagerAnalyticsService {

    /** 客戶資料存取（含 LAZY 商機 / 互動，靠 default_batch_fetch_size 批次載入）。 */
    private final CustomerRepository customers;

    /** 互動情緒意圖分析結果存取，供情緒平均聚合。 */
    private final InteractionInsightRepository insights;

    public ManagerAnalyticsService(CustomerRepository customers, InteractionInsightRepository insights) {
        this.customers = customers;
        this.insights = insights;
    }

    /**
     * 聚合所有業務的績效統計與團隊總覽。
     *
     * @return Manager 業務分析回應（業務清單依成交金額降序）
     */
    public Dtos.ManagerAnalyticsResponse analytics() {
        var all = customers.findAll();
        var today = LocalDate.now();

        // 每個客戶的情緒分數平均（先依 customerId 聚合，避免逐客戶查 DB）
        Map<Long, Double> avgScoreByCustomer = insights.findAll().stream()
                .collect(Collectors.groupingBy(InteractionInsight::getCustomerId,
                        Collectors.averagingInt(InteractionInsight::getSentimentScore)));

        // 混合口徑：客戶層級指標按「客戶 owner」分組、商機層級指標按「商機 owner」分組，業務名單取兩者聯集。
        // 回填後商機 owner = 客戶 owner，故切換當下與舊口徑等價；僅商機改派他人時才分歧。
        // 兩側 ownerName 皆恆非空：customer.owner_name 為 schema NOT NULL；商機 owner_name 由 V18 自 customer.owner_name 回填。
        // 商機側 filter 僅為防禦（理論上不丟任何商機，等價性成立）。
        var customersByOwner = all.stream().collect(Collectors.groupingBy(Customer::getOwnerName));
        var oppsByOwner = all.stream().flatMap(c -> c.getOpportunities().stream())
                .filter(o -> o.getOwnerName() != null)
                .collect(Collectors.groupingBy(Opportunity::getOwnerName));
        var ownerNames = new TreeSet<String>();
        ownerNames.addAll(customersByOwner.keySet());
        ownerNames.addAll(oppsByOwner.keySet());

        var owners = ownerNames.stream()
                .map(name -> buildOwnerStats(name,
                        customersByOwner.getOrDefault(name, List.of()),
                        oppsByOwner.getOrDefault(name, List.of()),
                        today, avgScoreByCustomer))
                .sorted((a, b) -> b.wonAmount().compareTo(a.wonAmount()))
                .toList();

        return new Dtos.ManagerAnalyticsResponse(buildTeamSummary(all.size(), owners), owners);
    }

    /**
     * 聚合單一業務的績效。
     *
     * @param ownerName 業務顯示名稱
     * @param ownerCustomers 該業務（依客戶 owner）負責的客戶
     * @param opps 該業務（依商機 owner）經手的商機
     * @param today 基準日
     * @param avgScoreByCustomer 每客戶情緒平均
     * @return 該業務的統計
     */
    private Dtos.OwnerStats buildOwnerStats(String ownerName, List<Customer> ownerCustomers, List<Opportunity> opps,
                                            LocalDate today, Map<Long, Double> avgScoreByCustomer) {
        // ownerId 先取客戶端正規關聯，無則退而取商機端 owner（純商機 owner 情況）
        Long ownerId = ownerCustomers.stream()
                .map(c -> c.getOwner() == null ? null : c.getOwner().getId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> opps.stream()
                        .map(o -> o.getOwner() == null ? null : o.getOwner().getId())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
        var pipelineAmount = opps.stream()
                .filter(o -> o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST)
                .map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeCount = opps.stream()
                .filter(o -> o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST)
                .count();
        var wonOpps = opps.stream().filter(o -> o.getStage() == OpportunityStage.CLOSED_WON).toList();
        var wonAmount = wonOpps.stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long wonCount = wonOpps.size();
        long lostCount = opps.stream().filter(o -> o.getStage() == OpportunityStage.CLOSED_LOST).count();
        // 成交率 = won /（won + lost）；無已關閉商機時為 0
        double winRate = (wonCount + lostCount) == 0 ? 0.0 : (double) wonCount / (wonCount + lostCount);

        long highRisk = ownerCustomers.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();

        // 互動活躍度：有互動客戶的「最後互動距今天數」平均
        var daysList = ownerCustomers.stream()
                .map(c -> lastInteractionDays(c, today))
                .filter(Objects::nonNull)
                .toList();
        Double avgDays = daysList.isEmpty() ? null
                : daysList.stream().mapToLong(Long::longValue).average().orElse(0);

        // 客戶情緒平均：取有分數的客戶平均
        var scoreList = ownerCustomers.stream()
                .map(c -> avgScoreByCustomer.get(c.getId()))
                .filter(Objects::nonNull)
                .toList();
        Double avgSentiment = scoreList.isEmpty() ? null
                : scoreList.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        long renewalsThisMonth = countRenewalsInRange(ownerCustomers,
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        int q = (today.getMonthValue() - 1) / 3;
        var qStart = LocalDate.of(today.getYear(), q * 3 + 1, 1);
        var qEnd = qStart.plusMonths(3).minusDays(1);
        long renewalsThisQuarter = countRenewalsInRange(ownerCustomers, qStart, qEnd);

        return new Dtos.OwnerStats(ownerId, ownerName, ownerCustomers.size(), highRisk,
                pipelineAmount, activeCount, wonAmount, wonCount, winRate,
                avgDays, avgSentiment, renewalsThisMonth, renewalsThisQuarter);
    }

    /**
     * 客戶最後互動距今天數（無互動回 null）。
     */
    private Long lastInteractionDays(Customer c, LocalDate today) {
        LocalDateTime last = c.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return last == null ? null : ChronoUnit.DAYS.between(last.toLocalDate(), today);
    }

    /**
     * 計算續約日落在 [from, to]（含端點）的客戶數。
     */
    private long countRenewalsInRange(List<Customer> ownerCustomers, LocalDate from, LocalDate to) {
        return ownerCustomers.stream()
                .map(Customer::getRenewalDueDate)
                .filter(Objects::nonNull)
                .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                .count();
    }

    /**
     * 由各業務統計彙總團隊總覽。
     */
    private Dtos.TeamSummary buildTeamSummary(int totalCustomers, List<Dtos.OwnerStats> owners) {
        var totalWon = owners.stream().map(Dtos.OwnerStats::wonAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalPipeline = owners.stream().map(Dtos.OwnerStats::pipelineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalHighRisk = owners.stream().mapToLong(Dtos.OwnerStats::highRiskCount).sum();
        double avgWinRate = owners.isEmpty() ? 0.0
                : owners.stream().mapToDouble(Dtos.OwnerStats::winRate).average().orElse(0);
        return new Dtos.TeamSummary(totalCustomers, totalWon, totalPipeline, totalHighRisk, avgWinRate, owners.size());
    }
}
