import { test, expect } from "@playwright/test";

/**
 * SP1 煙霧測試：驗證重構後主動線行為不變。
 * 前置：後端需已啟動（127.0.0.1:18080），seed 帳號 sales@aurora.local / password123。
 */
test("未登入導向登入頁、登入後可在儀表板與客戶頁間切換並操作", async ({ page }) => {
  // 1. 未登入 → /login
  await page.goto("/");
  await expect(page).toHaveURL(/\/login/);

  // 2. 登入 → /dashboard
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.locator(".dashboard-grid")).toBeVisible();
  // 圖表依賴後端報表聚合查詢，冷啟動/負載時較慢，給較長逾時避免時序 flake
  await expect(page.locator('[data-promo-chart="industry"]')).toBeVisible({ timeout: 20000 });

  // SP5：RFM 客戶分群區塊出現且有資料列
  await expect(page.locator('[data-promo-chart="rfm"]')).toBeVisible({ timeout: 20000 });
  await expect(page.locator(".rfm-row").first()).toBeVisible({ timeout: 20000 });

  // SP6：情緒意圖雷達區塊渲染（資料可能為空，仍應出現區塊骨架）
  await expect(page.locator('[data-promo-chart="sentiment-intent"]')).toBeVisible({ timeout: 20000 });

  // 3. 圖表下鑽 Modal 可開可關
  await page.locator('[data-promo-chart="industry"] .bar-row').first().click();
  await expect(page.locator(".modal-overlay")).toBeVisible();
  await page.locator(".report-footer button").click();
  await expect(page.locator(".modal-overlay")).toHaveCount(0);

  // 4. 側邊欄切到客戶頁
  await page.click("text=客戶工作台");
  await expect(page).toHaveURL(/\/customers/);
  await expect(page.locator(".customer-list")).toBeVisible();

  // 5. 點客戶 → URL 變 /customers/:id，詳情顯示
  await page.locator(".customer-row").first().click();
  await expect(page).toHaveURL(/\/customers\/\d+/);
  await expect(page.locator(".customer-hero")).toBeVisible();
  // Agent 歷程改為按鈕開啟 Modal（不再固定佔版面）：點開應顯示 AI 呼叫歷史與 Agent 決策歷程，關閉後續測
  await page.locator('.hero-actions >> text=AI 歷程').click();
  await expect(page.locator(".modal-content")).toBeVisible();
  await expect(page.getByText("Agent 決策歷程")).toBeVisible();
  await page.locator(".report-footer button").click();
  await expect(page.locator(".modal-overlay")).toHaveCount(0);

  // 6. 開 AI 聊天視窗
  await page.locator(".hero-actions >> text=詢問 AI 助理").click();
  await expect(page.locator(".chat-window")).toBeVisible();
  // SP4：送出問題 → 等 AI 回答串流完成並出現採納/拒絕（callId 經 SSE 抵達）→ 按採納
  await page.locator(".chat-suggestions button").first().click();
  await expect(page.locator('.ai-feedback button[title="採納"]')).toBeVisible({ timeout: 60000 });
  await page.locator('.ai-feedback button[title="採納"]').click();
  await expect(page.locator(".ai-feedback.done")).toBeVisible();
  await page.locator(".chat-close").click();

  // 7. 新增互動：填寫送出後應存下並出現在時間線（迴歸「新增互動沒存下」bug）
  await page.locator("text=+ 新增互動").click();
  await expect(page.locator(".modal-content")).toBeVisible();
  await page.locator('select[name="type"]').selectOption("PHONE");
  await page.fill('input[name="occurredAt"]', "2026-06-19T10:00");
  await page.fill('textarea[name="content"]', "E2E 新增互動驗證");
  await page.locator('.modal-actions button[type="submit"]').click();
  await expect(page.locator(".modal-content")).toHaveCount(0);
  await expect(page.locator(".timeline").getByText("E2E 新增互動驗證").first()).toBeVisible();

  // 8. 登出 → /login
  await page.locator(".user-card button").click();
  await expect(page).toHaveURL(/\/login/);
});
