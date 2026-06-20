package com.aicrm.crm.api;

import com.aicrm.crm.service.AgentFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Trace API，展示 Embabel GOAP 流程的教學版決策軌跡。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    /** Agent flow 服務。 */
    private final AgentFlowService agentFlowService;

    public AgentController(AgentFlowService agentFlowService) {
        this.agentFlowService = agentFlowService;
    }

    /**
     * 取得指定客戶的 Agent Trace。
     *
     * @param id 客戶 ID
     * @return Trace DTO
     */
    @GetMapping("/customers/{id}/trace")
    public Dtos.AgentTraceResponse trace(@PathVariable Long id) {
        return agentFlowService.trace(id);
    }
}

