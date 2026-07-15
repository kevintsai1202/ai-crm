import { chromium } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

/**
 * 截圖用途：擷取登入頁與登入後首頁（Dashboard）側邊欄底部的語言切換元件（LanguageSwitcher），
 * 供設計討論使用（確認目前原生 <select> 是否需要重新設計樣式）。
 * 前端須先以 `pnpm dev` 啟動，後端須先啟動（127.0.0.1:18080）。
 * 執行方式：在 frontend/ 目錄下 `node scripts/screenshot-language-switcher.mjs`。
 */
async function main() {
  const frontendUrl = process.env.FRONTEND_URL || "http://127.0.0.1:5173/";
  const outputDir = resolve(".design-review-screenshots");
  await mkdir(outputDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, locale: "zh-TW" });
  const page = await context.newPage();

  try {
    // ① 登入頁：截整頁 + 語言切換元件特寫
    await page.goto(frontendUrl, { waitUntil: "networkidle" });
    await page.waitForSelector(".login-panel");
    await page.screenshot({ path: resolve(outputDir, "01-login-page.png") });
    await page.locator(".login-copy .lang-switcher").screenshot({ path: resolve(outputDir, "02-login-lang-switcher.png") });

    // ② 登入後首頁（Dashboard）：截整頁 + 側邊欄底部語言切換元件特寫
    await page.fill('input[name="username"]', "sales@aurora.local");
    await page.fill('input[name="password"]', "password123");
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/dashboard/);
    await page.waitForSelector(".sidebar");
    await page.screenshot({ path: resolve(outputDir, "03-dashboard-page.png") });
    await page.locator(".sidebar .lang-switcher").screenshot({ path: resolve(outputDir, "04-sidebar-lang-switcher.png") });

    // ③ 切到英文後再截一次，確認雙語系下外觀是否一致
    await page.locator(".sidebar .lang-switcher-select").selectOption("en");
    await page.waitForTimeout(300);
    await page.screenshot({ path: resolve(outputDir, "05-dashboard-en.png") });

    console.log(`screenshots captured: ${outputDir}`);
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
