import { test, expect } from "@playwright/test";

/**
 * 驗證新增客戶表單：產業為下拉選單（有選項）、負責業務為下拉且預設帶登入者本人。
 * 僅檢查 UI，不送出以免污染資料。
 */
test("新增客戶：產業/負責業務為下拉，業務預設本人", async ({ page }) => {
  await page.goto("/login");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  await page.click("text=客戶工作台");
  await expect(page).toHaveURL(/\/customers/);
  await page.locator("text=+ 新增客戶").click();
  await expect(page.locator(".modal-content")).toBeVisible();

  // 產業為 select 且載入到選項
  const industry = page.locator('select[name="industry"]');
  await expect(industry).toBeVisible();
  await expect.poll(async () => industry.locator("option").count()).toBeGreaterThan(1);

  // 負責業務為 select（值為帳號 id），預設選中登入者本人，選項標示「（我）」
  const owner = page.locator('select[name="ownerId"]');
  await expect(owner).toBeVisible();
  // 預設值非空（已選中某帳號 id）
  await expect.poll(async () => await owner.inputValue()).not.toBe("");
  // 被選中的選項文字含「（我）」（業務代表本人）
  const selectedText = await owner.locator("option:checked").textContent();
  expect(selectedText).toContain("（我）");
  expect(selectedText).toContain("業務代表");

  console.log("=== 產業選項數 ===", await industry.locator("option").count());
  console.log("=== 被選中業務 ===", selectedText);
});
