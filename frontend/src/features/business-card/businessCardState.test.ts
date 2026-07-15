import { describe, expect, it } from "vitest";
import type {
  BusinessCardConfirmResponse,
  BusinessCardDuplicateCandidate,
  RecognizedBusinessCard,
} from "../../types";
import {
  LOW_CONFIDENCE_THRESHOLD,
  buildConfirmSummary,
  canProceedFromReview,
  lowConfidenceFields,
  type DuplicateStrategy,
} from "./businessCardState";

/** 建立測試用辨識結果，僅覆寫案例關注欄位。 */
function recognized(overrides: Partial<RecognizedBusinessCard>): RecognizedBusinessCard {
  return {
    personName: "王小明",
    title: "業務經理",
    email: "ming@example.com",
    phone: "0912345678",
    companyName: "範例科技",
    website: null,
    confidence: { personName: 0.95, email: 0.92, phone: 0.9, companyName: 0.88 },
    warnings: [],
    ...overrides,
  };
}

describe("名片精靈狀態", () => {
  it("標示信心低於門檻的欄位，供 UI 提示人工校正", () => {
    const card = recognized({
      confidence: { personName: 0.95, email: 0.4, phone: 0.55, companyName: 0.88 },
    });

    expect(lowConfidenceFields(card).sort()).toEqual(["email", "phone"]);
    expect(LOW_CONFIDENCE_THRESHOLD).toBe(0.6);
  });

  it("無 confidence 資料時不誤標任何欄位", () => {
    expect(lowConfidenceFields(recognized({ confidence: {} }))).toEqual([]);
  });

  it("有重複候選但未選策略時，禁止進入確認步驟", () => {
    const candidates: BusinessCardDuplicateCandidate[] = [
      { customerId: 7, customerName: "範例科技", matchedBy: ["EMAIL"] },
    ];

    expect(canProceedFromReview(candidates, null)).toBe(false);
    expect(canProceedFromReview(candidates, { action: "CREATE" })).toBe(true);
    expect(canProceedFromReview(candidates, { action: "MERGE", customerId: 7 })).toBe(true);
  });

  it("MERGE 未指定客戶 ID 時，仍禁止進入確認步驟", () => {
    const candidates: BusinessCardDuplicateCandidate[] = [
      { customerId: 7, customerName: "範例科技", matchedBy: ["PHONE"] },
    ];
    const strategy = { action: "MERGE", customerId: null } as unknown as DuplicateStrategy;

    expect(canProceedFromReview(candidates, strategy)).toBe(false);
  });

  it("沒有重複候選時，未選策略也可進入確認步驟", () => {
    expect(canProceedFromReview([], null)).toBe(true);
  });

  it("最終摘要包含客戶、聯絡人、商機與任務四類連結", () => {
    const response: BusinessCardConfirmResponse = {
      intakeId: 1,
      customerId: 100,
      contactId: 200,
      opportunityId: 300,
      taskId: 400,
    };

    const summary = buildConfirmSummary(response);

    expect(summary.map((item) => item.entity)).toEqual([
      "customer",
      "contact",
      "opportunity",
      "task",
    ]);
    expect(summary.map((item) => item.id)).toEqual([100, 200, 300, 400]);
  });
});
