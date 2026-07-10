import { useEffect, useState } from "react";
import type { DashboardReports, DrilldownSource } from "../../../types";
import { formatCompactMoney, formatDateTime, riskLabel, stageLabel } from "../../../lib/format";
import { fetchDashboardReports } from "../../../api";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/** 圖表下鑽回呼型別。 */
export type DrillFn = (type: string, key: string, title: string) => void;

/**
 * 產生 7 個獨立的 CRM 報表圖表區塊（各自可拖拉與關閉）。
 * 函式級註解：reports 為 null 時各區塊以 LoadingCard 佔位；近期關鍵互動點列帶來源區塊跳客戶。
 *
 * @param reports 報表資料（可為 null）
 * @param onDrill 圖表下鑽回呼
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @returns 7 個報表區塊
 */
export function reportBlocks(
  reports: DashboardReports | null,
  onDrill: DrillFn,
  onSelectCustomer: (id: number, source: DrilldownSource) => void
): DashboardBlock[] {
  return [
    { id: "chart-pipeline", title: "銷售漏斗 Pipeline", wide: true, render: () => reports ? <PipelineFunnel data={reports.pipelineByStage} onDrill={onDrill} /> : <LoadingCard title="銷售漏斗" wide /> },
    { id: "chart-forecast", title: "月度營收 Forecast", wide: true, render: () => reports ? <MonthlyForecastChart data={reports.monthlyForecast} onDrill={onDrill} /> : <LoadingCard title="月度營收" wide /> },
    { id: "chart-industry", title: "產業營收分布", render: () => reports ? <IndustryBreakdown data={reports.industryBreakdown} onDrill={onDrill} /> : <LoadingCard title="產業營收分布" /> },
    { id: "chart-risk", title: "客戶風險結構", render: () => reports ? <RiskBreakdown data={reports.riskBreakdown} onDrill={onDrill} /> : <LoadingCard title="客戶風險結構" /> },
    { id: "chart-renewal", title: "續約到期預測", render: () => reports ? <RenewalForecast data={reports.renewalForecast} onDrill={onDrill} /> : <LoadingCard title="續約到期預測" /> },
    { id: "chart-leaderboard", title: "業務排行榜", render: () => reports ? <OwnerLeaderboard data={reports.ownerLeaderboard} onDrill={onDrill} /> : <LoadingCard title="業務排行榜" /> },
    { id: "chart-activity", title: "近期關鍵互動", wide: true, render: () => reports ? <ActivityReportList data={reports.recentActivities} onSelectCustomer={(id) => onSelectCustomer(id, { from: "dashboard", section: "近期關鍵互動", blockId: "chart-activity" })} /> : <LoadingCard title="近期關鍵互動" wide /> }
  ];
}

/** 漏斗呈現的階段順序(由上而下):早期機會在上、實際成交在最底。 */
const FUNNEL_STAGE_ORDER = ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON"];

/**
 * 銷售漏斗圖，以商機階段呈現 pipeline 金額與筆數。
 * 函式級註解：漏斗代表「商機推進」的轉化路徑,故上寬下窄、上方為早期機會(資格評估)、
 * 最底為實際成交(已成交)。「已流失」是掉出漏斗、非更深階段,故不納入漏斗(可在商機看板查看)。
 * 各層寬度由 CSS 階梯固定(funnel-layer-N),金額大小以橫向漸層填充比例直觀呈現。
 */
