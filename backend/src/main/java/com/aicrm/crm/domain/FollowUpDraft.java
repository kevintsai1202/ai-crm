package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * AI 跟進信草稿。人工修改一律形成新版本（parentId 鏈 + versionNumber 遞增），舊版不覆寫，
 * 以保留完整編修歷程。保存產生草稿的模型／Provider、grounding 引用依據與核准者資訊。
 */
@Entity
@Table(name = "follow_up_drafts")
public class FollowUpDraft extends AuditableEntity {
    /** 草稿主鍵。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** 所屬客戶（跟進信對象），亦為 owner scope 依據。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id", nullable = false) private Customer customer;
    /** 選填關聯商機。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "opportunity_id") private Opportunity opportunity;
    /** 建立者帳號，亦為水平權限範圍。 */
    @Column(name = "creator_username", nullable = false) private String creatorUsername;
    /** 草稿版本號（1 起算）；人工修改遞增。 */
    @Column(name = "version_number", nullable = false) private int versionNumber;
    /** 上一版本草稿 id，串成版本鏈；第一版為 null。 */
    @Column(name = "parent_id") private Long parentId;
    /** 產生此草稿的 AI 模型；deterministic fallback 時為 null。 */
    @Column(name = "model") private String model;
    /** 產生此草稿的 AI Provider id；deterministic 時為 null。 */
    @Column(name = "ai_provider_id") private Long aiProviderId;
    /** AI 引用依據（grounding）：客戶／商機／近期互動摘要，供人工審核。 */
    @Column(columnDefinition = "text") private String grounding;
    /** 信件主旨。 */
    @Column(nullable = false) private String subject;
    /** 信件內文。 */
    @Column(columnDefinition = "text", nullable = false) private String body;
    /** 是否為人工修改後的版本（true 表示由人工編輯產生）。 */
    @Column(name = "edited", nullable = false) private boolean edited;
    /** 核准者帳號；核准並寄送後填入。 */
    @Column(name = "approved_by") private String approvedBy;
    /** 核准時間。 */
    @Column(name = "approved_at") private Instant approvedAt;

    protected FollowUpDraft() {}

    /**
     * 建立草稿版本。第一版由服務以 versionNumber=1、parentId=null、edited=false 建立；
     * 人工修改時由服務以遞增版本號、指向舊版的 parentId 與 edited=true 建立新一筆。
     */
    public FollowUpDraft(Customer customer, Opportunity opportunity, String creatorUsername, String model,
            Long aiProviderId, String grounding, String subject, String body, int versionNumber, Long parentId, boolean edited) {
        this.customer = customer; this.opportunity = opportunity; this.creatorUsername = creatorUsername;
        this.model = model; this.aiProviderId = aiProviderId; this.grounding = grounding;
        this.subject = subject; this.body = body; this.versionNumber = versionNumber; this.parentId = parentId;
        this.edited = edited;
    }

    /** 記錄核准者與核准時間（核准並寄送時呼叫）。 */
    public void approve(String username) { this.approvedBy = username; this.approvedAt = Instant.now(); }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Opportunity getOpportunity() { return opportunity; }
    public String getCreatorUsername() { return creatorUsername; }
    public int getVersionNumber() { return versionNumber; }
    public Long getParentId() { return parentId; }
    public String getModel() { return model; }
    public Long getAiProviderId() { return aiProviderId; }
    public String getGrounding() { return grounding; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public boolean isEdited() { return edited; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
}
