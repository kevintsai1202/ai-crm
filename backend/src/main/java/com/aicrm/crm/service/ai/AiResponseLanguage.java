package com.aicrm.crm.service.ai;

/**
 * 依前端傳入的語系碼，統一產生 LLM 回覆語言指示。
 *
 * <p>設計：語言控制以 {@link #systemLanguage(String)} 放在 <b>system prompt 尾端</b>為主
 * （system 層級指令對回覆語言最具約束力）；各服務的 {@code SYSTEM_PROMPT} 常數不再寫死
 * 「使用繁體中文」，改由本工具依語系補上，確保繁中預設行為不變、英文時能真正切換。
 * {@link #directive(String)} 為輔助，接在動態 user prompt 尾端再次強調（僅英文時非空），
 * 對「JSON 文字欄位」等易漂移情境多一層保險。</p>
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
     * system prompt 尾端的語言指示（中英皆非空，為回覆語言的主要約束）。
     *
     * @param lang 前端語系碼（如 "en"、"zh-TW"，可為 null）
     * @return 英文時為強制英文指示；繁中或未指定時為繁中指示
     */
    public static String systemLanguage(String lang) {
        if (isEnglish(lang)) {
            return "\n\nIMPORTANT: Respond ONLY in English. Even though the customer data, "
                    + "knowledge base and other context may be written in Chinese, your entire "
                    + "reply — including every field, heading and bullet — must be written in English.";
        }
        return "\n\n請務必全程使用繁體中文回答。";
    }

    /**
     * user prompt 尾端的輔助語言指示（僅英文時非空，多一層強調）。
     *
     * @param lang 前端語系碼（如 "en"、"zh-TW"，可為 null）
     * @return 英文時為強制英文的指示字串；繁中或未指定時為空字串
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
