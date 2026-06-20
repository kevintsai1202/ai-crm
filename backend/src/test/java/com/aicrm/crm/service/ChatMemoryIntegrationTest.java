package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.repository.ChatMessageRepository;
import com.aicrm.crm.repository.ChatMessageVectorRepository;
import com.aicrm.crm.service.embedding.DeterministicEmbedding;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 對話記憶整合測試：對同一客戶連續兩次 chat，驗證訊息落庫（user+assistant）且帶 embedding 可被語意檢索。
 */
class ChatMemoryIntegrationTest extends PostgresTestBase {

    @Autowired InsightService insightService;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired ChatMessageVectorRepository chatMessageVectorRepository;

    @Test
    void twoTurnsPersistMessagesWithEmbedding() {
        // 連續兩輪對話（同一客戶 id=1，seed 資料）
        insightService.chat(new Dtos.ChatRequest(1L, "第一個問題：續約計畫"));
        insightService.chat(new Dtos.ChatRequest(1L, "第二個問題：續約的下一步"));

        // 2 輪 × (user + assistant) = 該客戶至少 4 筆落庫（scoped by customer，不受其他測試干擾）
        assertThat(chatMessageRepository.findTop6ByCustomerIdOrderByCreatedAtDesc(1L)).hasSizeGreaterThanOrEqualTo(4);

        // 訊息帶 embedding：用非零向量做 scoped 語意檢索應回非空（全零向量 cosine 為 NaN，無意義）
        var queryVec = DeterministicEmbedding.embed("續約", 1024);
        var hits = chatMessageVectorRepository.searchTopK(1L, queryVec, 5);
        assertThat(hits).isNotEmpty();
        // 命中項保留角色資訊（USER / ASSISTANT）
        assertThat(hits.get(0).role()).isIn("USER", "ASSISTANT");
    }
}
