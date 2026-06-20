package com.aicrm.crm.repository;

import com.aicrm.crm.domain.InteractionInsight;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 互動情緒意圖分析結果資料存取介面，附 upsert 查詢、批次補算來源與雷達聚合查詢。
 */
public interface InteractionInsightRepository extends JpaRepository<InteractionInsight, Long> {

    /**
     * 依互動 ID 查詢既有分析（供 upsert 判斷）。
     *
     * @param interactionId 互動 ID
     * @return 既有分析（可能不存在）
     */
    Optional<InteractionInsight> findByInteractionId(Long interactionId);

    /**
     * 查詢指定客戶的所有互動分析結果（供客戶詳情一次撈取後 map by interactionId）。
     *
     * @param customerId 客戶 ID
     * @return 該客戶的分析結果清單
     */
    List<InteractionInsight> findByCustomerId(Long customerId);

    /**
     * 查詢尚無 insight 的互動（供 analyzeMissing 批次補算）。
     * 回傳每列為 [Long interactionId, Long customerId, String content]，避免在服務層觸發 LAZY 關聯。
     *
     * @return 尚未分析的互動列
     */
    @Query("select i.id, i.customer.id, i.content from Interaction i "
            + "where i.id not in (select ins.interactionId from InteractionInsight ins)")
    List<Object[]> findInteractionsWithoutInsight();

    /**
     * 意圖分布統計（intent 名稱 + 筆數），筆數多者在前。
     * 回傳每列為 [String intent, long count]。
     *
     * @return 意圖分布列
     */
    @Query("select i.intent, count(i) from InteractionInsight i group by i.intent order by count(i) desc")
    List<Object[]> countByIntent();

    /**
     * 近 N 個月情緒趨勢：以互動發生時間 occurred_at 的 yyyy-MM 分群，統計三種情緒筆數。
     * 回傳每列為 [String month, long positive, long neutral, long negative]，月份升冪。
     * 補空月由服務層處理。
     *
     * @param since 起算時間（含），早於此者不計
     * @return 月情緒趨勢列
     */
    @Query(value = "select to_char(i.occurred_at, 'YYYY-MM') as month, "
            + "count(*) filter (where ins.sentiment = 'POSITIVE') as positive, "
            + "count(*) filter (where ins.sentiment = 'NEUTRAL') as neutral, "
            + "count(*) filter (where ins.sentiment = 'NEGATIVE') as negative "
            + "from interaction_insights ins join interactions i on i.id = ins.interaction_id "
            + "where i.occurred_at >= :since "
            + "group by to_char(i.occurred_at, 'YYYY-MM') order by month",
            nativeQuery = true)
    List<Object[]> sentimentTrendSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    /**
     * 高風險互動：情緒為 NEGATIVE 且意圖屬流失 / 客訴，依互動時間新到舊排序。
     * 回傳每列為 [Long customerId, String customerName, LocalDateTime occurredAt, String type, String intent, String sentiment, String content]。
     *
     * @param pageable 取前 N 筆
     * @return 高風險互動列
     */
    @Query(value = "select c.id, c.name, i.occurred_at, i.type, ins.intent, ins.sentiment, i.content "
            + "from interaction_insights ins "
            + "join interactions i on i.id = ins.interaction_id "
            + "join customers c on c.id = ins.customer_id "
            + "where ins.sentiment = 'NEGATIVE' and ins.intent in ('CHURN_SIGNAL', 'COMPLAINT') "
            + "order by i.occurred_at desc",
            nativeQuery = true)
    List<Object[]> findHighRiskInteractions(org.springframework.data.domain.Pageable pageable);

    /**
     * 客戶層情緒風險聚合：每位客戶的負面數 / 流失訊號數 / 客訴數，至少有一項風險者才列入。
     * 回傳每列為 [Long customerId, String name, long negativeCount, long churnSignalCount, long complaintCount]。
     * 加權分數與排序由服務層計算。
     *
     * @return 客戶風險聚合列
     */
    @Query(value = "select c.id, c.name, "
            + "count(*) filter (where ins.sentiment = 'NEGATIVE') as negative_count, "
            + "count(*) filter (where ins.intent = 'CHURN_SIGNAL') as churn_count, "
            + "count(*) filter (where ins.intent = 'COMPLAINT') as complaint_count "
            + "from interaction_insights ins join customers c on c.id = ins.customer_id "
            + "group by c.id, c.name "
            + "having count(*) filter (where ins.sentiment = 'NEGATIVE') > 0 "
            + "or count(*) filter (where ins.intent = 'CHURN_SIGNAL') > 0 "
            + "or count(*) filter (where ins.intent = 'COMPLAINT') > 0",
            nativeQuery = true)
    List<Object[]> aggregateCustomerRisk();
}
