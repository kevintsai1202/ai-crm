package com.aicrm.crm.repository;

import com.aicrm.crm.domain.ManagerInsight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Manager AI 分析快取存取。
 */
public interface ManagerInsightRepository extends JpaRepository<ManagerInsight, Long> {

    /**
     * 取得團隊診斷快取（scope=TEAM 僅一列）。
     *
     * @param scope 固定傳 "TEAM"
     * @return 快取列（可空）
     */
    Optional<ManagerInsight> findFirstByScope(String scope);

    /**
     * 取得個別業務 coaching 快取。
     *
     * @param scope 固定傳 "OWNER"
     * @param ownerName 業務顯示名稱
     * @return 快取列（可空）
     */
    Optional<ManagerInsight> findFirstByScopeAndOwnerName(String scope, String ownerName);
}
