package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** CRM 正式任務實體，是提醒、延期、完成與行事曆匯出的單一真實來源。 */
@Entity
@Table(name = "crm_tasks")
public class CrmTask extends AuditableEntity {
    /** 任務主鍵。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** 所屬客戶。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id", nullable = false) private Customer customer;
    /** 選填關聯商機。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "opportunity_id") private Opportunity opportunity;
    /** 選填關聯聯絡人。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contact_id") private Contact contact;
    /** 任務類型。 */
    @Enumerated(EnumType.STRING) @Column(name = "task_type", nullable = false) private CrmTaskType type;
    /** 任務狀態。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CrmTaskStatus status = CrmTaskStatus.OPEN;
    /** 任務優先級。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CrmTaskPriority priority;
    /** 任務標題。 */
    @Column(nullable = false) private String title;
    /** 任務說明。 */
    @Column(length = 4000) private String description;
    /** 負責業務。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_id", nullable = false) private AppUser assignee;
    /** 預定開始時間（Asia/Taipei 業務時間）。 */
    @Column(name = "scheduled_start", nullable = false) private LocalDateTime scheduledStart;
    /** 預定結束時間（Asia/Taipei 業務時間）。 */
    @Column(name = "scheduled_end", nullable = false) private LocalDateTime scheduledEnd;
    /** 完成時間。 */
    @Column(name = "completed_at") private LocalDateTime completedAt;
    /** 延期次數。 */
    @Column(name = "postpone_count", nullable = false) private int postponeCount;
    /** 建立來源。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CrmTaskSource source;
    /** JPA 樂觀鎖版本。 */
    @Version @Column(nullable = false) private long version;

    protected CrmTask() {}

    /** 建立開放狀態任務。 */
    public CrmTask(Customer customer, Opportunity opportunity, Contact contact, CrmTaskType type,
                   CrmTaskPriority priority, String title, String description, AppUser assignee,
                   LocalDateTime scheduledStart, LocalDateTime scheduledEnd, CrmTaskSource source) {
        this.customer = customer; this.opportunity = opportunity; this.contact = contact; this.type = type;
        this.priority = priority; this.title = title; this.description = description; this.assignee = assignee;
        this.scheduledStart = scheduledStart; this.scheduledEnd = scheduledEnd; this.source = source;
    }

    /** 延後尚未結束的任務並累加延期次數。 */
    public void postpone(LocalDateTime start, LocalDateTime end) {
        if (status == CrmTaskStatus.COMPLETED || status == CrmTaskStatus.CANCELLED) throw new IllegalStateException("已結束任務不可延期");
        this.scheduledStart = start; this.scheduledEnd = end; this.postponeCount++;
    }

    /** 將尚未結束的任務標記完成。 */
    public void complete(LocalDateTime at) {
        if (status == CrmTaskStatus.COMPLETED || status == CrmTaskStatus.CANCELLED) throw new IllegalStateException("任務已結束");
        this.status = CrmTaskStatus.COMPLETED; this.completedAt = at;
    }

    /** 編輯任務可變欄位，狀態轉換另由明確操作處理。 */
    public void update(CrmTaskType type, CrmTaskPriority priority, String title, String description,
                       AppUser assignee, LocalDateTime start, LocalDateTime end) {
        if (status == CrmTaskStatus.COMPLETED || status == CrmTaskStatus.CANCELLED) throw new IllegalStateException("已結束任務不可編輯");
        this.type = type; this.priority = priority; this.title = title; this.description = description;
        this.assignee = assignee; this.scheduledStart = start; this.scheduledEnd = end;
    }

    public Long getId(){return id;} public Customer getCustomer(){return customer;} public Opportunity getOpportunity(){return opportunity;}
    public Contact getContact(){return contact;} public CrmTaskType getType(){return type;} public CrmTaskStatus getStatus(){return status;}
    public CrmTaskPriority getPriority(){return priority;} public String getTitle(){return title;} public String getDescription(){return description;}
    public AppUser getAssignee(){return assignee;} public LocalDateTime getScheduledStart(){return scheduledStart;}
    public LocalDateTime getScheduledEnd(){return scheduledEnd;} public LocalDateTime getCompletedAt(){return completedAt;}
    public int getPostponeCount(){return postponeCount;} public CrmTaskSource getSource(){return source;} public long getVersion(){return version;}
}
