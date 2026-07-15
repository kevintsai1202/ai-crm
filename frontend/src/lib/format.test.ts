import { describe, expect, it } from "vitest";
import {
  closeReasonLabel, formatCompactMoney, formatDate, formatDateTime, formatMoney,
  intentLabel, leadSourceLabel, riskLabel, rfmSegmentLabel, stageLabel
} from "./format";

describe("format helpers", () => {
  it("riskLabel maps levels to i18n keys", () => {
    expect(riskLabel("HIGH")).toBe("common:enums.risk.HIGH");
    expect(riskLabel("LOW")).toBe("common:enums.risk.LOW");
    // 未知值原樣回傳，不拋錯
    expect(riskLabel("UNKNOWN")).toBe("UNKNOWN");
  });

  it("stageLabel maps stages to i18n keys", () => {
    expect(stageLabel("PROPOSAL")).toBe("common:enums.stage.PROPOSAL");
    expect(stageLabel("CLOSED_WON")).toBe("common:enums.stage.CLOSED_WON");
  });

  it("intentLabel hides OTHER and empty values", () => {
    expect(intentLabel("ASK_PRICING")).toBe("common:enums.intent.ASK_PRICING");
    expect(intentLabel("OTHER")).toBe("");
    expect(intentLabel(null)).toBe("");
    expect(intentLabel(undefined)).toBe("");
  });

  it("leadSourceLabel maps sources to i18n keys", () => {
    expect(leadSourceLabel("INBOUND")).toBe("common:enums.leadSource.INBOUND");
  });

  it("closeReasonLabel maps reasons and null to i18n keys", () => {
    expect(closeReasonLabel("WON_PRICE")).toBe("common:enums.closeReason.WON_PRICE");
    expect(closeReasonLabel(null)).toBe("common:notFilled");
  });

  it("rfmSegmentLabel maps backend literal segment text to i18n keys", () => {
    expect(rfmSegmentLabel("冠軍客戶")).toBe("common:enums.rfmSegment.champion");
    expect(rfmSegmentLabel("瀕危流失")).toBe("common:enums.rfmSegment.atRisk");
    // 未知值原樣回傳
    expect(rfmSegmentLabel("未知分群")).toBe("未知分群");
  });

  it("formatMoney formats by explicit locale", () => {
    expect(formatMoney(12345, "zh-TW")).toBe(
      new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(12345)
    );
    expect(formatMoney(12345, "en")).toBe(
      new Intl.NumberFormat("en", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(12345)
    );
  });

  it("formatCompactMoney formats by explicit locale", () => {
    expect(formatCompactMoney(1234567, "en")).toBe(
      new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(1234567)
    );
  });

  it("formatDateTime uses noDataLabel when value is null", () => {
    expect(formatDateTime(null, "en", "No data yet")).toBe("No data yet");
    expect(formatDateTime("2026-01-01T10:00:00", "en", "No data yet")).toMatch(/2026/);
  });

  it("formatDate uses noDataLabel when value is null", () => {
    expect(formatDate(null, "zh-TW", "尚無資料")).toBe("尚無資料");
  });
});
