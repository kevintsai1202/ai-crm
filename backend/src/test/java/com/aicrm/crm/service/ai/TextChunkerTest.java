package com.aicrm.crm.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * TextChunker 單元測試。
 */
class TextChunkerTest {

    @Test
    void blankYieldsEmpty() {
        assertThat(TextChunker.chunk(null)).isEmpty();
        assertThat(TextChunker.chunk("   ")).isEmpty();
    }

    @Test
    void shortTextSingleChunk() {
        assertThat(TextChunker.chunk("短文一段")).containsExactly("短文一段");
    }

    @Test
    void longTextMultipleWithOverlap() {
        var text = "A".repeat(1000);
        var parts = TextChunker.chunk(text, 600, 80);
        assertThat(parts.size()).isGreaterThan(1);
        assertThat(parts.get(0)).hasSize(600);
        // 第二段開頭應與第一段尾端重疊 80 字
        assertThat(parts.get(1).substring(0, 80)).isEqualTo(parts.get(0).substring(520));
    }
}
