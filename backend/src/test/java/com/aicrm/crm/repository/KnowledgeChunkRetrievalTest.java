package com.aicrm.crm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chunk 級向量檢索整合測試。
 */
class KnowledgeChunkRetrievalTest extends PostgresTestBase {

    @Autowired KnowledgeChunkVectorRepository chunkRepo;
    @Autowired JdbcTemplate jdbc;

    private float[] unit(int dim, int hot) {
        var v = new float[dim];
        v[hot] = 1.0f;
        return v;
    }

    @Test
    void searchTopK_prefersMatchingChunk() {
        var docId = jdbc.queryForObject("select id from knowledge_documents order by id limit 1", Long.class);
        assertThat(docId).isNotNull();
        chunkRepo.deleteByDocumentId(docId);
        chunkRepo.insertChunk(docId, 0, "片段A", unit(1024, 0));
        chunkRepo.insertChunk(docId, 1, "片段B", unit(1024, 1));

        var hits = chunkRepo.searchTopK(unit(1024, 1), 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).content()).isEqualTo("片段B");
        assertThat(hits.get(0).similarity().doubleValue()).isGreaterThan(0.9);
        assertThat(hits.get(0).title()).isNotBlank();
    }
}
