import type { CrmTask } from "../../types";

/** 工作檯顯示列，逾期是衍生 UI 狀態，不回寫正式任務。 */
export interface ActiveTaskRow {
  task: CrmTask;
  overdue: boolean;
}

/** 選出尚待處理的正式任務，依預定開始時間由早到晚排序。 */
export function selectActiveTasks(tasks: CrmTask[], now = new Date()): ActiveTaskRow[] {
  return tasks
    .filter((task) => task.status === "OPEN" || task.status === "IN_PROGRESS")
    .sort((left, right) => left.scheduledStart.localeCompare(right.scheduledStart))
    .map((task) => ({ task, overdue: new Date(task.scheduledEnd).getTime() < now.getTime() }));
}

/** 以延期 API 的完整回應取代舊任務，避免自行推算 version 或延期次數。 */
export function mergePostponedTask(tasks: CrmTask[], postponed: CrmTask): CrmTask[] {
  return tasks.map((task) => task.id === postponed.id ? postponed : task);
}

/** 在本地業務牆鐘時間上增加一天，避免 `toISOString` 將 +08:00 誤轉為 UTC。 */
export function shiftTaskScheduleOneDay(task: CrmTask): { scheduledStart: string; scheduledEnd: string } {
  /** 對 ISO local date-time 的日期部分做日曆運算並保留原時間。 */
  const shift = (value: string): string => {
    const [datePart, timePart] = value.split("T");
    const [year, month, day] = datePart.split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day + 1));
    const shiftedDate = date.toISOString().slice(0, 10);
    return `${shiftedDate}T${timePart.slice(0, 8)}`;
  };
  return { scheduledStart: shift(task.scheduledStart), scheduledEnd: shift(task.scheduledEnd) };
}
