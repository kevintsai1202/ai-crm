import type { AgentTraceResponse, CustomerDetail } from "../../../types";
import { riskLabel, formatMoney, formatDateTime } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";
import { Timeline } from "./Timeline";
import { OpportunityBoard } from "./OpportunityBoard";
import { TracePanel } from "../../agent-trace/TracePanel";

/**
 * 客戶詳情、商機、AI 與 Trace 的主內容。
 */
export function CustomerDetailPanel({ detail, loading, trace, onStageChange, onOpenChat, onAssess, userRole }: { detail: CustomerDetail | null; loading: boolean; trace: AgentTraceResponse | null; onStageChange: (opportunityId: number, newStage: string) => void; onOpenChat: () => void; onAssess: () => void; userRole?: string }) {
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
          {userRole === "ADMIN" ? <button type="button" className="btn-danger">刪除客戶</button> : null}
          {userRole === "MANAGER" || userRole === "ADMIN" ? <button type="button" className="btn-secondary">審核建議</button> : null}
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
          <span className="kpi-value">{formatDateTime(detail.customer.renewalDueDate)}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">最近互動</span>
          <span className="kpi-value">{formatDateTime(detail.customer.lastInteractionAt)}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">客戶狀態</span>
          <span className="kpi-value">{statusLabels[detail.customer.status] ?? detail.customer.status}</span>
        </div>
      </div>
      <div className="detail-grid">
        <Timeline interactions={detail.interactions} />
        <OpportunityBoard opportunities={detail.opportunities} onStageChange={onStageChange} />
      </div>
      <div className="detail-grid">
        <TracePanel trace={trace} />
      </div>
    </section>
  );
}
