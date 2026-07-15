package com.aicrm.crm.repository;

import com.aicrm.crm.domain.StakeholderRole;
import com.aicrm.crm.domain.StakeholderSuggestionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stakeholder 決策角色資料存取介面（V27）。
 *
 * <p>以巢狀屬性路徑 contact.customer.id 依客戶查詢，避免額外去正規化欄位造成不一致。</p>
 */
public interface StakeholderRoleRepository extends JpaRepository<StakeholderRole, Long> {

    /**
     * 取某客戶全部決策角色（不分狀態），供去重與整體檢視。
     *
     * @param customerId 客戶 id
     * @return 角色清單
     */
    List<StakeholderRole> findByContact_Customer_Id(Long customerId);

    /**
     * 取某客戶特定狀態的決策角色（例如已確認事實或待確認建議）。
     *
     * @param customerId 客戶 id
     * @param status 確認狀態
     * @return 角色清單
     */
    List<StakeholderRole> findByContact_Customer_IdAndStatus(Long customerId, StakeholderSuggestionStatus status);

    /**
     * 判斷某聯絡人是否已存在任何角色紀錄（供 suggest 去重：每位聯絡人僅產生一次角色建議）。
     *
     * @param contactId 聯絡人 id
     * @return 是否已存在
     */
    boolean existsByContact_Id(Long contactId);
}
