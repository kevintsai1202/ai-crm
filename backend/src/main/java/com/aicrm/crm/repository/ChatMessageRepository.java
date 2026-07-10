package com.aicrm.crm.repository;

import com.aicrm.crm.domain.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 對話訊息 JPA 存取：提供時序查詢（最近 N 則）。
 * 函式級註解：embedding 不在此實體，向量檢索由 ChatMessageVectorRepository 負責。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 取指定客戶最近 6 則訊息（依建立時間由新到舊）。
     *
     * @param customerId 客戶 id
     * @return 最近 6 則訊息（新到舊）
     */
    List<ChatMessage> findTop6ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /**
     * 取指定客戶訊息（新到舊），搭配 {@link Pageable} 限制筆數，供前端歷史 UI 使用。
     *
     * @param customerId 客戶 id
     * @param pageable 分頁（通常 page=0、size=limit）
     * @return 訊息列表（新到舊）
     */
    List<ChatMessage> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
