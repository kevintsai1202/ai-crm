import { useEffect, useMemo, useState } from "react";
import { fetchManagerAnalytics } from "../../api";
import type { ManagerAnalyticsResponse } from "../../types";
import { formatCompactMoney } from "../../lib/format";
import { AiBadge } from "../../components/common/AiBadge";
import { ManagerInsightModal } from "./ManagerInsightModal";

/** 業務績效表可排序的欄位鍵。 */
type SortKey = "wonAmount" | "winRate" | "pipelineAmount" | "customerCount" | "highRiskCount";

/**
 * 業務分析頁（MANAGER/ADMIN）：團隊 KPI + 可排序業務績效表 + AI 彈窗（團隊診斷 / 業務 coaching）。
 * 函式級註解：純統計來自 /api/manager/analytics；AI 走彈窗（比照儀表板）——topbar 開團隊診斷，表格列開業務 coaching。
 */
export function TeamAnalyticsPage() {
  const [data, setData] = useState<ManagerAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("wonAmount");
  // AI 彈窗狀態：null=未開；{scope, owner?}
  const [modal, setModal] = useState<{ scope: "TEAM" | "OWNER"; owner?: string } | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(null);
      try {
        setData(await fetchManagerAnalytics());
      } catch (e) {
        console.error("載入業務分析失敗:", e);
        setError("載入業務分析失敗");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // 依選定欄位降序排序（不可變副本）
  const sortedOwners = useMemo(() => {
    if (!data) return [];
    return [...data.owners].sort((a, b) => Number(b[sortKey]) - Number(a[sortKey]));
  }, [data, sortKey]);

  if (loading) return <div className="panel"><div className="sr-empty">載入中…</div></div>;
  if (error || !data) return <div className="panel"><div className="sr-empty">{error ?? "無資料"}</div></div>;

  const t = data.team;
  return (
    <>
      <section className="topbar">
        <div>
          <p>Manager Console</p>
          <h2>業務分析</h2>
        </div>
        <div className="topbar-actions">
          <button type="button" className="btn-assess topbar-assess" onClick={() => setModal({ scope: "TEAM" })}>
            🤖 團隊整體診斷 <AiBadge onDark />
          </button>
        </div>
      </section>

      <div className="team-kpi-row">
        <Kpi label="客戶總數" value={String(t.totalCustomers)} />
        <Kpi label="全團隊成交金額" value={formatCompactMoney(t.totalWonAmount, "zh-TW")} />
        <Kpi label="進行中商機" value={formatCompactMoney(t.totalPipeline, "zh-TW")} />
        <Kpi label="高風險客戶" value={String(t.totalHighRisk)} />
        <Kpi label="平均成交率" value={`${Math.round(t.avgWinRate * 100)}%`} />
        <Kpi label="業務人數" value={String(t.ownerCount)} />
      </div>

      <div className="panel">
        <div className="panel-head">
          <h3>各業務績效</h3>
          <label className="sort-select">
            排序：
            <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)}>
              <option value="wonAmount">成交金額</option>
              <option value="winRate">成交率</option>
              <option value="pipelineAmount">進行中商機</option>
              <option value="customerCount">客戶數</option>
              <option value="highRiskCount">高風險數</option>
            </select>
          </label>
        </div>
        <table className="admin-user-table">
          <thead>
            <tr>
              <th>業務</th><th>客戶</th><th>高風險</th><th>進行中商機</th>
              <th>已成交</th><th>成交率</th><th>平均互動間隔</th><th>本季續約</th><th>Coaching</th>
            </tr>
          </thead>
          <tbody>
            {sortedOwners.map((o) => (
              <tr key={o.ownerName}>
                <td>{o.ownerName}</td>
                <td>{o.customerCount}</td>
                <td>{o.highRiskCount}</td>
                <td>{formatCompactMoney(o.pipelineAmount, "zh-TW")}（{o.activeOpportunityCount}）</td>
                <td>{formatCompactMoney(o.wonAmount, "zh-TW")}（{o.wonCount}）</td>
                <td>{Math.round(o.winRate * 100)}%</td>
                <td>{o.avgDaysSinceInteraction == null ? "—" : `${Math.round(o.avgDaysSinceInteraction)} 天`}</td>
                <td>{o.renewalsThisQuarter}</td>
                <td>
                  <button type="button" className="btn-secondary" onClick={() => setModal({ scope: "OWNER", owner: o.ownerName })}>
                    輔導報告 <AiBadge />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {modal ? (
        <ManagerInsightModal scope={modal.scope} owner={modal.owner} onClose={() => setModal(null)} />
      ) : null}
    </>
  );
}

/** 單一 KPI 卡。 */
function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi-card">
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value">{value}</strong>
    </div>
  );
}
