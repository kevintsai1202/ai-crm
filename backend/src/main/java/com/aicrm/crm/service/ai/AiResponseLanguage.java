package com.aicrm.crm.service.ai;

/**
 * 依前端傳入的語系碼，產生要 append 到 LLM user prompt 尾端的「輸出語言指示」。
 *
 * <p>設計取捨：不改動各服務既有的 system prompt 常數（其內已含繁體中文語氣約束），
 * 僅在動態組出的 user prompt 尾端附加一段語言指示；繁中或未指定時回傳空字串，
 * 使既有以繁體中文為預設的行為完全不變。英文時回傳「強指示」，明確覆蓋 grounding
 * 與 system prompt 中殘留的繁中要求，確保整段回覆（含 JSON 文字欄位）皆為英文。</p>
 */
public final class AiResponseLanguage {

    private AiResponseLanguage() {
        // 工具類別，禁止實例化
    }

    /** 英文語系判定：以 "en" 開頭（如 en、en-US）視為英文；其餘（含 null）視為繁中預設。 */
    private static boolean isEnglish(String lang) {
        return lang != null && lang.trim().toLowerCase().startsWith("en");
    }

    /**
     * 產生要接到 user prompt 尾端的語言指示。
     *
     * @param lang 前端語系碼（如 "en"、"zh-TW"，可為 null）
     * @return 英文時為強制英文的指示字串；繁中或未指定時為空字串（維持繁中預設）
     */
    public static String directive(String lang) {
        if (isEnglish(lang)) {
            return "\n\n[OUTPUT LANGUAGE] Write your entire response in English only, "
                    + "including every text field (for example name and rationale). "
                    + "Ignore any instruction above that asks you to answer in Traditional Chinese (繁體中文).";
        }
        return "";
    }
}
