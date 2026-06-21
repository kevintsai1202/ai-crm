import { test, expect } from "@playwright/test";

/**
 * 業務分析頁（MANAGER）E2E：
 * A. manager 可見「📈 業務分析」側欄、進 /team 見團隊 KPI 列與業務績效表；可切換排序；
 *    topbar「團隊整體診斷」開彈窗→重新分析產出報告→AI 歷程彈窗；點業務「輔導報告」開 coaching 彈窗。
 * B. sales 看不到「業務分析」側欄，直接連 /team 會被導回儀表板。
 *
 * 前置：後端需在 127.0.0.1:18080 啟動（Vite proxy /api → 18080）；seed 帳號密碼為 password123。
 */

/** 共用登入流程（與其餘 e2e 一致）。 */
async function login(page: import("@playwright/test").Page, username: string) {
  await page.goto("/login");
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
}

test("MANAGER 可進業務分析頁（KPI/業務表/排序/AI 彈窗/AI 歷程）", async ({ page }) => {
  await login(page, "manager@aurora.local");

  // 側欄有「業務分析」並可進入 /team
  await expect(page.locator(".side-nav-link", { hasText: "業務分析" })).toBeVisible();
  await page.locator(".side-nav-link", { hasText: "業務分析" }).click();
  await expect(page).toHaveURL(/\/team/);

  // 團隊 KPI 列出現（6 張卡）
  await expect(page.locator(".team-kpi-row")).toBeVisible();
  expect(await page.locator(".kpi-card").count()).toBe(6);

  // 業務績效表至少一列（dev seed 有業務）
  await expect(page.locator(".admin-user-table")).toBeVisible();
  const ownerRows = page.locator(".admin-user-table tbody tr");
  expect(await ownerRows.count()).toBeGreaterThanOrEqual(1);

  // 切換排序（成交率）不應報錯、表格仍在
  await page.locator(".sort-select select").selectOption("winRate");
  await expect(page.locator(".admin-user-table")).toBeVisible();

  // topbar「團隊整體診斷」開彈窗
  await page.locator(".topbar-actions button", { hasText: "團隊整體診斷" }).click();
  const modal = page.locator(".report-modal", { hasText: "團隊整體診斷" });
  await expect(modal).toBeVisible();

  // 重新分析 → 產出報告（無金鑰為教學版摘要；放寬等待涵蓋真 LLM）
  await modal.locator("button", { hasText: "重新分析" }).click();
  await expect(modal.locator(".markdown-body")).toBeVisible({ timeout: 60000 });

  // AI 歷程 → 歷程彈窗出現（以歷程彈窗獨有的「歷次 AI 呼叫紀錄」副標定位，避免與診斷彈窗的「AI 歷程」按鈕字樣相撞）
  await modal.locator("button", { hasText: "AI 歷程" }).click();
  const history = page.locator(".report-modal", { hasText: "歷次 AI 呼叫紀錄" });
  await expect(history).toBeVisible();
  // 剛產出過團隊診斷 → 歷程至少一筆
  await expect(history.locator(".ai-history-item").first()).toBeVisible({ timeout: 10000 });
  await history.locator(".report-footer button", { hasText: "關閉" }).click();

  // 關閉診斷彈窗
  await modal.locator(".report-footer button", { hasText: "關閉" }).click();

  // 點第一列業務「輔導報告」→ 業務 coaching 彈窗出現
  await ownerRows.first().locator("button", { hasText: "輔導報告" }).click();
  await expect(page.locator(".report-modal", { hasText: "的輔導報告" })).toBeVisible();
});

test("非 MANAGER/ADMIN 看不到業務分析且無法進入", async ({ page }) => {
  await login(page, "sales@aurora.local");
  await expect(page.locator(".side-nav-link", { hasText: "業務分析" })).toHaveCount(0);
  // 直接連結會被 ManagerRoute 導回儀表板
  await page.goto("/team");
  await expect(page).toHaveURL(/\/dashboard/);
});
