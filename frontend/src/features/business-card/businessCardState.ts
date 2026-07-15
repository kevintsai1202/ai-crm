import type {
  BusinessCardConfirmResponse,
  BusinessCardDuplicateCandidate,
  ConfirmBusinessCardRequest,
  RecognizedBusinessCard,
} from "../../types";

/** 低於此信心值的欄位提示人工校正；與後端 Vision 解析語意一致採 0–1。 */
export const LOW_CONFIDENCE_THRESHOLD = 0.6;

/** 人工在重複候選畫面選定的處理策略：全新建檔或合併既有客戶。 */
export type DuplicateStrategy =
  | { action: "CREATE" }
  | { action: "MERGE"; customerId: number };

/** 確認完成後導向正式資料的單一連結項目。 */
export interface ConfirmSummaryItem {
  entity: "customer" | "contact" | "opportunity" | "task";
  label: string;
  id: number;
}

/**
 * 找出信心值低於門檻的欄位鍵，供 UI 標示需人工複查。
 *
 * @param recognized Vision 辨識結果
 * @returns 低信心欄位鍵陣列；無 confidence 資料時回空陣列
 */
export function lowConfidenceFields(recognized: RecognizedBusinessCard): string[] {
  return Object.entries(recognized.confidence)
    .filter(([, score]) => score < LOW_CONFIDENCE_THRESHOLD)
    .map(([field]) => field);
}

/**
 * 判斷是否可從審核步驟進入確認步驟。
 * 有重複候選時必須明確選擇策略（MERGE 需帶 customerId），避免誤建或漏合併。
 *
 * @param candidates 後端回傳的重複候選
 * @param strategy 目前選定的策略，未選為 null
 * @returns 可否進入確認步驟
 */
export function canProceedFromReview(
  candidates: BusinessCardDuplicateCandidate[],
  strategy: DuplicateStrategy | null,
): boolean {
  // 無重複候選時屬單純新建，不強制選擇策略。
  if (candidates.length === 0) return true;
  if (strategy === null) return false;
  if (strategy.action === "CREATE") return true;
  // MERGE 必須指定有效的既有客戶 ID。
  return typeof strategy.customerId === "number";
}

/**
 * 將確認回應轉為固定順序的四類連結摘要，供結果頁一鍵導向。
 *
 * @param response 名片確認回應
 * @returns 客戶、聯絡人、商機、任務四項連結
 */
export function buildConfirmSummary(
  response: BusinessCardConfirmResponse,
): ConfirmSummaryItem[] {
  return [
    { entity: "customer", label: "客戶", id: response.customerId },
    { entity: "contact", label: "聯絡人", id: response.contactId },
    { entity: "opportunity", label: "商機", id: response.opportunityId },
    { entity: "task", label: "電話任務", id: response.taskId },
  ];
}

/** 名片精靈可編輯表單；涵蓋客戶、聯絡人與商機／任務欄位。 */
export interface BusinessCardForm {
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  taxId: string;
  industry: string;
  contactName: string;
  contactTitle: string;
  contactEmail: string;
  opportunityName: string;
  opportunityAmount: string;
  expectedCloseDate: string;
  callAt: string;
}

/**
 * 以辨識結果建立表單初值；未辨識欄位留白待人工補齊。
 *
 * @param recognized Vision 辨識結果，可能為 null
 * @returns 表單初值
 */
export function initialFormFromRecognized(recognized: RecognizedBusinessCard | null): BusinessCardForm {
  const name = recognized?.personName ?? "";
  const company = recognized?.companyName ?? "";
  return {
    customerName: company,
    customerEmail: recognized?.email ?? "",
    customerPhone: recognized?.phone ?? "",
    taxId: "",
    industry: "",
    contactName: name,
    contactTitle: recognized?.title ?? "",
    contactEmail: recognized?.email ?? "",
    opportunityName: company ? `${company} 新商機` : "名片新商機",
    opportunityAmount: "0",
    expectedCloseDate: "",
    callAt: "",
  };
}

/**
 * 將表單與策略組成後端確認請求；金額轉為數字，空日期轉為 null。
 *
 * @param form 目前表單值
 * @param strategy 重複處理策略（無候選時視為 CREATE）
 * @returns 後端 ConfirmBusinessCardRequest
 */
export function buildConfirmRequest(
  form: BusinessCardForm,
  strategy: DuplicateStrategy | null,
): ConfirmBusinessCardRequest {
  const resolved = strategy ?? { action: "CREATE" as const };
  return {
    customerAction: resolved.action,
    customerId: resolved.action === "MERGE" ? resolved.customerId : null,
    customerName: form.customerName.trim(),
    customerEmail: form.customerEmail.trim(),
    customerPhone: form.customerPhone.trim(),
    taxId: form.taxId.trim(),
    industry: form.industry.trim(),
    contactName: form.contactName.trim(),
    contactTitle: form.contactTitle.trim(),
    contactEmail: form.contactEmail.trim(),
    opportunityName: form.opportunityName.trim(),
    opportunityAmount: Number(form.opportunityAmount) || 0,
    expectedCloseDate: form.expectedCloseDate.trim() ? form.expectedCloseDate : null,
    callAt: form.callAt,
  };
}
