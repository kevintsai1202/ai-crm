package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 教學版 Embabel Agent Flow，模擬 GOAP action 與 Blackboard 路徑。
 */
@Service
@Transactional(readOnly = true)
public class AgentFlowService {

    /** 客戶查詢服務。 */
    private final CustomerService customers;

    /** 風險與 RAG 洞察服務。 */
    private final InsightService insights;

    public AgentFlowService(CustomerService customers, InsightService insights) {
        this.customers = customers;
        this.insights = insights;
    }

    /**
     * 產生指定客戶的 Agent Trace。
     *
     * @param customerId 客戶 ID
     * @return Agent Trace DTO
     */
    public Dtos.AgentTraceResponse trace(Long customerId) {
        var customer = customers.findDetail(customerId);
        var risk = insights.calculateOpportunityRisk(customer);
        var steps = new java.util.ArrayList<Dtos.AgentStepResponse>();
        steps.add(step(1, "fetchSnapshot", "SUCCESS", "customerId=" + customerId, customer.getName()));
        if (customer.getInteractions().isEmpty() || customer.getOpportunities().isEmpty()) {
            steps.add(step(2, "summarizeHistory", "NEEDS_MORE_DATA", "interactions/opportunities", "資料不足，Blackboard 標記 needsMoreData=true"));
            return new Dtos.AgentTraceResponse(customerId, "NEEDS_MORE_DATA", "請業務補齊近期互動與商機資料後再產生建議。", steps);
        }
        steps.add(step(2, "summarizeHistory", "SUCCESS", "interactions=" + customer.getInteractions().size(), "完成互動摘要"));
        steps.add(step(3, "assessRisk", "SUCCESS", "risk signals", "流失=" + risk.churnRisk() + ", 續約延遲=" + risk.renewalDelayRisk()));
        if (risk.churnRisk() >= 75 || risk.renewalDelayRisk() >= 75) {
            steps.add(step(4, "routeToManager", "REVIEW_REQUIRED", "riskScore", "風險超過門檻，轉交 MANAGER 審閱"));
            return new Dtos.AgentTraceResponse(customerId, "MANAGER_REVIEW", "此客戶風險過高，建議由銷售經理介入安排關懷會議。", steps);
        }
        steps.add(step(4, "retrieveKnowledge", "SUCCESS", "playbook", "匹配產品、服務條款與續約話術"));
        steps.add(step(5, "draftProposal", "SUCCESS", "snapshot+risk+knowledge", "完成下一步業務建議草稿"));
        steps.add(step(6, "verifySafety", "APPROVED", "draft", "未發現未證實價格或洩密風險"));
        return new Dtos.AgentTraceResponse(customerId, "APPROVED", "建議安排合約條款會議，並引用企業服務條款說明支援等級。", steps);
    }

    /**
     * 建立 Trace 步驟物件。
     *
     * @param order 執行順序
     * @param action action 名稱
     * @param status 執行狀態
     * @param input 輸入摘要
     * @param output 輸出摘要
     * @return Trace 步驟 DTO
     */
    private Dtos.AgentStepResponse step(int order, String action, String status, String input, String output) {
        return new Dtos.AgentStepResponse(order, action, status, 18L + order * 11L, input, output);
    }
}

