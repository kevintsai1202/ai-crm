package com.aicrm.crm.service;

import java.util.regex.Pattern;

/**
 * PII 遮罩工具：送 LLM 前遮蔽 email / 電話 / 統編，降低個資外洩風險。
 */
public final class PiiMasker {

    /** Email 樣式（使用者名稱@網域）。 */
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

    /** 電話樣式：台灣手機（09xx）與市話（0x-xxxxxxx）。 */
    private static final Pattern PHONE = Pattern.compile("09\\d{2}[-\\s]?\\d{3}[-\\s]?\\d{3}|0\\d{1,2}-?\\d{6,8}");

    /** 統一編號樣式：獨立的 8 位數字（前後不接其他數字）。 */
    private static final Pattern TAX_ID = Pattern.compile("(?<!\\d)\\d{8}(?!\\d)");

    private PiiMasker() {
    }

    /**
     * 遮罩單一字串中的 email / 電話 / 統編。
     *
     * @param text 原始字串（可為 null）
     * @return 遮罩後字串；null 或空白原樣回傳
     */
    public static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // 先遮 email（含 @），再遮電話，最後遮統編，避免電話片段被誤判為統編
        String r = EMAIL.matcher(text).replaceAll("[已遮罩EMAIL]");
        r = PHONE.matcher(r).replaceAll("[已遮罩電話]");
        r = TAX_ID.matcher(r).replaceAll("[已遮罩統編]");
        return r;
    }
}
