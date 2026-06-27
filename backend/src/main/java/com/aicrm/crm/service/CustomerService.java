package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.CustomerStatus;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.domain.Contact;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.time.LocalDate;
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

    /** 擁有權守衛：單一客戶存取時強制 SALES 僅能操作自己負責的客戶。 */
    private final com.aicrm.crm.security.OwnershipGuard ownershipGuard;

    /** Entity/DTO 轉換工具。 */
    private final CustomerMapper mapper = new CustomerMapper();

    public CustomerService(CustomerRepository customers,
                           SentimentIntentService sentimentIntentService,
                           InteractionInsightRepository interactionInsights,
                           InteractionRepository interactionRepository,
                           AppUserRepository users,
                           ContactRepository contacts,
                           com.aicrm.crm.security.OwnershipGuard ownershipGuard) {
        this.customers = customers;
        this.sentimentIntentService = sentimentIntentService;
        this.interactionInsights = interactionInsights;
        this.interactionRepository = interactionRepository;
        this.users = users;
        this.contacts = contacts;
        this.ownershipGuard = ownershipGuard;
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
    public Dtos.PageResponse<Dtos.CustomerSummaryResponse> search(AuthPrincipal principal, int page, int size,
                                                                  String keyword, String industry, String owner,
                                                                  CustomerStatus status, String riskLevel,
                                                                  LocalDate renewalFrom, LocalDate renewalTo) {
        int pageIdx = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 50);
        // risk_level 已為 DB 欄位(V13)，所有條件（含 riskLevel）皆可走 DB 層分頁，不需記憶體全撈。
        var spec = buildSpec(principal, keyword, industry, owner, status, riskLevel, renewalFrom, renewalTo);
        var pageable = PageRequest.of(pageIdx, pageSize, Sort.by("id").ascending());
        var result = customers.findAll(spec, pageable);
        var items = result.getContent().stream().map(mapper::toSummary).toList();
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
        var customer = customers.findDetailById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此客戶資料：" + id));
        // 單一客戶存取的集中鎖點：客戶 CRUD（getDetail/update/updateStatus/addContact/addInteraction）
        // 與 AI chat/assessment 皆經此載入，於此強制 SALES 僅能存取自己負責的客戶（防 IDOR 水平越權）。
        ownershipGuard.assertCanAccessOwner(customer.getOwnerName());
        return customer;
    }

    /**
     * 建立動態查詢條件。
     *
     * @param keyword 客戶名稱關鍵字
     * @param industry 產業
     * @param owner 負責業務
     * @return JPA Specification
     */
    private Specification<Customer> buildSpec(AuthPrincipal principal, String keyword, String industry, String owner,
                                              CustomerStatus status, String riskLevel,
                                              LocalDate renewalFrom, LocalDate renewalTo) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            // 以 AND 動態累加各條件；欄位有值才加入，組出彈性查詢
            var predicate = cb.conjunction();
            if (StringUtils.hasText(keyword)) {
                // 關鍵字擴大：名稱 / Email / 電話 / 統編 任一符合(OR LIKE)
                var kw = "%" + keyword.toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), kw),
                        cb.like(cb.lower(root.get("email")), kw),
                        cb.like(cb.lower(root.get("phone")), kw),
                        cb.like(cb.lower(root.get("taxId")), kw)
                ));
            }
            if (StringUtils.hasText(industry)) {
                predicate = cb.and(predicate, cb.equal(root.get("industry"), industry));
            }
            // SALES 角色：強制只顯示自己負責的客戶，忽略傳入的 owner 篩選參數
            if (principal != null && principal.role() == Role.SALES) {
                predicate = cb.and(predicate, cb.equal(root.get("ownerName"), principal.displayName()));
            } else if (StringUtils.hasText(owner)) {
                predicate = cb.and(predicate, cb.equal(root.get("ownerName"), owner));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            // risk_level 為 DB 欄位(V13 落地)，直接加 WHERE 條件走索引
            if (StringUtils.hasText(riskLevel)) {
                predicate = cb.and(predicate, cb.equal(cb.upper(root.get("riskLevel")), riskLevel.toUpperCase()));
            }
            // 續約到期日區間(between；單邊有值也支援)
            if (renewalFrom != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.<LocalDate>get("renewalDueDate"), renewalFrom));
            }
            if (renewalTo != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.<LocalDate>get("renewalDueDate"), renewalTo));
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
        // findAll 已載入全部客戶；互動/商機等 LAZY 關聯於聚合時存取，由 default_batch_fetch_size 批次載入。
        // （原本逐客戶再 findDetailById 只是重撈同一實體、未 fetch 關聯，純屬冗餘的 N+1。）
        return customers.findAll();
    }
}
