import { useTranslation } from "react-i18next";
import type { CustomerSummary } from "../../../types";
import { riskLabel } from "../../../lib/format";

/**
 * 客戶列表，支援選取客戶與空結果提示。
 */
export function CustomerList({ customers, selectedId, onSelect, loading }: { customers: CustomerSummary[]; selectedId?: number; onSelect: (id: number) => void; loading?: boolean }) {
  const { t } = useTranslation(["customers", "common"]);
  return (
    <section className="panel customer-list">
      <div className="panel-title">
        <h3>{t("customers:list.title")}</h3>
        <span>{t("customers:list.countSuffix", { count: customers.length })}</span>
      </div>
      {loading ? (
        <div className="skeleton-list">
          {[1, 2, 3].map((n) => <div className="skeleton-row" key={n} />)}
        </div>
      ) : customers.length === 0 ? (
        <div className="empty-state-box">
          <p>{t("customers:list.emptyTitle")}</p>
          <small>{t("customers:list.emptyHint")}</small>
        </div>
      ) : (
        customers.map((customer) => (
          <button className={customer.id === selectedId ? "customer-row active" : "customer-row"} type="button" onClick={() => onSelect(customer.id)} key={customer.id}>
            <span className={`risk-dot ${customer.riskLevel.toLowerCase()}`} />
            <div>
              <strong>{customer.name}</strong>
              <small>{customer.industry} / {customer.ownerName}</small>
            </div>
            <em>{t(riskLabel(customer.riskLevel))}</em>
          </button>
        ))
      )}
    </section>
  );
}
