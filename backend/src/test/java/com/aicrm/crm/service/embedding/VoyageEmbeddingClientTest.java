package com.aicrm.crm.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VoyageEmbeddingClient 單元測試：無金鑰時走 deterministic fallback，回正確維度且不需網路。
 */
class VoyageEmbeddingClientTest {

    @Test
    void withoutApiKey_fallsBackDeterministically() {
        var client = new VoyageEmbeddingClient("", "voyage-4-lite",
                "https://api.voyageai.com/v1/embeddings", 1024);
        var vecs = client.embed(List.of("續約風險話術", "企業服務條款"), EmbeddingClient.InputType.DOCUMENT);
        assertThat(vecs).hasSize(2);
        assertThat(vecs.get(0)).hasSize(1024);
        // 與 DeterministicEmbedding 一致（確定性）
        assertThat(vecs.get(0)).containsExactly(DeterministicEmbedding.embed("續約風險話術", 1024));
    }

    @Test
    void dimensionReportedFromConfig() {
        var client = new VoyageEmbeddingClient("", "voyage-4-lite", "http://x", 1024);
        assertThat(client.dimension()).isEqualTo(1024);
    }
}
