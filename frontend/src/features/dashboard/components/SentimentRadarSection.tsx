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
function IntentDistributionCard({ data }: { data: SentimentRadarResponse }) {
  // 意圖分布長條的最大值，用於計算每列長條寬度比例
  const intentMax = Math.max(1, ...data.intentDistribution.map((i) => i.count));
  return (
    <article className="panel report-card sr-card" data-promo-chart="sentiment-intent">
      <div className="panel-title"><h3>意圖分布</h3><span>互動意圖分類統計</span></div>
      <div className="sr-intent-list">
        {data.intentDistribution.length === 0 ? (
          <div className="sr-empty">尚無意圖資料</div>
        ) : (
          data.intentDistribution.map((row) => {
            const label = intentLabel(row.intent) || (row.intent === "OTHER" ? "其他" : row.intent);
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
function SentimentTrendCard({ data }: { data: SentimentRadarResponse }) {
  // 情緒趨勢每月三色堆疊長條的最大總量，用於正規化高度
  const trendMax = Math.max(1, ...data.sentimentTrend.map((p) => p.positive + p.neutral + p.negative));
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>情緒趨勢</h3><span>近 12 月 · 正向 / 中性 / 負向</span></div>
      {data.sentimentTrend.length === 0 ? (
        <div className="sr-empty">尚無情緒趨勢資料</div>
      ) : (
        <>
          <div className="sr-trend-chart">
            {data.sentimentTrend.map((p) => {
              const total = p.positive + p.neutral + p.negative;
              return (
                <div className="sr-trend-col" key={p.month} title={`${p.month}｜正 ${p.positive} / 中 ${p.neutral} / 負 ${p.negative}`}>
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
            <span><i className="sr-dot pos" />正向</span>
            <span><i className="sr-dot neu" />中性</span>
            <span><i className="sr-dot neg" />負向</span>
          </div>
        </>
      )}
    </article>
  );
}

/**
 * 視覺 3：高風險互動清單卡。
 */
function HighRiskCard({ data, onSelectCustomer }: { data: SentimentRadarResponse; onSelectCustomer: (id: number, source: DrilldownSource) => void }) {
  return (
    <article className="panel report-card wide sr-card">
      <div className="panel-title"><h3>高風險互動</h3><span>負向情緒且意圖為流失 / 客訴 · 點擊跳客戶</span></div>
      <PaginatedList
        items={data.highRiskInteractions}
        pageSize={5}
        emptyText="目前無高風險互動"
        rowKey={(item, i) => `${item.customerId}-${i}`}
        renderRow={(item) => (
          <button type="button" className="sr-row clickable" onClick={() => onSelectCustomer(item.customerId, { from: "dashboard", section: "高風險互動", blockId: "sr-highrisk" })}>
            <span className={`sr-dot ${sentimentClass(item.sentiment)}`} />
            <strong className="sr-row-name">{item.customerName}</strong>
            {intentLabel(item.intent) ? <span className="sr-tag">{intentLabel(item.intent)}</span> : null}
            <span className="sr-row-content">{item.content}</span>
            <em className="sr-row-time">{formatDateTime(item.occurredAt)}</em>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 視覺 4：流失雷達（依加權分數排序）。
 */
function ChurnRadarCard({ data, onSelectCustomer }: { data: SentimentRadarResponse; onSelectCustomer: (id: number, source: DrilldownSource) => void }) {
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>流失雷達</h3><span>負面 / 流失 / 客訴加權排序 · 點擊跳客戶</span></div>
      <PaginatedList
        items={data.churnRadar}
        pageSize={5}
        emptyText="尚無流失風險客戶"
        rowKey={(row) => String(row.customerId)}
        renderRow={(row) => (
          <button type="button" className="sr-row sr-churn-row clickable" onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: "流失雷達", blockId: "sr-churn" })}>
            <strong className="sr-row-name">{row.name}</strong>
            <span className="sr-churn-meta" title={`負面 ${row.negativeCount} · 流失 ${row.churnSignalCount} · 客訴 ${row.complaintCount}`}>
              負 {row.negativeCount} · 流 {row.churnSignalCount} · 訴 {row.complaintCount}
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
function PriorityCareCard({ data, onSelectCustomer }: { data: SentimentRadarResponse; onSelectCustomer: (id: number, source: DrilldownSource) => void }) {
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>優先關懷</h3><span>建議優先聯繫客戶 · 點擊跳客戶</span></div>
      <PaginatedList
        items={data.priorityCare}
        pageSize={5}
        emptyText="尚無優先關懷對象"
        rowKey={(row) => String(row.customerId)}
        renderRow={(row) => (
          <button type="button" className="sr-row sr-care-row clickable" onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: "優先關懷", blockId: "sr-care" })}>
            <strong className="sr-row-name">{row.name}</strong>
            <span className="sr-row-content">{row.reason}</span>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 產生 5 個獨立的情緒意圖視覺區塊（各自可拖拉與關閉）。
 * 函式級註解：data 為 null 時各區塊以 LoadingCard 佔位；清單卡內部分頁，點列帶來源區塊跳客戶。
 *
 * @param data 情緒雷達聚合資料（可為 null）
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @returns 5 個情緒視覺區塊
 */
export function sentimentBlocks(
  data: SentimentRadarResponse | null,
  onSelectCustomer: (id: number, source: DrilldownSource) => void
): DashboardBlock[] {
  return [
    { id: "sr-intent", title: "意圖分布", render: () => data ? <IntentDistributionCard data={data} /> : <LoadingCard title="意圖分布" /> },
    { id: "sr-trend", title: "情緒趨勢", render: () => data ? <SentimentTrendCard data={data} /> : <LoadingCard title="情緒趨勢" /> },
    { id: "sr-highrisk", title: "高風險互動", wide: true, render: () => data ? <HighRiskCard data={data} onSelectCustomer={onSelectCustomer} /> : <LoadingCard title="高風險互動" wide /> },
    { id: "sr-churn", title: "流失雷達", render: () => data ? <ChurnRadarCard data={data} onSelectCustomer={onSelectCustomer} /> : <LoadingCard title="流失雷達" /> },
    { id: "sr-care", title: "優先關懷", render: () => data ? <PriorityCareCard data={data} onSelectCustomer={onSelectCustomer} /> : <LoadingCard title="優先關懷" /> }
  ];
}
