package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.domain.Contact;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.ContactRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import com.aicrm.crm.repository.InteractionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * CRM 客戶業務邏輯，集中處理查詢、建立、狀態更新與互動新增。
 */
@Service
@Transactional
public class CustomerService {

    /** 記錄情緒意圖分析失敗等事件。 */
    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    /** 客戶資料存取介面。 */
    private final CustomerRepository customers;

    /** 情緒意圖分類服務：新增互動後觸發分析落庫。 */
    private final SentimentIntentService sentimentIntentService;

    /** 互動情緒意圖分析結果存取：詳情組裝時帶出每則互動的情緒 / 意圖。 */
    private final InteractionInsightRepository interactionInsights;

    /** 互動紀錄存取：新增互動時直接持久化以可靠取得 id（不依賴 cascade merge）。 */
    private final InteractionRepository interactionRepository;

    /** 使用者存取：建立客戶解析負責業務帳號、提供負責業務下拉選項。 */
    private final AppUserRepository users;

    /** 聯絡人存取：新增客戶聯絡人時持久化。 */
    private final ContactRepository contacts;

    /** Entity/DTO 轉換工具。 */
    private final CustomerMapper mapper = new CustomerMapper();

    public CustomerService(CustomerRepository customers,
                           SentimentIntentService sentimentIntentService,
                           InteractionInsightRepository interactionInsights,
                           InteractionRepository interactionRepository,
                           AppUserRepository users,
                           ContactRepository contacts) {
        this.customers = customers;
        this.sentimentIntentService = sentimentIntentService;
        this.interactionInsights = interactionInsights;
        this.interactionRepository = interactionRepository;
        this.users = users;
        this.contacts = contacts;
    }

