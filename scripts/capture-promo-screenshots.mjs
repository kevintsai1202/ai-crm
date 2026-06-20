import playwright from "file:///D:/GitHub/hahow-ai-full-stack/teaching-site/node_modules/playwright/index.js";
import { mkdir, readFile, rename } from "node:fs/promises";
import { resolve } from "node:path";

const { chromium } = playwright;

const CHARTS = [
  { id: "pipeline", filename: "01-pipeline-funnel.png", trigger: "[data-promo-chart='pipeline'] .funnel-stage-wrapper" },
  { id: "forecast", filename: "02-monthly-forecast.png", trigger: "[data-promo-chart='forecast'] .chart-hit" },
  { id: "industry", filename: "03-industry-breakdown.png", trigger: "[data-promo-chart='industry'] .bar-row" },
  { id: "risk", filename: "04-risk-breakdown.png", trigger: "[data-promo-chart='risk'] .risk-chip" },
  { id: "renewal", filename: "05-renewal-forecast.png", trigger: "[data-promo-chart='renewal'] .renewal-bar" },
  { id: "leaderboard", filename: "06-owner-leaderboard.png", trigger: "[data-promo-chart='leaderboard'] .leader-row" },
  { id: "activity", filename: "07-recent-activities.png", trigger: "[data-promo-chart='activity'] .activity-item" }
];

const POSTERS = [
  {
    filename: "01-hahow-ai-crm-dashboard.png",
    source: ["charts", "01-pipeline-funnel.png"],
    eyebrow: "Hahow 提案素材",
    title: "用 AI CRM 把全端作品做成可展示的業務工作台",
    copy: "React Dashboard、Spring Boot API、JWT、RAG 與 Agent Trace 一次串起，截圖可直接放進課程提案與社群宣傳。"
  },
  {
    filename: "02-hahow-chart-drilldown.png",
    source: ["interactions", "02-chart-drilldown-open.png"],
    eyebrow: "互動圖表展示",
    title: "每個報表都能點開底層客戶與商機",
    copy: "從銷售漏斗、月度 Forecast 到風險結構，展示的不只是畫面，而是完整可操作的產品流程。"
  },
  {
    filename: "03-hahow-ai-agent-flow.png",
    source: ["03-customer-ai-agent-detail.png"],
    eyebrow: "AI Agent Demo",
    title: "客戶 360、AI 助理與 Agent Trace 同畫面呈現",
    copy: "讓學員看到資料、推理、建議與引用來源如何在真實 CRM 介面裡協作。"
  }
];

/**
 * Capture role：擷取 CRM 課程宣傳用畫面，不做斷言，只產出可審稿截圖與操作影片。
 */
async function main() {
  const frontendUrl = process.env.FRONTEND_URL || "http://127.0.0.1:5173/";
  const outputDir = resolve("frontend", ".promo-screenshots");
  const chartDir = resolve(outputDir, "charts");
  const interactionDir = resolve(outputDir, "interactions");
  const videoDir = resolve(outputDir, "video");
  const verticalDir = resolve("frontend", ".hahow-promo-vertical");

  await Promise.all([outputDir, chartDir, interactionDir, videoDir, verticalDir].map((dir) => mkdir(dir, { recursive: true })));

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1200 },
    deviceScaleFactor: 1,
    locale: "zh-TW",
    recordVideo: { dir: videoDir, size: { width: 1440, height: 1200 } }
  });
  const page = await context.newPage();

  try {
    await loginToWorkspace(page, frontendUrl);
    await captureDesktopSet(page, outputDir, chartDir, interactionDir);
    await recordOperationFlow(page);
    const rawVideoPath = await page.video().path();
    await context.close();
    await rename(rawVideoPath, resolve(videoDir, "ai-crm-operation-flow.webm")).catch(() => {});
    await createVerticalPosters(browser, outputDir, verticalDir);
    console.log(`promo screenshots captured: ${outputDir}`);
    console.log(`hahow vertical posters captured: ${verticalDir}`);
    console.log(`operation video captured: ${resolve(videoDir, "ai-crm-operation-flow.webm")}`);
  } finally {
    if (!context.pages().every((p) => p.isClosed())) {
      await context.close().catch(() => {});
    }
    await browser.close();
  }
}

/**
 * 登入工作台並等待圖表區載入完成。
 *
 * @param page Playwright 頁面
 * @param frontendUrl 前端網址
 */
