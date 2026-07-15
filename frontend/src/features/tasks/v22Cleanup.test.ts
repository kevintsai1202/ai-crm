import { describe, expect, it, vi } from "vitest";
import { executeV22Cleanup } from "./v22Cleanup";

describe("V22 E2E cleanup", () => {
  it("依 exact prefix/customer 清除所有任務後才刪客戶", async () => {
    const tasks = [
      { id: 1, customerId: 8, title: "E2E_V22_20260715000000000_A", version: 2 },
      { id: 2, customerId: 8, title: "E2E_V22_20260715000000000_B", version: 4 },
      { id: 3, customerId: 9, title: "other", version: 0 },
    ];
    const deleteTask = vi.fn(async (id: number) => { tasks.splice(tasks.findIndex((task) => task.id === id), 1); });
    const deleteCustomer = vi.fn(async () => undefined);

    await executeV22Cleanup({ prefix: "E2E_V22_20260715000000000_", customerId: 8,
      listTasks: async () => [...tasks], deleteTask, deleteCustomer });

    expect(deleteTask.mock.calls.map((call) => call[0])).toEqual([1, 2]);
    expect(deleteCustomer).toHaveBeenCalledWith(8);
  });

  it("逐筆彙整錯誤且任務仍存在時禁止刪客戶", async () => {
    const deleteCustomer = vi.fn();
    const task = { id: 7, customerId: 8, title: "E2E_V22_20260715000000000_A", version: 1 };

    await expect(executeV22Cleanup({ prefix: "E2E_V22_20260715000000000_", customerId: 8,
      listTasks: async () => [task], deleteTask: async () => { throw new Error("delete failed"); }, deleteCustomer }))
      .rejects.toThrow(/task 7.*仍有 1 筆任務/);
    expect(deleteCustomer).not.toHaveBeenCalled();
  });
});
