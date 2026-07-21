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

    /** 繁中與 null 應回傳空字串（維持既有以繁體中文為預設的行為，prompt 不變）。 */
    @Test
    void chineseOrNull_producesEmpty() {
        assertThat(AiResponseLanguage.directive("zh-TW")).isEmpty();
        assertThat(AiResponseLanguage.directive(null)).isEmpty();
        assertThat(AiResponseLanguage.directive("  ")).isEmpty();
    }
}
