package com.aicrm.crm.service.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 知識庫文字切段器：依字元視窗切 chunk，供 embedding 與 RAG 檢索。
 */
public final class TextChunker {

    /** 預設 chunk 目標長度（中文字元約等）。 */
    public static final int DEFAULT_SIZE = 600;

    /** 預設重疊長度。 */
    public static final int DEFAULT_OVERLAP = 80;

    /** 短於此時仍產生單一 chunk。 */
    public static final int MIN_SINGLE_CHUNK = 1;

    private TextChunker() {
    }

    /**
     * 以預設 size/overlap 切段。
     *
     * @param text 原文
     * @return 非空 chunk 列表（原文空白則空列表）
     */
    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 依 size 與 overlap 切段。
     *
     * @param text 原文
     * @param size 視窗大小（≥1）
     * @param overlap 重疊（0 ≤ overlap &lt; size）
     * @return chunk 列表
     */
    public static List<String> chunk(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var normalized = text.trim();
        int s = Math.max(1, size);
        int o = Math.max(0, Math.min(overlap, s - 1));
        if (normalized.length() <= s) {
            return List.of(normalized);
        }
        List<String> parts = new ArrayList<>();
        int step = Math.max(1, s - o);
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(normalized.length(), start + s);
            parts.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
        }
        return parts;
    }
}
