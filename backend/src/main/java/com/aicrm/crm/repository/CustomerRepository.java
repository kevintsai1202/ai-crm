package com.aicrm.crm.repository;

import com.aicrm.crm.domain.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * 客戶資料存取介面，支援分頁、動態查詢與詳情載入。
 */
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    /**
     * 載入客戶詳情與關聯資料，避免 Controller 觸發 LazyInitialization。
     *
     * @param id 客戶 ID
     * @return 客戶詳情
     */
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findDetailById(Long id);

    /**
     * 取得所有不重複的產業名稱（供新增客戶下拉選單）。
     *
     * @return 依字母排序的產業清單
     */
    @Query("select distinct c.industry from Customer c where c.industry <> '' order by c.industry")
    List<String> findDistinctIndustries();

    /**
     * 取得所有不重複的負責業務名稱（供新增客戶下拉選單）。
     *
     * @return 依字母排序的業務清單
     */
    @Query("select distinct c.ownerName from Customer c where c.ownerName <> '' order by c.ownerName")
    List<String> findDistinctOwners();

    /**
     * 取得尚未回填風險等級的客戶 ID（供啟動補算）。
     *
     * @return risk_level 為 null 的客戶 ID 清單
     */
    @Query("select c.id from Customer c where c.riskLevel is null")
    List<Long> findIdsByRiskLevelIsNull();

    /**
     * 載入某業務（依去正規化 owner_name 欄位）負責的所有客戶。供工作檯個人 AI 計算待辦。
     *
     * @param ownerName 業務顯示名稱
     * @return 該業務負責的客戶清單
     */
    List<Customer> findByOwnerName(String ownerName);
}
