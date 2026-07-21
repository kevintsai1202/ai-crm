package com.aicrm.crm.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link AiResponseLanguage} 單元測試：驗證語言指示依前端語系碼正確產生，
 * 且繁中／未指定時維持「空字串」以保留既有繁中預設行為。純函式，不需容器。
 */
class AiResponseLanguageTest {

    /** 英文語系（en / en-US）應產生非空的英文輸出指示。 */
    @Test
    void english_producesEnglishDirective() {
        assertThat(AiResponseLanguage.directive("en"))
                .contains("English")
                .contains("[OUTPUT LANGUAGE]");
        assertThat(AiResponseLanguage.directive("en-US")).contains("English");
    }

    /** 繁中與 null 的 user 尾端輔助指示應為空字串（英文才需額外強調）。 */
    @Test
    void chineseOrNull_producesEmpty() {
        assertThat(AiResponseLanguage.directive("zh-TW")).isEmpty();
        assertThat(AiResponseLanguage.directive(null)).isEmpty();
        assertThat(AiResponseLanguage.directive("  ")).isEmpty();
    }

    /** systemLanguage 為主要語言約束：英文回英文指示、繁中/null 回繁中指示，皆非空。 */
    @Test
    void systemLanguage_bindsResponseLanguage() {
        assertThat(AiResponseLanguage.systemLanguage("en")).contains("English").contains("ONLY");
        assertThat(AiResponseLanguage.systemLanguage("en-US")).contains("English");
        assertThat(AiResponseLanguage.systemLanguage("zh-TW")).contains("繁體中文");
        assertThat(AiResponseLanguage.systemLanguage(null)).contains("繁體中文");
    }
}
