package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * RAG 相關性整合測試：注入語意可控的 fake embedding，驗證引用會隨提問語意改變。
 */
class RagRelevanceTest extends PostgresTestBase {

    /** 語意可控的 fake：依關鍵字落到正交基底，讓檢索可預期。 */
    @TestConfiguration
    static class FakeEmbeddingConfig {
        // bean 名稱與真實 @Primary 的 voyageEmbeddingClient 相同，於測試 profile 啟用同名覆寫
        @Bean("voyageEmbeddingClient")
        @Primary
        EmbeddingClient fakeEmbeddingClient() {
            return new EmbeddingClient() {
                @Override public int dimension() { return 1024; }
                @Override public List<float[]> embed(List<String> texts, InputType type) {
                    return texts.stream().map(this::vec).toList();
                }
                private float[] vec(String t) {
                    var v = new float[1024];
                    if (t.contains("續約")) v[0] = 1f;
                    else if (t.contains("條款") || t.contains("服務")) v[1] = 1f;
                    else if (t.contains("巡檢") || t.contains("手機")) v[2] = 1f;
                    else v[3] = 1f;
                    return v;
                }
            };
        }
    }

    @Autowired InsightService insightService;
    @Autowired com.aicrm.crm.service.KnowledgeIndexer knowledgeIndexer;

    @BeforeEach
    void reindexWithFake() {
        knowledgeIndexer.reindexAll(); // 用 fake 重建 seed 文件向量
    }

    @Test
    void citationsFollowQuerySemantics() {
        var resp = insightService.chat(new Dtos.ChatRequest(1L, "請分析這位客戶的續約風險"));
        assertThat(resp.citations()).isNotEmpty();
        // 續約查詢的 top1 應為續約話術文件
        assertThat(resp.citations().get(0).title()).contains("續約");
    }
}
