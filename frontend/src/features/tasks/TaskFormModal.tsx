import { useEffect, useState, type FormEvent } from "react";
import { createTask } from "../../api/index";
import type { CrmTask } from "../../types";
import { useTranslation } from "react-i18next";

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
  const { t } = useTranslation("operations");
  const startDefault = new Date(Date.now() + 60 * 60_000);
  const [title, setTitle] = useState(t("tasks.defaultTitle", { customer: customerName ? ` - ${customerName}` : "" }));
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
      setError(t("tasks.timeError"));
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
      setError(t("tasks.createError"));
    } finally {
      setSaving(false);
    }
  }

  return <div className="modal-overlay" onClick={onClose}>
    <form className="modal-content task-form-modal" role="dialog" aria-modal="true" aria-labelledby="task-form-title" onSubmit={handleSubmit} onClick={(event) => event.stopPropagation()}>
      <div className="modal-header"><h3 id="task-form-title">{t("tasks.formTitle")}</h3><button type="button" className="chat-close" aria-label={t("tasks.closeAria")} onClick={onClose}>✕</button></div>
      <label>{t("tasks.customer")}<input value={customerName ?? t("tasks.customerNumber", { id: customerId })} disabled /></label>
      <label>{t("tasks.titleLabel")}<input name="taskTitle" required value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label>{t("tasks.priority")}<select name="taskPriority" value={priority} onChange={(event) => setPriority(event.target.value as CrmTask["priority"])}>{(["LOW", "NORMAL", "HIGH", "URGENT"] as const).map((value) => <option key={value} value={value}>{t(`tasks.priorities.${value}`)}</option>)}</select></label>
      <label>{t("tasks.start")}<input name="taskStart" type="datetime-local" required value={scheduledStart} onChange={(event) => setScheduledStart(event.target.value)} /></label>
      <label>{t("tasks.end")}<input name="taskEnd" type="datetime-local" required value={scheduledEnd} onChange={(event) => setScheduledEnd(event.target.value)} /></label>
      <label>{t("tasks.description")}<textarea name="taskDescription" value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="modal-actions"><button type="button" className="btn-secondary" onClick={onClose}>{t("tasks.cancel")}</button><button type="submit" disabled={saving}>{saving ? t("tasks.creating") : t("tasks.create")}</button></div>
    </form>
  </div>;
}
