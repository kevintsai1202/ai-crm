import { describe, expect, it, vi } from "vitest";
import { executeTaskAction } from "./taskActions";

describe("CRM 任務動作協調", () => {
  it("同一任務動作 pending 時雙擊只呼叫一次 API", async () => {
    let release!: () => void;
    const action = vi.fn(() => new Promise<void>((resolve) => { release = resolve; }));
    const pending = new Set<string>();
    const first = executeTaskAction({ key: "1:complete", pending, action, recover: vi.fn(), onError: vi.fn(), onPendingChange: vi.fn() });
    const second = executeTaskAction({ key: "1:complete", pending, action, recover: vi.fn(), onError: vi.fn(), onPendingChange: vi.fn() });

    expect(action).toHaveBeenCalledTimes(1);
    expect(await second).toBe(false);
    release();
    expect(await first).toBe(true);
  });

  it("409 時提示重新載入、執行 recovery 並清除 pending", async () => {
    const recover = vi.fn(async () => undefined);
    const onError = vi.fn();
    const pendingChanges: boolean[] = [];
    const pending = new Set<string>();

    const result = await executeTaskAction({
      key: "2:postpone", pending,
      action: async () => { throw { response: { status: 409 } }; },
      recover, onError,
      onPendingChange: (keys) => pendingChanges.push(keys.has("2:postpone")),
    });

    expect(result).toBe(false);
    expect(onError).toHaveBeenCalledWith("任務已被其他使用者更新，已重新載入最新資料，請再試一次。");
    expect(recover).toHaveBeenCalledTimes(1);
    expect(pending.size).toBe(0);
    expect(pendingChanges).toEqual([true, false]);
  });
});
