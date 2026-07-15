import type { TFunction } from "i18next";
import type { UsageSummaryResponse } from "../../../types";
import { AiBadge } from "../../../components/common/AiBadge";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * AI 用量治理卡（SP4，MANAGER/ADMIN 可見）：呈現呼叫次數、token 用量、真實/fallback 比、採納/拒絕統計。
 */
function UsageCard({ usage, t, locale }: { usage: UsageSummaryResponse; t: TFunction; locale: string }) {
  const cells: [string, string | number][] = [
    [t("dashboard:usage.totalCalls"), usage.totalCalls],
    [t("dashboard:usage.totalTokens"), usage.totalTokens.toLocaleString(locale)],
    [t("dashboard:usage.realVsFallback"), `${usage.realCalls} / ${usage.fallbackCalls}`],
    [t("dashboard:usage.adopted"), usage.adopted],
    [t("dashboard:usage.rejected"), usage.rejected]
  ];
  return (
    <article className="panel report-card wide" data-promo-chart="ai-usage">
      <div className="panel-title">
        <h3>{t("dashboard:usage.title")} <AiBadge /></h3>
        <span>{t("dashboard:usage.subtitle")}</span>
      </div>
      <div className="ai-usage-grid">
        {cells.map(([label, value]) => (
          <div className="ai-usage-cell" key={label}>
            <small>{label}</small>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
    </article>
  );
}

/**
 * 產生 AI 用量治理區塊（可拖拉與關閉；僅 MANAGER / ADMIN 載入）。非 React 元件，t/locale 由呼叫端傳入。
 *
 * @param usage 用量彙總（可為 null）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 toLocaleString 用
 * @returns AI 用量區塊
 */
export function usageBlock(usage: UsageSummaryResponse | null, t: TFunction, locale: string): DashboardBlock {
  return {
    id: "usage",
    title: t("dashboard:usage.title"),
    wide: true,
    render: () => usage
      ? <UsageCard usage={usage} t={t} locale={locale} />
      : <LoadingCard title={t("dashboard:usage.title")} wide loadingText={t("dashboard:loading")} />
  };
}
