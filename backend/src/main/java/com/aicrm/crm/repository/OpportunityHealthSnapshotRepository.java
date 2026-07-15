package com.aicrm.crm.repository;

import com.aicrm.crm.domain.OpportunityHealthSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商機健康度 snapshot 資料存取介面。
 *
 * <p>排序一律以 calculated_at 為主、id 為次；固定 clock 下多次重算的 calculated_at 可能相同，
 * 以 id 次序保證「最新一筆」與趨勢排序穩定可重現。</p>
 */
public interface OpportunityHealthSnapshotRepository extends JpaRepository<OpportunityHealthSnapshot, Long> {

    /**
     * 取某商機最新一筆 snapshot（calculated_at 最新、同時間取 id 最大）。
     *
     * @param opportunityId 商機 id
     * @return 最新 snapshot（可能不存在）
     */
    Optional<OpportunityHealthSnapshot> findFirstByOpportunityIdOrderByCalculatedAtDescIdDesc(Long opportunityId);

    /**
     * 取某商機全部 snapshot（由舊到新），供趨勢呈現。
     *
     * @param opportunityId 商機 id
     * @return snapshot 歷史列（時間升冪）
     */
    List<OpportunityHealthSnapshot> findByOpportunityIdOrderByCalculatedAtAscIdAsc(Long opportunityId);
}
