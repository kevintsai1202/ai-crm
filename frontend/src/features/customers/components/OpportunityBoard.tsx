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
export function OpportunityBoard({ opportunities, onStageChange }: { opportunities: CustomerDetail["opportunities"]; onStageChange: (opportunityId: number, newStage: string) => void }) {
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
                <KanbanCard key={opportunity.id} opportunity={opportunity} />
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
 */
function KanbanCard({ opportunity }: { opportunity: CustomerDetail["opportunities"][number] }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: opportunity.id });
  return (
    <article
      ref={setNodeRef}
      className={`opportunity-card${isDragging ? " dragging" : ""}`}
      {...listeners}
      {...attributes}
    >
      <span>{opportunity.type}</span>
      <b>{opportunity.name}</b>
      <small>{formatMoney(opportunity.amount)}</small>
    </article>
  );
}
