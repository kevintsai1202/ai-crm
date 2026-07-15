import type { MeetingChange } from "../../types";

/**
 * 依後端 selectedByDefault 建立初始選取集合。
 * 低信心的 Stakeholder 建議後端已標記 selectedByDefault=false，故預設不選。
 *
 * @param changes 會議草稿變更
 * @returns 預設選取的 changeId 集合
 */
export function initialSelectedIds(changes: MeetingChange[]): Set<string> {
  return new Set(changes.filter((change) => change.selectedByDefault).map((change) => change.changeId));
}

/**
 * 切換單一變更的選取狀態；回傳新集合，不就地修改輸入。
 *
 * @param selected 目前選取集合
 * @param changeId 要切換的變更 ID
 * @returns 切換後的新集合
 */
export function toggleSelection(selected: Set<string>, changeId: string): Set<string> {
  const next = new Set(selected);
  if (next.has(changeId)) next.delete(changeId);
  else next.add(changeId);
  return next;
}

/**
 * 將選取集合轉為送出用的穩定排序清單。
 *
 * @param selected 選取集合
 * @returns 排序後的 changeId 陣列
 */
export function selectedChangeIds(selected: Set<string>): string[] {
  return [...selected].sort();
}