async function loginToWorkspace(page, frontendUrl) {
  await page.goto(frontendUrl, { waitUntil: "networkidle" });
  await page.fill("input[name=username]", "sales@aurora.local");
  await page.fill("input[name=password]", "password123");
  await page.click("button:has-text('登入')");
  await page.waitForSelector("[data-promo-chart='pipeline']", { timeout: 15000 });
  await page.waitForSelector("[data-promo-chart='leaderboard']", { timeout: 15000 });
}

/**
 * 產出桌面全頁、圖表拆圖、下鑽互動與表單/AI 區塊截圖。
 *
 * @param page Playwright 頁面
 * @param outputDir 主輸出目錄
 * @param chartDir 圖表拆圖目錄
 * @param interactionDir 互動過程目錄
 */
async function captureDesktopSet(page, outputDir, chartDir, interactionDir) {
  await page.screenshot({ path: resolve(outputDir, "01-dashboard-reports-full.png"), fullPage: true });
  await page.locator(".report-grid").screenshot({ path: resolve(outputDir, "02-crm-classic-charts.png") });

  for (const chart of CHARTS) {
    await page.locator(`[data-promo-chart='${chart.id}']`).screenshot({ path: resolve(chartDir, chart.filename) });
  }

  await page.locator("[data-promo-chart='pipeline']").screenshot({ path: resolve(interactionDir, "01-chart-before-drilldown.png") });
  await hoverFirst(page, "[data-promo-chart='pipeline'] .funnel-stage-wrapper");
  await page.locator("[data-promo-chart='pipeline']").screenshot({ path: resolve(interactionDir, "01b-chart-hover-state.png") });
  await clickFirst(page, "[data-promo-chart='pipeline'] .funnel-stage-wrapper");
  await page.waitForSelector(".drill-list, .empty-state-box", { timeout: 10000 });
  await page.locator(".report-modal").screenshot({ path: resolve(interactionDir, "02-chart-drilldown-open.png") });
  await page.click(".report-footer button");

  await page.click("button:has-text('詢問 AI 助理')");
  await page.fill(".chat-footer textarea", "請分析這位客戶的風險並給業務下一步建議");
  await page.click(".chat-footer button:has-text('送出')");
  await page.waitForSelector(".chat-msg.assistant .markdown-body", { timeout: 20000 });
  await page.locator(".chat-window").screenshot({ path: resolve(interactionDir, "03-ai-chat-response.png") });
  await page.click(".chat-close");

  await page.locator(".detail-stack").screenshot({ path: resolve(outputDir, "03-customer-ai-agent-detail.png") });
  await page.click("button:has-text('+ 新增客戶')");
  await page.waitForSelector("text=新增客戶", { timeout: 5000 });
  await page.screenshot({ path: resolve(outputDir, "04-add-customer-operation.png"), fullPage: false });
  await page.keyboard.press("Escape").catch(() => {});
  await page.mouse.click(20, 20);

  await page.click("button:has-text('+ 新增互動')");
  await page.waitForSelector("text=新增互動", { timeout: 5000 });
  await page.screenshot({ path: resolve(outputDir, "05-add-interaction-operation.png"), fullPage: false });
  await page.keyboard.press("Escape").catch(() => {});
  await page.mouse.click(20, 20);
}

/**
 * 讓錄影中包含明確的圖表、下鑽、AI 聊天與表單操作流程。
 *
 * @param page Playwright 頁面
 */
async function recordOperationFlow(page) {
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: "smooth" }));
  await page.waitForTimeout(600);
  await clickFirst(page, "[data-promo-chart='forecast'] .chart-hit");
  await page.waitForSelector(".report-modal", { timeout: 10000 });
  await page.waitForTimeout(900);
  await page.click(".report-footer button");
  await page.waitForTimeout(400);
  await page.click("button:has-text('詢問 AI 助理')");
  await page.waitForTimeout(400);
  await page.fill(".chat-footer textarea", "用三點條列總結最近的互動重點");
  await page.click(".chat-footer button:has-text('送出')");
  await page.waitForSelector(".chat-msg.assistant .markdown-body", { timeout: 20000 });
  await page.waitForTimeout(1000);
  await page.click(".chat-close");
  await page.waitForTimeout(400);
  await page.click("button:has-text('+ 新增客戶')");
  await page.waitForSelector("text=新增客戶", { timeout: 5000 });
  await page.fill("input[name=name]", "Demo AI 客戶股份有限公司");
  await page.fill("input[name=email]", "demo@example.com");
  await page.fill("input[name=phone]", "0912345678");
  await page.waitForTimeout(1200);
}

