import { describe, expect, it } from "vitest";
import { canRetry, deliveryStatusLabel, isDirty } from "./followUpState";

describe("跟進信寄送狀態", () => {
  it("只有 FAILED 可重試", () => {
    expect(canRetry("FAILED")).toBe(true);
    expect(canRetry("SENT")).toBe(false);
    expect(canRetry("QUEUED")).toBe(false);
  });

  it("狀態標籤對應中文", () => {
    expect(deliveryStatusLabel("SENT")).toBe("已寄出");
    expect(deliveryStatusLabel("FAILED")).toBe("寄送失敗");
    expect(deliveryStatusLabel("QUEUED")).toBe("排入寄送");
  });

  it("偵測草稿是否被人工修改（需存檔為新版本）", () => {
    expect(isDirty({ subject: "A", body: "B" }, "A", "B")).toBe(false);
    expect(isDirty({ subject: "A", body: "B" }, "A2", "B")).toBe(true);
    expect(isDirty({ subject: "A", body: "B" }, "A", "B2")).toBe(true);
    // 前後空白不算差異。
    expect(isDirty({ subject: "A", body: "B" }, " A ", "B")).toBe(false);
  });
});
