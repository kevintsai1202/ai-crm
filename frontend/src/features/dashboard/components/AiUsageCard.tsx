import type { UsageSummaryResponse } from "../../../types";
import { AiBadge } from "../../../components/common/AiBadge";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * AI 用量治理卡（SP4，MANAGER/ADMIN 可見）：呈現呼叫次數、token 用量、真實/fallback 比、採納/拒絕統計。
 */
function UsageCard({ usage }: { usage: UsageSummaryResponse }) {
  const cells: [string, string | number][] = [
    ["AI 呼叫次數", usage.totalCalls],
    ["Token 用量", usage.totalTokens.toLocaleString("zh-TW")],
    ["真實 LLM / Fallback", `${usage.realCalls} / ${usage.fallbackCalls}`],
    ["採納", usage.adopted],
    ["拒絕", usage.rejected]
  ];
  return (
    <article className="panel report-card wide" data-promo-chart="ai-usage">
      <div className="panel-title">
        <h3>AI 用量治理 <AiBadge /></h3>
        <span>僅主管 / 管理員可見</span>
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
 * 產生 AI 用量治理區塊（可拖拉與關閉；僅 MANAGER / ADMIN 載入）。
 *
 * @param usage 用量彙總（可為 null）
 * @returns AI 用量區塊
 */
export function usageBlock(usage: UsageSummaryResponse | null): DashboardBlock {
  return {
    id: "usage",
    title: "AI 用量治理",
    wide: true,
    render: () => usage ? <UsageCard usage={usage} /> : <LoadingCard title="AI 用量" wide />
  };
}
