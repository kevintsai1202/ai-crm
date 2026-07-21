import { useCallback, useEffect, useRef, useState } from "react";
import { completeTask, downloadTaskIcs, fetchTasks, postponeTask } from "../../api/index";
import type { CrmTask } from "../../types";
import { mergePostponedTask, parseTaskDateTime, selectActiveTasks, shiftTaskScheduleOneDay } from "./taskState";
import { executeTaskAction } from "./taskActions";
import { useTranslation } from "react-i18next";

interface TaskPanelProps {
  customerId?: number;
  refreshKey?: number;
  compact?: boolean;
}

/** 格式化工作檯顯示時間。 */
function formatTaskTime(value: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: "short", timeStyle: "short", timeZone: "Asia/Taipei" }).format(parseTaskDateTime(value));
}

/** 顯示正式 CRM 任務；規則式 AI 建議由外層另列，絕不冒充持久狀態。 */
export function TaskPanel({ customerId, refreshKey = 0, compact = false }: TaskPanelProps) {
  const { t, i18n } = useTranslation("operations");
  const [tasks, setTasks] = useState<CrmTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const pendingRef = useRef(new Set<string>());
  const [pendingKeys, setPendingKeys] = useState<ReadonlySet<string>>(new Set());

  /** 從正式 `/api/tasks` 重新載入任務。 */
  const loadTasks = useCallback(async (): Promise<void> => {
    setLoading(true);
    try { setTasks(await fetchTasks()); setError(""); setActionError(""); }
    catch (cause) { setError(t("tasks.loadError")); throw cause; }
    finally { setLoading(false); }
  }, [t]);

  useEffect(() => { void loadTasks().catch(() => undefined); }, [loadTasks, refreshKey]);

  /** 將任務順延一天，使用後端完整回應更新 optimistic-lock version。 */
  async function handlePostpone(task: CrmTask) {
    await runAction(task, async () => {
      const schedule = shiftTaskScheduleOneDay(task);
      const updated = await postponeTask(task, schedule.scheduledStart, schedule.scheduledEnd);
      setTasks((current) => mergePostponedTask(current, updated));
    });
  }

  /** 完成後直接移除 API 回應已標示 COMPLETED 的正式任務。 */
  async function handleComplete(task: CrmTask) {
    await runAction(task, async () => {
      const completed = await completeTask(task);
      setTasks((current) => current.map((item) => item.id === completed.id ? completed : item));
    });
  }

  /** 下載行事曆並套用同一套防重送與錯誤恢復流程。 */
  async function handleDownload(task: CrmTask) { await runAction(task, () => downloadTaskIcs(task)); }

  /** 執行單一任務動作，同一任務 pending 期間停用全部相關按鈕。 */
  async function runAction(task: CrmTask, action: () => Promise<unknown>) {
    await executeTaskAction({ key: String(task.id), pending: pendingRef.current, action,
      recover: loadTasks, onError: setActionError, onPendingChange: setPendingKeys });
  }

  const scoped = customerId ? tasks.filter((task) => task.customerId === customerId) : tasks;
  const rows = selectActiveTasks(scoped);
  return <section className={`task-panel${compact ? " task-panel-compact" : ""}`} data-testid="crm-task-panel">
    <div className="task-panel-head"><h4>{t("tasks.title")}</h4><button type="button" className="btn-secondary" onClick={() => void loadTasks().catch(() => undefined)}>{t("tasks.refresh")}</button></div>
    {actionError ? <p className="form-error" role="alert">{actionError}</p> : null}
    {loading ? <p>{t("tasks.loading")}</p> : error ? <p role="alert">{error}</p> : rows.length === 0 ? <p className="workspace-empty">{t("tasks.empty")}</p> :
      <ul className="task-list">{rows.map(({ task, overdue }) => <li key={task.id} data-task-id={task.id} className={overdue ? "task-overdue" : ""}>
        <div><strong>{task.title}</strong><span>{task.type === "PHONE_CALL" ? t("tasks.phone") : task.type} · {t("tasks.customerNumber", { id: task.customerId })}</span><time>{formatTaskTime(task.scheduledStart, i18n.language)}{overdue ? ` · ${t("tasks.overdue")}` : ""}</time></div>
        <div className="task-actions"><button type="button" disabled={pendingKeys.has(String(task.id))} onClick={() => void handlePostpone(task)}>{t("tasks.postpone")}</button><button type="button" disabled={pendingKeys.has(String(task.id))} onClick={() => void handleDownload(task)}>{t("tasks.calendar")}</button><button type="button" disabled={pendingKeys.has(String(task.id))} onClick={() => void handleComplete(task)}>{t("tasks.complete")}</button></div>
      </li>)}</ul>}
  </section>;
}
