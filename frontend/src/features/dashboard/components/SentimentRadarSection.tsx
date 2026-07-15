import type { TFunction } from "i18next";
import type { DrilldownSource, SentimentRadarResponse } from "../../../types";
import { formatDateTime, intentLabel } from "../../../lib/format";
import { PaginatedList } from "../../../components/common/PaginatedList";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * 將情緒值（POSITIVE / NEUTRAL / NEGATIVE）轉成色點 CSS class。
 */
function sentimentClass(sentiment: string | null | undefined) {
  const map: Record<string, string> = { POSITIVE: "pos", NEUTRAL: "neu", NEGATIVE: "neg" };
  return map[sentiment || ""] || "neu";
}

/**
 * 視覺 1：意圖分布長條卡。
 */
function IntentDistributionCard({ data, t }: { data: SentimentRadarResponse; t: TFunction }) {
  // 意圖分布長條的最大值，用於計算每列長條寬度比例
  const intentMax = Math.max(1, ...data.intentDistribution.map((i) => i.count));
  return (
    <article className="panel report-card sr-card" data-promo-chart="sentiment-intent">
      <div className="panel-title"><h3>{t("dashboard:sentiment.intentTitle")}</h3><span>{t("dashboard:sentiment.intentSubtitle")}</span></div>
      <div className="sr-intent-list">
        {data.intentDistribution.length === 0 ? (
          <div className="sr-empty">{t("dashboard:sentiment.intentEmpty")}</div>
        ) : (
          data.intentDistribution.map((row) => {
            const key = intentLabel(row.intent);
            const label = key ? t(key) : (row.intent === "OTHER" ? t("dashboard:sentiment.intentOther") : row.intent);
            return (
              <div className="sr-intent-row" key={row.intent}>
                <span className="sr-intent-label">{label}</span>
                <span className="sr-bar-track"><span className="sr-bar-fill" style={{ width: `${(row.count / intentMax) * 100}%` }} /></span>
                <b className="sr-intent-count">{row.count}</b>
              </div>
            );
          })
        )}
      </div>
    </article>
  );
}

/**
 * 視覺 2：近 12 月情緒趨勢（正/中/負三色堆疊長條）。
 */
function SentimentTrendCard({ data, t }: { data: SentimentRadarResponse; t: TFunction }) {
  // 情緒趨勢每月三色堆疊長條的最大總量，用於正規化高度
  const trendMax = Math.max(1, ...data.sentimentTrend.map((p) => p.positive + p.neutral + p.negative));
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.trendTitle")}</h3><span>{t("dashboard:sentiment.trendSubtitle")}</span></div>
      {data.sentimentTrend.length === 0 ? (
        <div className="sr-empty">{t("dashboard:sentiment.trendEmpty")}</div>
      ) : (
        <>
          <div className="sr-trend-chart">
            {data.sentimentTrend.map((p) => {
              const total = p.positive + p.neutral + p.negative;
              return (
                <div className="sr-trend-col" key={p.month} title={t("dashboard:sentiment.trendTooltip", { month: p.month, positive: p.positive, neutral: p.neutral, negative: p.negative })}>
                  <div className="sr-trend-stack" style={{ height: `${(total / trendMax) * 100}%` }}>
                    <span className="sr-seg neg" style={{ flexGrow: p.negative }} />
                    <span className="sr-seg neu" style={{ flexGrow: p.neutral }} />
                    <span className="sr-seg pos" style={{ flexGrow: p.positive }} />
                  </div>
                  <small className="sr-trend-x">{p.month.slice(5)}</small>
                </div>
              );
            })}
          </div>
          <div className="sr-legend">
            <span><i className="sr-dot pos" />{t("dashboard:sentiment.legendPositive")}</span>
            <span><i className="sr-dot neu" />{t("dashboard:sentiment.legendNeutral")}</span>
            <span><i className="sr-dot neg" />{t("dashboard:sentiment.legendNegative")}</span>
          </div>
        </>
      )}
    </article>
  );
}

/**
 * 視覺 3：高風險互動清單卡。
 */
