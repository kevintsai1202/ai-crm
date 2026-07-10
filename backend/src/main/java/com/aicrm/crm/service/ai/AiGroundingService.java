package com.aicrm.crm.service.ai;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.service.PiiMasker;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * AI Grounding 組裝：客戶資料 + 風險 + RAG 引用 + 對話記憶（送 LLM 前 PII 遮罩）。
 * 自 InsightService 抽出（SP12-F），行為與原文一致。
 */
@Service
public class AiGroundingService {

    /**
     * 組裝餵給 LLM 的 grounding context。
     *
     * @param customer 客戶
     * @param risk 風險
     * @param citations RAG 引用
     * @param memory 對話記憶（可空）
     * @return Markdown context
     */
    public String buildGroundingContext(Customer customer, Dtos.RiskResponse risk,
                                        List<Dtos.CitationResponse> citations, String memory) {
        var knowledge = citations.stream()
                .map(c -> "- [" + c.docType() + "] " + c.title() + "：" + c.content())
                .collect(Collectors.joining("\n"));
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
                PiiMasker.mask(customerContext(customer)),
                risk.churnRisk(),
                risk.renewalDelayRisk(),
                risk.reasons().isEmpty() ? "無顯著風險訊號" : String.join("、", risk.reasons()),
                knowledge.isBlank() ? "（無可用引用）" : knowledge);
    }

    /**
     * 組裝單一客戶完整資料區塊（未遮罩；由 buildGroundingContext 統一遮罩）。
     *
     * @param customer 客戶
     * @return Markdown
     */
    public String customerContext(Customer customer) {
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
}
