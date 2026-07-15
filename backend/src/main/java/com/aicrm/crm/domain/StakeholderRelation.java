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

/**
 * Stakeholder 關係（V27）：綁定兩位<b>同一客戶</b>的 Contact，保存關係類型、資料來源與確認狀態。
 *
 * <p>跨 customer 的關係在服務層被拒絕（兩位 Contact 必須屬同一 customer）。
 * AI 建議與人工確認事實以 {@code status} 區分。</p>
 */
@Entity
@Table(name = "stakeholder_relations")
public class StakeholderRelation extends AuditableEntity {

    /** 關係主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 關係起點聯絡人。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_contact_id", nullable = false)
    private Contact fromContact;

    /** 關係終點聯絡人。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_contact_id", nullable = false)
    private Contact toContact;

    /** 關係類型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 24)
    private StakeholderRelationType relationType;

    /** 資料來源（AI 推測 / 人工輸入）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StakeholderSource source;

    /** 確認狀態。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StakeholderSuggestionStatus status;

    /** 信心分數（0–100）。 */
    @Column(nullable = false)
    private int confidence;

    protected StakeholderRelation() {
    }

    /**
     * 建立一筆 Stakeholder 關係。
     *
     * @param fromContact 起點聯絡人
     * @param toContact 終點聯絡人
     * @param relationType 關係類型
     * @param source 資料來源
     * @param status 確認狀態
     * @param confidence 信心分數（0–100）
     */
    public StakeholderRelation(Contact fromContact, Contact toContact, StakeholderRelationType relationType,
                               StakeholderSource source, StakeholderSuggestionStatus status, int confidence) {
        this.fromContact = fromContact;
        this.toContact = toContact;
        this.relationType = relationType;
        this.source = source;
        this.status = status;
        this.confidence = confidence;
    }

    /** 將建議確認為事實（狀態轉為 CONFIRMED）。 */
    public void confirm() {
        this.status = StakeholderSuggestionStatus.CONFIRMED;
    }

    /** 拒絕建議（狀態轉為 REJECTED，保留稽核）。 */
    public void reject() {
        this.status = StakeholderSuggestionStatus.REJECTED;
    }

    public Long getId() { return id; }
    public Contact getFromContact() { return fromContact; }
    public Contact getToContact() { return toContact; }
    public StakeholderRelationType getRelationType() { return relationType; }
    public StakeholderSource getSource() { return source; }
    public StakeholderSuggestionStatus getStatus() { return status; }
    public int getConfidence() { return confidence; }
}
