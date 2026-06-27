package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 客戶聯絡人實體，提供前端客戶詳情頁呈現。
 */
@Entity
@Table(name = "contacts")
public class Contact extends AuditableEntity {

    /** 聯絡人主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬客戶。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 聯絡人姓名。 */
    @Column(nullable = false)
    private String name;

    /** 聯絡人職稱。 */
    @Column(nullable = false)
    private String title;

    /** 聯絡人 email。 */
    @Column(nullable = false)
    private String email;

    protected Contact() {
    }

    /**
     * 建立聯絡人並綁定所屬客戶。
     *
     * @param customer 所屬客戶
     * @param name 聯絡人姓名
     * @param title 聯絡人職稱
     * @param email 聯絡人 email
     */
    public Contact(Customer customer, String name, String title, String email) {
        this.customer = customer;
        this.name = name;
        this.title = title;
        this.email = email;
    }

    /**
     * 更新聯絡人資訊（編輯用）。
     *
     * @param name 聯絡人姓名
     * @param title 聯絡人職稱
     * @param email 聯絡人 email
     */
    public void updateInfo(String name, String title, String email) {
        this.name = name;
        this.title = title;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getEmail() { return email; }
    public Customer getCustomer() { return customer; }
}

