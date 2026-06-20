package com.aicrm.crm.repository;

import com.aicrm.crm.domain.Interaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 互動紀錄資料存取介面。
 */
public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    /**
     * 查詢指定客戶的互動紀錄，最新紀錄在前。
     *
     * @param customerId 客戶 ID
     * @return 互動紀錄清單
     */
    List<Interaction> findByCustomerIdOrderByOccurredAtDesc(Long customerId);
}

