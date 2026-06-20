package com.aicrm.crm.service.embedding;

import java.util.List;

/**
 * 向量嵌入用戶端：將文字轉為固定維度向量，供知識庫 RAG 檢索使用。
 */
public interface EmbeddingClient {

    /** 嵌入輸入型別：查詢或文件（部分模型會據此最佳化）。 */
    enum InputType { QUERY, DOCUMENT }

    /**
     * 將多段文字嵌入為向量。
     *
     * @param texts 文字清單
     * @param type 輸入型別
     * @return 與輸入等長的向量清單（每個 float[] 長度為 dimension()）
     */
    List<float[]> embed(List<String> texts, InputType type);

    /** 向量維度。 */
    int dimension();
}
