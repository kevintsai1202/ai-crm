package com.aicrm.crm.api;

import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.repository.AppUserRepository;
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

    /** 帳號資料存取介面：指派負責業務時使用。 */
    private final AppUserRepository users;

    public OpportunityController(OpportunityRepository opportunityRepository,
                                 CustomerRepository customerRepository,
                                 AppUserRepository users) {
        this.opportunityRepository = opportunityRepository;
        this.customerRepository = customerRepository;
        this.users = users;
    }

    /**
     * 階段預設成交機率（與 V18 回填一致）。
     *
     * @param stage 商機階段
     * @return 預設機率（0–100）
     */
    private int defaultProbability(OpportunityStage stage) {
        return switch (stage) {
            case QUALIFICATION -> 20;
            case PROPOSAL -> 50;
            case NEGOTIATION -> 75;
            case CLOSED_WON -> 100;
            case CLOSED_LOST -> 0;
        };
    }

    /**
     * 統一組裝 OpportunityResponse（13 欄）。
     *
     * @param o 商機 entity
     * @return 商機回應 DTO
     */
    private Dtos.OpportunityResponse toResponse(com.aicrm.crm.domain.Opportunity o) {
        return new Dtos.OpportunityResponse(o.getId(), o.getName(), o.getStage(), o.getAmount(),
                o.getExpectedCloseDate(), o.getType(),
                o.getOwner() == null ? null : o.getOwner().getId(), o.getOwnerName(),
                o.getLeadSource(), o.getProbability(), o.getCloseReason(),
                o.getCloseReasonNote(), o.getActualCloseDate());
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
        var leadSource = request.leadSource() == null ? LeadSource.OUTBOUND : request.leadSource();
        var probability = request.probability() == null ? defaultProbability(request.stage()) : request.probability();
        var opportunity = new com.aicrm.crm.domain.Opportunity(customer, request.name(), request.stage(),
                request.amount(), request.expectedCloseDate(), request.type(), leadSource, probability);
        // 指派負責業務：優先採請求中指定的 ownerId，否則沿用客戶負責人
        var owner = request.ownerId() != null
                ? users.findById(request.ownerId()).orElse(null)
                : customer.getOwner();
        opportunity.assignOwner(owner);
        opportunityRepository.save(opportunity);
        return toResponse(opportunity);
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
        // 更新銷售欄位：若請求未帶則維持原值
        opportunity.applySalesFields(
                request.leadSource() == null ? opportunity.getLeadSource() : request.leadSource(),
                request.probability() == null ? opportunity.getProbability() : request.probability());
        if (request.ownerId() != null) {
            opportunity.assignOwner(users.findById(request.ownerId()).orElse(null));
        }
        opportunityRepository.save(opportunity);
        return toResponse(opportunity);
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
     * 更新商機階段（Kanban 拖拽切換）；CLOSED_WON / CLOSED_LOST 走結案流程。
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
        // 結案階段走 closeWith，一般階段走 updateStage
        if (request.stage() == OpportunityStage.CLOSED_WON || request.stage() == OpportunityStage.CLOSED_LOST) {
            var closeDate = request.actualCloseDate() == null ? java.time.LocalDate.now() : request.actualCloseDate();
            opportunity.closeWith(request.stage(), request.closeReason(), request.closeReasonNote(), closeDate);
        } else {
            opportunity.updateStage(request.stage());
        }
        opportunityRepository.save(opportunity);
        return toResponse(opportunity);
    }
}
