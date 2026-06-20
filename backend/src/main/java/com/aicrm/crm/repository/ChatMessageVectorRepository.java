package com.aicrm.crm.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 對話訊息向量存取：以 JdbcTemplate 操作 pgvector 欄位（寫入含 embedding 的訊息、scoped by customer 的 cosine 檢索）。
 * 函式級註解：embedding 不映進 JPA 實體，集中於此用 native SQL + cast(? as vector) 處理（同 KnowledgeVectorRepository）。
 */
@Repository
public class ChatMessageVectorRepository {

    private final JdbcTemplate jdbc;

    public ChatMessageVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 寫入一則對話訊息（含 embedding），回傳新生成的主鍵 id。
     *
     * @param customerId 客戶 id
     * @param role 角色字串（USER / ASSISTANT）
     * @param content 訊息內容
     * @param vec embedding 向量
     * @param createdAt 建立時間
     * @return 新訊息 id
     */
    public long save(Long customerId, String role, String content, float[] vec, OffsetDateTime createdAt) {
        var sql = "insert into chat_messages (customer_id, role, content, embedding, created_at) "
                + "values (?, ?, ?, cast(? as vector), ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            // 指定回傳的 key 欄位為 id，避免不同驅動回傳整列造成轉型問題
            var ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, customerId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setString(4, toVectorLiteral(vec));
            ps.setObject(5, createdAt);
            return ps;
        }, keyHolder);
        var key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    /**
     * 在指定客戶範圍內做 cosine 近鄰檢索 top-K，回傳訊息（含角色，依相似度高到低）。
     *
     * @param customerId 客戶 id
     * @param queryVec 查詢向量
     * @param k 取回筆數
     * @return 訊息清單（角色 + 內容）
     */
    public List<Hit> searchTopK(Long customerId, float[] queryVec, int k) {
        return jdbc.query(
                "select role, content from chat_messages "
                        + "where customer_id = ? and embedding is not null "
                        + "order by embedding <=> cast(? as vector) limit ?",
                (rs, n) -> new Hit(rs.getString("role"), rs.getString("content")),
                customerId, toVectorLiteral(queryVec), k);
    }

    /**
     * 語意檢索命中項：保留角色資訊，讓注入記憶能標示 USER/ASSISTANT。
     *
     * @param role 角色（USER / ASSISTANT）
     * @param content 訊息內容
     */
    public record Hit(String role, String content) {}

    /** 將 float[] 轉為 pgvector 字面值 "[v1,v2,...]"。 */
    private String toVectorLiteral(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
