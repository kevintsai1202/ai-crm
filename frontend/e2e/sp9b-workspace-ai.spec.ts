import { test, expect } from "@playwright/test";

/**
 * SP9-B 我的工作檯個人 AI 端對端驗證（可重跑）。
 *
 * 前置：後端需在 127.0.0.1:18080 運行，且已產生示範資料（owner 帳號如「王小明」有客戶）。
 * 流程：以示範業務登入 → 進「我的工作台」→ 驗證 AI 區塊、產生工作建議（待辦/AI 總結）、個人問答有回應。
 * 資料隔離（SALES 只見自己）由後端整合測試 WorkspaceAiServiceTest 保證，此處驗證 UI 串接正常。
 */
test("我的工作檯個人 AI：產生建議 + 個人問答", async ({ page }) => {
  // 1) 以示範業務登入（王小明擁有示範客戶）
  await page.goto("/login");
  await page.fill('input[name="username"]', "王小明@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  // 2) 進入我的工作台（以側欄連結做 SPA 導航；硬載入深層路由會被 AuthContext 導回 dashboard）
  await page.click('a[href="/my-work"]');
  await expect(page).toHaveURL(/\/my-work/);
  const panel = page.locator(".workspace-ai-panel");
  await expect(panel).toBeVisible();

  // 3) 產生工作建議：點按後等待「待辦清單」或「AI 總結」出現
  await panel.getByRole("button", { name: /產生我的工作建議/ }).click();
  // 待辦或 AI 總結至少一者要在 60 秒內出現（串流）
  await expect
    .poll(async () => {
      const todoCount = await panel.locator(".todo-item").count();
      const summaryText = (await panel.locator(".workspace-ai-summary .markdown-body").innerText().catch(() => "")) || "";
      return todoCount > 0 || summaryText.trim().length > 0;
    }, { timeout: 60_000, intervals: [1000] })
    .toBeTruthy();

  // 4) 個人問答（總覽）：送出問題，等待 AI 回覆氣泡出現內容
  const chat = panel.locator(".workspace-chat");
  await chat.locator('input').fill("我有哪些客戶該優先跟進？");
  await chat.getByRole("button", { name: /送出|回覆中/ }).click();
  await expect
    .poll(async () => {
      const assistant = chat.locator(".chat-bubble.assistant");
      const count = await assistant.count();
      if (count === 0) return 0;
      return (await assistant.last().innerText()).trim().length;
    }, { timeout: 60_000, intervals: [1000] })
    .toBeGreaterThan(0);
});
