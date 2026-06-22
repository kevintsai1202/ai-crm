package com.aicrm.crm.repository;

import com.aicrm.crm.domain.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 供應商設定資料存取。
 */
public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    /** 檢查名稱是否已存在（新增時用）。 */
    boolean existsByName(String name);
    /** 檢查名稱是否已存在但排除自身（更新時用）。 */
    boolean existsByNameAndIdNot(String name, Long id);
}
