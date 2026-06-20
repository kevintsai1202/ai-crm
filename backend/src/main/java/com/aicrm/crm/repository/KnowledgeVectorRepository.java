package com.aicrm.crm.repository;

import com.aicrm.crm.api.Dtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 知識文件向量存取：以 JdbcTemplate 操作 pgvector 欄位（寫入 embedding、cosine 近鄰檢索）。
 * 函式級註解：embedding 不映進 JPA 實體，集中於此用 native SQL + cast(? as vector) 處理。
 */
@Repository
public class KnowledgeVectorRepository {

    private final JdbcTemplate jdbc;

    public KnowledgeVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 寫入指定文件的 embedding。
     *
     * @param id 文件 id
     * @param vec 向量
     */
    public void updateEmbedding(long id, float[] vec) {
        jdbc.update("update knowledge_documents set embedding = cast(? as vector) where id = ?",
                toVectorLiteral(vec), id);
    }

    /**
     * 取回尚未建立 embedding 的文件（id 與 content）。
     *
     * @return [id(Long), content(String)] 清單
     */
    public List<Object[]> findMissingEmbedding() {
        return jdbc.query(
                "select id, content from knowledge_documents where embedding is null",
                (rs, n) -> new Object[]{rs.getLong("id"), rs.getString("content")});
    }

    /**
     * 取回全部文件（id 與 content），供強制重建索引。
     *
     * @return [id(Long), content(String)] 清單
     */
    public List<Object[]> findAllForIndex() {
        return jdbc.query(
                "select id, content from knowledge_documents",
                (rs, n) -> new Object[]{rs.getLong("id"), rs.getString("content")});
    }

    /**
     * cosine 近鄰檢索 top-K，回傳引用（similarity = 1 - cosine_distance）。
     *
     * @param queryVec 查詢向量
     * @param k 取回筆數
     * @return 引用清單（依相似度高到低）
     */
    public List<Dtos.CitationResponse> searchTopK(float[] queryVec, int k) {
        var literal = toVectorLiteral(queryVec);
        return jdbc.query(
                "select doc_type, title, content, 1 - (embedding <=> cast(? as vector)) as similarity "
                        + "from knowledge_documents where embedding is not null "
                        + "order by embedding <=> cast(? as vector) limit ?",
                (rs, n) -> new Dtos.CitationResponse(
                        rs.getString("title"),
                        rs.getString("doc_type"),
                        rs.getString("content"),
                        BigDecimal.valueOf(rs.getDouble("similarity")).setScale(4, RoundingMode.HALF_UP)),
                literal, literal, k);
    }

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
