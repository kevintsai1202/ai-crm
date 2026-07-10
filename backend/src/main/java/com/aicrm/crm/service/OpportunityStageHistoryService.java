package com.aicrm.crm.service;

import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityStageHistory;
import com.aicrm.crm.repository.OpportunityStageHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商機階段歷史：寫入轉換紀錄、計算各階段平均停留與超時筆數。
 */
@Service
public class OpportunityStageHistoryService {

    /** 各開放階段超時門檻（天）。 */
    private static final Map<OpportunityStage, Integer> SLA_DAYS = Map.of(
            OpportunityStage.QUALIFICATION, 14,
            OpportunityStage.PROPOSAL, 14,
            OpportunityStage.NEGOTIATION, 21
    );

    private final OpportunityStageHistoryRepository historyRepository;

    public OpportunityStageHistoryService(OpportunityStageHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * 記錄階段變更（from 與 to 相同則略過）。
     *
     * @param opportunity 已有 id 的商機
     * @param fromStage 原階段（新建可 null）
     * @param toStage 新階段
     */
    @Transactional
    public void record(Opportunity opportunity, OpportunityStage fromStage, OpportunityStage toStage) {
        if (opportunity == null || opportunity.getId() == null || toStage == null) {
            return;
        }
        if (fromStage == toStage) {
            return;
        }
        // AuthPrincipal 目前無 userId 欄位，操作者 id 暫留 null（可後續擴充 JWT）
        historyRepository.save(new OpportunityStageHistory(
                opportunity.getId(), fromStage, toStage, Instant.now(), null));
    }

    /**
     * 計算各階段：平均停留天數、超時筆數（僅針對目前仍在該階段的商機）。
     *
     * @param opportunities 目前商機快照（已篩 leadSource 等）
     * @return stage → 指標
     */
    @Transactional(readOnly = true)
    public Map<OpportunityStage, StageDwellStats> computeDwellStats(List<Opportunity> opportunities) {
        Map<OpportunityStage, StageDwellStats> result = new EnumMap<>(OpportunityStage.class);
        for (OpportunityStage s : OpportunityStage.values()) {
            result.put(s, new StageDwellStats(0.0, 0));
        }
        if (opportunities == null || opportunities.isEmpty()) {
            return result;
        }
        var ids = opportunities.stream().map(Opportunity::getId).toList();
        var histories = historyRepository.findByOpportunityIdIn(ids);
        // opportunityId → 該商機最後一次進入各 to_stage 的時間
        Map<Long, Map<OpportunityStage, Instant>> lastEnter = new HashMap<>();
        for (var h : histories) {
            lastEnter
                    .computeIfAbsent(h.getOpportunityId(), k -> new EnumMap<>(OpportunityStage.class))
                    .merge(h.getToStage(), h.getChangedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        Instant now = Instant.now();
        Map<OpportunityStage, long[]> accum = new EnumMap<>(OpportunityStage.class);
        // [0]=天數總和*1000 用 double 加總, [1]=count, [2]=overdue
        for (OpportunityStage s : OpportunityStage.values()) {
            accum.put(s, new long[]{0, 0, 0});
        }
        Map<OpportunityStage, Double> daySum = new EnumMap<>(OpportunityStage.class);
        for (OpportunityStage s : OpportunityStage.values()) {
            daySum.put(s, 0.0);
        }

        for (Opportunity o : opportunities) {
            var stage = o.getStage();
            if (stage == null) {
                continue;
            }
            Instant entered = null;
            var map = lastEnter.get(o.getId());
            if (map != null) {
                entered = map.get(stage);
            }
            if (entered == null && o.getCreatedAt() != null) {
                entered = o.getCreatedAt();
            }
            if (entered == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(entered, now);
            daySum.merge(stage, (double) days, Double::sum);
            long[] a = accum.get(stage);
            a[1]++; // count
            Integer sla = SLA_DAYS.get(stage);
            if (sla != null && days > sla) {
                a[2]++; // overdue
            }
        }

        for (OpportunityStage s : OpportunityStage.values()) {
            long[] a = accum.get(s);
            long n = a[1];
            double avg = n == 0 ? 0.0 : daySum.get(s) / n;
            result.put(s, new StageDwellStats(avg, a[2]));
        }
        return result;
    }

    /**
     * 階段停留指標。
     *
     * @param avgDaysInStage 平均停留天數
     * @param overdueCount 超時筆數
     */
    public record StageDwellStats(double avgDaysInStage, long overdueCount) {}
}
