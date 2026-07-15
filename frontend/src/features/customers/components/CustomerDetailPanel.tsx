import type { ContactResponse, CustomerDetail, OpportunityResponse } from "../../../types";
import { riskLabel, formatMoney, formatDate } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";
import { Timeline } from "./Timeline";
import { OpportunityBoard } from "./OpportunityBoard";
import { ContactsPanel } from "./ContactsPanel";
import { UpcomingPanel } from "./UpcomingPanel";

/**
 * 客戶詳情、聯絡人、商機、互動、AI 與 Trace 的主內容。
 * 函式級註解：所有 CRUD 操作的入口（編輯 / 刪除客戶、聯絡人、商機、互動）皆在此鋪設，
 * 實際邏輯與重載集中於上層 CustomersPage，透過 props 傳入。
 */
export function CustomerDetailPanel({
  detail,
  loading,
  onStageChange,
  onOpenChat,
  onAssess,
  onOpenAiHistory,
  onEditCustomer,
  onDeleteCustomer,
  onAddContact,
  onEditContact,
  onDeleteContact,
  onEditOpportunity,
  onDeleteOpportunity,
  onEditInteraction,
  onDeleteInteraction,
  userRole
}: {
  detail: CustomerDetail | null;
  loading: boolean;
  onStageChange: (opportunityId: number, newStage: string) => void;
  onOpenChat: () => void;
  onAssess: () => void;
  onOpenAiHistory: () => void;
  onEditCustomer: () => void;
  onDeleteCustomer: () => void;
  onAddContact: () => void;
  onEditContact: (contact: ContactResponse) => void;
  onDeleteContact: (contact: ContactResponse) => void;
  onEditOpportunity: (opportunity: OpportunityResponse) => void;
  onDeleteOpportunity: (opportunity: OpportunityResponse) => void;
  onEditInteraction: (interaction: CustomerDetail["interactions"][number]) => void;
  onDeleteInteraction: (interaction: CustomerDetail["interactions"][number]) => void;
  userRole?: string;
}) {
  // 載入中且尚無詳情：顯示 skeleton，避免空白久候
  if (!detail && loading) {
    return (
      <section className="detail-stack" aria-busy="true" aria-label="載入客戶詳情中">
        <div className="panel customer-hero skeleton-block">
          <div className="skeleton-line w-30" />
          <div className="skeleton-line w-50 h-lg" />
          <div className="skeleton-line w-40" />
        </div>
        <div className="kpi-row">
          {[0, 1, 2, 3].map((i) => (
            <div className="kpi-card skeleton-block" key={i}>
              <div className="skeleton-line w-40" />
              <div className="skeleton-line w-60 h-lg" />
            </div>
          ))}
        </div>
        <div className="panel skeleton-block">
          <div className="skeleton-line w-30" />
          <div className="skeleton-line" />
          <div className="skeleton-line" />
          <div className="skeleton-line w-70" />
        </div>
      </section>
    );
  }
  if (!detail) {
    return <section className="panel empty-state">尚未選取客戶</section>;
  }
  // 客戶狀態英文 enum 轉中文小對照（僅 KPI 摘要使用）
  const statusLabels: Record<string, string> = { ACTIVE: "使用中", INACTIVE: "停用", LEVERAGED: "重點客戶" };
  return (
    <section className="detail-stack">
      <div className="panel customer-hero">
        <div>
          <small>{detail.customer.industry}</small>
          <h3>{detail.customer.name}</h3>
          <p>{detail.customer.email} / {detail.customer.phone}</p>
        </div>
        <div className="hero-actions">
          <span className={`risk-badge ${detail.customer.riskLevel.toLowerCase()}`}>{riskLabel(detail.customer.riskLevel)}</span>
          <button type="button" className="btn-primary" onClick={onAssess}>🩺 整體評估<AiBadge onDark /></button>
          <button type="button" className="btn-primary" onClick={onOpenChat}>💬 詢問 AI 助理<AiBadge onDark /></button>
          <button type="button" className="btn-secondary" onClick={onOpenAiHistory}>🧭 AI 歷程</button>
          <button type="button" className="btn-secondary" onClick={onEditCustomer}>✏️ 編輯客戶</button>
          {userRole === "ADMIN" ? <button type="button" className="btn-danger" onClick={onDeleteCustomer}>刪除客戶</button> : null}
        </div>
      </div>
      {loading ? <div className="loading-line">資料更新中...</div> : null}
      {/* KPI 摘要卡：橫向凸顯商機金額 / 合約到期 / 最近互動 / 客戶狀態四個關鍵指標 */}
      <div className="kpi-row">
        <div className="kpi-card">
          <span className="kpi-label">商機金額</span>
          <span className="kpi-value kpi-value-accent">{formatMoney(detail.customer.opportunityAmount)}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">合約到期</span>
          <span className="kpi-value">{formatDate(detail.customer.renewalDueDate)}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">最近互動</span>
          <span className="kpi-value">{formatDate(detail.customer.lastInteractionAt)}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">客戶狀態</span>
          <span className="kpi-value">{statusLabels[detail.customer.status] ?? detail.customer.status}</span>
        </div>
      </div>
      {/* 本週待跟進:未來 7 天的即將互動 / 續約到期 / 商機成交,置於上方提醒主動跟進 */}
      <UpcomingPanel detail={detail} />
      <ContactsPanel
        contacts={detail.contacts}
        onAdd={onAddContact}
        onEdit={onEditContact}
        onDelete={onDeleteContact}
      />
      {/* 時間線改橫向 banner、商機看板含 5 欄,皆改整列全寬呈現(原本半寬欄會擠) */}
      <Timeline interactions={detail.interactions} onEdit={onEditInteraction} onDelete={onDeleteInteraction} />
      <OpportunityBoard customerId={detail.customer.id} opportunities={detail.opportunities} onStageChange={onStageChange} onEdit={onEditOpportunity} onDelete={onDeleteOpportunity} />
    </section>
  );
}
