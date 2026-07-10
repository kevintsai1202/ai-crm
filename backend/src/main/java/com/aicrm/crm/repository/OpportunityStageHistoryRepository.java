package com.aicrm.crm.repository;

import com.aicrm.crm.domain.OpportunityStageHistory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商機階段歷史存取。
 */
public interface OpportunityStageHistoryRepository extends JpaRepository<OpportunityStageHistory, Long> {

    /**
     * 取某商機進入指定階段的最後一次紀錄（最新 changed_at）。
     *
     * @param opportunityId 商機 id
     * @param toStage 目標階段名稱
     * @return 最新一筆（若有）
     */
    @Query(value = """
            select * from opportunity_stage_history
            where opportunity_id = :oppId and to_stage = :toStage
            order by changed_at desc
            limit 1
            """, nativeQuery = true)
    Optional<OpportunityStageHistory> findLatestEntryToStage(
            @Param("oppId") Long opportunityId, @Param("toStage") String toStage);

    /**
     * 批次取多筆商機的全部歷史（由服務層在記憶體聚合最新進入時間）。
     *
     * @param opportunityIds 商機 id 集合
     * @return 歷史列
     */
    List<OpportunityStageHistory> findByOpportunityIdIn(Collection<Long> opportunityIds);
}
