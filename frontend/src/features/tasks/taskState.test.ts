import { describe, expect, it } from "vitest";
import type { CrmTask } from "../../types";
import { mergePostponedTask, selectActiveTasks, shiftTaskScheduleOneDay } from "./taskState";

/** 建立測試用 CRM 任務，僅覆寫案例關注的欄位。 */
function task(overrides: Partial<CrmTask>): CrmTask {
  return {
    id: 1,
    customerId: 10,
    opportunityId: null,
    contactId: null,
    type: "PHONE_CALL",
    status: "OPEN",
    priority: "NORMAL",
    title: "電話追蹤",
    description: null,
    assigneeId: 2,
    assigneeName: "業務",
    scheduledStart: "2026-07-15T09:00:00",
    scheduledEnd: "2026-07-15T09:30:00",
    completedAt: null,
    postponeCount: 0,
    source: "MANUAL",
    version: 0,
    revisionTimestamp: "2026-07-15T00:00:00Z",
    ...overrides,
  };
}

describe("CRM 工作檯任務狀態", () => {
  it("只顯示 OPEN 與 IN_PROGRESS，並依預定開始時間排序及標示逾期", () => {
    const rows = selectActiveTasks([
      task({ id: 3, status: "COMPLETED", scheduledStart: "2026-07-14T08:00:00" }),
      task({ id: 2, status: "IN_PROGRESS", scheduledStart: "2026-07-15T11:00:00", scheduledEnd: "2026-07-15T11:30:00" }),
      task({ id: 1, status: "OPEN", scheduledStart: "2026-07-15T08:00:00" }),
      task({ id: 4, status: "CANCELLED", scheduledStart: "2026-07-13T08:00:00" }),
    ], new Date("2026-07-15T10:00:00+08:00"));

    expect(rows.map((row) => row.task.id)).toEqual([1, 2]);
    expect(rows.map((row) => row.overdue)).toEqual([true, false]);
  });

  it("延期成功後以 API 回應更新時間、次數與 version", () => {
    const original = task({ id: 8, postponeCount: 0, version: 2 });
    const postponed = task({
      id: 8,
      scheduledStart: "2026-07-16T14:00:00",
      scheduledEnd: "2026-07-16T14:30:00",
      postponeCount: 1,
      version: 3,
    });

    const result = mergePostponedTask([original], postponed);

    expect(result[0]).toEqual(postponed);
  });

  it("延期一天保留 Asia/Taipei 牆鐘時間，不轉成 UTC", () => {
    expect(shiftTaskScheduleOneDay(task({
      scheduledStart: "2030-07-16T14:00:00",
      scheduledEnd: "2030-07-16T14:30:00",
    }))).toEqual({ scheduledStart: "2030-07-17T14:00:00", scheduledEnd: "2030-07-17T14:30:00" });
  });
});
