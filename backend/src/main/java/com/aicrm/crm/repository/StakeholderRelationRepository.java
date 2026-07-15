package com.aicrm.crm.repository;

import com.aicrm.crm.domain.StakeholderRelation;
import com.aicrm.crm.domain.StakeholderRelationType;
import com.aicrm.crm.domain.StakeholderSuggestionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stakeholder 關係資料存取介面（V27）。
 *
 * <p>關係的兩端 Contact 同屬一客戶，故以起點 fromContact.customer.id 依客戶查詢即可涵蓋。</p>
 */
public interface StakeholderRelationRepository extends JpaRepository<StakeholderRelation, Long> {

    /**
     * 取某客戶全部關係（不分狀態）。
     *
     * @param customerId 客戶 id
     * @return 關係清單
     */
    List<StakeholderRelation> findByFromContact_Customer_Id(Long customerId);

    /**
     * 取某客戶特定狀態的關係（例如已確認事實或待確認建議）。
     *
     * @param customerId 客戶 id
     * @param status 確認狀態
     * @return 關係清單
     */
    List<StakeholderRelation> findByFromContact_Customer_IdAndStatus(Long customerId, StakeholderSuggestionStatus status);

    /**
     * 判斷是否已存在相同「起點 / 終點 / 關係類型」的關係（供 suggest 去重）。
     *
     * @param fromContactId 起點聯絡人 id
     * @param toContactId 終點聯絡人 id
     * @param relationType 關係類型
     * @return 是否已存在
     */
    boolean existsByFromContact_IdAndToContact_IdAndRelationType(Long fromContactId, Long toContactId,
                                                                 StakeholderRelationType relationType);
}