function PipelineFunnel({ data, onDrill }: { data: DashboardReports["pipelineByStage"]; onDrill: DrillFn }) {
  // 來源切換:""=全部、INBOUND=主動上門、OUTBOUND=業務開發；非全部時 fetch 該來源的漏斗子集
  const [source, setSource] = useState<"" | "INBOUND" | "OUTBOUND" | "REFERRAL">("");
  const [stageData, setStageData] = useState(data);
  // props data 變動（重新載入）時同步
  useEffect(() => { setStageData(data); }, [data]);
  // 切換來源:全部用 props data，其餘 fetch /dashboard/reports?leadSource= 取 pipelineByStage
  useEffect(() => {
    if (!source) { setStageData(data); return; }
    let cancelled = false;
    fetchDashboardReports(source).then((r) => { if (!cancelled) setStageData(r.pipelineByStage); }).catch(() => {});
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  // 僅保留漏斗階段並依「資格評估→提案→議價→已成交」由上而下排序(排除已流失)
  const funnelData = FUNNEL_STAGE_ORDER
    .map((stage) => stageData.find((item) => item.stage === stage))
    .filter((item): item is DashboardReports["pipelineByStage"][number] => Boolean(item));

  // 取得漏斗各階段中的最大金額以作為比例計算基準，最小設為 1 避免除以零
  const max = Math.max(...funnelData.map((item) => item.amount), 1);

  // 各階段填充色(由早期到成交,綠色漸深;不含已流失)
  const colorConfigs = [
    { fill: "#14b8a6", bg: "rgba(15, 118, 110, 0.08)" }, // 資格評估：薄荷綠
    { fill: "#2dd4bf", bg: "rgba(13, 148, 136, 0.08)" }, // 提案：亮薄荷綠
    { fill: "#5eead4", bg: "rgba(13, 148, 136, 0.08)" }, // 議價：粉薄荷綠
    { fill: "#34d399", bg: "rgba(16, 185, 129, 0.08)" }  // 已成交：翡翠綠(漏斗最底=實際成交)
  ];

  return (
    <article className="panel report-card wide" data-promo-chart="pipeline">
      <div className="panel-title">
        <h3>銷售漏斗 Pipeline</h3>
        <span>機會(上)→ 成交(下)</span>
      </div>
      {/* 來源切換:全部 / 主動上門 / 業務開發 */}
      <div className="funnel-source-tabs">
        {([["", "全部"], ["INBOUND", "主動上門"], ["OUTBOUND", "業務開發"], ["REFERRAL", "推薦轉介"]] as const).map(([v, label]) => (
          <button
            type="button"
            key={v}
            className={`source-tab ${source === v ? "active" : ""}`}
            onClick={() => setSource(v)}
          >{label}</button>
        ))}
      </div>
      <div className="funnel-container">
        {funnelData.map((item, index) => {
          // 計算當前階段的金額比例，底限設為 6% 確保即使零元也有一點點點綴，上限 100%
          const amountPercent = Math.min(100, Math.max(6, (item.amount / max) * 100));

          // 取得該層專屬的色彩配置，重要物件取用
          const colors = colorConfigs[index] || colorConfigs[0];

          // 組裝 CSS 漸層背景：左側亮色代表已填充數據，右側半透明代表未填充容量
          const backgroundGradient = `linear-gradient(90deg, ${colors.fill} 0%, ${colors.fill} ${amountPercent}%, ${colors.bg} ${amountPercent}%, ${colors.bg} 100%)`;

          return (
            <div
              className={`funnel-stage-wrapper funnel-layer-${index} clickable`}
              key={item.stage}
              style={{ background: backgroundGradient }}
              title={`點擊查看 ${stageLabel(item.stage)} 的 ${item.count} 筆商機` +
                (item.avgDaysInStage != null ? ` · 平均停留 ${item.avgDaysInStage} 天` : "") +
                (item.overdueCount ? ` · ${item.overdueCount} 筆超時` : "")}
              role="button"
              tabIndex={0}
              onClick={() => onDrill("stage", item.stage, `銷售漏斗 · ${stageLabel(item.stage)}`)}
              onKeyDown={(e) => { if (e.key === "Enter") onDrill("stage", item.stage, `銷售漏斗 · ${stageLabel(item.stage)}`); }}
            >
              <div className="funnel-content">
                <strong>{stageLabel(item.stage)}</strong>
                <span>
                  {item.count} 筆
                  {item.avgDaysInStage != null && item.count > 0 ? ` · 均 ${item.avgDaysInStage} 天` : ""}
                  {item.overdueCount ? ` · ⚠${item.overdueCount}` : ""}
                </span>
                <b>{formatCompactMoney(item.amount)}</b>
              </div>
            </div>
          );
        })}
      </div>
    </article>
  );
}

/**
 * 月度營收預測折線圖。
 */
function MonthlyForecastChart({ data, onDrill }: { data: DashboardReports["monthlyForecast"]; onDrill: DrillFn }) {
  // 以總額為比例基準（總額 >= 加權，確保兩條線都在繪圖範圍內）
  const max = Math.max(...data.map((item) => item.totalAmount), 1);
  const xOf = (index: number) => 24 + (index * 320) / Math.max(data.length - 1, 1);
  const yOf = (value: number) => 150 - (value / max) * 110;
  const totalPoints = data.map((item, i) => `${xOf(i)},${yOf(item.totalAmount)}`).join(" ");
  const weightedPoints = data.map((item, i) => `${xOf(i)},${yOf(item.weightedAmount)}`).join(" ");
  return (
    <article className="panel report-card wide" data-promo-chart="forecast">
      <div className="panel-title"><h3>月度營收 Forecast</h3><span>實線=總額 · 虛線=加權預測</span></div>
      <svg className="line-chart" viewBox="0 0 368 180" role="img" aria-label="月度營收預測折線圖">
        {/* 總額 pipeline（實線） */}
        <polyline points={totalPoints} fill="none" stroke="#0f766e" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        {/* 機率加權預測（橘色虛線） */}
        <polyline points={weightedPoints} fill="none" stroke="#f59e0b" strokeWidth="3" strokeDasharray="6 4" strokeLinecap="round" strokeLinejoin="round" />
        {data.map((item, index) => (
          <circle key={item.label} cx={xOf(index)} cy={yOf(item.totalAmount)} r="5" fill="#102b40" />
        ))}
        {data.map((item, index) => (
          <text key={item.label} x={xOf(index)} y="172" textAnchor="middle">{item.label.slice(5)}</text>
        ))}
        {/* 透明點擊熱區：每月一條，方便點選下鑽 */}
        {data.map((item, index) => (
          <rect
            key={`hit-${item.label}`}
            className="chart-hit"
            x={xOf(index) - 16} y="0" width="32" height="180"
            onClick={() => onDrill("forecastMonth", item.label, `月度營收 · ${item.label}`)}
          >
            <title>{`點擊查看 ${item.label} 預計成交商機`}</title>
          </rect>
        ))}
      </svg>
    </article>
  );
}

/**
 * 產業分布長條圖。
 */
function IndustryBreakdown({ data, onDrill }: { data: DashboardReports["industryBreakdown"]; onDrill: DrillFn }) {
  const top = [...data].sort((a, b) => b.amount - a.amount).slice(0, 8);
  const max = Math.max(...top.map((item) => item.amount), 1);
  return (
    <article className="panel report-card" data-promo-chart="industry">
      <div className="panel-title"><h3>產業營收分布</h3><span>點擊查看客戶</span></div>
      <div className="bar-list">
        {top.map((item) => (
          <button type="button" className="bar-row clickable" key={item.label} onClick={() => onDrill("industry", item.label, `產業 · ${item.label}`)}>
            <span>{item.label}</span>
            <div><i style={{ width: `${Math.max(8, (item.amount / max) * 100)}%` }} /></div>
            <b>{formatCompactMoney(item.amount)}</b>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 客戶風險結構甜甜圈替代圖。
 */
function RiskBreakdown({ data, onDrill }: { data: DashboardReports["riskBreakdown"]; onDrill: DrillFn }) {
  const total = data.reduce((sum, item) => sum + item.value, 0) || 1;
  return (
    <article className="panel report-card" data-promo-chart="risk">
      <div className="panel-title"><h3>客戶風險結構</h3><span>點擊查看客戶</span></div>
      <div className="risk-report">
        {data.map((item) => (
          <button type="button" className={`risk-chip clickable ${item.label.toLowerCase()}`} key={item.label} onClick={() => onDrill("risk", item.label, `風險 · ${riskLabel(item.label)}`)}>
            <strong>{riskLabel(item.label)}</strong>
            <b>{item.value}</b>
            <small>{Math.round((item.value / total) * 100)}%</small>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 續約預測圖表。
 */
function RenewalForecast({ data, onDrill }: { data: DashboardReports["renewalForecast"]; onDrill: DrillFn }) {
  const top = data.slice(0, 8);
  const max = Math.max(...top.map((item) => item.count), 1);
  return (
    <article className="panel report-card" data-promo-chart="renewal">
      <div className="panel-title"><h3>續約到期預測</h3><span>點擊月份查看</span></div>
      <div className="renewal-bars">
        {top.map((item) => (
          <button type="button" className="renewal-bar clickable" key={item.label} title={`點擊查看 ${item.label} 續約客戶`} onClick={() => onDrill("renewalMonth", item.label, `續約到期 · ${item.label}`)}>
            <span style={{ height: `${Math.max(18, (item.count / max) * 110)}px` }} />
            <small>{item.label.slice(5)}</small>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 業務排行榜報表。
 */
function OwnerLeaderboard({ data, onDrill }: { data: DashboardReports["ownerLeaderboard"]; onDrill: DrillFn }) {
  return (
    <article className="panel report-card" data-promo-chart="leaderboard">
      <div className="panel-title"><h3>業務排行榜</h3><span>點擊查看客戶</span></div>
      <div className="leaderboard">
        {data.map((owner, index) => (
          <button type="button" className="leader-row clickable" key={owner.ownerName} onClick={() => onDrill("owner", owner.ownerName, `業務 · ${owner.ownerName}`)}>
            <b>{index + 1}</b>
            <span>{owner.ownerName}<small>{owner.customerCount} 客戶 / 高風險 {owner.highRiskCount}</small></span>
            <strong>{formatCompactMoney(owner.opportunityAmount)}</strong>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 近期活動報表。
 */
function ActivityReportList({ data, onSelectCustomer }: { data: DashboardReports["recentActivities"]; onSelectCustomer: (id: number) => void }) {
  return (
    <article className="panel report-card wide" data-promo-chart="activity">
      <div className="panel-title"><h3>近期關鍵互動</h3><span>點擊跳到客戶</span></div>
      <div className="activity-report">
        {data.map((activity) => (
          <button type="button" className="activity-item clickable" key={`${activity.customerId}-${activity.occurredAt}`} onClick={() => onSelectCustomer(activity.customerId)}>
            <strong>{activity.customerName}</strong>
            <span>{activity.type} / {formatDateTime(activity.occurredAt)}</span>
            <p>{activity.content}</p>
          </button>
        ))}
      </div>
    </article>
  );
}
