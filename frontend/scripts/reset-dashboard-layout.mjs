import { chromium } from "@playwright/test";

/**
 * 還原 seed 帳號（sales@aurora.local）的儀表板版面偏好為預設值。
 * 用途：sp7-layout.spec.ts 等 e2e 測試會關閉/加回區塊並存回後端偏好，
 * 若測試中途失敗（例如斷言逾時）會讓「已關閉區塊」的狀態殘留在後端，
 * 造成下次執行時區塊已經是隱藏狀態而找不到元素。此腳本用於手動清乾淨測試汙染的狀態，
 * 可重複執行。
 */
const BASE_URL = process.env.BASE_URL ?? "http://127.0.0.1:5173";

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.goto(BASE_URL);
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/);

  await page.click(".layout-btn");
  await page.click(".btn-reset-layout");
  await page.click(".chat-close");

  console.log("已還原版面偏好為預設值。");
  await browser.close();
}

main();
