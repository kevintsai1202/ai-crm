import playwright from "file:///D:/GitHub/hahow-ai-full-stack/teaching-site/node_modules/playwright/index.js";
import { mkdir } from "node:fs/promises";

const { chromium } = playwright;

/**
 * 使用 Playwright 驗證前端登入、CRM 圖表報表、AI 回答與 Agent Trace 是否可見。
 */
async function main() {
  const frontendUrl = process.env.FRONTEND_URL || "http://127.0.0.1:5175/";
  await mkdir("frontend/.verification", { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const logs = [];
  page.on("console", (message) => logs.push(`console:${message.type()}:${message.text()}`));
  page.on("response", (response) => {
    if (response.url().includes("/api/")) {
      logs.push(`response:${response.status()}:${response.url()}`);
    }
  });

  try {
    await page.goto(frontendUrl, { waitUntil: "networkidle" });
    await page.fill("input[name=username]", "sales@aurora.local");
    await page.fill("input[name=password]", "password123");
    await page.click("button:has-text('登入')");
    await page.waitForSelector("text=客戶列表", { timeout: 10000 });
    await page.waitForSelector("text=銷售漏斗 Pipeline", { timeout: 10000 });
    await page.waitForSelector("text=月度營收 Forecast", { timeout: 10000 });
    await page.waitForSelector("text=產業營收分布", { timeout: 10000 });
    await page.waitForSelector("text=客戶風險結構", { timeout: 10000 });
    await page.waitForSelector("text=業務排行榜", { timeout: 10000 });
    await page.waitForSelector("text=AI 助理", { timeout: 10000 });
    await page.waitForSelector("text=Agent Trace", { timeout: 10000 });
    await askAiAssistant(page, "請分析這位客戶的風險");
    await page.waitForSelector(".chat-msg.assistant .markdown-body", { timeout: 20000 });
    await page.waitForFunction(() => {
      const submitButton = document.querySelector(".chat-footer button");
      return submitButton && !submitButton.textContent?.includes("回應中");
    }, { timeout: 30000 });

    const body = await page.textContent("body");
    const aiAnswer = await page.locator(".chat-msg.assistant .markdown-body").last().textContent();
    if (!body?.includes("星河製造") || !body.includes("銷售漏斗 Pipeline") || !aiAnswer || aiAnswer.trim().length < 10) {
      throw new Error("前端未呈現 CRM 客戶、圖表報表或 AI 回答內容");
    }

    await page.screenshot({ path: "frontend/.verification/dashboard.png", fullPage: true });
    console.log("frontend playwright verification passed");
  } catch (error) {
    await page.screenshot({ path: "frontend/.verification/frontend-failure.png", fullPage: true });
    console.error(logs.join("\n"));
    throw error;
  } finally {
    await browser.close();
  }
}

/**
 * 透過新版右下角 AI 聊天視窗送出驗證問題。
 *
 * @param page Playwright 頁面
 * @param message 驗證用問題
 */
async function askAiAssistant(page, message) {
  await page.click("button:has-text('詢問 AI 助理')");
  await page.waitForSelector(".chat-window", { timeout: 10000 });
  await page.fill(".chat-footer textarea", message);
  await page.click(".chat-footer button:has-text('送出')");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
