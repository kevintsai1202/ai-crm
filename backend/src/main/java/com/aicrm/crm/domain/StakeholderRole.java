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
 * Stakeholder 決策角色（V27）：綁定單一 Contact，保存其決策角色、影響力、立場、信心、
 * 資料來源（AI / MANUAL）與確認狀態（SUGGESTED / CONFIRMED / REJECTED）。
 *
 * <p>AI 建議與人工確認事實以 {@code status} 區分：只有 CONFIRMED 顯示為已確認圖；
 * SUGGESTED 為待確認建議；REJECTED 保留稽核紀錄但不顯示為事實。</p>
 */
@Entity
@Table(name = "stakeholder_roles")
public class StakeholderRole extends AuditableEntity {

    /** 角色主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 綁定的聯絡人（決策角色掛在單一 Contact）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    /** 決策角色類型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 32)
    private StakeholderRoleType roleType;

    /** 影響力程度。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StakeholderInfluence influence;

    /** 對我方方案的立場。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StakeholderStance stance;

    /** 信心分數（0–100），deterministic 由職稱推論而得。 */
    @Column(nullable = false)
    private int confidence;

    /** 資料來源（AI 推測 / 人工輸入）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StakeholderSource source;

    /** 確認狀態。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StakeholderSuggestionStatus status;

    protected StakeholderRole() {
    }

    /**
     * 建立一筆 Stakeholder 決策角色。
     *
     * @param contact 綁定的聯絡人
     * @param roleType 決策角色類型
     * @param influence 影響力
     * @param stance 立場
     * @param confidence 信心分數（0–100）
     * @param source 資料來源
     * @param status 確認狀態
     */
    public StakeholderRole(Contact contact, StakeholderRoleType roleType, StakeholderInfluence influence,
                           StakeholderStance stance, int confidence, StakeholderSource source,
                           StakeholderSuggestionStatus status) {
        this.contact = contact;
        this.roleType = roleType;
        this.influence = influence;
        this.stance = stance;
        this.confidence = confidence;
        this.source = source;
        this.status = status;
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
    public Contact getContact() { return contact; }
    public StakeholderRoleType getRoleType() { return roleType; }
    public StakeholderInfluence getInfluence() { return influence; }
    public StakeholderStance getStance() { return stance; }
    public int getConfidence() { return confidence; }
    public StakeholderSource getSource() { return source; }
    public StakeholderSuggestionStatus getStatus() { return status; }
}
