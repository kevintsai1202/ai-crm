import { test, expect } from "@playwright/test";

/**
 * SP9-B 我的工作檯個人 AI 端對端驗證（可重跑）。
 *
 * 前置：後端需在 127.0.0.1:18080 運行，且已產生示範資料（owner 帳號如「王小明」有客戶）。
 * 流程：示範業務登入 → 我的工作台 → 開「AI 工作建議」Modal 產生待辦/總結 → 浮動對話視窗個人問答。
 * 資料隔離（SALES 只見自己）由後端整合測試 WorkspaceAiServiceTest 保證。
 */
test("我的工作檯個人 AI：工作建議 Modal + 浮動對話問答", async ({ page }) => {
  // 1) 登入（王小明擁有示範客戶）
  await page.goto("/login");
  await page.fill('input[name="username"]', "王小明@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  // 2) 以側欄連結進我的工作台（硬載入深層路由會被 AuthContext 導回 dashboard）
  await page.click('a[href="/my-work"]');
  await expect(page).toHaveURL(/\/my-work/);

  // 3) 開「AI 工作建議」Modal → 產生 → 等待待辦或 AI 總結出現
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
  // 關閉 Modal（用 header 的 ✕，避免與 footer「關閉」按鈕在 strict mode 衝突）
  await modal.locator(".chat-close").click();
  await expect(modal).toHaveCount(0);

  // 4) 個人問答：右下角浮動「AI 助理」→ 對話視窗 → 送出 → 等待 AI 回覆
  await page.locator(".chat-launcher").click();
  const chat = page.locator(".chat-window");
  await expect(chat).toBeVisible();
  await chat.locator("textarea").fill("我有哪些客戶該優先跟進？");
  await chat.getByRole("button", { name: /送出|回應中/ }).click();
  await expect
    .poll(async () => {
      const assistant = chat.locator(".chat-msg.assistant .markdown-body");
      if (await assistant.count() === 0) return 0;
      return (await assistant.last().innerText()).trim().length;
    }, { timeout: 60_000, intervals: [1000] })
    .toBeGreaterThan(0);
});
