import { test, expect } from "@playwright/test";

/**
 * RGL 拖放診斷腳本（可重跑）：
 * 目的：找出「無法拖放大小與位置」的根因。
 * 收集：①console 錯誤、②grid item 的 class 與 inline style、
 *       ③拖曳把手/resize 把手是否存在且可點、④實際拖一下看 transform 是否變化。
 */
test("診斷 RGL 是否可拖曳/縮放", async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error" || msg.type() === "warning") consoleErrors.push(`[${msg.type()}] ${msg.text()}`);
  });
  page.on("pageerror", (err) => consoleErrors.push(`[pageerror] ${err.message}`));

  // 登入
  await page.goto("/login");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.locator(".dashboard-grid")).toBeVisible();
  await page.waitForTimeout(1500); // 等 layout 偏好載入 + RGL 量測寬度

  // 1. dashboard-grid 容器寬度與高度
  const gridBox = await page.locator(".dashboard-grid").boundingBox();
  console.log("=== dashboard-grid box ===", JSON.stringify(gridBox));

  // 2. 第一個 grid item 的 class / style
  const firstItem = page.locator(".react-grid-item").first();
  const itemCount = await page.locator(".react-grid-item").count();
  console.log("=== react-grid-item count ===", itemCount);
  if (itemCount > 0) {
    const cls = await firstItem.getAttribute("class");
    const style = await firstItem.getAttribute("style");
    console.log("=== first item class ===", cls);
    console.log("=== first item style ===", style);
  }

  // 3. 把手是否存在
  console.log("=== .block-drag-handle count ===", await page.locator(".block-drag-handle").count());
  console.log("=== .react-resizable-handle count ===", await page.locator(".react-resizable-handle").count());

  // 4. 量測 drag handle，模擬拖曳，看 transform 變化
  const handle = page.locator(".react-grid-item .block-drag-handle").first();
  const before = await firstItem.getAttribute("style");
  const hb = await handle.boundingBox();
  console.log("=== handle box ===", JSON.stringify(hb));
  if (hb) {
    await page.mouse.move(hb.x + hb.width / 2, hb.y + hb.height / 2);
    await page.mouse.down();
    await page.mouse.move(hb.x + 250, hb.y + 180, { steps: 12 });
    await page.mouse.move(hb.x + 260, hb.y + 190, { steps: 3 });
    await page.mouse.up();
    await page.waitForTimeout(400);
  }
  const after = await firstItem.getAttribute("style");
  console.log("=== style BEFORE drag ===", before);
  console.log("=== style AFTER  drag ===", after);
  console.log("=== drag changed? ===", before !== after);

  // 5. 測試角落縮放：拖 .react-resizable-handle 看 width/height 變化
  const resizeTarget = page.locator(".react-grid-item").first();
  const rBefore = await resizeTarget.getAttribute("style");
  const rh = await resizeTarget.locator(".react-resizable-handle").first().boundingBox();
  console.log("=== resize handle box ===", JSON.stringify(rh));
  if (rh) {
    await page.mouse.move(rh.x + rh.width / 2, rh.y + rh.height / 2);
    await page.mouse.down();
    await page.mouse.move(rh.x + 200, rh.y + 160, { steps: 12 });
    await page.mouse.move(rh.x + 210, rh.y + 170, { steps: 3 });
    await page.mouse.up();
    await page.waitForTimeout(400);
  }
  const rAfter = await resizeTarget.getAttribute("style");
  console.log("=== style BEFORE resize ===", rBefore);
  console.log("=== style AFTER  resize ===", rAfter);
  console.log("=== resize changed? ===", rBefore !== rAfter);

  // 6. 印出 console 錯誤/警告
  console.log("=== CONSOLE ERRORS/WARNINGS ===\n" + consoleErrors.join("\n"));
});
