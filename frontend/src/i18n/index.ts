import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import en from "./locales/en/common.json";
import zhTW from "./locales/zh-TW/common.json";
import { SUPPORTED_LANGS, FALLBACK_LANG } from "./detect";

/**
 * i18next 全域初始化（整個 app 只在此執行一次，由 main.tsx import 觸發）。
 * 資源以靜態 import 打包，無非同步載入，故關閉 Suspense 以免需要額外 Suspense 邊界。
 */
void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: en },
      "zh-TW": { common: zhTW }
    },
    fallbackLng: FALLBACK_LANG,
    supportedLngs: SUPPORTED_LANGS,
    defaultNS: "common",
    interpolation: { escapeValue: false }, // React 已防 XSS
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: "ai-crm-lang",
      caches: ["localStorage"]
    },
    react: { useSuspense: false }
  });

export default i18n;
