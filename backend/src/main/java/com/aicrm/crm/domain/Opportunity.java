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

    protected Opportunity() {
    }

    /**
     * 建立商機（供示範資料生成器使用），並維護與客戶的雙向關聯。
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
        this.customer = customer;
        this.name = name;
        this.stage = stage;
        this.amount = amount;
        this.expectedCloseDate = expectedCloseDate;
        this.type = type;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public OpportunityStage getStage() { return stage; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public OpportunityType getType() { return type; }
    public Customer getCustomer() { return customer; }

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

