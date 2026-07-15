import { useEffect, useState } from "react";
import type { TFunction } from "i18next";
import type { DashboardReports, DrilldownSource } from "../../../types";
import { formatCompactMoney, formatDateTime, riskLabel, stageLabel } from "../../../lib/format";
import { fetchDashboardReports } from "../../../api";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/** 圖表下鑽回呼型別。 */
export type DrillFn = (type: string, key: string, title: string) => void;

/**
 * 產生 7 個獨立的 CRM 報表圖表區塊（各自可拖拉與關閉）。非 React 元件，t/locale 由呼叫端傳入。
 * 函式級註解：reports 為 null 時各區塊以 LoadingCard 佔位；近期關鍵互動點列帶來源區塊跳客戶。
 *
 * @param reports 報表資料（可為 null）
 * @param onDrill 圖表下鑽回呼
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 formatCompactMoney/formatDateTime 用
 * @returns 7 個報表區塊
 */
export function reportBlocks(
  reports: DashboardReports | null,
  onDrill: DrillFn,
  onSelectCustomer: (id: number, source: DrilldownSource) => void,
  t: TFunction,
  locale: string
): DashboardBlock[] {
  const loadingText = t("dashboard:loading");
  return [
    { id: "chart-pipeline", title: t("dashboard:charts.pipeline.title"), wide: true, render: () => reports ? <PipelineFunnel data={reports.pipelineByStage} onDrill={onDrill} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.pipeline.title")} wide loadingText={loadingText} /> },
    { id: "chart-forecast", title: t("dashboard:charts.forecast.title"), wide: true, render: () => reports ? <MonthlyForecastChart data={reports.monthlyForecast} onDrill={onDrill} t={t} /> : <LoadingCard title={t("dashboard:charts.forecast.title")} wide loadingText={loadingText} /> },
    { id: "chart-industry", title: t("dashboard:charts.industry.title"), render: () => reports ? <IndustryBreakdown data={reports.industryBreakdown} onDrill={onDrill} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.industry.title")} loadingText={loadingText} /> },
    { id: "chart-risk", title: t("dashboard:charts.risk.title"), render: () => reports ? <RiskBreakdown data={reports.riskBreakdown} onDrill={onDrill} t={t} /> : <LoadingCard title={t("dashboard:charts.risk.title")} loadingText={loadingText} /> },
    { id: "chart-renewal", title: t("dashboard:charts.renewal.title"), render: () => reports ? <RenewalForecast data={reports.renewalForecast} onDrill={onDrill} t={t} /> : <LoadingCard title={t("dashboard:charts.renewal.title")} loadingText={loadingText} /> },
    { id: "chart-leaderboard", title: t("dashboard:charts.leaderboard.title"), render: () => reports ? <OwnerLeaderboard data={reports.ownerLeaderboard} onDrill={onDrill} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.leaderboard.title")} loadingText={loadingText} /> },
    { id: "chart-activity", title: t("dashboard:charts.activity.title"), wide: true, render: () => reports ? <ActivityReportList data={reports.recentActivities} onSelectCustomer={(id) => onSelectCustomer(id, { from: "dashboard", section: t("dashboard:charts.activity.title"), blockId: "chart-activity" })} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.activity.title")} wide loadingText={loadingText} /> }
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
function PipelineFunnel({ data, onDrill, t, locale }: { data: DashboardReports["pipelineByStage"]; onDrill: DrillFn; t: TFunction; locale: string }) {
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

  // 來源切換分頁定義（value, i18n key）
  const sourceTabs: readonly [typeof source, string][] = [
    ["", "dashboard:charts.pipeline.sourceAll"],
    ["INBOUND", "dashboard:charts.pipeline.sourceInbound"],
    ["OUTBOUND", "dashboard:charts.pipeline.sourceOutbound"],
    ["REFERRAL", "dashboard:charts.pipeline.sourceReferral"]
  ];

  return (
    <article className="panel report-card wide" data-promo-chart="pipeline">
      <div className="panel-title">
        <h3>{t("dashboard:charts.pipeline.title")}</h3>
        <span>{t("dashboard:charts.pipeline.subtitle")}</span>
      </div>
      {/* 來源切換:全部 / 主動上門 / 業務開發 */}
      <div className="funnel-source-tabs">
        {sourceTabs.map(([v, labelKey]) => (
          <button
            type="button"
            key={v}
            className={`source-tab ${source === v ? "active" : ""}`}
            onClick={() => setSource(v)}
          >{t(labelKey)}</button>
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

          const stageText = t(stageLabel(item.stage));
          const hoverTitle = t("dashboard:charts.pipeline.hoverTitle", { stage: stageText, count: item.count }) +
            (item.avgDaysInStage != null ? t("dashboard:charts.pipeline.avgDaysSuffix", { days: item.avgDaysInStage }) : "") +
            (item.overdueCount ? t("dashboard:charts.pipeline.overdueSuffix", { count: item.overdueCount }) : "");
          const drillTitle = `${t("dashboard:charts.pipeline.title")} · ${stageText}`;

          return (
            <div
              className={`funnel-stage-wrapper funnel-layer-${index} clickable`}
              key={item.stage}
              style={{ background: backgroundGradient }}
              title={hoverTitle}
              role="button"
              tabIndex={0}
              onClick={() => onDrill("stage", item.stage, drillTitle)}
              onKeyDown={(e) => { if (e.key === "Enter") onDrill("stage", item.stage, drillTitle); }}
            >
              <div className="funnel-content">
                <strong>{stageText}</strong>
                <span>
                  {t("dashboard:charts.pipeline.countSuffix", { count: item.count })}
                  {item.avgDaysInStage != null && item.count > 0 ? t("dashboard:charts.pipeline.avgDaysInline", { days: item.avgDaysInStage }) : ""}
                  {item.overdueCount ? ` · ⚠${item.overdueCount}` : ""}
                </span>
                <b>{formatCompactMoney(item.amount, locale)}</b>
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
function MonthlyForecastChart({ data, onDrill, t }: { data: DashboardReports["monthlyForecast"]; onDrill: DrillFn; t: TFunction }) {
  // 以總額為比例基準（總額 >= 加權，確保兩條線都在繪圖範圍內）
  const max = Math.max(...data.map((item) => item.totalAmount), 1);
  const xOf = (index: number) => 24 + (index * 320) / Math.max(data.length - 1, 1);
  const yOf = (value: number) => 150 - (value / max) * 110;
  const totalPoints = data.map((item, i) => `${xOf(i)},${yOf(item.totalAmount)}`).join(" ");
  const weightedPoints = data.map((item, i) => `${xOf(i)},${yOf(item.weightedAmount)}`).join(" ");
  return (
    <article className="panel report-card wide" data-promo-chart="forecast">
      <div className="panel-title"><h3>{t("dashboard:charts.forecast.title")}</h3><span>{t("dashboard:charts.forecast.subtitle")}</span></div>
      <svg className="line-chart" viewBox="0 0 368 180" role="img" aria-label={t("dashboard:charts.forecast.ariaLabel")}>
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
            onClick={() => onDrill("forecastMonth", item.label, `${t("dashboard:charts.forecast.title")} · ${item.label}`)}
          >
            <title>{t("dashboard:charts.forecast.hoverTitle", { month: item.label })}</title>
          </rect>
        ))}
      </svg>
    </article>
  );
}

/**
 * 產業分布長條圖。
 */
function IndustryBreakdown({ data, onDrill, t, locale }: { data: DashboardReports["industryBreakdown"]; onDrill: DrillFn; t: TFunction; locale: string }) {
  const top = [...data].sort((a, b) => b.amount - a.amount).slice(0, 8);
  const max = Math.max(...top.map((item) => item.amount), 1);
  return (
    <article className="panel report-card" data-promo-chart="industry">
      <div className="panel-title"><h3>{t("dashboard:charts.industry.title")}</h3><span>{t("dashboard:charts.industry.subtitle")}</span></div>
      <div className="bar-list">
        {top.map((item) => (
          <button type="button" className="bar-row clickable" key={item.label} onClick={() => onDrill("industry", item.label, t("dashboard:charts.industry.drillTitle", { label: item.label }))}>
            <span>{item.label}</span>
            <div><i style={{ width: `${Math.max(8, (item.amount / max) * 100)}%` }} /></div>
            <b>{formatCompactMoney(item.amount, locale)}</b>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 客戶風險結構甜甜圈替代圖。
 */
function RiskBreakdown({ data, onDrill, t }: { data: DashboardReports["riskBreakdown"]; onDrill: DrillFn; t: TFunction }) {
  const total = data.reduce((sum, item) => sum + item.value, 0) || 1;
  return (
    <article className="panel report-card" data-promo-chart="risk">
      <div className="panel-title"><h3>{t("dashboard:charts.risk.title")}</h3><span>{t("dashboard:charts.risk.subtitle")}</span></div>
      <div className="risk-report">
        {data.map((item) => {
          const label = t(riskLabel(item.label));
          return (
            <button type="button" className={`risk-chip clickable ${item.label.toLowerCase()}`} key={item.label} onClick={() => onDrill("risk", item.label, t("dashboard:charts.risk.drillTitle", { label }))}>
              <strong>{label}</strong>
              <b>{item.value}</b>
              <small>{Math.round((item.value / total) * 100)}%</small>
            </button>
          );
        })}
      </div>
    </article>
  );
}

/**
 * 續約預測圖表。
 */
function RenewalForecast({ data, onDrill, t }: { data: DashboardReports["renewalForecast"]; onDrill: DrillFn; t: TFunction }) {
  const top = data.slice(0, 8);
  const max = Math.max(...top.map((item) => item.count), 1);
  return (
    <article className="panel report-card" data-promo-chart="renewal">
      <div className="panel-title"><h3>{t("dashboard:charts.renewal.title")}</h3><span>{t("dashboard:charts.renewal.subtitle")}</span></div>
      <div className="renewal-bars">
        {top.map((item) => (
          <button type="button" className="renewal-bar clickable" key={item.label} title={t("dashboard:charts.renewal.hoverTitle", { month: item.label })} onClick={() => onDrill("renewalMonth", item.label, t("dashboard:charts.renewal.drillTitle", { label: item.label }))}>
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
function OwnerLeaderboard({ data, onDrill, t, locale }: { data: DashboardReports["ownerLeaderboard"]; onDrill: DrillFn; t: TFunction; locale: string }) {
  return (
    <article className="panel report-card" data-promo-chart="leaderboard">
      <div className="panel-title"><h3>{t("dashboard:charts.leaderboard.title")}</h3><span>{t("dashboard:charts.leaderboard.subtitle")}</span></div>
      <div className="leaderboard">
        {data.map((owner, index) => (
          <button type="button" className="leader-row clickable" key={owner.ownerName} onClick={() => onDrill("owner", owner.ownerName, t("dashboard:charts.leaderboard.drillTitle", { label: owner.ownerName }))}>
            <b>{index + 1}</b>
            <span>{owner.ownerName}<small>{t("dashboard:charts.leaderboard.customersSuffix", { count: owner.customerCount })} / {t("dashboard:charts.leaderboard.highRiskSuffix", { count: owner.highRiskCount })}</small></span>
            <strong>{formatCompactMoney(owner.opportunityAmount, locale)}</strong>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 近期活動報表。
 */
function ActivityReportList({ data, onSelectCustomer, t, locale }: { data: DashboardReports["recentActivities"]; onSelectCustomer: (id: number) => void; t: TFunction; locale: string }) {
  return (
    <article className="panel report-card wide" data-promo-chart="activity">
      <div className="panel-title"><h3>{t("dashboard:charts.activity.title")}</h3><span>{t("dashboard:charts.activity.subtitle")}</span></div>
      <div className="activity-report">
        {data.map((activity) => (
          <button type="button" className="activity-item clickable" key={`${activity.customerId}-${activity.occurredAt}`} onClick={() => onSelectCustomer(activity.customerId)}>
            <strong>{activity.customerName}</strong>
            <span>{activity.type} / {formatDateTime(activity.occurredAt, locale, t("common:noData"))}</span>
            <p>{activity.content}</p>
          </button>
        ))}
      </div>
    </article>
  );
}
