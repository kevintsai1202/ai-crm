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
import java.time.LocalDateTime;

/**
 * 客戶互動紀錄，供 AI 摘要、風險評估與時間線 UI 使用。
 */
@Entity
@Table(name = "interactions")
public class Interaction extends AuditableEntity {

    /** 互動紀錄主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬客戶。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 互動類型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InteractionType type;

    /** 互動發生時間。 */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** 互動內容。 */
    @Column(nullable = false, length = 2000)
    private String content;

    protected Interaction() {
    }

    /**
     * 建立互動紀錄。
     *
     * @param type 互動類型
     * @param occurredAt 發生時間
     * @param content 內容
     */
    public Interaction(InteractionType type, LocalDateTime occurredAt, String content) {
        this.type = type;
        this.occurredAt = occurredAt;
        this.content = content;
    }

    /**
     * 綁定客戶關聯。
     *
     * @param customer 所屬客戶
     */
    public void attachCustomer(Customer customer) {
        this.customer = customer;
    }

    public Long getId() { return id; }
    public InteractionType getType() { return type; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getContent() { return content; }
}

