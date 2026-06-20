package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Opportunity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 負責 Entity 與 DTO 轉換，避免 Controller 直接曝露資料庫模型。
 */
public class CustomerMapper {

    /**
     * 將客戶轉為列表摘要 DTO。
     *
     * @param customer 客戶實體
     * @return 客戶摘要 DTO
     */
    public Dtos.CustomerSummaryResponse toSummary(Customer customer) {
        var lastInteractionAt = customer.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        var amount = customer.getOpportunities().stream()
                .map(Opportunity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dtos.CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getTaxId(),
                customer.getIndustry(),
                customer.getOwnerName(),
                customer.getStatus(),
                calculateRiskLevel(customer, lastInteractionAt),
                customer.getRenewalDueDate(),
                lastInteractionAt,
                amount
        );
    }

    /**
     * 將客戶完整資料轉為詳情 DTO，並為每則互動帶入情緒 / 意圖（無分析結果則為 null）。
     *
     * @param customer 客戶實體
     * @param insightByInteractionId 以互動 ID 為鍵的情緒意圖分析結果
     * @return 客戶詳情 DTO
     */
    public Dtos.CustomerDetailResponse toDetail(Customer customer, Map<Long, InteractionInsight> insightByInteractionId) {
        return new Dtos.CustomerDetailResponse(
                toSummary(customer),
                customer.getContacts().stream()
                        .map(c -> new Dtos.ContactResponse(c.getId(), c.getName(), c.getTitle(), c.getEmail()))
                        .toList(),
                customer.getInteractions().stream()
                        .sorted((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()))
                        .map(i -> {
                            // 帶入該互動的情緒 / 意圖；無分析結果則維持 null。
                            var insight = insightByInteractionId.get(i.getId());
                            var sentiment = insight == null ? null : insight.getSentiment().name();
                            var intent = insight == null ? null : insight.getIntent().name();
                            return new Dtos.InteractionResponse(i.getId(), i.getType(), i.getOccurredAt(), i.getContent(), sentiment, intent);
                        })
                        .toList(),
                customer.getOpportunities().stream()
                        .map(o -> new Dtos.OpportunityResponse(o.getId(), o.getName(), o.getStage(), o.getAmount(), o.getExpectedCloseDate(), o.getType()))
                        .toList()
        );
    }

    /**
     * 依近期互動與續約日期計算前端顯示用風險等級。
     *
     * @param customer 客戶實體
     * @param lastInteractionAt 最近互動時間
     * @return HIGH / MEDIUM / LOW
     */
    private String calculateRiskLevel(Customer customer, LocalDateTime lastInteractionAt) {
        if (lastInteractionAt == null) {
            return "MEDIUM";
        }
        var days = ChronoUnit.DAYS.between(lastInteractionAt.toLocalDate(), LocalDate.now());
        var renewalDueDate = customer.getRenewalDueDate();
        if (days > 60 || (renewalDueDate != null && renewalDueDate.isBefore(LocalDate.now()))) {
            return "HIGH";
        }
        if (days > 30) {
            return "MEDIUM";
        }
        return "LOW";
    }
}

