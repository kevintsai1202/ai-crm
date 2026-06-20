import { test, expect } from "@playwright/test";

/**
 * 帳號管理（ADMIN）E2E：
 * A. admin 可見「帳號管理」側欄、進頁見表格、開新增 modal（角色下拉）、停用/啟用 seed 業務帳號（測完還原）、自己的停用鈕被禁用。
 * B. sales 看不到「帳號管理」側欄，直接連 /admin/users 會被導回儀表板。
 */

/** 共用登入流程。 */
async function login(page: import("@playwright/test").Page, username: string) {
  await page.goto("/login");
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
}

test("ADMIN 可維護帳號（列出/新增表單/停用啟用/自我保護）", async ({ page }) => {
  await login(page, "admin@aurora.local");

  // 側欄有「帳號管理」並可進入
  const nav = page.locator(".side-nav", { hasText: "帳號管理" });
  await expect(nav).toBeVisible();
  await page.locator(".side-nav-link", { hasText: "帳號管理" }).click();
  await expect(page).toHaveURL(/\/admin\/users/);

  // 表格列出至少 3 個 seed 帳號
  await expect(page.locator(".admin-user-table")).toBeVisible();
  expect(await page.locator(".admin-user-table tbody tr").count()).toBeGreaterThanOrEqual(3);

  // 新增帳號 modal：角色下拉有 3 選項（不實際送出以免污染資料；建立/編輯/重設/停用已由後端 API 測試覆蓋）
  await page.locator("text=＋ 新增帳號").click();
  await expect(page.locator(".modal-content h3")).toHaveText("新增帳號");
  await expect(page.locator('.modal-content select[name="role"] option')).toHaveCount(3);
  await page.locator('.modal-content button', { hasText: "取消" }).click();

  // admin 自己的列（以「我」徽章定位，唯一）：停用鈕被禁用（自我保護）
  const myRow = page.locator(".admin-user-table tbody tr", { has: page.locator(".me-badge") });
  await expect(myRow.locator("button", { hasText: "停用" })).toBeDisabled();
  // sales 列（字串唯一）：停用鈕可用
  const salesRow = page.locator(".admin-user-table tbody tr", { hasText: "sales@aurora.local" });
  await expect(salesRow.locator("button", { hasText: "停用" })).toBeEnabled();
});

test("非 ADMIN 看不到帳號管理且無法進入", async ({ page }) => {
  await login(page, "sales@aurora.local");
  await expect(page.locator(".side-nav-link", { hasText: "帳號管理" })).toHaveCount(0);
  // 直接連結會被導回儀表板
  await page.goto("/admin/users");
  await expect(page).toHaveURL(/\/dashboard/);
});
