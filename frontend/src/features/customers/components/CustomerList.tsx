import type { CustomerSummary } from "../../../types";
import { riskLabel } from "../../../lib/format";

/**
 * 客戶列表，支援選取客戶與空結果提示。
 */
export function CustomerList({ customers, selectedId, onSelect, loading }: { customers: CustomerSummary[]; selectedId?: number; onSelect: (id: number) => void; loading?: boolean }) {
  return (
    <section className="panel customer-list">
      <div className="panel-title">
        <h3>客戶列表</h3>
        <span>{customers.length} 筆</span>
      </div>
      {loading ? (
        <div className="skeleton-list">
          {[1, 2, 3].map((n) => <div className="skeleton-row" key={n} />)}
        </div>
      ) : customers.length === 0 ? (
        <div className="empty-state-box">
          <p>查無符合條件的客戶</p>
          <small>請調整搜尋條件或清除篩選</small>
        </div>
      ) : (
        customers.map((customer) => (
          <button className={customer.id === selectedId ? "customer-row active" : "customer-row"} type="button" onClick={() => onSelect(customer.id)} key={customer.id}>
            <span className={`risk-dot ${customer.riskLevel.toLowerCase()}`} />
            <div>
              <strong>{customer.name}</strong>
              <small>{customer.industry} / {customer.ownerName}</small>
            </div>
            <em>{riskLabel(customer.riskLevel)}</em>
          </button>
        ))
      )}
    </section>
  );
}
