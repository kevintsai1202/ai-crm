package com.aicrm.crm.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CRM 客戶實體，保存合約、續約與 AI 風險評估所需欄位。
 */
@Entity
@Table(name = "customers")
public class Customer extends AuditableEntity {

    /** 客戶主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客戶名稱。 */
    @Column(nullable = false)
    private String name;

    /** 客戶主要 email。 */
    @Column(nullable = false)
    private String email;

    /** 客戶聯絡電話。 */
    @Column(nullable = false)
    private String phone;

    /** 統一編號。 */
    @Column(name = "tax_id", nullable = false, length = 8)
    private String taxId;

    /** 產業別，用於前端篩選。 */
    @Column(nullable = false)
    private String industry;

    /** 負責業務顯示名稱（去正規化快取，恆等於 owner.displayName；供既有字串彙總報表使用）。 */
    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    /** 負責業務帳號（正規關聯）。可為 null 以相容回填前的舊資料；應用層寫入時一律設定。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 客戶狀態。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    /** 合約起始日。 */
    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    /** 合約到期日。 */
    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    /** 預計續約日。 */
    @Column(name = "renewal_due_date")
    private LocalDate renewalDueDate;

    /** 客戶聯絡人清單，預設延遲載入。 */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Contact> contacts = new ArrayList<>();

    /** 客戶互動紀錄清單，預設延遲載入。 */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Interaction> interactions = new ArrayList<>();

    /** 客戶商機清單，預設延遲載入。 */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Opportunity> opportunities = new ArrayList<>();

    protected Customer() {
    }

    /**
     * 建立客戶基本資料。
     *
     * @param name 客戶名稱
     * @param email 電子郵件
     * @param phone 電話
     * @param taxId 統一編號
     * @param industry 產業別
     * @param ownerName 負責業務
     */
    public Customer(String name, String email, String phone, String taxId, String industry, String ownerName) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.taxId = taxId;
        this.industry = industry;
        this.ownerName = ownerName;
    }

    /**
     * 更新合約日期欄位。
     *
     * @param contractStartDate 合約起始日
     * @param contractEndDate 合約到期日
     * @param renewalDueDate 預計續約日
     */
    public void updateContractDates(LocalDate contractStartDate, LocalDate contractEndDate, LocalDate renewalDueDate) {
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.renewalDueDate = renewalDueDate;
    }

    /**
     * 指派負責業務帳號，並同步去正規化的 ownerName 顯示快取，確保兩者一致。
     *
     * @param owner 負責業務帳號
     */
    public void assignOwner(AppUser owner) {
        this.owner = owner;
        this.ownerName = owner.getDisplayName();
    }

    /**
     * 更新客戶狀態。
     *
     * @param status 新狀態
     */
    public void updateStatus(CustomerStatus status) {
        this.status = status;
    }

    /**
     * 加入互動紀錄並維護雙向關聯。
     *
     * @param interaction 互動紀錄
     */
    public void addInteraction(Interaction interaction) {
        interaction.attachCustomer(this);
        interactions.add(interaction);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getTaxId() { return taxId; }
    public String getIndustry() { return industry; }
    public String getOwnerName() { return ownerName; }
    public AppUser getOwner() { return owner; }
    public CustomerStatus getStatus() { return status; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public LocalDate getRenewalDueDate() { return renewalDueDate; }
    public List<Contact> getContacts() { return contacts; }
    public List<Interaction> getInteractions() { return interactions; }
    public List<Opportunity> getOpportunities() { return opportunities; }
}

