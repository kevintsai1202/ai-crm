package com.aicrm.crm.service.ai;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.repository.KnowledgeChunkVectorRepository;
import com.aicrm.crm.repository.KnowledgeDocumentRepository;
import com.aicrm.crm.repository.KnowledgeVectorRepository;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RAG 引用服務：chunk 向量 → 文件向量 → similarityHint 三層 fallback。
 * 自 InsightService 抽出，降低 AI 核心耦合。
 */
@Service
public class RagCitationService {

    private static final Logger log = LoggerFactory.getLogger(RagCitationService.class);

    private final EmbeddingClient embeddingClient;
    private final KnowledgeChunkVectorRepository chunkRepo;
    private final KnowledgeVectorRepository documentVectorRepo;
    private final KnowledgeDocumentRepository knowledgeDocuments;

    public RagCitationService(EmbeddingClient embeddingClient,
                              KnowledgeChunkVectorRepository chunkRepo,
                              KnowledgeVectorRepository documentVectorRepo,
                              KnowledgeDocumentRepository knowledgeDocuments) {
        this.embeddingClient = embeddingClient;
        this.chunkRepo = chunkRepo;
        this.documentVectorRepo = documentVectorRepo;
        this.knowledgeDocuments = knowledgeDocuments;
    }

    /**
     * 依查詢語意取 top-3 引用。
     *
     * @param query 查詢文字
     * @return 引用清單
     */
    public List<Dtos.CitationResponse> loadCitations(String query) {
        try {
            var vec = embeddingClient.embed(List.of(query), EmbeddingClient.InputType.QUERY).get(0);
            // 1) chunk 級
            try {
                var chunkHits = chunkRepo.searchTopK(vec, 3);
                if (!chunkHits.isEmpty()) {
                    return chunkHits;
                }
            } catch (Exception e) {
                log.debug("chunk 檢索不可用，改文件級：{}", e.getMessage());
            }
            // 2) 文件級（相容舊索引）
            var docHits = documentVectorRepo.searchTopK(vec, 3);
            if (!docHits.isEmpty()) {
                return docHits;
            }
        } catch (Exception e) {
            log.warn("向量檢索失敗，改用 similarityHint fallback：{}", e.getMessage());
        }
        // 3) 人工相似度提示
        return knowledgeDocuments.findTop3ByOrderBySimilarityHintDesc().stream()
                .map(doc -> new Dtos.CitationResponse(
                        doc.getTitle(), doc.getDocType(), doc.getContent(), doc.getSimilarityHint()))
                .toList();
    }
}
