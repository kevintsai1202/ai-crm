package com.aicrm.crm.api;

import com.aicrm.crm.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客戶 REST API，提供列表、詳情、建立、狀態更新與互動新增。
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    /** 客戶服務。 */
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * 分頁查詢客戶列表。
     *
     * @param page 頁碼
     * @param size 每頁筆數
     * @param keyword 名稱關鍵字
     * @param industry 產業
     * @param owner 負責業務
     * @return 分頁客戶摘要
     */
    @GetMapping
    public Dtos.PageResponse<Dtos.CustomerSummaryResponse> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) com.aicrm.crm.domain.CustomerStatus status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate renewalFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate renewalTo
    ) {
        return customerService.search(page, size, keyword, industry, owner, status, riskLevel, renewalFrom, renewalTo);
    }

    /**
     * 取得新增客戶表單的下拉選項（現有產業與負責業務清單）。
     *
     * @return 產業與業務清單
     */
    @GetMapping("/options")
    public Dtos.CustomerOptionsResponse options() {
        return customerService.getFormOptions();
    }

    /**
     * 查詢單一客戶詳情。
     *
     * @param id 客戶 ID
     * @return 客戶詳情
     */
    @GetMapping("/{id}")
    public Dtos.CustomerDetailResponse get(@PathVariable Long id) {
        return customerService.getDetail(id);
    }

    /**
     * 建立新客戶。
     *
     * @param request 建立請求
     * @return 建立後客戶摘要
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.CustomerSummaryResponse create(@Valid @RequestBody Dtos.CreateCustomerRequest request) {
        return customerService.create(request);
    }

    /**
     * 完整編輯客戶。
     *
     * @param id 客戶 ID
     * @param request 完整編輯請求
     * @return 更新後客戶摘要
     */
    @PutMapping("/{id}")
    public Dtos.CustomerSummaryResponse update(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    /**
     * 刪除客戶（連同其聯絡人、互動、商機）。
     *
     * @param id 客戶 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }

    /**
     * 新增客戶聯絡人。
     *
     * @param id 客戶 ID
     * @param request 新增聯絡人請求
     * @return 新增後聯絡人
     */
    @PostMapping("/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ContactResponse addContact(@PathVariable Long id, @Valid @RequestBody Dtos.CreateContactRequest request) {
        return customerService.addContact(id, request);
    }

    /**
     * 更新客戶狀態。
     *
     * @param id 客戶 ID
     * @param request 狀態請求
     * @return 更新後客戶摘要
     */
    @PutMapping("/{id}/status")
    public Dtos.CustomerSummaryResponse updateStatus(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateStatusRequest request) {
        return customerService.updateStatus(id, request);
    }

    /**
     * 新增客戶互動紀錄。
     *
     * @param id 客戶 ID
     * @param request 互動紀錄請求
     * @return 新增後互動紀錄
     */
    @PostMapping("/{id}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.InteractionResponse addInteraction(@PathVariable Long id, @Valid @RequestBody Dtos.CreateInteractionRequest request) {
        return customerService.addInteraction(id, request);
    }
}

