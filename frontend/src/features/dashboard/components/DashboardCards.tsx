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
 * 函式級註解：dashboard 為 null 時以 0 / 預設值呈現，不阻斷渲染。
 *
 * @param dashboard 儀表板摘要（可為 null）
 * @param riskCounts 風險統計（高風險 fallback 用）
 * @returns 4 個 KPI 區塊
 */
export function kpiBlocks(dashboard: DashboardSummary | null, riskCounts: Record<string, number>): DashboardBlock[] {
  return [
    { id: "kpi-customers", title: "KPI · 客戶數", short: true, render: () => <KpiCard label="客戶數" value={dashboard?.customerCount ?? 0} /> },
    { id: "kpi-active-opps", title: "KPI · 活躍商機", short: true, render: () => <KpiCard label="活躍商機" value={dashboard?.activeOpportunityCount ?? 0} /> },
    { id: "kpi-pipeline", title: "KPI · 商機總額", short: true, render: () => <KpiCard label="商機總額" value={formatMoney(dashboard?.opportunityAmount ?? 0)} /> },
    { id: "kpi-high-risk", title: "KPI · 高風險客戶", short: true, render: () => <KpiCard label="高風險客戶" value={dashboard?.highRiskCount ?? riskCounts.HIGH ?? 0} /> }
  ];
}
