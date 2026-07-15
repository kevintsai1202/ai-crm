import { describe, expect, it } from "vitest";
import type { MeetingChange } from "../../types";
import { initialSelectedIds, selectedChangeIds, toggleSelection } from "./changeSelection";

/** 建立測試用會議變更。 */
function change(overrides: Partial<MeetingChange>): MeetingChange {
  return {
    changeId: "interaction-1",
    type: "INTERACTION",
    description: "紀錄本次會議互動",
    lowConfidence: false,
    selectedByDefault: true,
    detail: {},
    ...overrides,
  };
}

describe("會議變更選取狀態", () => {
  it("預設選取 selectedByDefault 為真者，低信心 Stakeholder 建議預設不選", () => {
    const changes = [
      change({ changeId: "interaction-1", selectedByDefault: true }),
      change({ changeId: "task-1", type: "TASK", selectedByDefault: true }),
      change({ changeId: "stakeholder-1", type: "STAKEHOLDER_SUGGESTION", lowConfidence: true, selectedByDefault: false }),
    ];

    const selected = initialSelectedIds(changes);

    expect([...selected].sort()).toEqual(["interaction-1", "task-1"]);
    expect(selected.has("stakeholder-1")).toBe(false);
  });

  it("toggle 可加入與移除單一變更", () => {
    const base = new Set<string>(["interaction-1"]);

    const added = toggleSelection(base, "stakeholder-1");
    expect(added.has("stakeholder-1")).toBe(true);
    // 原集合不被就地修改。
    expect(base.has("stakeholder-1")).toBe(false);

    const removed = toggleSelection(added, "interaction-1");
    expect(removed.has("interaction-1")).toBe(false);
  });

  it("selectedChangeIds 以穩定順序輸出送出用清單", () => {
    expect(selectedChangeIds(new Set(["task-1", "interaction-1"]))).toEqual(["interaction-1", "task-1"]);
  });
});
