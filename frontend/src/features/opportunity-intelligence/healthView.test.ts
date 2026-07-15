import { describe, expect, it } from "vitest";
import { healthTier, sortedTrend } from "./healthView";

describe("商機健康度呈現", () => {
  it("依總分分級", () => {
    expect(healthTier(85).label).toBe("健康");
    expect(healthTier(55).label).toBe("留意");
    expect(healthTier(30).label).toBe("風險");
  });

  it("分級提供對應顏色語意", () => {
    expect(healthTier(85).tone).toBe("good");
    expect(healthTier(55).tone).toBe("warn");
    expect(healthTier(30).tone).toBe("bad");
  });

  it("趨勢依計算時間由舊到新排序，供折線呈現", () => {
    const points = [
      { totalScore: 60, calculatedAt: "2026-07-15T03:00:00Z" },
      { totalScore: 40, calculatedAt: "2026-07-15T01:00:00Z" },
      { totalScore: 50, calculatedAt: "2026-07-15T02:00:00Z" },
    ];
    expect(sortedTrend(points).map((p) => p.totalScore)).toEqual([40, 50, 60]);
  });
});
