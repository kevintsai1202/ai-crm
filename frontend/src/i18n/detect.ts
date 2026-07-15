/** 前端支援的語言碼。 */
export type SupportedLang = "en" | "zh-TW";

/** 支援語言清單（供 detector、切換元件、初始化共用）。 */
export const SUPPORTED_LANGS: SupportedLang[] = ["en", "zh-TW"];

/** fallback / 預設主語言。 */
export const FALLBACK_LANG: SupportedLang = "en";

/**
 * 決定初始語言：優先採用已儲存的合法選擇，否則依瀏覽器語系（zh* → zh-TW，其餘 → en）。
 * 與 i18next-browser-languagedetector 的行為對齊，並可獨立單元測試。
 * @param browserLang 瀏覽器語系字串（如 navigator.language）
 * @param stored localStorage 已存語言（可能為 null 或非法值）
 */
export function detectLanguage(browserLang: string | undefined, stored: string | null): SupportedLang {
  // 已存合法選擇最優先
  if (stored === "en" || stored === "zh-TW") return stored;
  // 依瀏覽器語系判斷：中文各變體一律對映繁中，其餘皆英文
  const lang = (browserLang ?? "").toLowerCase();
  if (lang.startsWith("zh")) return "zh-TW";
  return FALLBACK_LANG;
}
