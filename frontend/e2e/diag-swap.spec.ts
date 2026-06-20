import { test } from "@playwright/test";

/** 測相鄰卡拖曳：把 active-opps（第二格）拖到 customers（第一格）上，看 customers 是「對調到第二格」還是「被下擠」。 */
test("相鄰卡拖曳是對調還是下擠", async ({ page }) => {
  await page.goto("/login");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/);
  await page.locator(".dashboard-grid").waitFor();
  await page.waitForTimeout(2500);
  // 先還原預設版面，確保從乾淨的已知排列開始（避免被先前測試殘留的存檔污染）
  await page.locator(".layout-btn").click();
  await page.locator(".btn-reset-layout").click();
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".drawer-overlay").click({ position: { x: 5, y: 5 } }).catch(() => {});
  await page.waitForTimeout(600);
  await page.evaluate(() => window.scrollTo(0, 0));

  async function pos(id: string) {
    return page.$eval(`#${id}`, (el) => { const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y) }; });
  }
  const cBefore = await pos("block-kpi-customers");
  const aBefore = await pos("block-kpi-active-opps");
  console.log("=== before  customers", JSON.stringify(cBefore), " active-opps", JSON.stringify(aBefore));

  // 拖 active-opps 的把手到 customers 中心
  const h = await page.locator("#block-kpi-active-opps .block-drag-handle").boundingBox();
  const cBox = await page.locator("#block-kpi-customers").boundingBox();
  if (!h || !cBox) throw new Error("找不到卡");
  await page.mouse.move(h.x + h.width / 2, h.y + h.height / 2);
  await page.mouse.down();
  await page.mouse.move(cBox.x + 30, cBox.y + 30, { steps: 25 });
  await page.mouse.move(cBox.x + 25, cBox.y + 25, { steps: 5 });
  await page.mouse.up();
  await page.waitForTimeout(600);

  const cAfter = await pos("block-kpi-customers");
  const aAfter = await pos("block-kpi-active-opps");
  console.log("=== after   customers", JSON.stringify(cAfter), " active-opps", JSON.stringify(aAfter));
  console.log("=== customers 是否移到 active-opps 原位(對調)?", cAfter.x === aBefore.x && cAfter.y === aBefore.y);
  console.log("=== customers 是否被下擠(同欄、y變大)?", cAfter.x === cBefore.x && cAfter.y > cBefore.y);
});
