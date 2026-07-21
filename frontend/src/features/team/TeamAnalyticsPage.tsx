import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
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
  const { t, i18n } = useTranslation("operations");
  const [data, setData] = useState<ManagerAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>("wonAmount");
  // AI 彈窗狀態：null=未開；{scope, owner?}
  const [modal, setModal] = useState<{ scope: "TEAM" | "OWNER"; owner?: string } | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(false);
      try {
        setData(await fetchManagerAnalytics());
      } catch (e) {
        console.error("Failed to load team analytics:", e);
        setError(true);
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

  if (loading) return <div className="panel"><div className="sr-empty">{t("team.loading")}</div></div>;
  if (error || !data) return <div className="panel"><div className="sr-empty">{t(error ? "team.loadError" : "team.noData")}</div></div>;

  return (
    <>
      <section className="topbar">
        <div>
          <p>{t("team.console")}</p>
          <h2>{t("team.title")}</h2>
        </div>
        <div className="topbar-actions">
          <button type="button" className="btn-assess topbar-assess" onClick={() => setModal({ scope: "TEAM" })}>
            {t("team.teamDiagnosis")} <AiBadge onDark />
          </button>
        </div>
      </section>

      <div className="team-kpi-row">
        <Kpi label={t("team.kpi.customers")} value={String(data.team.totalCustomers)} />
        <Kpi label={t("team.kpi.wonAmount")} value={formatCompactMoney(data.team.totalWonAmount, i18n.language)} />
        <Kpi label={t("team.kpi.pipeline")} value={formatCompactMoney(data.team.totalPipeline, i18n.language)} />
        <Kpi label={t("team.kpi.highRisk")} value={String(data.team.totalHighRisk)} />
        <Kpi label={t("team.kpi.winRate")} value={`${Math.round(data.team.avgWinRate * 100)}%`} />
        <Kpi label={t("team.kpi.owners")} value={String(data.team.ownerCount)} />
      </div>

      <div className="panel">
        <div className="panel-head">
          <h3>{t("team.performance")}</h3>
          <label className="sort-select">
            {t("team.sort")}
            <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)}>
              <option value="wonAmount">{t("team.sortOptions.wonAmount")}</option>
              <option value="winRate">{t("team.sortOptions.winRate")}</option>
              <option value="pipelineAmount">{t("team.sortOptions.pipelineAmount")}</option>
              <option value="customerCount">{t("team.sortOptions.customerCount")}</option>
              <option value="highRiskCount">{t("team.sortOptions.highRiskCount")}</option>
            </select>
          </label>
        </div>
        {/* 欄位較多時保留表格可讀寬度，窄螢幕改由此區塊局部橫向捲動。 */}
        <div className="table-scroll team-performance-table-scroll" role="region" aria-label={t("team.tableLabel")} tabIndex={0}>
          <table className="admin-user-table">
            <thead>
              <tr>
                <th>{t("team.columns.owner")}</th><th>{t("team.columns.customers")}</th><th>{t("team.columns.highRisk")}</th><th>{t("team.columns.pipeline")}</th>
                <th>{t("team.columns.won")}</th><th>{t("team.columns.winRate")}</th><th>{t("team.columns.interactionGap")}</th><th>{t("team.columns.renewals")}</th><th>{t("team.columns.coaching")}</th>
              </tr>
            </thead>
            <tbody>
              {sortedOwners.map((o) => (
                <tr key={o.ownerName}>
                  <td>{o.ownerName}</td>
                  <td>{o.customerCount}</td>
                  <td>{o.highRiskCount}</td>
                  <td>{formatCompactMoney(o.pipelineAmount, i18n.language)} ({o.activeOpportunityCount})</td>
                  <td>{formatCompactMoney(o.wonAmount, i18n.language)} ({o.wonCount})</td>
                  <td>{Math.round(o.winRate * 100)}%</td>
                  <td>{o.avgDaysSinceInteraction == null ? "—" : t("team.days", { count: Math.round(o.avgDaysSinceInteraction) })}</td>
                  <td>{o.renewalsThisQuarter}</td>
                  <td>
                    <button type="button" className="btn-secondary" onClick={() => setModal({ scope: "OWNER", owner: o.ownerName })}>
                      {t("team.coachingReport")} <AiBadge />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