    /**
     * 分頁查詢客戶，支援關鍵字、產業與負責業務篩選。
     *
     * @param page 頁碼
     * @param size 每頁筆數
     * @param keyword 客戶名稱關鍵字
     * @param industry 產業
     * @param owner 負責業務
     * @return 分頁客戶摘要
     */
    @Transactional(readOnly = true)
    public Dtos.PageResponse<Dtos.CustomerSummaryResponse> search(int page, int size, String keyword, String industry, String owner) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("id").ascending());
        var result = customers.findAll(buildSpec(keyword, industry, owner), pageable);
        var items = result.getContent().stream()
                .map(customer -> customers.findDetailById(customer.getId()).orElse(customer))
                .map(mapper::toSummary)
                .toList();
        return new Dtos.PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 取得新增客戶表單所需的下拉選項：現有的不重複產業，與可指派的負責業務（SALES 帳號）。
     *
     * @return 產業清單與 SALES 帳號清單
     */
    @Transactional(readOnly = true)
    public Dtos.CustomerOptionsResponse getFormOptions() {
        var owners = users.findByRole(Role.SALES).stream()
                .filter(com.aicrm.crm.domain.AppUser::isEnabled) // 僅可登入（啟用）的業務可被指派
                .sorted((a, b) -> a.getDisplayName().compareTo(b.getDisplayName()))
                .map(u -> new Dtos.OwnerOption(u.getId(), u.getDisplayName()))
                .toList();
        return new Dtos.CustomerOptionsResponse(customers.findDistinctIndustries(), owners);
    }

    /**
     * 查詢客戶詳情。
     *
     * @param id 客戶 ID
     * @return 客戶詳情 DTO
     */
    @Transactional(readOnly = true)
    public Dtos.CustomerDetailResponse getDetail(Long id) {
        // 一次撈該客戶所有互動分析結果，以 interactionId 為鍵供 mapper 帶入各互動的情緒 / 意圖。
        Map<Long, InteractionInsight> insightByInteractionId = interactionInsights.findByCustomerId(id).stream()
                .collect(Collectors.toMap(InteractionInsight::getInteractionId, Function.identity(), (a, b) -> a));
        return mapper.toDetail(findDetail(id), insightByInteractionId);
    }

    /**
     * 建立新客戶。
     *
     * @param request 建立客戶請求
     * @return 建立後的客戶摘要
     */
    public Dtos.CustomerSummaryResponse create(Dtos.CreateCustomerRequest request) {
        // 解析負責業務帳號（正規關聯），找不到則回 404
        var owner = users.findById(request.ownerId())
                .orElseThrow(() -> new EntityNotFoundException("負責業務帳號不存在：" + request.ownerId()));
        var customer = new Customer(request.name(), request.email(), request.phone(), request.taxId(), request.industry(), owner.getDisplayName());
        customer.assignOwner(owner); // 同步設定 owner_id 與 owner_name
        customer.updateContractDates(request.contractStartDate(), request.contractEndDate(), request.renewalDueDate());
        return mapper.toSummary(customers.save(customer));
    }

    /**
     * 完整編輯客戶（基本欄位、負責業務與合約日期）。
     *
     * @param id 客戶 ID
     * @param request 完整編輯請求
     * @return 更新後客戶摘要
     */
    public Dtos.CustomerSummaryResponse update(Long id, Dtos.UpdateCustomerRequest request) {
        var customer = findDetail(id);
        // 解析負責業務帳號（正規關聯），找不到則回 404
        var owner = users.findById(request.ownerId())
                .orElseThrow(() -> new EntityNotFoundException("負責業務帳號不存在：" + request.ownerId()));
        customer.updateBasicInfo(request.name(), request.email(), request.phone(), request.taxId(), request.industry());
        customer.assignOwner(owner); // 同步設定 owner_id 與 owner_name
        customer.updateContractDates(request.contractStartDate(), request.contractEndDate(), request.renewalDueDate());
        return mapper.toSummary(customers.save(customer));
    }

    /**
     * 刪除客戶（連同其子實體：聯絡人、互動、商機）。
     *
     * <p>因 interaction_insights.interaction_id 外鍵無 ON DELETE CASCADE，須先刪掉該客戶所有分析列，
     * 再刪客戶（子實體靠 JPA cascade 一併刪除）。</p>
     *
     * @param id 客戶 ID
     */
    public void delete(Long id) {
        interactionInsights.deleteByCustomerId(id);
        customers.deleteById(id);
    }

    /**
     * 更新客戶狀態。
     *
     * @param id 客戶 ID
     * @param request 狀態更新請求
     * @return 更新後客戶摘要
     */
    public Dtos.CustomerSummaryResponse updateStatus(Long id, Dtos.UpdateStatusRequest request) {
        var customer = findDetail(id);
        customer.updateStatus(request.status());
        return mapper.toSummary(customer);
    }

    /**
     * 新增指定客戶的聯絡人。
     *
     * @param id 客戶 ID
     * @param request 新增聯絡人請求
     * @return 新增後的聯絡人 DTO
     */
    public Dtos.ContactResponse addContact(Long id, Dtos.CreateContactRequest request) {
        var customer = findDetail(id);
        var contact = new Contact(customer, request.name(), request.title(), request.email());
        contacts.save(contact);
        return new Dtos.ContactResponse(contact.getId(), contact.getName(), contact.getTitle(), contact.getEmail());
    }

    /**
     * 新增指定客戶的互動紀錄。
     *
     * @param id 客戶 ID
     * @param request 新增互動請求
     * @return 新增後的互動 DTO
     */
    public Dtos.InteractionResponse addInteraction(Long id, Dtos.CreateInteractionRequest request) {
        var customer = findDetail(id);
        var interaction = new Interaction(request.type(), request.occurredAt(), request.content());
        customer.addInteraction(interaction);
        // 直接持久化互動以可靠取得 id（cascade + merge 不保證回填我們持有的 interaction 參照的 id）
        interactionRepository.saveAndFlush(interaction);
        // 觸發情緒意圖分析（允許 LLM，無金鑰時 SentimentIntentService 內部自動 fallback）。
        // 單筆分析失敗不可讓新增互動整個失敗：僅記錄日誌。
        try {
            sentimentIntentService.analyzeAndSave(interaction.getId(), id, interaction.getContent(), true);
        } catch (Exception e) {
            log.warn("新增互動的情緒意圖分析失敗，interactionId={}：{}", interaction.getId(), e.getMessage());
        }
        // 取回剛分析的情緒 / 意圖（單筆分析失敗時為 null）。
        var insight = interactionInsights.findByInteractionId(interaction.getId()).orElse(null);
        var sentiment = insight == null ? null : insight.getSentiment().name();
        var intent = insight == null ? null : insight.getIntent().name();
        return new Dtos.InteractionResponse(interaction.getId(), interaction.getType(), interaction.getOccurredAt(), interaction.getContent(), sentiment, intent);
    }

    /**
     * 供 AI Portfolio 評估取得所有客戶（含互動、商機、聯絡人關聯）。
     *
     * @return 含關聯詳情的客戶清單
     */
    @Transactional(readOnly = true)
    public java.util.List<Customer> findAllWithDetail() {
        return allCustomersWithDetail();
    }

    /**
     * 供 AI service 取得完整客戶 Entity。
     *
     * @param id 客戶 ID
     * @return 客戶 Entity
     */
    @Transactional(readOnly = true)
    public Customer findDetail(Long id) {
        return customers.findDetailById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此客戶資料：" + id));
    }

    /**
     * 建立動態查詢條件。
     *
     * @param keyword 客戶名稱關鍵字
     * @param industry 產業
     * @param owner 負責業務
     * @return JPA Specification
     */
    private Specification<Customer> buildSpec(String keyword, String industry, String owner) {
        return (root, query, cb) -> {
            query.distinct(true);
            var predicate = cb.conjunction();
            if (StringUtils.hasText(keyword)) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(industry)) {
                predicate = cb.and(predicate, cb.equal(root.get("industry"), industry));
            }
            if (StringUtils.hasText(owner)) {
                predicate = cb.and(predicate, cb.equal(root.get("ownerName"), owner));
            }
            return predicate;
        };
    }

    /**
     * 載入所有客戶與詳情關聯，集中供 Dashboard 聚合使用。
     *
     * @return 含關聯資料的客戶清單
     */
    private java.util.List<Customer> allCustomersWithDetail() {
        return customers.findAll().stream()
                .map(customer -> customers.findDetailById(customer.getId()).orElse(customer))
                .toList();
    }
}