function HighRiskCard({ data, onSelectCustomer, t, locale }: {
  data: SentimentRadarResponse;
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
  locale: string;
}) {
  return (
    <article className="panel report-card wide sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.highRiskTitle")}</h3><span>{t("dashboard:sentiment.highRiskSubtitle")}</span></div>
      <PaginatedList
        items={data.highRiskInteractions}
        pageSize={5}
        emptyText={t("dashboard:sentiment.highRiskEmpty")}
        rowKey={(item, i) => `${item.customerId}-${i}`}
        renderRow={(item) => (
          <button type="button" className="sr-row clickable" onClick={() => onSelectCustomer(item.customerId, { from: "dashboard", section: t("dashboard:sentiment.highRiskTitle"), blockId: "sr-highrisk" })}>
            <span className={`sr-dot ${sentimentClass(item.sentiment)}`} />
            <strong className="sr-row-name">{item.customerName}</strong>
            {intentLabel(item.intent) ? <span className="sr-tag">{t(intentLabel(item.intent))}</span> : null}
            <span className="sr-row-content">{item.content}</span>
            <em className="sr-row-time">{formatDateTime(item.occurredAt, locale, t("common:noData"))}</em>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 視覺 4：流失雷達（依加權分數排序）。
 */
function ChurnRadarCard({ data, onSelectCustomer, t }: {
  data: SentimentRadarResponse;
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
}) {
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.churnTitle")}</h3><span>{t("dashboard:sentiment.churnSubtitle")}</span></div>
      <PaginatedList
        items={data.churnRadar}
        pageSize={5}
        emptyText={t("dashboard:sentiment.churnEmpty")}
        rowKey={(row) => String(row.customerId)}
        renderRow={(row) => (
          <button type="button" className="sr-row sr-churn-row clickable" onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: t("dashboard:sentiment.churnTitle"), blockId: "sr-churn" })}>
            <strong className="sr-row-name">{row.name}</strong>
            <span className="sr-churn-meta" title={t("dashboard:sentiment.churnMetaTitle", { negative: row.negativeCount, churn: row.churnSignalCount, complaint: row.complaintCount })}>
              {t("dashboard:sentiment.churnMetaInline", { negative: row.negativeCount, churn: row.churnSignalCount, complaint: row.complaintCount })}
            </span>
            <b className="sr-score">{row.score}</b>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 視覺 5：優先關懷清單卡。
 */
function PriorityCareCard({ data, onSelectCustomer, t }: {
  data: SentimentRadarResponse;
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
}) {
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.careTitle")}</h3><span>{t("dashboard:sentiment.careSubtitle")}</span></div>
      <PaginatedList
        items={data.priorityCare}
        pageSize={5}
        emptyText={t("dashboard:sentiment.careEmpty")}
        rowKey={(row) => String(row.customerId)}
        renderRow={(row) => (
          <button type="button" className="sr-row sr-care-row clickable" onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: t("dashboard:sentiment.careTitle"), blockId: "sr-care" })}>
            <strong className="sr-row-name">{row.name}</strong>
            <span className="sr-row-content">{row.reason}</span>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 產生 5 個獨立的情緒意圖視覺區塊（各自可拖拉與關閉）。非 React 元件，t/locale 由呼叫端傳入。
 * 函式級註解：data 為 null 時各區塊以 LoadingCard 佔位；清單卡內部分頁，點列帶來源區塊跳客戶。
 *
 * @param data 情緒雷達聚合資料（可為 null）
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 formatDateTime 用
 * @returns 5 個情緒視覺區塊
 */
export function sentimentBlocks(
  data: SentimentRadarResponse | null,
  onSelectCustomer: (id: number, source: DrilldownSource) => void,
  t: TFunction,
  locale: string
): DashboardBlock[] {
  return [
    { id: "sr-intent", title: t("dashboard:sentiment.intentTitle"), render: () => data ? <IntentDistributionCard data={data} t={t} /> : <LoadingCard title={t("dashboard:sentiment.intentTitle")} loadingText={t("dashboard:loading")} /> },
    { id: "sr-trend", title: t("dashboard:sentiment.trendTitle"), render: () => data ? <SentimentTrendCard data={data} t={t} /> : <LoadingCard title={t("dashboard:sentiment.trendTitle")} loadingText={t("dashboard:loading")} /> },
    { id: "sr-highrisk", title: t("dashboard:sentiment.highRiskTitle"), wide: true, render: () => data ? <HighRiskCard data={data} onSelectCustomer={onSelectCustomer} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:sentiment.highRiskTitle")} wide loadingText={t("dashboard:loading")} /> },
    { id: "sr-churn", title: t("dashboard:sentiment.churnTitle"), render: () => data ? <ChurnRadarCard data={data} onSelectCustomer={onSelectCustomer} t={t} /> : <LoadingCard title={t("dashboard:sentiment.churnTitle")} loadingText={t("dashboard:loading")} /> },
    { id: "sr-care", title: t("dashboard:sentiment.careTitle"), render: () => data ? <PriorityCareCard data={data} onSelectCustomer={onSelectCustomer} t={t} /> : <LoadingCard title={t("dashboard:sentiment.careTitle")} loadingText={t("dashboard:loading")} /> }
  ];
}
