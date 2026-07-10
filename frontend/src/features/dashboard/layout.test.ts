import { describe, expect, it } from "vitest";
import { parseLayout, resolveOverlaps, serializeLayout } from "./layout";

describe("dashboard layout", () => {
  it("serialize and parse round-trip", () => {
    const layout = [
      { i: "a", x: 0, y: 0, w: 1, h: 1 },
      { i: "b", x: 1, y: 0, w: 2, h: 2 }
    ];
    const serialized = serializeLayout(layout as any);
    const parsed = parseLayout(serialized);
    expect(parsed).toEqual(layout);
  });

  it("parseLayout rejects bad entries", () => {
    expect(parseLayout(["bad"])).toBeNull();
    expect(parseLayout(["a:0:0:1"])).toBeNull();
  });

  it("resolveOverlaps separates stacked cards", () => {
    const layout = [
      { i: "a", x: 0, y: 0, w: 2, h: 2 },
      { i: "b", x: 0, y: 0, w: 2, h: 2 }
    ];
    const fixed = resolveOverlaps(layout as any);
    const a = fixed.find((x) => x.i === "a")!;
    const b = fixed.find((x) => x.i === "b")!;
    // 兩者不應重疊
    expect(a.y + a.h <= b.y || b.y + b.h <= a.y || a.x + a.w <= b.x || b.x + b.w <= a.x).toBe(true);
  });
});
