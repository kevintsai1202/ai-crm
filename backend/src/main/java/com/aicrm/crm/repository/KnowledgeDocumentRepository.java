package com.aicrm.crm.repository;

import com.aicrm.crm.domain.KnowledgeDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RAG 文件片段資料存取介面。
 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    /**
     * 依相似度提示值排序，模擬向量檢索的前 N 筆結果。
     *
     * @return 文件片段清單
     */
    List<KnowledgeDocument> findTop3ByOrderBySimilarityHintDesc();
}

