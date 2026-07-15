import type { StakeholderSuggestionStatus } from "../../types";

/** 已確認用實線、其餘（含 AI 待確認）用虛線，確保視覺可區分事實與建議。 */
export function edgeStyle(status: StakeholderSuggestionStatus): "solid" | "dashed" {
  return status === "CONFIRMED" ? "solid" : "dashed";
}

/** 是否為待確認（僅 SUGGESTED）。 */
export function isPending(status: StakeholderSuggestionStatus): boolean {
  return status === "SUGGESTED";
}

/** 組合節點標籤：有職稱時附註於括號內。 */
export function nodeLabel(name: string, title: string | null): string {
  return title ? `${name}（${title}）` : name;
}
