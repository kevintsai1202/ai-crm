import { useEffect, useState, type FormEvent } from "react";
import { createTask } from "../../api/index";
import type { CrmTask } from "../../types";

interface TaskFormModalProps {
  customerId: number;
  customerName?: string;
  assigneeId: number;
  onCreated: (task: CrmTask) => void;
  onClose: () => void;
}

/** 將 Date 轉成 datetime-local 可編輯字串。 */
function toLocalInput(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

/** 建立電話追蹤任務 Modal；正式狀態只由建立 API 回應產生。 */
export function TaskFormModal({ customerId, customerName, assigneeId, onCreated, onClose }: TaskFormModalProps) {
  const startDefault = new Date(Date.now() + 60 * 60_000);
  const [title, setTitle] = useState(`電話追蹤${customerName ? `－${customerName}` : ""}`);
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<CrmTask["priority"]>("NORMAL");
  const [scheduledStart, setScheduledStart] = useState(toLocalInput(startDefault));
  const [scheduledEnd, setScheduledEnd] = useState(toLocalInput(new Date(startDefault.getTime() + 30 * 60_000)));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    /** 按 Escape 關閉 Modal，與可見關閉按鈕行為一致。 */
    const handleEscape = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [onClose]);

  /** 驗證時間後建立正式 PHONE_CALL 任務。 */
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (scheduledEnd <= scheduledStart) {
      setError("結束時間必須晚於開始時間。");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const created = await createTask({
        customerId, opportunityId: null, contactId: null, type: "PHONE_CALL", priority,
        title: title.trim(), description: description.trim() || null, assigneeId,
        scheduledStart: `${scheduledStart}:00`, scheduledEnd: `${scheduledEnd}:00`, source: "MANUAL",
      });
      onCreated(created);
    } catch {
      setError("建立任務失敗，請確認資料後重試。");
    } finally {
      setSaving(false);
    }
  }

  return <div className="modal-overlay" onClick={onClose}>
    <form className="modal-content task-form-modal" role="dialog" aria-modal="true" aria-labelledby="task-form-title" onSubmit={handleSubmit} onClick={(event) => event.stopPropagation()}>
      <div className="modal-header"><h3 id="task-form-title">安排電話追蹤</h3><button type="button" className="chat-close" aria-label="關閉安排電話追蹤" onClick={onClose}>✕</button></div>
      <label>客戶<input value={customerName ?? `客戶 #${customerId}`} disabled /></label>
      <label>標題<input name="taskTitle" required value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label>優先級<select name="taskPriority" value={priority} onChange={(event) => setPriority(event.target.value as CrmTask["priority"])}><option value="LOW">低</option><option value="NORMAL">一般</option><option value="HIGH">高</option><option value="URGENT">緊急</option></select></label>
      <label>開始時間<input name="taskStart" type="datetime-local" required value={scheduledStart} onChange={(event) => setScheduledStart(event.target.value)} /></label>
      <label>結束時間<input name="taskEnd" type="datetime-local" required value={scheduledEnd} onChange={(event) => setScheduledEnd(event.target.value)} /></label>
      <label>說明<textarea name="taskDescription" value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="modal-actions"><button type="button" className="btn-secondary" onClick={onClose}>取消</button><button type="submit" disabled={saving}>{saving ? "建立中…" : "建立電話任務"}</button></div>
    </form>
  </div>;
}
