package com.aicrm.crm.service;

import com.aicrm.crm.domain.ChatMessage;
import com.aicrm.crm.domain.ChatRole;
import com.aicrm.crm.repository.ChatMessageRepository;
import com.aicrm.crm.repository.ChatMessageVectorRepository;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 對話記憶服務：對話歷史落庫並以「時序 + 語意向量」雙路召回。
 *
 * <p>函式級註解：保存採 {@code REQUIRES_NEW} 可寫交易，與 {@link InsightService} 的 readOnly 交易隔離。
 * 若僅用預設 REQUIRED 傳播，從 InsightService 的 readOnly 交易呼叫時會「加入」該唯讀交易，
 * 導致 INSERT 失敗（SQLSTATE 25006）；故 save 強制開啟全新可寫交易。</p>
 */
@Service
public class ChatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryService.class);

    /** 時序召回則數（約 3 輪對話）。 */
    private static final int RECENT_LIMIT = 6;

    /** 語意相似召回則數。 */
    private static final int SEMANTIC_LIMIT = 3;

    /** 每則記憶截斷上限（字元），避免 prompt 膨脹。 */
    private static final int MAX_CONTENT_LEN = 200;

    /** 向量嵌入用戶端。 */
    private final EmbeddingClient embeddingClient;

    /** 對話訊息向量存取（寫入 + 語意檢索）。 */
    private final ChatMessageVectorRepository vectorRepo;

    /** 對話訊息 JPA 存取（時序查詢）。 */
    private final ChatMessageRepository messageRepo;

    public ChatMemoryService(EmbeddingClient embeddingClient,
                             ChatMessageVectorRepository vectorRepo,
                             ChatMessageRepository messageRepo) {
        this.embeddingClient = embeddingClient;
        this.vectorRepo = vectorRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * 保存一則對話訊息（先嵌入為 DOCUMENT 向量再落庫）。
     *
     * @param customerId 客戶 id
     * @param role 角色
     * @param content 內容
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long customerId, ChatRole role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        var vec = embeddingClient.embed(List.of(content), EmbeddingClient.InputType.DOCUMENT).get(0);
        vectorRepo.save(customerId, role.name(), content, vec, OffsetDateTime.now());
    }

    /**
     * 召回對話記憶：取「最近 6 則（時序，舊→新）」+「語意相似 3 則（向量檢索）」，去重、各截斷後組成
     * 「# 對話記憶」Markdown 區塊；無任何記憶則回空字串。
     *
     * @param customerId 客戶 id
     * @param query 本輪查詢文字（用於語意檢索；不含本則記憶）
     * @return 對話記憶 Markdown 區塊，或空字串
     */
    @Transactional(readOnly = true)
    public String recall(Long customerId, String query) {
        // 1. 時序：最近 6 則（DB 回新到舊，反轉為舊到新更符合對話閱讀順序）
        var recent = messageRepo.findTop6ByCustomerIdOrderByCreatedAtDesc(customerId);
        var ordered = new ArrayList<>(recent);
        java.util.Collections.reverse(ordered);

        // 用 LinkedHashSet 保留插入順序並去重（先時序、後語意）
        Set<String> lines = new LinkedHashSet<>();
        for (ChatMessage m : ordered) {
            lines.add(format(m.getRole().name(), m.getContent()));
        }

        // 2. 語意：相似 3 則（檢索失敗不致命，記憶仍可用時序部分）
        try {
            var vec = embeddingClient.embed(List.of(query), EmbeddingClient.InputType.QUERY).get(0);
            for (var hit : vectorRepo.searchTopK(customerId, vec, SEMANTIC_LIMIT)) {
                lines.add(format(hit.role(), hit.content()));
            }
        } catch (Exception e) {
            log.warn("對話記憶語意檢索失敗，僅用時序記憶：{}", e.getMessage());
        }

        if (lines.isEmpty()) {
            return "";
        }
        return "# 對話記憶（最近 " + RECENT_LIMIT + " 則 + 語意相似 " + SEMANTIC_LIMIT + " 則）\n"
                + String.join("\n", lines);
    }

    /**
     * 格式化單則記憶為清單項，並截斷過長內容。
     *
     * @param role 角色名稱（語意檢索無角色時可為 null）
     * @param content 內容
     * @return 格式化字串
     */
    private String format(String role, String content) {
        var truncated = content.length() > MAX_CONTENT_LEN
                ? content.substring(0, MAX_CONTENT_LEN) + "…"
                : content;
        return role == null ? "- " + truncated : "- [" + role + "] " + truncated;
    }
}
