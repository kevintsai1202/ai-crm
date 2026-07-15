import type { DrilldownResponse } from "../../types";
import { formatMoney, formatCompactMoney, riskLabel } from "../../lib/format";
import i18n from "../../i18n";

/**
 * 圖表下鑽明細 Modal：列出某段落底層的商機/客戶，點項目可跳到客戶詳情。
 */
export function DrilldownModal({ state, onSelectCustomer, onClose }: { state: { loading: boolean; title: string; data: DrilldownResponse | null }; onSelectCustomer: (id: number) => void; onClose: () => void }) {
  const data = state.data;
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{state.title}</h3>
            {data ? <small>{data.count} 筆 · 金額合計 {formatMoney(data.totalAmount, "zh-TW")}</small> : null}
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>
        <div className="report-body">
          {state.loading ? (
            <p className="chat-typing">載入明細中<span>…</span></p>
          ) : !data || data.items.length === 0 ? (
            <div className="empty-state-box"><p>查無明細</p></div>
          ) : (
            <div className="drill-list">
              {data.items.map((item, i) => (
                <button type="button" className="drill-item" key={`${item.customerId}-${i}`} onClick={() => onSelectCustomer(item.customerId)}>
                  <div className="drill-main">
                    <strong>{item.customerName}</strong>
                    <span className="drill-sub">{item.title ? item.title : `${item.industry} / ${item.ownerName}`}</span>
                  </div>
                  <div className="drill-meta">
                    {item.status ? <span className={`drill-status ${item.status === "已完成" ? "done" : item.status === "已流失" ? "lost" : "active"}`}>{item.status}</span> : null}
                    {item.riskLevel ? <span className={`risk-badge ${item.riskLevel.toLowerCase()}`}>{i18n.t(riskLabel(item.riskLevel), { lng: "zh-TW" })}</span> : null}
                    {item.amount ? <b>{formatCompactMoney(item.amount, "zh-TW")}</b> : null}
                    {item.date ? <small>{item.date}</small> : null}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="report-footer">
          <button type="button" onClick={onClose}>關閉</button>
        </div>
      </div>
    </div>
  );
}
