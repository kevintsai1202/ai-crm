package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallLog;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.repository.KnowledgeDocumentRepository;
import com.aicrm.crm.repository.KnowledgeVectorRepository;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * InsightService AI 可切換策略測試：空金鑰時走 deterministic fallback，不呼叫 ChatModel。
 */
class InsightServiceFallbackTest {

    @SuppressWarnings("unchecked")
    @Test
    void chat_withoutApiKey_usesDeterministicFallback() {
        var customerService = mock(CustomerService.class);
        var knowledge = mock(KnowledgeDocumentRepository.class);
        var embeddingClient = mock(EmbeddingClient.class);
        var vectorRepo = mock(KnowledgeVectorRepository.class);
        var provider = (ObjectProvider<ChatModel>) mock(ObjectProvider.class);
        var governance = mock(AiGovernanceService.class);
        // fallback 路徑會寫 ai_call_log，回傳含 id 的紀錄供 callId 使用
        when(governance.record(any(AiCallType.class), any(), any(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new AiCallLog(1L, AiCallType.CHAT, null, 0, 0, 0, false, true, "x"));

        var customer = new Customer("艾克玫", "a@b.c", "0912345678", "12345678", "雲端服務", "業務A");
        when(customerService.findDetail(1L)).thenReturn(customer);
        when(knowledge.findTop3ByOrderBySimilarityHintDesc()).thenReturn(List.of());
        // 向量檢索回空，使 loadCitations graceful fallback 至 similarityHint（此處亦為空）
        when(embeddingClient.embed(List.of("請評估這位客戶"), EmbeddingClient.InputType.QUERY))
                .thenReturn(List.of(new float[1024]));
        when(vectorRepo.searchTopK(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(3)))
                .thenReturn(List.of());

        var chatMemory = mock(ChatMemoryService.class); // recall 預設回 null/空、save 不動作，不影響 fallback 行為
        var service = new InsightService(customerService, knowledge, embeddingClient, vectorRepo, provider, governance, chatMemory, ""); // 空金鑰 → aiEnabled=false
        var response = service.chat(new Dtos.ChatRequest(1L, "請評估這位客戶"));

        assertThat(response.answer()).contains("艾克玫");          // deterministic 內容含客戶名
        assertThat(response.answer()).contains("業務A");
        assertThat(response.risk()).isNotNull();
        verifyNoInteractions(provider);                           // 空金鑰時根本不取 ChatModel
    }
}
