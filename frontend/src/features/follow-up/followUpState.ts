import type { OutboundEmailResponse } from "../../types";

/** 寄送狀態是否可重試：僅 FAILED 允許。 */
export function canRetry(status: OutboundEmailResponse["status"]): boolean {
  return status === "FAILED";
}

/** 將寄送狀態轉為中文標籤。 */
export function deliveryStatusLabel(status: OutboundEmailResponse["status"]): string {
  switch (status) {
    case "SENT": return "已寄出";
    case "FAILED": return "寄送失敗";
    case "QUEUED": return "排入寄送";
  }
}

/**
 * 判斷目前編輯內容是否與草稿不同（需存為新版本）；前後空白不計。
 *
 * @param draft 目前草稿的 subject/body
 * @param subject 編輯中的主旨
 * @param body 編輯中的內容
 * @returns 是否有實質修改
 */
export function isDirty(draft: { subject: string; body: string }, subject: string, body: string): boolean {
  return draft.subject.trim() !== subject.trim() || draft.body.trim() !== body.trim();
}
