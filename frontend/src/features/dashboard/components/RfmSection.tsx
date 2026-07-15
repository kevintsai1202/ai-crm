import type { TFunction } from "i18next";
import type { DrilldownSource, RfmResponse } from "../../../types";
import { formatCompactMoney, rfmSegmentLabel } from "../../../lib/format";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * 將分群標籤對應到 CSS class（用於不同顏色的色票）。分群文字本身來自後端固定中文字面值，
 * class 對照與翻譯（rfmSegmentLabel）各自獨立維護，互不影響。
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
function RfmCard({ data, onSelectCustomer, t, locale }: {
  data: RfmResponse[];
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
  locale: string;
}) {
  return (
    <article className="panel report-card wide" data-promo-chart="rfm">
      <div className="panel-title">
        <h3>{t("dashboard:rfm.title")}</h3>
        <span>{t("dashboard:rfm.subtitle")}</span>
      </div>
      <div className="rfm-table">
        <div className="rfm-head">
          <span>{t("dashboard:rfm.colCustomer")}</span>
          <span>{t("dashboard:rfm.colSegment")}</span>
          <span>{t("dashboard:rfm.colR")}</span>
          <span>{t("dashboard:rfm.colF")}</span>
          <span>{t("dashboard:rfm.colM")}</span>
          <span>{t("dashboard:rfm.colAmount")}</span>
        </div>
        {data.map((row) => (
          <button type="button" className="rfm-row clickable" key={row.customerId} onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: t("dashboard:rfm.title"), blockId: "rfm" })}>
            <strong>{row.name}</strong>
            <span className={`rfm-seg ${segmentClass(row.segment)}`}>{t(rfmSegmentLabel(row.segment))}</span>
            <em title={t("dashboard:rfm.recencyTitle", { days: row.recencyDays })}>{row.rScore}</em>
            <em title={t("dashboard:rfm.frequencyTitle", { count: row.frequency })}>{row.fScore}</em>
            <em>{row.mScore}</em>
            <b>{formatCompactMoney(row.monetary, locale)}</b>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 產生 RFM 客戶分群區塊（可拖拉與關閉）。非 React 元件，t/locale 由呼叫端傳入。
 *
 * @param data RFM 分群清單（可為 null）
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 formatCompactMoney 用
 * @returns RFM 區塊
 */
export function rfmBlock(
  data: RfmResponse[] | null,
  onSelectCustomer: (id: number, source: DrilldownSource) => void,
  t: TFunction,
  locale: string
): DashboardBlock {
  return {
    id: "rfm",
    title: t("dashboard:rfm.title"),
    wide: true,
    render: () => data
      ? <RfmCard data={data} onSelectCustomer={onSelectCustomer} t={t} locale={locale} />
      : <LoadingCard title={t("dashboard:rfm.title")} wide loadingText={t("dashboard:loading")} />
  };
}
