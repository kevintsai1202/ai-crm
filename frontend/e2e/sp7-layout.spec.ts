import { test, expect } from "@playwright/test";

/**
 * SP7 煙霧測試：清單分頁、下鑽麵包屑返回、區塊關閉 + 抽屜加回。
 * 前置：後端 127.0.0.1:18080（已套 V9）、seed sales@aurora.local / password123。
 */
test("SP7 版面：分頁 / 麵包屑 / 關閉加回", async ({ page }) => {
  // 登入
  await page.goto("/");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  // 重整後仍保有登入態（使用者卡與登出鈕由 token 還原，不應消失）
  await page.reload();
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.locator(".user-card button")).toBeVisible();

  // 卡片層級區塊出現（高風險互動、流失雷達各為獨立卡片）
  await expect(page.locator('#block-sr-highrisk')).toBeVisible();

  // 清單分頁：若高風險互動超過 5 筆，分頁列出現且可下一頁
  const pager = page.locator('#block-sr-highrisk .paginated-list .pagination').first();
  if (await pager.count() > 0) {
    await pager.getByRole("button", { name: "下一頁" }).click();
    await expect(pager.getByText(/第 2 \/ /)).toBeVisible();
  }

  // 下鑽麵包屑：點流失雷達任一列 → 客戶頁出現麵包屑 → 點「儀表板」返回
  const churnRow = page.locator('#block-sr-churn .sr-churn-row').first();
  if (await churnRow.count() > 0) {
    await churnRow.click();
    await expect(page).toHaveURL(/\/customers\/\d+/);
    await expect(page.locator('.breadcrumb')).toContainText("流失雷達");
    await page.locator('.breadcrumb-link', { hasText: "儀表板" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  }

  // 關閉區塊 + 抽屜加回：關掉 RFM 區塊 → 抽屜加回
  await page.locator('#block-rfm .block-close').click();
  await expect(page.locator('#block-rfm')).toHaveCount(0);
  await page.locator('.layout-btn').click();
  await expect(page.locator('.drawer')).toBeVisible();
  await page.locator('.drawer-item', { hasText: "RFM" }).getByRole("button", { name: /加回/ }).click();
  await expect(page.locator('#block-rfm')).toBeVisible();
});
