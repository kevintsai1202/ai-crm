package com.aicrm.crm.api;

import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商機 REST API，提供新增與階段更新（供 Kanban 拖拽使用）。
 */
@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {

    /** 商機資料存取介面。 */
    private final OpportunityRepository opportunityRepository;

    /** 客戶資料存取介面：新增商機時用來解析所屬客戶。 */
    private final CustomerRepository customerRepository;

    public OpportunityController(OpportunityRepository opportunityRepository, CustomerRepository customerRepository) {
        this.opportunityRepository = opportunityRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * 新增商機（掛在指定客戶下）。
     *
     * @param request 新增商機請求（含 customerId）
     * @return 新增後的商機 DTO
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Dtos.OpportunityResponse create(@Valid @RequestBody Dtos.CreateOpportunityRequest request) {
        // 解析所屬客戶，查無則回 404 語意
        var customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new EntityNotFoundException("查無此客戶：" + request.customerId()));
        var opportunity = new Opportunity(customer, request.name(), request.stage(), request.amount(),
                request.expectedCloseDate(), request.type());
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

    /**
     * 編輯商機明細（name/amount/expectedCloseDate/type；不含階段）。
     *
     * @param id 商機 ID
     * @param request 商機編輯請求
     * @return 更新後的商機 DTO
     */
    @PutMapping("/{id}")
    @Transactional
    public Dtos.OpportunityResponse update(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateOpportunityRequest request) {
        var opportunity = opportunityRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此商機：" + id));
        opportunity.updateDetails(request.name(), request.amount(), request.expectedCloseDate(), request.type());
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

    /**
     * 刪除商機。
     *
     * @param id 商機 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        opportunityRepository.deleteById(id);
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
