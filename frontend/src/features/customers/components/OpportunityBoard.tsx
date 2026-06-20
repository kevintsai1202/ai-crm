import type { CustomerDetail } from "../../../types";
import { formatMoney, stageLabel } from "../../../lib/format";
import { updateOpportunityStage } from "../../../api";

/** 商機階段全集(依銷售流程排序);看板依此分欄,下拉亦用此清單。 */
const STAGES = ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON", "CLOSED_LOST"] as const;

/**
 * 商機看板:依階段分欄呈現 pipeline,改階段以卡片下拉選單操作(可靠、支援觸控與鍵盤)。
 *
 * @param opportunities 商機清單
 * @param onStageChange 階段變更(樂觀更新本地)callback
 * @param onEdit 點擊編輯 callback
 * @param onDelete 點擊刪除 callback
 */
export function OpportunityBoard({ opportunities, onStageChange, onEdit, onDelete }: {
  opportunities: CustomerDetail["opportunities"];
  onStageChange: (opportunityId: number, newStage: string) => void;
  onEdit: (opportunity: CustomerDetail["opportunities"][number]) => void;
  onDelete: (opportunity: CustomerDetail["opportunities"][number]) => void;
}) {
  /**
   * 變更商機階段:先樂觀更新本地,再呼叫 API;失敗則回滾為原階段。
   *
   * @param opportunityId 商機 ID
   * @param newStage 新階段
   * @param currentStage 原階段(回滾用)
   */
  function changeStage(opportunityId: number, newStage: string, currentStage: string) {
    if (newStage === currentStage) return;
    onStageChange(opportunityId, newStage);
    updateOpportunityStage(opportunityId, newStage).catch(() => {
      // API 失敗回滾本地狀態,避免畫面與後端不一致
      onStageChange(opportunityId, currentStage);
    });
  }

  return (
    <section className="panel">
      <div className="panel-title"><h3>商機看板</h3><span>依階段分欄</span></div>
      <div className="kanban">
        {STAGES.map((stage) => {
          const items = opportunities.filter((o) => o.stage === stage);
          return (
            <div className="kanban-col" key={stage}>
              <strong>{stageLabel(stage)}<span className="kanban-count">{items.length}</span></strong>
              {items.map((opportunity) => (
                <article className="opportunity-card" key={opportunity.id}>
                  <div className="card-actions">
                    <button type="button" className="card-icon-btn" title="編輯商機" onClick={() => onEdit(opportunity)}>✏️</button>
                    <button type="button" className="card-icon-btn" title="刪除商機" onClick={() => onDelete(opportunity)}>🗑️</button>
                  </div>
                  <span>{opportunity.type}</span>
                  <b>{opportunity.name}</b>
                  <small>{formatMoney(opportunity.amount)}</small>
                  {/* 階段下拉:取代拖拽,改階段即送出 */}
                  <select
                    className="stage-select"
                    value={opportunity.stage}
                    onChange={(e) => changeStage(opportunity.id, e.target.value, opportunity.stage)}
                  >
                    {STAGES.map((s) => (
                      <option key={s} value={s}>{stageLabel(s)}</option>
                    ))}
                  </select>
                </article>
              ))}
            </div>
          );
        })}
      </div>
    </section>
  );
}
