import { useTranslation } from "react-i18next";
import { SUPPORTED_LANGS } from "../../i18n/detect";

/**
 * 語言切換下拉：切換 i18next 語言，變更會由 languageDetector 自動寫回 localStorage(ai-crm-lang)。
 * 用原生 <select> 以確保無障礙且不引入額外樣式負擔。
 * @param className 供外層版面調整用的樣式類別
 */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { t, i18n } = useTranslation();
  return (
    <select
      className={className}
      aria-label={t("lang.label")}
      value={i18n.resolvedLanguage}
      onChange={(event) => {
        void i18n.changeLanguage(event.target.value);
      }}
    >
      {SUPPORTED_LANGS.map((lng) => (
        <option key={lng} value={lng}>
          {t(`lang.${lng}`)}
        </option>
      ))}
    </select>
  );
}
