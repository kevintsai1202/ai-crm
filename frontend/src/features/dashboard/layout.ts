import type { Layout, LayoutItem } from "react-grid-layout/legacy";
import type { DashboardBlock } from "./blockTypes";

/** 儀表板網格欄數（以 KPI 1×1 為最小單位，共 4 欄）。 */
export const RGL_COLS = 4;
/** 單列高度（px）；一般卡預設 h=2（≈320），KPI 卡 h=1（153）。 */
export const RGL_ROW_HEIGHT = 153;
/** 區塊間距 [x, y]（px）。 */
export const RGL_MARGIN: [number, number] = [14, 14];

/**
 * 區塊預設尺寸（格數）：wide 寬 2、其餘 1；short(KPI) 高 1、其餘 2。
 * 使用者可之後用角落把手自由調整寬高，最小 1×1。
 */
export function blockDefaultSize(block: { wide?: boolean; short?: boolean }): { w: number; h: number } {
  return { w: block.wide ? 2 : 1, h: block.short ? 1 : 2 };
}

/** 將 RGL layout 序列化為 "id:x:y:w:h" 字串陣列（沿用後端 visibleOrder: string[] 欄位）。 */
export function serializeLayout(layout: Layout): string[] {
  return layout.map((it) => `${it.i}:${it.x}:${it.y}:${it.w}:${it.h}`);
}

/** 解析 "id:x:y:w:h" 字串陣列為 RGL layout；任一筆格式不符回 null（相容舊版偏好 → 回退預設）。 */
export function parseLayout(arr: string[]): Layout | null {
  const out: LayoutItem[] = [];
  for (const entry of arr) {
    const p = entry.split(":");
    if (p.length !== 5) return null;
    const i = p[0];
    const x = Number(p[1]); const y = Number(p[2]); const w = Number(p[3]); const h = Number(p[4]);
    if (!i || ![x, y, w, h].every(Number.isInteger) || x < 0 || y < 0 || w < 1 || h < 1) return null;
    out.push({ i, x, y, w, h });
  }
  return out;
}

/** 兩個格子矩形是否重疊。 */
function rectsOverlap(a: LayoutItem, b: LayoutItem): boolean {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

/**
 * 落點重疊清理器：把殘留重疊的卡往下擠開，保證無重疊。
 * 依 y,x 排序，只有「真的撞到」的卡才下移到障礙物正下方，不撞的維持原位（保留空格、不全域緊湊）。
 */
export function resolveOverlaps(layout: Layout): Layout {
  const sorted = [...layout].sort((a, b) => a.y - b.y || a.x - b.x);
  const placed: LayoutItem[] = [];
  for (const it of sorted) {
    const item = { ...it };
    let collided = true;
    while (collided) {
      collided = false;
      for (const p of placed) {
        if (rectsOverlap(item, p)) { item.y = p.y + p.h; collided = true; break; }
      }
    }
    placed.push(item);
  }
  return placed;
}

/**
 * 拖曳落點重排（對調優先）：以拖曳開始時的乾淨基準 base 重算，把被拖卡 id 放到 moved 的位置。
 * - 落在空白 → 僅移動被拖卡。
 * - 僅與「一張同尺寸卡」相鄰碰撞 → 對調：被撞卡移到被拖卡原位（填補拖走後空出的格）。
 * - 其餘（多張碰撞／尺寸不同）→ 下擠收尾。
 * 每幀都從同一份 base 重算 → 被擠/對調的卡會隨被拖卡移開而回到原位。
 */
export function swapPreferred(base: Layout, id: string, moved: { x: number; y: number; w: number; h: number }): Layout {
  const origin = base.find((it) => it.i === id);
  if (!origin) return base;
  const dPrime: LayoutItem = { ...origin, x: moved.x, y: moved.y, w: moved.w, h: moved.h };
  const collisions = base.filter((it) => it.i !== id && rectsOverlap(it, dPrime));
  if (collisions.length === 0) {
    return base.map((it) => (it.i === id ? dPrime : it));
  }
  if (collisions.length === 1 && collisions[0].w === origin.w && collisions[0].h === origin.h) {
    const b = collisions[0];
    return base.map((it) => (it.i === id ? dPrime : it.i === b.i ? { ...it, x: origin.x, y: origin.y } : it));
  }
  return resolveOverlaps(base.map((it) => (it.i === id ? dPrime : it)));
}

/**
 * 在現有 layout 中找出可容納 w×h 的第一個空位（由上而下、由左而右掃描）。
 * compactType:null 無壓縮器，加回區塊不能用 y:Infinity（會算出 Infinity 而塌陷），須自算真實格位。
 */
export function findFreeSlot(layout: Layout, w: number, h: number): { x: number; y: number } {
  const width = Math.min(w, RGL_COLS);
  const collides = (x: number, y: number) =>
    layout.some((it) => x < it.x + it.w && it.x < x + width && y < it.y + it.h && it.y < y + h);
  for (let y = 0; y < 1000; y++) {
    for (let x = 0; x + width <= RGL_COLS; x++) {
      if (!collides(x, y)) return { x, y };
    }
  }
  return { x: 0, y: 1000 };
}

/**
 * 依區塊順序產生預設 layout（在 4 欄內由左到右、上到下緊密排列）。
 */
export function defaultLayout(blocks: DashboardBlock[]): Layout {
  const layout: LayoutItem[] = [];
  let x = 0;
  let y = 0;
  let rowMaxH = 0;
  for (const b of blocks) {
    const { w, h } = blockDefaultSize(b);
    if (x + w > RGL_COLS) { x = 0; y += rowMaxH; rowMaxH = 0; }
    layout.push({ i: b.id, x, y, w, h });
    x += w;
    rowMaxH = Math.max(rowMaxH, h);
  }
  return layout;
}
