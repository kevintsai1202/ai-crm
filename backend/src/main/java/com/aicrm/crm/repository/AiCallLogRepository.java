package com.aicrm.crm.repository;

import com.aicrm.crm.domain.AiCallLog;
import com.aicrm.crm.domain.AiCallType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * AI 呼叫紀錄資料存取介面，附用量彙總查詢。
 */
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    /**
     * 查某客戶的所有 AI 呼叫紀錄，新到舊排序（供「AI 歷程」Modal）。
     * customer_id 已有索引（V6 idx_ai_call_log_customer）。
     *
     * @param customerId 客戶 ID
     * @return 該客戶歷次 AI 呼叫紀錄
     */
    List<AiCallLog> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

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

    /**
     * 依類型查無客戶、無 subject 的呼叫紀錄（供 TEAM_ANALYSIS / PORTFOLIO 歷程）。
     *
     * @param type 呼叫類型
     * @return 該類型歷次呼叫（新到舊）
     */
    List<AiCallLog> findByCallTypeAndCustomerIdIsNullAndSubjectIsNullOrderByCreatedAtDesc(AiCallType type);

    /**
     * 依類型 + subject 查呼叫紀錄（供 OWNER_COACHING 分業務歷程）。
     *
     * @param type 呼叫類型
     * @param subject 分群鍵（ownerName）
     * @return 該業務歷次呼叫（新到舊）
     */
    List<AiCallLog> findByCallTypeAndSubjectOrderByCreatedAtDesc(AiCallType type, String subject);
}
