import type { DrilldownSource, RfmResponse } from "../../../types";
import { formatCompactMoney } from "../../../lib/format";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * 將分群標籤對應到 CSS class（用於不同顏色的色票）。
 */
function segmentClass(segment: string) {
  const map: Record<string, string> = {
    "冠軍客戶": "champion",
    "忠誠客戶": "loyal",
    "具潛力": "potential",
    "需關注": "attention",
    "瀕危流失": "atrisk"
  };
  return map[segment] || "potential";
}

/**
 * RFM 客戶分群區塊：列出每位客戶的 R/F/M 分數與分群標籤，點擊跳到操作頁客戶詳情。
 * 函式級註解：R 為距今最後互動天數（越小越好）、F 為互動次數、M 為商機金額；rScore/fScore/mScore 為 1-5 分級。
 */
function RfmCard({ data, onSelectCustomer }: { data: RfmResponse[]; onSelectCustomer: (id: number, source: DrilldownSource) => void }) {
  return (
    <article className="panel report-card wide" data-promo-chart="rfm">
      <div className="panel-title">
        <h3>RFM 客戶分群</h3>
        <span>點擊跳到客戶 · R 近期 / F 頻率 / M 金額</span>
      </div>
      <div className="rfm-table">
        <div className="rfm-head">
          <span>客戶</span>
          <span>分群</span>
          <span>R</span>
          <span>F</span>
          <span>M</span>
          <span>金額</span>
        </div>
        {data.map((row) => (
          <button type="button" className="rfm-row clickable" key={row.customerId} onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: "RFM 客戶分群", blockId: "rfm" })}>
            <strong>{row.name}</strong>
            <span className={`rfm-seg ${segmentClass(row.segment)}`}>{row.segment}</span>
            <em title={`距上次互動 ${row.recencyDays} 天`}>{row.rScore}</em>
            <em title={`互動 ${row.frequency} 次`}>{row.fScore}</em>
            <em>{row.mScore}</em>
            <b>{formatCompactMoney(row.monetary)}</b>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 產生 RFM 客戶分群區塊（可拖拉與關閉）。
 *
 * @param data RFM 分群清單（可為 null）
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @returns RFM 區塊
 */
export function rfmBlock(data: RfmResponse[] | null, onSelectCustomer: (id: number, source: DrilldownSource) => void): DashboardBlock {
  return {
    id: "rfm",
    title: "RFM 客戶分群",
    wide: true,
    render: () => data ? <RfmCard data={data} onSelectCustomer={onSelectCustomer} /> : <LoadingCard title="RFM 分群" wide />
  };
}
