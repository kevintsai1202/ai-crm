import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import en from "./locales/en/common.json";
import zhTW from "./locales/zh-TW/common.json";
import enDashboard from "./locales/en/dashboard.json";
import zhTWDashboard from "./locales/zh-TW/dashboard.json";
import { SUPPORTED_LANGS, FALLBACK_LANG, detectLanguage } from "./detect";

/** localStorage 儲存語言選擇的 key（與 detect/切換元件一致）。 */
const LANG_STORAGE_KEY = "ai-crm-lang";

/**
 * 自訂語言偵測器：以 detectLanguage 純函式決定初始語言，並負責讀寫 localStorage，
 * 使 runtime 偵測行為與 detect.ts 的單元測試一致（zh* → zh-TW，其餘 → en）。
 */
const languageDetector = new LanguageDetector();
languageDetector.addDetector({
  name: "aiCrmDetector",
  lookup() {
    // 讀取已儲存的語言選擇；私密模式等環境可能拋出例外，失敗則視為未儲存
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(LANG_STORAGE_KEY);
    } catch {
      stored = null;
    }
    const browserLang = typeof navigator !== "undefined" ? navigator.language : undefined;
    return detectLanguage(browserLang, stored);
  },
  cacheUserLanguage(lng: string) {
    // 切換語言時寫回 localStorage，供下次載入時優先採用
    try {
      localStorage.setItem(LANG_STORAGE_KEY, lng);
    } catch {
      /* 忽略儲存失敗（例如無痕模式限制） */
    }
  }
});

/**
 * i18next 全域初始化（整個 app 只在此執行一次，由 main.tsx import 觸發）。
 * 資源以靜態 import 打包，無非同步載入，故關閉 Suspense 以免需要額外 Suspense 邊界。
 */
void i18n
  .use(languageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: en, dashboard: enDashboard },
      "zh-TW": { common: zhTW, dashboard: zhTWDashboard }
    },
    fallbackLng: FALLBACK_LANG,
    supportedLngs: SUPPORTED_LANGS,
    defaultNS: "common",
    interpolation: { escapeValue: false }, // React 已防 XSS
    detection: {
      order: ["aiCrmDetector"],
      caches: ["aiCrmDetector"]
    },
    react: { useSuspense: false }
  });

export default i18n;
