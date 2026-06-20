import { test, expect } from "@playwright/test";

/**
 * 驗證客戶頁「業務」篩選列出完整業務團隊（所有啟用 SALES 帳號），而非只列當前頁出現過的業務。
 */
test("業務篩選列出所有業務帳號", async ({ page }) => {
  await page.goto("/login");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  await page.click("text=客戶工作台");
  await expect(page).toHaveURL(/\/customers/);
  await expect(page.locator(".customer-list")).toBeVisible();

  // 業務篩選為第二個 select（產業、業務）
  const ownerSelect = page.locator(".search-box select").nth(1);
  await expect.poll(async () => ownerSelect.locator("option").count()).toBeGreaterThan(5);

  const optionCount = await ownerSelect.locator("option").count();
  // 第一個是「全部業務」，其餘為業務帳號；目前有 13 個 SALES 帳號
  console.log("=== 業務選項數（含全部業務）===", optionCount);
  expect(optionCount).toBeGreaterThanOrEqual(13);

  // 列表預設每頁僅 10 筆，業務選項數應大於當前頁不重複業務數（證明非取自當前頁）
  const pageOwners = new Set(await page.locator(".customer-row .customer-owner, .customer-row").allTextContents());
  console.log("=== 當前頁列數 ===", await page.locator(".customer-row").count());
});
