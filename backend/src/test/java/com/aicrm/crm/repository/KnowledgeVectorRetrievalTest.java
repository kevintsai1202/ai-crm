package com.aicrm.crm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量檢索整合測試：寫入已知向量後，searchTopK 依 cosine 距離回傳最近鄰。
 */
class KnowledgeVectorRetrievalTest extends PostgresTestBase {

    @Autowired KnowledgeVectorRepository vectorRepo;
    @Autowired JdbcTemplate jdbc;

    /** 建立第 hot 維為 1、其餘為 0 的正交單位向量。 */
    private float[] unit(int dim, int hot) {
        var v = new float[dim];
        v[hot] = 1.0f;
        return v;
    }

    @Test
    void searchTopK_returnsNearestByCosine() {
        // 取 seed 三筆文件 id，分別賦予彼此正交的單位向量
        var ids = jdbc.queryForList("select id from knowledge_documents order by id", Long.class);
        assertThat(ids).hasSizeGreaterThanOrEqualTo(3);
        vectorRepo.updateEmbedding(ids.get(0), unit(1024, 0));
        vectorRepo.updateEmbedding(ids.get(1), unit(1024, 1));
        vectorRepo.updateEmbedding(ids.get(2), unit(1024, 2));

        // 查詢向量貼近第 2 筆（hot=1）
        var hits = vectorRepo.searchTopK(unit(1024, 1), 3);
        assertThat(hits).hasSize(3);
        // 最近鄰相似度應≈1，且排第一
        assertThat(hits.get(0).similarity().doubleValue()).isGreaterThan(0.9);
    }
}
