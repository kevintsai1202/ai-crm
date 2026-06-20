package com.aicrm.crm.api;

import com.aicrm.crm.repository.OpportunityRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商機 REST API，提供階段更新（供 Kanban 拖拽使用）。
 */
@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {

    /** 商機資料存取介面。 */
    private final OpportunityRepository opportunityRepository;

    public OpportunityController(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = opportunityRepository;
    }

    /**
     * 更新商機階段（Kanban 拖拽切換）。
     *
     * @param id 商機 ID
     * @param request 階段更新請求
     * @return 更新後的商機 DTO
     */
    @PutMapping("/{id}/stage")
    @Transactional
    public Dtos.OpportunityResponse updateStage(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateStageRequest request) {
        var opportunity = opportunityRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此商機：" + id));
        opportunity.updateStage(request.stage());
        opportunityRepository.save(opportunity);
        return new Dtos.OpportunityResponse(
                opportunity.getId(),
                opportunity.getName(),
                opportunity.getStage(),
                opportunity.getAmount(),
                opportunity.getExpectedCloseDate(),
                opportunity.getType()
        );
    }
}
