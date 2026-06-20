package com.aicrm.crm.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * DeterministicEmbedding 單元測試：維度、確定性、正規化。
 */
class DeterministicEmbeddingTest {

    @Test
    void producesRequestedDimension() {
        assertThat(DeterministicEmbedding.embed("續約風險話術", 1024)).hasSize(1024);
    }

    @Test
    void sameInputSameVector() {
        var a = DeterministicEmbedding.embed("企業服務條款", 1024);
        var b = DeterministicEmbedding.embed("企業服務條款", 1024);
        assertThat(a).containsExactly(b);
    }

    @Test
    void isL2Normalised() {
        var v = DeterministicEmbedding.embed("智慧手機巡檢方案", 1024);
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void differentInputsDiffer() {
        var a = DeterministicEmbedding.embed("續約", 1024);
        var b = DeterministicEmbedding.embed("巡檢方案保固", 1024);
        assertThat(a).isNotEqualTo(b);
    }
}
