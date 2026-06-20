package com.aicrm.crm.repository;

import com.aicrm.crm.domain.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * AI 呼叫紀錄資料存取介面，附用量彙總查詢。
 */
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    /**
     * 加總所有呼叫的 total_tokens。
     *
     * @return token 總數（無資料時為 0）
     */
    @Query("select coalesce(sum(l.totalTokens), 0) from AiCallLog l")
    long sumTotalTokens();

    /**
     * 統計真實 LLM 呼叫次數（ai_enabled = true）。
     *
     * @return 真實呼叫次數
     */
    long countByAiEnabledTrue();

    /**
     * 統計 fallback 呼叫次數（ai_enabled = false）。
     *
     * @return fallback 呼叫次數
     */
    long countByAiEnabledFalse();
}
