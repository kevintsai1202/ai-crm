import { useState } from "react";
import { useNavigate } from "react-router-dom";
import type { CustomerDetail } from "../../../types";
import { formatMoney, stageLabel } from "../../../lib/format";
import { updateOpportunityStage } from "../../../api";
import { CloseOpportunityModal } from "./CloseOpportunityModal";

/** 商機階段全集(依銷售流程排序);看板依此分欄,下拉亦用此清單。 */
const STAGES = ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON", "CLOSED_LOST"] as const;

/** 結案階段集合（選到時需先收集輸贏原因）。 */
const CLOSE_STAGES = ["CLOSED_WON", "CLOSED_LOST"];

/**
 * 商機看板:依階段分欄呈現 pipeline,改階段以卡片下拉選單操作(可靠、支援觸控與鍵盤)。
 * 選到結案階段(CLOSED_WON/CLOSED_LOST)時先彈出結案原因 Modal 收集 closeReason,再送出。
 *
 * @param opportunities 商機清單
 * @param onStageChange 階段變更(樂觀更新本地)callback
 * @param onEdit 點擊編輯 callback
 * @param onDelete 點擊刪除 callback
 */
export function OpportunityBoard({ customerId, opportunities, onStageChange, onEdit, onDelete }: {
  customerId: number;
  opportunities: CustomerDetail["opportunities"];
  onStageChange: (opportunityId: number, newStage: string) => void;
  onEdit: (opportunity: CustomerDetail["opportunities"][number]) => void;
  onDelete: (opportunity: CustomerDetail["opportunities"][number]) => void;
}) {
  /** 待結案的商機（選到結案階段時暫存,待 Modal 填原因後送出）。 */
  const [pendingClose, setPendingClose] = useState<{ id: number; stage: "CLOSED_WON" | "CLOSED_LOST"; current: string } | null>(null);
  const navigate = useNavigate();

  /**
   * 樂觀更新本地並呼叫 API;失敗回滾。close 為結案資訊(選填)。
   *
   * @param opportunityId 商機 ID
   * @param newStage 新階段
   * @param currentStage 原階段(回滾用)
   * @param close 結案資訊(closeReason/closeReasonNote/actualCloseDate),非結案時不帶
   */
  function commitStage(opportunityId: number, newStage: string, currentStage: string,
                       close?: { closeReason: string; closeReasonNote: string; actualCloseDate: string }) {
    onStageChange(opportunityId, newStage);
    updateOpportunityStage(opportunityId, newStage, close).catch(() => {
      // API 失敗回滾本地狀態,避免畫面與後端不一致
      onStageChange(opportunityId, currentStage);
    });
  }

  /**
   * 變更商機階段:結案階段先開 Modal 收原因,其餘直接送出。
   *
   * @param opportunityId 商機 ID
   * @param newStage 新階段
   * @param currentStage 原階段(回滾用)
   */
  function changeStage(opportunityId: number, newStage: string, currentStage: string) {
    if (newStage === currentStage) return;
    if (CLOSE_STAGES.includes(newStage)) {
      // 結案階段:暫存待 Modal 填原因(此時不改本地,select 維持原值)
      setPendingClose({ id: opportunityId, stage: newStage as "CLOSED_WON" | "CLOSED_LOST", current: currentStage });
      return;
    }
    commitStage(opportunityId, newStage, currentStage);
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
                    <button type="button" className="card-icon-btn" title="商機智能" data-testid={`oi-open-${opportunity.id}`} onClick={() => navigate(`/opportunities/${opportunity.id}/intelligence?customerId=${customerId}`)}>📊</button>
                    <button type="button" className="card-icon-btn" title="編輯商機" onClick={() => onEdit(opportunity)}>✏️</button>
                    <button type="button" className="card-icon-btn" title="刪除商機" onClick={() => onDelete(opportunity)}>🗑️</button>
                  </div>
                  <span>{opportunity.type}</span>
                  <b>{opportunity.name}</b>
                  <small>{formatMoney(opportunity.amount)}</small>
                  {/* 負責業務(SP8);未指派時不顯示 */}
                  {opportunity.ownerName ? <small className="opportunity-owner">負責：{opportunity.ownerName}</small> : null}
                  {/* 階段下拉:取代拖拽,改階段即送出(結案階段先彈 Modal) */}
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
      {/* 結案原因 Modal:選到結案階段時出現,確認後帶 closeReason 送出 */}
      {pendingClose ? (
        <CloseOpportunityModal
          stage={pendingClose.stage}
          onSubmit={(data) => {
            commitStage(pendingClose.id, pendingClose.stage, pendingClose.current, data);
            setPendingClose(null);
          }}
          onClose={() => setPendingClose(null)}
        />
      ) : null}
    </section>
  );
}
