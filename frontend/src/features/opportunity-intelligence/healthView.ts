import type { HealthTrendPoint } from "../../types";

/** 健康度分級結果。 */
export interface HealthTier {
  label: "健康" | "留意" | "風險";
  tone: "good" | "warn" | "bad";
}

/**
 * 依總分（0–100）分級：≥70 健康、≥45 留意、其餘風險。
 *
 * @param total 健康度總分
 * @returns 分級標籤與顏色語意
 */
export function healthTier(total: number): HealthTier {
  if (total >= 70) return { label: "健康", tone: "good" };
  if (total >= 45) return { label: "留意", tone: "warn" };
  return { label: "風險", tone: "bad" };
}

/**
 * 將趨勢點依計算時間由舊到新排序，供折線由左至右呈現。
 *
 * @param trend 未排序趨勢點
 * @returns 由舊到新的趨勢點
 */
export function sortedTrend(trend: HealthTrendPoint[]): HealthTrendPoint[] {
  return [...trend].sort((a, b) => a.calculatedAt.localeCompare(b.calculatedAt));
}
