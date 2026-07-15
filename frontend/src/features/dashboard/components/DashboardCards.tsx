import type { TFunction } from "i18next";
import type { DashboardSummary } from "../../../types";
import { formatMoney } from "../../../lib/format";
import type { DashboardBlock } from "../blockTypes";

/**
 * 單張 KPI 統計小卡。
 */
function KpiCard({ label, value }: { label: string; value: string | number }) {
  return (
    <article className="metric-card">
      <small>{label}</small>
      <strong>{value}</strong>
    </article>
  );
}

/**
 * 產生 4 張獨立的 KPI 統計區塊（客戶數 / 活躍商機 / 商機總額 / 高風險），各自可拖拉與關閉。
 * 函式級註解：dashboard 為 null 時以 0 / 預設值呈現，不阻斷渲染。非 React 元件，不可呼叫
 * useTranslation()，故 t（與需要格式化金額的 locale）皆由呼叫端（DashboardPage）傳入。
 *
 * @param dashboard 儀表板摘要（可為 null）
 * @param riskCounts 風險統計（高風險 fallback 用）
 * @param t react-i18next 的 t 函式（呼叫端已 scope 到 ["dashboard","common"]）
 * @param locale 目前語言（i18n.language），供 formatMoney 格式化用
 * @returns 4 個 KPI 區塊
 */
export function kpiBlocks(
  dashboard: DashboardSummary | null,
  riskCounts: Record<string, number>,
  t: TFunction,
  locale: string
): DashboardBlock[] {
  return [
    { id: "kpi-customers", title: t("dashboard:kpi.customers.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.customers.label")} value={dashboard?.customerCount ?? 0} /> },
    { id: "kpi-active-opps", title: t("dashboard:kpi.activeOpps.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.activeOpps.label")} value={dashboard?.activeOpportunityCount ?? 0} /> },
    { id: "kpi-pipeline", title: t("dashboard:kpi.pipeline.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.pipeline.label")} value={formatMoney(dashboard?.opportunityAmount ?? 0, locale)} /> },
    { id: "kpi-high-risk", title: t("dashboard:kpi.highRisk.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.highRisk.label")} value={dashboard?.highRiskCount ?? riskCounts.HIGH ?? 0} /> }
  ];
}