/**
 * 以截圖素材合成 Hahow 提案用直式文宣圖稿。
 *
 * @param browser Playwright browser
 * @param outputDir 主輸出目錄
 * @param verticalDir 直式文宣輸出目錄
 */
async function createVerticalPosters(browser, outputDir, verticalDir) {
  const posterPage = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  try {
    for (const poster of POSTERS) {
      const sourcePath = resolve(outputDir, ...poster.source);
      const imageBase64 = await readFile(sourcePath, "base64");
      await posterPage.setContent(renderPosterHtml({ ...poster, imageBase64 }), { waitUntil: "domcontentloaded" });
      await posterPage.screenshot({ path: resolve(verticalDir, poster.filename), fullPage: false });
    }
  } finally {
    await posterPage.close();
  }
}

/**
 * 產生單張直式文宣的 HTML。
 *
 * @param poster 文案與 base64 圖片資料
 * @returns 可截圖的 HTML 字串
 */
function renderPosterHtml(poster) {
  return `<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      width: 1080px;
      height: 1920px;
      font-family: "Microsoft JhengHei UI", "Segoe UI", sans-serif;
      color: #102232;
      background: linear-gradient(180deg, #ecf7f4 0%, #dceef7 56%, #102b40 56%, #102b40 100%);
      overflow: hidden;
    }
    .poster {
      position: relative;
      width: 100%;
      height: 100%;
      padding: 90px 76px 72px;
    }
    .eyebrow {
      display: inline-block;
      padding: 10px 18px;
      border-radius: 999px;
      background: #0f766e;
      color: #fff;
      font-size: 28px;
      font-weight: 800;
    }
    h1 {
      margin: 36px 0 24px;
      font-size: 78px;
      line-height: 1.12;
      letter-spacing: 0;
    }
    .copy {
      width: 86%;
      margin: 0;
      color: #405569;
      font-size: 34px;
      line-height: 1.55;
      font-weight: 600;
    }
    .shot {
      position: absolute;
      left: 76px;
      right: 76px;
      top: 790px;
      border: 12px solid rgba(255,255,255,.92);
      border-radius: 8px;
      background: #fff;
      box-shadow: 0 34px 90px rgba(12, 31, 51, .28);
      overflow: hidden;
    }
    .shot img {
      display: block;
      width: 100%;
      max-height: 610px;
      object-fit: cover;
      object-position: top center;
    }
    .bottom {
      position: absolute;
      left: 76px;
      right: 76px;
      bottom: 82px;
      color: #f8fffd;
    }
    .bottom strong {
      display: block;
      font-size: 58px;
      line-height: 1.14;
      letter-spacing: 0;
    }
    .bottom span {
      display: block;
      margin-top: 18px;
      color: rgba(248,255,253,.78);
      font-size: 28px;
      font-weight: 700;
    }
  </style>
</head>
<body>
  <main class="poster">
    <span class="eyebrow">${escapeHtml(poster.eyebrow)}</span>
    <h1>${escapeHtml(poster.title)}</h1>
    <p class="copy">${escapeHtml(poster.copy)}</p>
    <figure class="shot"><img src="data:image/png;base64,${poster.imageBase64}" alt=""></figure>
    <div class="bottom">
      <strong>AI CRM 智慧業務助理</strong>
      <span>Full-stack demo / Dashboard / Agent Trace / RAG</span>
    </div>
  </main>
</body>
</html>`;
}

/**
 * 滑鼠移到第一個符合條件的元素中心。
 *
 * @param page Playwright 頁面
 * @param selector CSS selector
 */
async function hoverFirst(page, selector) {
  const target = page.locator(selector).first();
  await target.scrollIntoViewIfNeeded();
  await target.hover();
}

/**
 * 點擊第一個符合條件的元素。
 *
 * @param page Playwright 頁面
 * @param selector CSS selector
 */
async function clickFirst(page, selector) {
  const target = page.locator(selector).first();
  await target.scrollIntoViewIfNeeded();
  await target.click();
}

/**
 * 避免文宣 HTML 中的文案破壞標籤結構。
 *
 * @param value 需輸出的文字
 * @returns HTML-safe 文字
 */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
