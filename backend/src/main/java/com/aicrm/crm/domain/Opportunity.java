package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客戶商機實體，支撐漏斗、Kanban 與續約風險情境。
 */
@Entity
@Table(name = "opportunities")
public class Opportunity extends AuditableEntity {

    /** 商機主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬客戶。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 商機名稱。 */
    @Column(nullable = false)
    private String name;

    /** 商機階段。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpportunityStage stage;

    /** 商機金額。 */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** 預計成交日。 */
    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    /** 商機類型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpportunityType type;

    /** 負責業務帳號（正規關聯）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 負責業務顯示名稱（去正規化快取，與 owner.displayName 同步）。 */
    @Column(name = "owner_name")
    private String ownerName;

    /** 商機來源。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "lead_source", nullable = false)
    private LeadSource leadSource = LeadSource.OUTBOUND;

    /** 成交機率（0–100），加權預測用。 */
    @Column
    private Integer probability;

    /** 結案（輸/贏）原因；僅 CLOSED_* 時填。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private CloseReason closeReason;

    /** 結案補充說明。 */
    @Column(name = "close_reason_note")
    private String closeReasonNote;

    /** 實際成交/結案日。 */
    @Column(name = "actual_close_date")
    private LocalDate actualCloseDate;

    protected Opportunity() {
    }

    /**
     * 建立商機（向後相容六參數版，委派至完整建構子）。
     *
     * @param customer 所屬客戶
     * @param name 商機名稱
     * @param stage 商機階段
     * @param amount 商機金額
     * @param expectedCloseDate 預計成交日
     * @param type 商機類型
     */
    public Opportunity(Customer customer, String name, OpportunityStage stage, BigDecimal amount,
                       LocalDate expectedCloseDate, OpportunityType type) {
        this(customer, name, stage, amount, expectedCloseDate, type, LeadSource.OUTBOUND, null);
    }

    /**
     * 完整建構子（SP8）：含來源與成交機率。
     *
     * @param customer 所屬客戶
     * @param name 商機名稱
     * @param stage 商機階段
     * @param amount 商機金額
     * @param expectedCloseDate 預計成交日
     * @param type 商機類型
     * @param leadSource 商機來源
     * @param probability 成交機率（0–100）
     */
    public Opportunity(Customer customer, String name, OpportunityStage stage, BigDecimal amount,
                       LocalDate expectedCloseDate, OpportunityType type,
                       LeadSource leadSource, Integer probability) {
        this.customer = customer;
        this.name = name;
        this.stage = stage;
        this.amount = amount;
        this.expectedCloseDate = expectedCloseDate;
        this.type = type;
        this.leadSource = leadSource;
        this.probability = probability;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public OpportunityStage getStage() { return stage; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public OpportunityType getType() { return type; }
    public Customer getCustomer() { return customer; }
    public AppUser getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public LeadSource getLeadSource() { return leadSource; }
    public Integer getProbability() { return probability; }
    public CloseReason getCloseReason() { return closeReason; }
    public String getCloseReasonNote() { return closeReasonNote; }
    public LocalDate getActualCloseDate() { return actualCloseDate; }

    /**
     * 指派負責業務並同步去正規化的 ownerName。
     *
     * @param owner 負責業務帳號
     */
    public void assignOwner(AppUser owner) {
        this.owner = owner;
        this.ownerName = owner == null ? null : owner.getDisplayName();
    }

    /**
     * 設定來源與機率（新增/編輯共用）。
     *
     * @param leadSource 商機來源
     * @param probability 成交機率（0–100）
     */
    public void applySalesFields(LeadSource leadSource, Integer probability) {
        this.leadSource = leadSource;
        this.probability = probability;
    }

    /**
     * 結案：設定階段 + 輸贏原因 + 實際成交日。
     *
     * @param stage 結案階段（CLOSED_WON 或 CLOSED_LOST）
     * @param closeReason 結案原因
     * @param note 結案補充說明
     * @param actualCloseDate 實際成交/結案日
     */
    public void closeWith(OpportunityStage stage, CloseReason closeReason, String note, LocalDate actualCloseDate) {
        this.stage = stage;
        this.closeReason = closeReason;
        this.closeReasonNote = note;
        this.actualCloseDate = actualCloseDate;
    }

    /**
     * 更新商機階段（供 Kanban 拖拽使用）。
     *
     * @param stage 新的商機階段
     */
    public void updateStage(OpportunityStage stage) {
        this.stage = stage;
    }

    /**
     * 更新商機明細（編輯用，不含階段）。
     *
     * @param name 商機名稱
     * @param amount 商機金額
     * @param expectedCloseDate 預計成交日
     * @param type 商機類型
     */
    public void updateDetails(String name, BigDecimal amount, LocalDate expectedCloseDate, OpportunityType type) {
        this.name = name;
        this.amount = amount;
        this.expectedCloseDate = expectedCloseDate;
        this.type = type;
    }
}

