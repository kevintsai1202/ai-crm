import { test, expect } from "@playwright/test";

/**
 * SP9-B 工作檯個人 AI 端對端驗證（可重跑）。
 *
 * 我的工作台已併入客戶工作台：AI 工作建議改由客戶工作台 action-bar 的「✨ AI 工作建議」按鈕開啟 Modal。
 * 前置：後端需在 127.0.0.1:18080 運行，且已產生示範資料（owner 帳號如「王小明」有客戶）。
 * 資料隔離（SALES 只見自己）由後端整合測試 WorkspaceAiServiceTest 保證。
 */
test("AI 工作建議 Modal：待辦 + AI 總結 + 建議商機", async ({ page }) => {
  // 1) 登入（王小明擁有示範客戶）
  await page.goto("/login");
  await page.fill('input[name="username"]', "王小明@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  // 2) 以側欄連結進客戶工作台（SPA 導航）
  await page.click('a[href="/customers"]');
  await expect(page).toHaveURL(/\/customers/);

  // 3) action-bar 開「AI 工作建議」Modal → 產生 → 等待待辦或 AI 總結出現
  await page.getByRole("button", { name: /AI 工作建議/ }).click();
  const modal = page.locator(".workspace-ai-modal");
  await expect(modal).toBeVisible();
  await modal.getByRole("button", { name: /產生我的工作建議/ }).click();
  await expect
    .poll(async () => {
      const todoCount = await modal.locator(".todo-item").count();
      const summary = (await modal.locator(".workspace-ai-summary .markdown-body").innerText().catch(() => "")) || "";
      return todoCount > 0 || summary.trim().length > 0;
    }, { timeout: 60_000, intervals: [1000] })
    .toBeTruthy();
});
