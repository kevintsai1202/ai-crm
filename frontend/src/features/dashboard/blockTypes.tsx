import type { ReactNode } from "react";

/**
 * 儀表板可拖拉/關閉的單一卡片區塊定義（SP7 卡片層級粒度）。
 * 函式級註解：每個區塊對應畫面上一張卡片，由各 section 檔的 builder 函式產生；
 * DashboardPage 以 visibleOrder（id 有序陣列）控制顯示順序與顯隱。
 */
export interface DashboardBlock {
  /** 穩定識別 id（對應容器 id="block-{id}" 與偏好儲存） */
  id: string;
  /** 抽屜顯示用的中文名 */
  title: string;
  /** 是否在 4 欄網格中跨 2 欄 */
  wide?: boolean;
  /** 是否為矮卡（KPI）：只佔 1 列，可與另一張矮卡在同欄上下堆疊；一般卡佔 2 列 */
  short?: boolean;
  /** 渲染該卡片（含 .panel 卡片本體） */
  render: () => ReactNode;
}

/**
 * 資料尚未載入時的佔位卡片，維持區塊在網格中的位置。
 */
export function LoadingCard({ title, wide }: { title: string; wide?: boolean }) {
  return (
    <article className={`panel report-card${wide ? " wide" : ""}`}>
      <div className="loading-line">{title}載入中...</div>
    </article>
  );
}
