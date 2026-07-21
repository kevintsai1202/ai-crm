import type { DrilldownResponse } from "../../types";
import { formatMoney, formatCompactMoney, riskLabel } from "../../lib/format";
import i18n from "../../i18n";
import { useTranslation } from "react-i18next";

/** 後端下鑽狀態目前回傳固定中文值；集中為常數，只用於正規化，不直接顯示。 */
const COMPLETED_STATUS = "已完成";
const LOST_STATUS = "已流失";

/**
 * 圖表下鑽明細 Modal：列出某段落底層的商機/客戶，點項目可跳到客戶詳情。
 */
export function DrilldownModal({ state, onSelectCustomer, onClose }: { state: { loading: boolean; title: string; data: DrilldownResponse | null }; onSelectCustomer: (id: number) => void; onClose: () => void }) {
  const { t } = useTranslation("common");
  const data = state.data;
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{state.title}</h3>
            {data ? <small>{t("drilldown.summary", { count: data.count, amount: formatMoney(data.totalAmount, i18n.language) })}</small> : null}
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label={t("actions.close")}>✕</button>
        </div>
        <div className="report-body">
          {state.loading ? (
            <p className="chat-typing">{t("drilldown.loading")}<span>…</span></p>
          ) : !data || data.items.length === 0 ? (
            <div className="empty-state-box"><p>{t("drilldown.empty")}</p></div>
          ) : (
            <div className="drill-list">
              {data.items.map((item, i) => (
                <button type="button" className="drill-item" key={`${item.customerId}-${i}`} onClick={() => onSelectCustomer(item.customerId)}>
                  <div className="drill-main">
                    <strong>{item.customerName}</strong>
                    <span className="drill-sub">{item.title ? item.title : `${item.industry} / ${item.ownerName}`}</span>
                  </div>
                  <div className="drill-meta">
                    {item.status ? <span className={`drill-status ${item.status === COMPLETED_STATUS ? "done" : item.status === LOST_STATUS ? "lost" : "active"}`}>
                      {item.status === COMPLETED_STATUS ? t("drilldown.status.completed") : item.status === LOST_STATUS ? t("drilldown.status.lost") : item.status}
                    </span> : null}
                    {item.riskLevel ? <span className={`risk-badge ${item.riskLevel.toLowerCase()}`}>{i18n.t(riskLabel(item.riskLevel))}</span> : null}
                    {item.amount ? <b>{formatCompactMoney(item.amount, i18n.language)}</b> : null}
                    {item.date ? <small>{item.date}</small> : null}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="report-footer">
          <button type="button" onClick={onClose}>{t("actions.close")}</button>
        </div>
      </div>
    </div>
  );
}
