import { test } from "@playwright/test";

/**
 * 重現「抽屜加回的區塊變形/重疊」：關閉一張 KPI → 開抽屜 → 加回 → 讀回它的
 * inline style（transform/width/height）與是否與其他卡重疊，定位 y:Infinity 問題。
 */
test("抽屜加回區塊的尺寸與重疊", async ({ page }) => {
  await page.goto("/login");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/);
  await page.locator(".dashboard-grid").waitFor();
  await page.waitForTimeout(2000);

  // 關閉「活躍商機」KPI
  await page.locator("#block-kpi-active-opps .block-close").click();
  await page.waitForTimeout(400);

  // 開抽屜並加回
  await page.locator(".layout-btn").click();
  await page.waitForTimeout(300);
  await page.locator(".drawer button", { hasText: "加回" }).first().click().catch(async () => {
    // 後備：點任何含「活躍商機」的加回鈕
    await page.getByText("活躍商機").last().click();
  });
  await page.waitForTimeout(600);
  // 關抽屜（若還開著）
  await page.locator(".drawer-overlay").click({ position: { x: 5, y: 5 } }).catch(() => {});
  await page.waitForTimeout(400);

  const info = await page.$$eval(".react-grid-item", (els) =>
    els.map((el) => {
      const r = el.getBoundingClientRect();
      return { id: el.id, x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), style: el.getAttribute("style") };
    })
  );
  const added = info.find((i) => i.id === "block-kpi-active-opps");
  console.log("=== ADDED BLOCK ===", JSON.stringify(added));
  // 重疊偵測
  const ov: string[] = [];
  for (let i = 0; i < info.length; i++)
    for (let j = i + 1; j < info.length; j++) {
      const a = info[i], b = info[j];
      const ix = Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x));
      const iy = Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
      if (ix * iy > 200) ov.push(`${a.id} ∩ ${b.id} = ${ix * iy}px²`);
    }
  console.log("=== OVERLAPS ===\n" + (ov.join("\n") || "無"));
});
