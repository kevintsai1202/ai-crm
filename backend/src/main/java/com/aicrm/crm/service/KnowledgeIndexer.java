package com.aicrm.crm.service;

import com.aicrm.crm.repository.KnowledgeChunkVectorRepository;
import com.aicrm.crm.repository.KnowledgeVectorRepository;
import com.aicrm.crm.service.ai.TextChunker;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 知識庫索引器：文件級 embedding + chunk 級 embedding（SP12）。
 * 啟動時補算缺向量文件與缺 chunk 文件；reindex 全量重建。
 */
@Service
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeVectorRepository vectorRepo;
    private final KnowledgeChunkVectorRepository chunkRepo;
    private final EmbeddingClient embeddingClient;

    public KnowledgeIndexer(KnowledgeVectorRepository vectorRepo,
                            KnowledgeChunkVectorRepository chunkRepo,
                            EmbeddingClient embeddingClient) {
        this.vectorRepo = vectorRepo;
        this.chunkRepo = chunkRepo;
        this.embeddingClient = embeddingClient;
    }

    /** 啟動後補文件向量與 chunk 索引（idempotent）。H2 無 pgvector 時靜默跳過。 */
    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        try {
            int docs = indexDocumentRows(vectorRepo.findMissingEmbedding());
            int chunks = indexChunkRows(chunkRepo.findDocumentsMissingChunks(), false);
            if (docs > 0 || chunks > 0) {
                log.info("知識庫啟動索引完成：文件向量 {} 筆、chunk 文件 {} 份", docs, chunks);
            }
        } catch (Exception e) {
            log.debug("知識庫向量索引跳過（H2 環境不支援 pgvector）：{}", e.getMessage());
        }
    }

    /**
     * 強制重建全部文件向量與 chunk。
     *
     * @return 重建的文件筆數（文件級）
     */
    public int reindexAll() {
        int docs = indexDocumentRows(vectorRepo.findAllForIndex());
        indexChunkRows(chunkRepo.findAllDocumentsForChunkIndex(), true);
        return docs;
    }

    /** 對 [id, content] 列批次嵌入並寫回文件級 embedding。 */
    private int indexDocumentRows(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        var texts = rows.stream().map(r -> (String) r[1]).toList();
        var vectors = embeddingClient.embed(texts, EmbeddingClient.InputType.DOCUMENT);
        for (int i = 0; i < rows.size(); i++) {
            vectorRepo.updateEmbedding((Long) rows.get(i)[0], vectors.get(i));
        }
        return rows.size();
    }

    /**
     * 為文件建立 chunk 向量。
     *
     * @param rows [id, content]
     * @param replace true 時先刪既有 chunk
     * @return 處理的文件份數
     */
    private int indexChunkRows(List<Object[]> rows, boolean replace) {
        if (rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object[] row : rows) {
            long docId = (Long) row[0];
            String content = (String) row[1];
            if (replace) {
                chunkRepo.deleteByDocumentId(docId);
            }
            var parts = TextChunker.chunk(content);
            if (parts.isEmpty()) {
                continue;
            }
            var vectors = embeddingClient.embed(parts, EmbeddingClient.InputType.DOCUMENT);
            for (int i = 0; i < parts.size(); i++) {
                chunkRepo.insertChunk(docId, i, parts.get(i), vectors.get(i));
            }
            count++;
        }
        return count;
    }
}
