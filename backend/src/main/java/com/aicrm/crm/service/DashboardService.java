package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.config.CacheConfig;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.repository.CustomerRepository;
import java.math.BigDecimal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard 聚合業務邏輯，集中處理首頁摘要、報表圖表與圖表下鑽明細。
 * 由 CustomerService 拆出（SP5），行為不變，只更換所在類別與注入來源。
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** 客戶資料存取介面（與 CustomerService 相同來源，保持行為一致）。 */
    private final CustomerRepository customers;

    /** Entity/DTO 轉換工具。 */
    private final CustomerMapper mapper = new CustomerMapper();

    /** 階段停留／超時（SP13）。 */
    private final OpportunityStageHistoryService stageHistory;

    public DashboardService(CustomerRepository customers, OpportunityStageHistoryService stageHistory) {
        this.customers = customers;
        this.stageHistory = stageHistory;
    }

    /**
     * 計算 Dashboard 摘要統計。
     *
     * @return Dashboard 統計 DTO
     */
    @Cacheable(CacheConfig.CACHE_DASHBOARD_SUMMARY)
    @Transactional(readOnly = true)
    public Dtos.DashboardSummary dashboardSummary() {
        var all = allCustomersWithDetail();
        var amount = all.stream()
                .flatMap(c -> c.getOpportunities().stream())
                .map(o -> o.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var activeOpp = all.stream()
                .flatMap(c -> c.getOpportunities().stream())
                .filter(o -> o.getStage() != OpportunityStage.CLOSED_LOST)
                .count();
        var highRisk = all.stream().map(mapper::toSummary).filter(c -> "HIGH".equals(c.riskLevel())).count();
        return new Dtos.DashboardSummary(all.size(), activeOpp, amount, highRisk);
    }

    /**
     * 產生 CRM 經典圖表與報表資料，供前端 Dashboard 視覺化使用。
     *
     * @return Dashboard 報表 DTO
     */
    @Cacheable(CacheConfig.CACHE_DASHBOARD_REPORTS)
    @Transactional(readOnly = true)
    public Dtos.DashboardReports dashboardReports() {
        return buildReports(null);
    }

    /**
     * 產生報表並可依商機來源過濾漏斗與預測；leadSource 為 null 表全部（切片查詢，不快取）。
     *
     * @param leadSource 商機來源（INBOUND/OUTBOUND/REFERRAL）；null 為不過濾
     * @return Dashboard 報表 DTO
     */
    @Transactional(readOnly = true)
    public Dtos.DashboardReports dashboardReports(String leadSource) {
        return buildReports(leadSource);
    }

    /**
     * 組裝報表內容；opportunities 依 leadSource 過濾（影響漏斗與月營收預測），
     * 客戶層級報表（產業/業務/續約）不受來源過濾。
     *
     * @param leadSource 商機來源；null 為不過濾
     * @return Dashboard 報表 DTO
     */
    private Dtos.DashboardReports buildReports(String leadSource) {
        var all = allCustomersWithDetail();
        var summaries = all.stream().map(mapper::toSummary).toList();
        var opportunities = all.stream().flatMap(customer -> customer.getOpportunities().stream())
                .filter(opp -> leadSource == null || opp.getLeadSource().name().equals(leadSource))
                .toList();

        var dwell = stageHistory.computeDwellStats(opportunities);
        var pipelineByStage = java.util.Arrays.stream(OpportunityStage.values())
                .map(stage -> {
                    var stageOpps = opportunities.stream().filter(opp -> opp.getStage() == stage).toList();
                    var amount = stageOpps.stream().map(opp -> opp.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
                    var stats = dwell.getOrDefault(stage,
                            new OpportunityStageHistoryService.StageDwellStats(0.0, 0));
                    return new Dtos.StageReport(
                            stage.name(),
                            stageOpps.size(),
                            amount,
                            Math.round(stats.avgDaysInStage() * 10) / 10.0,
                            stats.overdueCount());
                })
                .toList();

        var monthlyForecast = opportunities.stream()
                .filter(opp -> opp.getExpectedCloseDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        opp -> opp.getExpectedCloseDate().withDayOfMonth(1),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    // 總額含所有商機；加權預測排除已失單並依成交機率折算
                    var total = entry.getValue().stream()
                            .map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    var weighted = entry.getValue().stream()
                            .filter(opp -> opp.getStage() != OpportunityStage.CLOSED_LOST)
                            .map(opp -> opp.getAmount().multiply(BigDecimal.valueOf(
                                    opp.getProbability() == null ? 0 : opp.getProbability()))
                                    .divide(BigDecimal.valueOf(100)))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new Dtos.ForecastPoint(entry.getKey().toString().substring(0, 7),
                            total, weighted, entry.getValue().size());
                })
                .toList();

        var industryBreakdown = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(Customer::getIndustry, java.util.TreeMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new Dtos.MoneyChartPoint(
                        entry.getKey(),
                        entry.getValue().stream()
                                .flatMap(customer -> customer.getOpportunities().stream())
                                .map(opp -> opp.getAmount())
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().size()))
                .toList();

        var riskBreakdown = summaries.stream()
                .collect(java.util.stream.Collectors.groupingBy(Dtos.CustomerSummaryResponse::riskLevel, java.util.TreeMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new Dtos.ChartPoint(entry.getKey(), entry.getValue()))
                .toList();

        var ownerLeaderboard = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(Customer::getOwnerName, java.util.TreeMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var amount = entry.getValue().stream()
                            .flatMap(customer -> customer.getOpportunities().stream())
                            .map(opp -> opp.getAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    var highRisk = entry.getValue().stream()
                            .map(mapper::toSummary)
                            .filter(summary -> "HIGH".equals(summary.riskLevel()))
                            .count();
                    return new Dtos.OwnerReport(entry.getKey(), entry.getValue().size(), amount, highRisk);
                })
                .sorted((a, b) -> b.opportunityAmount().compareTo(a.opportunityAmount()))
                .toList();

        var renewalForecast = all.stream()
                .filter(customer -> customer.getRenewalDueDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        customer -> customer.getRenewalDueDate().withDayOfMonth(1),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new Dtos.MoneyChartPoint(
                        entry.getKey().toString().substring(0, 7),
                        entry.getValue().stream()
                                .flatMap(customer -> customer.getOpportunities().stream())
                                .map(opp -> opp.getAmount())
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().size()))
                .toList();

        var recentActivities = all.stream()
                .flatMap(customer -> customer.getInteractions().stream()
                        .map(interaction -> new Dtos.ActivityReport(customer.getId(), customer.getName(), interaction.getType(), interaction.getOccurredAt(), interaction.getContent())))
                .sorted((a, b) -> b.occurredAt().compareTo(a.occurredAt()))
                .limit(8)
                .toList();

        return new Dtos.DashboardReports(pipelineByStage, monthlyForecast, industryBreakdown, riskBreakdown, ownerLeaderboard, renewalForecast, recentActivities);
    }

    /**
     * 儀表板圖表的下鑽明細：依圖表類型與鍵值列出底層商機或客戶。
     * 函式級註解：stage/forecastMonth 回傳商機層級明細；industry/owner/risk/renewalMonth 回傳客戶層級明細。
     *
     * @param type 下鑽類型（stage / forecastMonth / renewalMonth / industry / owner / risk）
     * @param key 對應鍵值（階段名、YYYY-MM、產業、業務、風險等級）
     * @return 下鑽明細回應
     */
    @Transactional(readOnly = true)
    public Dtos.DrilldownResponse drilldown(String type, String key) {
        var all = allCustomersWithDetail();
        var items = new java.util.ArrayList<Dtos.DrilldownItem>();
        var total = BigDecimal.ZERO;
        var label = key;
        switch (type) {
            case "stage" -> {
                var stage = OpportunityStage.valueOf(key);
                label = stageLabel(stage);
                for (var c : all) {
                    for (var o : c.getOpportunities()) {
                        if (o.getStage() == stage) {
                            items.add(opportunityItem(c, o));
                            total = total.add(o.getAmount());
                        }
                    }
                }
            }
            case "forecastMonth" -> {
                label = key + " 預計成交";
                for (var c : all) {
                    for (var o : c.getOpportunities()) {
                        if (o.getExpectedCloseDate() != null && o.getExpectedCloseDate().toString().startsWith(key)) {
                            items.add(opportunityItem(c, o));
                            total = total.add(o.getAmount());
                        }
                    }
                }
            }
            case "renewalMonth" -> {
                label = key + " 續約到期";
                for (var c : all) {
                    if (c.getRenewalDueDate() != null && c.getRenewalDueDate().toString().startsWith(key)) {
                        var amount = oppTotal(c);
                        items.add(customerItem(c, amount, c.getRenewalDueDate().toString()));
                        total = total.add(amount);
                    }
                }
            }
            case "industry" -> {
                for (var c : all) {
                    if (key.equals(c.getIndustry())) {
                        var amount = oppTotal(c);
                        items.add(customerItem(c, amount, null));
                        total = total.add(amount);
                    }
                }
            }
            case "owner" -> {
                for (var c : all) {
                    if (key.equals(c.getOwnerName())) {
                        var amount = oppTotal(c);
                        items.add(customerItem(c, amount, null));
                        total = total.add(amount);
                    }
                }
            }
            case "risk" -> {
                for (var c : all) {
                    var summary = mapper.toSummary(c);
                    if (key.equals(summary.riskLevel())) {
                        var amount = summary.opportunityAmount() == null ? BigDecimal.ZERO : summary.opportunityAmount();
                        items.add(customerItem(c, amount, c.getRenewalDueDate() == null ? null : c.getRenewalDueDate().toString()));
                        total = total.add(amount);
                    }
                }
            }
            default -> {
                // 未知類型回傳空清單
            }
        }
        return new Dtos.DrilldownResponse(type, key, label, total, items.size(), items);
    }

    /**
     * 組裝商機層級的下鑽明細項目。
     */
    private Dtos.DrilldownItem opportunityItem(Customer c, Opportunity o) {
        return new Dtos.DrilldownItem(c.getId(), c.getName(), c.getIndustry(), c.getOwnerName(), null,
                o.getName(), o.getStage().name(), o.getAmount(),
                o.getExpectedCloseDate() == null ? "未定" : o.getExpectedCloseDate().toString(),
                stageStatus(o.getStage()));
    }

    /**
     * 組裝客戶層級的下鑽明細項目（含風險等級與商機總額）。
     */
    private Dtos.DrilldownItem customerItem(Customer c, BigDecimal amount, String date) {
        var summary = mapper.toSummary(c);
        return new Dtos.DrilldownItem(c.getId(), c.getName(), c.getIndustry(), c.getOwnerName(), summary.riskLevel(),
                null, null, amount, date, null);
    }

    /**
     * 計算客戶商機總額。
     */
    private BigDecimal oppTotal(Customer c) {
        return c.getOpportunities().stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 商機階段中文標籤。
     */
    private String stageLabel(OpportunityStage stage) {
        return switch (stage) {
            case QUALIFICATION -> "資格評估";
            case PROPOSAL -> "提案";
            case NEGOTIATION -> "議價";
            case CLOSED_WON -> "已成交";
            case CLOSED_LOST -> "已流失";
        };
    }

    /**
     * 商機狀態（進行中 / 已完成 / 已流失）。
     */
    private String stageStatus(OpportunityStage stage) {
        return switch (stage) {
            case CLOSED_WON -> "已完成";
            case CLOSED_LOST -> "已流失";
            default -> "進行中";
        };
    }

    /**
     * 載入所有客戶與詳情關聯，集中供 Dashboard 聚合使用。
     *
     * @return 含關聯資料的客戶清單
     */
    private java.util.List<Customer> allCustomersWithDetail() {
        // findAll 已載入全部客戶；互動/商機等 LAZY 關聯於聚合時存取，由 default_batch_fetch_size 批次載入。
        // （原本逐客戶再 findDetailById 只是重撈同一實體、未 fetch 關聯，純屬冗餘的 N+1。）
        return customers.findAll();
    }
}
