package com.aicrm.crm.service.embedding;

import java.nio.charset.StandardCharsets;

/**
 * 確定性嵌入 fallback：無外部金鑰時，用穩定雜湊把文字散佈到固定維度並 L2 正規化。
 * 函式級註解：相同輸入恆得相同向量，讓無金鑰 demo / 測試流程完整（相似度非語意級）。
 */
public final class DeterministicEmbedding {

    private DeterministicEmbedding() {
    }

    /**
     * 產生確定性向量。
     *
     * @param text 文字
     * @param dimension 維度
     * @return 已 L2 正規化的向量
     */
    public static float[] embed(String text, int dimension) {
        var vec = new float[dimension];
        if (text == null || text.isBlank()) {
            return vec; // 全零向量
        }
        // 以每個 token 的雜湊決定 bucket 與正負號，累加後正規化
        for (String token : text.toLowerCase().split("\\s+|(?<=\\p{IsHan})")) {
            if (token.isBlank()) continue;
            int h = stableHash(token);
            int bucket = Math.floorMod(h, dimension);
            vec[bucket] += (h & 1) == 0 ? 1.0f : -1.0f;
        }
        double norm = 0;
        for (float v : vec) norm += (double) v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) vec[i] /= (float) norm;
        }
        return vec;
    }

    /** 穩定雜湊（不依賴 String.hashCode 的 JVM 差異，用 FNV-1a）。 */
    private static int stableHash(String s) {
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
