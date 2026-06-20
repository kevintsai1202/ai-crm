import { useState, type ReactNode } from "react";
import {
  DndContext, DragOverlay, PointerSensor, useSensor, useSensors,
  useDraggable, useDroppable,
  type DragEndEvent, type DragStartEvent
} from "@dnd-kit/core";
import type { CustomerDetail } from "../../../types";
import { formatMoney } from "../../../lib/format";
import { updateOpportunityStage } from "../../../api";

/**
 * 商機 Kanban 看板（支援 @dnd-kit 拖拽切換階段）。
 */
export function OpportunityBoard({ opportunities, onStageChange, onEdit, onDelete }: { opportunities: CustomerDetail["opportunities"]; onStageChange: (opportunityId: number, newStage: string) => void; onEdit: (opportunity: CustomerDetail["opportunities"][number]) => void; onDelete: (opportunity: CustomerDetail["opportunities"][number]) => void }) {
  const stages = ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON"] as const;
  const [activeId, setActiveId] = useState<number | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  /** 拖拽開始時記錄當前拖拽的商機 ID。 */
  function handleDragStart(event: DragStartEvent) {
    setActiveId(Number(event.active.id));
  }

  /** 拖拽結束時呼叫 API 更新商機階段。 */
  function handleDragEnd(event: DragEndEvent) {
    setActiveId(null);
    const { active, over } = event;
    if (!over) return;
    const opportunityId = Number(active.id);
    const newStage = String(over.id);
    const current = opportunities.find((o) => o.id === opportunityId);
    if (current && current.stage !== newStage) {
      onStageChange(opportunityId, newStage);
      updateOpportunityStage(opportunityId, newStage).catch(() => {
        onStageChange(opportunityId, current.stage);
      });
    }
  }

  const activeOpp = opportunities.find((o) => o.id === activeId);

  return (
    <section className="panel">
      <div className="panel-title"><h3>商機看板</h3><span>可拖拽</span></div>
      <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
        <div className="kanban">
          {stages.map((stage) => (
            <KanbanColumn key={stage} stage={stage}>
              {opportunities.filter((o) => o.stage === stage).map((opportunity) => (
                <KanbanCard key={opportunity.id} opportunity={opportunity} onEdit={onEdit} onDelete={onDelete} />
              ))}
            </KanbanColumn>
          ))}
        </div>
        <DragOverlay>
          {activeOpp ? (
            <article className="opportunity-card dragging">
              <span>{activeOpp.type}</span>
              <b>{activeOpp.name}</b>
              <small>{formatMoney(activeOpp.amount)}</small>
            </article>
          ) : null}
        </DragOverlay>
      </DndContext>
    </section>
  );
}

/**
 * Kanban 階段欄位（Droppable 容器）。
 */
function KanbanColumn({ stage, children }: { stage: string; children: ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: stage });
  return (
    <div ref={setNodeRef} className={`kanban-col${isOver ? " drag-over" : ""}`}>
      <strong>{stage}</strong>
      {children}
    </div>
  );
}

/**
 * 可拖拽的商機卡片（Draggable）。
 * 函式級註解：卡片右上提供編輯 / 刪除小圖示；按鈕以 stopPropagation 阻斷 dnd-kit 拖拽啟動，
 * 避免點按鈕時誤觸拖拽（onPointerDown 與 onClick 皆需攔截）。
 *
 * @param opportunity 商機資料
 * @param onEdit 點擊編輯 callback
 * @param onDelete 點擊刪除 callback
 */
function KanbanCard({ opportunity, onEdit, onDelete }: { opportunity: CustomerDetail["opportunities"][number]; onEdit: (opportunity: CustomerDetail["opportunities"][number]) => void; onDelete: (opportunity: CustomerDetail["opportunities"][number]) => void }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: opportunity.id });
  return (
    <article
      ref={setNodeRef}
      className={`opportunity-card${isDragging ? " dragging" : ""}`}
      {...listeners}
      {...attributes}
    >
      <div className="card-actions">
        {/* 阻斷拖拽：按下與點擊都停止事件冒泡，避免觸發 @dnd-kit 的 PointerSensor */}
        <button
          type="button"
          className="card-icon-btn"
          title="編輯商機"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => { e.stopPropagation(); onEdit(opportunity); }}
        >✏️</button>
        <button
          type="button"
          className="card-icon-btn"
          title="刪除商機"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => { e.stopPropagation(); onDelete(opportunity); }}
        >🗑️</button>
      </div>
      <span>{opportunity.type}</span>
      <b>{opportunity.name}</b>
      <small>{formatMoney(opportunity.amount)}</small>
    </article>
  );
}
