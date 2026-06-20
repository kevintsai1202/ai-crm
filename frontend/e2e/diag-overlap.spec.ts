import { test } from "@playwright/test";

/**
 * 版面重疊診斷（可重跑）：重現「拖曳/縮放時被遮住」。
 * ①拖曳途中（滑鼠未放開）截圖並讀被拖卡片與鄰居的 computed z-index。
 * ②放開後重新量測幾何重疊，看是否殘留覆蓋。
 * ③縮放途中截圖並讀 z-index。
 */
test("重現拖曳/縮放遮擋並偵測重疊", async ({ page }) => {
  await page.goto("/login");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/);
  await page.locator(".dashboard-grid").waitFor();
  await page.waitForTimeout(2000);

  const items = page.locator(".react-grid-item");

  // 讀全部矩形 + z-index 的小工具
  async function snapshot() {
    return page.$$eval(".react-grid-item", (els) =>
      els.map((el) => {
        const r = el.getBoundingClientRect();
        return { id: el.id, x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), z: getComputedStyle(el).zIndex, cls: el.className };
      })
    );
  }
  function overlaps(rects: Awaited<ReturnType<typeof snapshot>>) {
    const out: string[] = [];
    for (let i = 0; i < rects.length; i++)
      for (let j = i + 1; j < rects.length; j++) {
        const a = rects[i], b = rects[j];
        const ix = Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x));
        const iy = Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
        if (ix * iy > 200) out.push(`${a.id}(z=${a.z}) ∩ ${b.id}(z=${b.z}) = ${ix * iy}px²`);
      }
    return out;
  }

  // === ① 拖曳途中 ===
  const handle = items.first().locator(".block-drag-handle");
  const hb = await handle.boundingBox();
  if (hb) {
    await page.mouse.move(hb.x + hb.width / 2, hb.y + hb.height / 2);
    await page.mouse.down();
    await page.mouse.move(hb.x + 240, hb.y + 360, { steps: 15 }); // 拖到下方第三排上方
    await page.waitForTimeout(300);
    await page.screenshot({ path: "test-results/overlap-mid-drag.png", fullPage: true });
    const mid = await snapshot();
    const dragging = mid.find((m) => m.cls.includes("react-draggable-dragging"));
    console.log("=== MID-DRAG dragging item ===", JSON.stringify(dragging));
    console.log("=== MID-DRAG overlaps ===\n" + (overlaps(mid).join("\n") || "無"));
    await page.mouse.up();
    await page.waitForTimeout(500);
  }
  const afterDrag = await snapshot();
  console.log("=== AFTER-DROP overlaps ===\n" + (overlaps(afterDrag).join("\n") || "無"));

  // === ③ 縮放途中 ===
  const rh = await items.first().locator(".react-resizable-handle").boundingBox();
  if (rh) {
    await page.mouse.move(rh.x + rh.width / 2, rh.y + rh.height / 2);
    await page.mouse.down();
    await page.mouse.move(rh.x + 230, rh.y + 330, { steps: 15 });
    await page.waitForTimeout(300);
    await page.screenshot({ path: "test-results/overlap-mid-resize.png", fullPage: true });
    const midR = await snapshot();
    const resizing = midR.find((m) => m.cls.includes("resizing"));
    console.log("=== MID-RESIZE resizing item ===", JSON.stringify(resizing));
    console.log("=== MID-RESIZE overlaps ===\n" + (overlaps(midR).join("\n") || "無"));
    await page.mouse.up();
    await page.waitForTimeout(500);
  }
  const afterResize = await snapshot();
  console.log("=== AFTER-RESIZE overlaps ===\n" + (overlaps(afterResize).join("\n") || "無"));
});
