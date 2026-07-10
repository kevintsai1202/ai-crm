import { describe, expect, it } from "vitest";
import { formatMoney, intentLabel, riskLabel, stageLabel } from "./format";

describe("format helpers", () => {
  it("riskLabel maps levels", () => {
    expect(riskLabel("HIGH")).toBe("高風險");
    expect(riskLabel("LOW")).toBe("低風險");
  });

  it("stageLabel maps stages", () => {
    expect(stageLabel("PROPOSAL")).toBe("提案");
    expect(stageLabel("CLOSED_WON")).toBe("已成交");
  });

  it("intentLabel hides OTHER", () => {
    expect(intentLabel("ASK_PRICING")).toBe("詢價");
    expect(intentLabel("OTHER")).toBe("");
    expect(intentLabel(null)).toBe("");
  });

  it("formatMoney returns non-empty currency string", () => {
    const s = formatMoney(12345);
    expect(s.length).toBeGreaterThan(0);
    expect(s).toMatch(/12/);
  });
});
