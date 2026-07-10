package com.aicrm.crm.repository;

import com.aicrm.crm.api.Dtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 知識 chunk 向量存取：寫入／刪除 chunk、cosine 近鄰檢索（join 文件標題）。
 */
@Repository
public class KnowledgeChunkVectorRepository {

    private final JdbcTemplate jdbc;

    public KnowledgeChunkVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 刪除指定文件的所有 chunk（重建前呼叫）。
     *
     * @param documentId 文件 id
     */
    public void deleteByDocumentId(long documentId) {
        jdbc.update("delete from knowledge_chunks where document_id = ?", documentId);
    }

    /**
     * 插入一筆 chunk（含 embedding）。
     *
     * @param documentId 文件 id
     * @param chunkIndex 段序（自 0）
     * @param content 片段內容
     * @param vec 向量
     */
    public void insertChunk(long documentId, int chunkIndex, String content, float[] vec) {
        jdbc.update(
                "insert into knowledge_chunks (document_id, chunk_index, content, embedding) "
                        + "values (?, ?, ?, cast(? as vector))",
                documentId, chunkIndex, content, toVectorLiteral(vec));
    }

    /**
     * 尚無任何 chunk 的文件（id + content），供啟動補索引。
     *
     * @return [id, content]
     */
    public List<Object[]> findDocumentsMissingChunks() {
        return jdbc.query(
                "select d.id, d.content from knowledge_documents d "
                        + "where not exists (select 1 from knowledge_chunks c where c.document_id = d.id)",
                (rs, n) -> new Object[]{rs.getLong("id"), rs.getString("content")});
    }

    /**
     * 全部文件（id + content），供強制 reindex。
     *
     * @return [id, content]
     */
    public List<Object[]> findAllDocumentsForChunkIndex() {
        return jdbc.query(
                "select id, content from knowledge_documents",
                (rs, n) -> new Object[]{rs.getLong("id"), rs.getString("content")});
    }

    /**
     * chunk 級 cosine top-K；content 為片段，title/doc_type 來自文件。
     *
     * @param queryVec 查詢向量
     * @param k 筆數
     * @return 引用列表
     */
    public List<Dtos.CitationResponse> searchTopK(float[] queryVec, int k) {
        var literal = toVectorLiteral(queryVec);
        return jdbc.query(
                "select d.doc_type, d.title, c.content, "
                        + "1 - (c.embedding <=> cast(? as vector)) as similarity "
                        + "from knowledge_chunks c "
                        + "join knowledge_documents d on d.id = c.document_id "
                        + "where c.embedding is not null "
                        + "order by c.embedding <=> cast(? as vector) limit ?",
                (rs, n) -> new Dtos.CitationResponse(
                        rs.getString("title"),
                        rs.getString("doc_type"),
                        rs.getString("content"),
                        BigDecimal.valueOf(rs.getDouble("similarity")).setScale(4, RoundingMode.HALF_UP)),
                literal, literal, k);
    }

    /** 將 float[] 轉為 pgvector 字面值。 */
    private String toVectorLiteral(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
