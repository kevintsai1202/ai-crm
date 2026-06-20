package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * PiiMasker 單元測試：驗證 email / 電話 / 統編被遮罩，一般業務文字不變，null/空字串安全回傳。
 */
class PiiMaskerTest {

    @Test
    void mask_email_isReplaced() {
        var result = PiiMasker.mask("聯絡窗口 claire.wang@starmill.example 已回覆");
        assertThat(result).doesNotContain("claire.wang@starmill.example");
        assertThat(result).contains("[已遮罩EMAIL]");
    }

    @Test
    void mask_phone_isReplaced() {
        var result = PiiMasker.mask("客戶電話 0911000001 請回撥");
        assertThat(result).doesNotContain("0911000001");
        assertThat(result).contains("[已遮罩電話]");
    }

    @Test
    void mask_taxId_isReplaced() {
        var result = PiiMasker.mask("統編 12345678 已驗證");
        assertThat(result).doesNotContain("12345678");
        assertThat(result).contains("[已遮罩統編]");
    }

    @Test
    void mask_normalBusinessText_unchanged() {
        var text = "客戶表示今年智慧工廠擴線順利，願意評估增加授權數量。";
        assertThat(PiiMasker.mask(text)).isEqualTo(text);
    }

    @Test
    void mask_nullAndBlank_returnedAsIs() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("")).isEqualTo("");
        assertThat(PiiMasker.mask("   ")).isEqualTo("   ");
    }

    @Test
    void mask_mixedPii_allReplaced() {
        var result = PiiMasker.mask("窗口 ops@haiyue.example 電話 0911000002 統編 22345678");
        assertThat(result).contains("[已遮罩EMAIL]", "[已遮罩電話]", "[已遮罩統編]");
        assertThat(result).doesNotContain("ops@haiyue.example", "0911000002", "22345678");
    }
}
