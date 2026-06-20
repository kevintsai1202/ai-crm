package com.aicrm.crm.repository;

import com.aicrm.crm.domain.AiFeedback;
import com.aicrm.crm.domain.FeedbackDecision;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 回饋紀錄資料存取介面。
 */
public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {

    /**
     * 依決定統計回饋次數（採納或拒絕）。
     *
     * @param decision 採納或拒絕
     * @return 對應次數
     */
    long countByDecision(FeedbackDecision decision);
}
