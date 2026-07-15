import { describe, expect, it } from "vitest";
import { executeV21Cleanup } from "../../e2e/fixtures/v21-cleanup";

describe("V21 E2E cleanup dependency", () => {
  it("assignment cleanup 失敗時不呼叫 model removal", async () => {
    let removeCalls = 0;

    await expect(executeV21Cleanup({
      readSettings: async () => ({ currentModel: "chat", currentProviderId: 1 }),
      clearAssignments: async () => { throw new Error("assignment failed"); },
      removeModels: async () => { removeCalls += 1; },
    })).rejects.toThrow("assignment failed");
    expect(removeCalls).toBe(0);
  });

  it("settings 讀取失敗時不呼叫 assignment cleanup 或 model removal", async () => {
    let assignmentCalls = 0;
    let removeCalls = 0;

    await expect(executeV21Cleanup({
      readSettings: async () => { throw new Error("settings failed"); },
      clearAssignments: async () => { assignmentCalls += 1; },
      removeModels: async () => { removeCalls += 1; },
    })).rejects.toThrow("settings failed");
    expect(assignmentCalls).toBe(0);
    expect(removeCalls).toBe(0);
  });

  it("assignment cleanup 成功後才呼叫 model removal", async () => {
    const callOrder: string[] = [];

    await executeV21Cleanup({
      readSettings: async () => {
        callOrder.push("read");
        return { currentModel: "chat", currentProviderId: 1 };
      },
      clearAssignments: async () => { callOrder.push("assignments"); },
      removeModels: async () => { callOrder.push("models"); },
    });
    expect(callOrder).toEqual(["read", "assignments", "models"]);
  });
});
