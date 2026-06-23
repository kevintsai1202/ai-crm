package com.aicrm.crm.service;

import com.aicrm.crm.repository.KnowledgeVectorRepository;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 知識庫索引器：啟動時為缺向量的文件補算 embedding；提供強制重建。
 */
@Service
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeVectorRepository vectorRepo;
    private final EmbeddingClient embeddingClient;

    public KnowledgeIndexer(KnowledgeVectorRepository vectorRepo, EmbeddingClient embeddingClient) {
        this.vectorRepo = vectorRepo;
        this.embeddingClient = embeddingClient;
    }

    /** 啟動後為尚未建立向量的文件補索引（idempotent）。H2 環境無 pgvector 時靜默跳過。 */
    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        try {
            int n = indexRows(vectorRepo.findMissingEmbedding());
            if (n > 0) log.info("知識庫啟動索引完成，補算 {} 筆向量", n);
        } catch (Exception e) {
            log.debug("知識庫向量索引跳過（H2 環境不支援 pgvector）：{}", e.getMessage());
        }
    }

    /**
     * 強制重建全部文件的向量。
     *
     * @return 重建筆數
     */
    public int reindexAll() {
        return indexRows(vectorRepo.findAllForIndex());
    }

    /** 對 [id, content] 列批次嵌入並寫回。 */
    private int indexRows(List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        var texts = rows.stream().map(r -> (String) r[1]).toList();
        var vectors = embeddingClient.embed(texts, EmbeddingClient.InputType.DOCUMENT);
        for (int i = 0; i < rows.size(); i++) {
            vectorRepo.updateEmbedding((Long) rows.get(i)[0], vectors.get(i));
        }
        return rows.size();
    }
}
