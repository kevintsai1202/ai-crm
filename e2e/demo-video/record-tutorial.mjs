// =====================================================================
// AI CRM 操作教學影片:Playwright 錄製腳本(可重跑)
//
// 為何用 Playwright 原生 recordVideo 而非 agent-browser record:
//   agent-browser 的錄影在畫面靜止時不補幀,影片時間軸會被壓縮、
//   且 record start 會重建 context 遺失登入狀態;Playwright 原生錄影
//   以固定幀率補幀(影片長度=實際操作時間),且 context 建立時可同時
//   指定 recordVideo 與 storageState,每個段落都是獨立乾淨的一段影片。
//
// 重跑方式(於 repo 根目錄):
//   node e2e/demo-video/record-tutorial.mjs                # 錄中文(預設)
//   node e2e/demo-video/record-tutorial.mjs --lang=en      # 錄英文 UI
//   node e2e/demo-video/record-tutorial.mjs --lang=en --only=chat
//
// 段落代號:login / dashboard / customers / chat / card / draft / admin
// 輸出:e2e/demo-video/recordings/<zh|en>/01-login.webm ~ 07-ai-opportunity.webm
//
// 前置需求:
//   1. frontend 已 pnpm install(借用其 @playwright/test 與 Chromium)
//   2. 先執行 make-business-card.ps1 產生示範名片 PNG
// =====================================================================
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";

const here = path.dirname(fileURLToPath(import.meta.url));
// 借用 frontend 的 Playwright 安裝(避免重複安裝瀏覽器)
const requireFrontend = createRequire(path.join(here, "..", "..", "frontend", "package.json"));
const { chromium } = requireFrontend("@playwright/test");

// ---------- 常數 ----------
const BASE = process.env.DEMO_BASE_URL || "https://aicrm-frontend-kt2026.zeabur.app"; // 目標站台
const API = process.env.DEMO_API_URL || "https://aicrm-backend-kt2026.zeabur.app";    // 後端 API
const CARD_PNG = path.join(here, "assets", "business-card.png"); // 示範名片
const VIEW = { width: 1920, height: 1080 };                    // 1080p 視窗

// --lang=zh|en 參數:決定 UI 語言與錄影輸出子目錄
const langArg = process.argv.find((a) => a.startsWith("--lang="));
const langRaw = (langArg ? langArg.slice(7) : "zh").trim().toLowerCase();
/** @type {"zh" | "en"} */
const LANG = langRaw === "en" || langRaw === "en-us" ? "en" : "zh";
/** app 內部 localStorage 值 */
const APP_LANG = LANG === "en" ? "en" : "zh-TW";
/** Playwright locale */
const BROWSER_LOCALE = LANG === "en" ? "en-US" : "zh-TW";
const REC = path.join(here, "recordings", LANG); // 分語言輸出,避免中英畫面混用

// 介面字串與聊天示範句(依語言)
const UI = LANG === "en"
  ? {
      navCustomers: "Customers",
      navAdmin: "System Settings",
      businessCard: /Business card intake/,
      startRecognition: /Start recognition/,
      cardDone: /Record creation complete/,
      aiSuggestions: /AI work suggestions/,
      generateSuggestions: /Generate my work recommendations/,
      draftsTitle: "AI opportunity suggestions",
      createAll: /Create selected/,
      created: /Created/,
      chatResponding: "Responding",
      chatSend: "Send",
      chatQ1: "Please analyze this customer's renewal risk and suggest next actions",
      chatQ2: "Great, based on that analysis, draft key points for a follow-up email to the customer",
      industry: "Artificial Intelligence",
    }
  : {
      navCustomers: "客戶工作台",
      navAdmin: "系統設定",
      businessCard: /名片建檔/,
      startRecognition: /開始辨識/,
      cardDone: "建檔完成",
      aiSuggestions: /AI 工作建議/,
      generateSuggestions: /產生我的工作建議/,
      draftsTitle: "AI 建議商機",
      createAll: /全部建立/,
      created: /已建立/,
      chatResponding: "回應中",
      chatSend: "送出",
      chatQ1: "請分析這位客戶的續約風險,並建議下一步行動",
      chatQ2: "很好,請根據以上分析,幫我草擬一封給客戶的跟進郵件重點",
      industry: "人工智慧",
    };

// 教學帳號登入結果(token + user),由 API 取得後注入各 context 的 sessionStorage
// (app 將 JWT 存 sessionStorage,Playwright storageState 抓不到 sessionStorage)
let salesAuth = null;

// --only=a,b,c 參數:只錄指定段落
const onlyArg = process.argv.find((a) => a.startsWith("--only="));
const only = onlyArg ? onlyArg.slice(7).split(",").map((s) => s.trim()).filter(Boolean) : [];
const shouldRun = (name) => only.length === 0 || only.includes(name);

/** 等待指定毫秒(教學影片刻意放慢節奏,讓觀眾看清畫面)。 */
const pace = (ms) => new Promise((r) => setTimeout(r, ms));

/** 平滑捲動指定距離並停留。 */
async function scrollBy(page, dy, stay = 2200) {
  await page.evaluate((y) => window.scrollBy({ top: y, behavior: "smooth" }), dy);
  await pace(stay);
}

/** 等待 AI 聊天串流結束:送出鈕從「回應中…」變回「送出/Send」。 */
async function waitChatIdle(page, timeout = 120000) {
  const responding = UI.chatResponding;
  const send = UI.chatSend;
  // 先等進入回應中(送出後很快出現;若已錯過就略過)
  await page.waitForFunction(
    (needle) => document.querySelector(".chat-footer button[type=submit]")?.textContent.includes(needle),
    responding,
    { timeout: 8000 },
  ).catch(() => {});
  // 再等回到送出代表串流完成
  await page.waitForFunction(
    (label) => document.querySelector(".chat-footer button[type=submit]")?.textContent.trim() === label,
    send,
    { timeout },
  );
}

/**
 * 錄製一個段落:建立含 recordVideo 的獨立 context,執行操作後存檔。
 * @param {string} file 輸出檔名(如 01-login.webm)
 * @param {{auth?: boolean}} opts auth=false 時不載入登入狀態(示範登入用)
 * @param {(page) => Promise<void>} fn 段落操作
 */
async function segment(browser, file, opts, fn) {
  const { auth = true } = opts;
  const ctx = await browser.newContext({
    viewport: VIEW,
    recordVideo: { dir: REC, size: VIEW },
    locale: BROWSER_LOCALE,
  });
  // 每個新頁面都先寫入語言與登入狀態(token/user 存 sessionStorage)
  await ctx.addInitScript(
    ({ appLang, authPayload }) => {
      localStorage.setItem("ai-crm-lang", appLang);
      if (authPayload) {
        sessionStorage.setItem("ai-crm-token", authPayload.token);
        sessionStorage.setItem("ai-crm-user", JSON.stringify(authPayload.user));
      }
    },
    { appLang: APP_LANG, authPayload: auth ? salesAuth : null },
  );
  const page = await ctx.newPage();
  page.setDefaultTimeout(30000);
  try {
    await fn(page);
  } finally {
    const video = page.video();
    await ctx.close(); // 關閉 context 後影片才寫盤
    if (video) {
      const target = path.join(REC, file);
      await video.saveAs(target);
      await video.delete(); // 移除 Playwright 的隨機檔名原檔
      console.log(`  ✔ ${file}`);
    }
  }
}

// ---------- 主流程 ----------
fs.mkdirSync(REC, { recursive: true });
if (!fs.existsSync(CARD_PNG)) throw new Error(`找不到示範名片 ${CARD_PNG},請先跑 make-business-card.ps1`);

console.log(`錄製語言:${LANG} (ai-crm-lang=${APP_LANG}) → ${REC}`);

const browser = await chromium.launch();

// 先以業務帳號透過 API 登入,取得 token 與 user 供各段落注入
{
  const res = await fetch(`${API}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "sales@aurora.local", password: "password123" }),
  });
  if (!res.ok) throw new Error(`API 登入失敗:HTTP ${res.status}`);
  salesAuth = await res.json();
  console.log(`登入成功:${salesAuth.user.displayName}(${salesAuth.user.username})`);
}

// ---------- 段落 1:登入(從乾淨狀態示範登入動作) ----------
if (shouldRun("login")) {
  console.log("=== 錄製:登入 ===");
  await segment(browser, "01-login.webm", { auth: false }, async (page) => {
    await page.goto(`${BASE}/login`, { waitUntil: "networkidle" });
    await pace(2800);                                   // 停留登入頁,讓觀眾看到表單與教學帳號
    await page.click(".login-form button[type=submit]");
    await page.waitForURL("**/dashboard");
    await page.waitForLoadState("networkidle");
    await pace(3500);                                   // 儀表板第一眼
  });
}

// ---------- 段落 2:儀表板導覽 ----------
if (shouldRun("dashboard")) {
  console.log("=== 錄製:儀表板 ===");
  await segment(browser, "02-dashboard.webm", {}, async (page) => {
    await page.goto(`${BASE}/dashboard`, { waitUntil: "networkidle" });
    await pace(2400);                                   // KPI 統計卡
    await scrollBy(page, 450);                          // 漏斗/預測圖
    await scrollBy(page, 500);                          // 產業/風險分布
    await scrollBy(page, 550, 2500);                    // 排行榜/活動報告
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: "smooth" }));
    await pace(2000);
  });
}

// ---------- 段落 3:客戶工作台 ----------
if (shouldRun("customers")) {
  console.log("=== 錄製:客戶工作台 ===");
  await segment(browser, "03-customers.webm", {}, async (page) => {
    await page.goto(`${BASE}/dashboard`, { waitUntil: "networkidle" });
    await pace(1500);
    await page.getByRole("link", { name: UI.navCustomers }).click(); // 側邊欄導覽
    await page.waitForLoadState("networkidle");
    await pace(2500);                                   // 客戶列表總覽
    await page.locator(".customer-row").first().click();
    await page.waitForLoadState("networkidle");
    await pace(2800);                                   // 客戶詳情
    await scrollBy(page, 500);                          // 互動時間軸
    await scrollBy(page, 550, 2500);                    // 商機看板
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: "smooth" }));
    await pace(1500);
  });
}

// ---------- 段落 4:AI 助理對話(gpt-5.6-sol) ----------
if (shouldRun("chat")) {
  console.log("=== 錄製:AI 助理對話 ===");
  await segment(browser, "04-ai-chat.webm", {}, async (page) => {
    await page.goto(`${BASE}/customers`, { waitUntil: "networkidle" });
    // 選第二位客戶(避開先前測試過的對話,呈現乾淨的建議問題畫面)
    await page.locator(".customer-row").nth(1).click();
    await page.waitForLoadState("networkidle");
    await pace(1500);
    await page.click(".chat-launcher");                 // 右下角 AI 助理浮動鈕
    await pace(2200);                                   // 顯示建議問題
    // 有建議問題就點第一個;沒有(該客戶已有對話)則手動輸入第一問
    const suggestion = page.locator(".chat-suggestions button").first();
    if (await suggestion.count()) {
      await suggestion.click();
    } else {
      await page.locator(".chat-footer textarea").pressSequentially(UI.chatQ1, { delay: 40 });
      await pace(600);
      await page.click(".chat-footer button[type=submit]");
    }
    await waitChatIdle(page);                           // 等 gpt-5.6-sol 串流完成
    await pace(2500);
    // 追問延伸問題:展示可與模型持續討論、延伸應用(逐字輸入呈現打字感)
    await page.locator(".chat-footer textarea").pressSequentially(UI.chatQ2, { delay: 40 });
    await pace(800);
    await page.click(".chat-footer button[type=submit]");
    await waitChatIdle(page);
    await pace(3000);
  });
}

// ---------- 段落 5:名片建檔精靈 ----------
if (shouldRun("card")) {
  console.log("=== 錄製:名片建檔 ===");
  await segment(browser, "06-business-card.webm", {}, async (page) => {
    // 記錄名片 API 回應,便於失敗時診斷
    page.on("response", (r) => {
      if (r.url().includes("business-card")) console.log(`  [card-api] ${r.status()} ${r.request().method()} ${r.url()}`);
    });
    await page.goto(`${BASE}/customers`, { waitUntil: "networkidle" });
    await pace(1000);
    await page.getByRole("button", { name: UI.businessCard }).click();
    await page.waitForLoadState("networkidle");
    await pace(2500);                                   // 精靈介紹文案
    await page.setInputFiles("input[name=businessCardFile]", CARD_PNG);
    await pace(1500);
    await page.getByRole("button", { name: UI.startRecognition }).click();
    await page.waitForSelector("input[name=bc-taxId]", { timeout: 90000 }); // 等 AI OCR 完成
    await pace(3000);                                   // 校正欄位畫面(欄位已由 AI 帶入)
    // 補上 OCR 沒有、但表單必填的欄位:統一編號與產業(逐字輸入呈現校正感)
    await page.locator("input[name=bc-taxId]").pressSequentially("24681357", { delay: 60 });
    await pace(600);
    await page.locator("input[name=bc-industry]").pressSequentially(UI.industry, { delay: 60 });
    await pace(1500);
    // 若辨識到重複客戶候選(email/電話/統編命中既有客戶),需先選處理策略「建立新客戶」
    // 才能啟用下一步;無重複候選時此區塊不出現,直接略過。
    const dupCreate = page.locator("[data-testid=bc-duplicates] input[name=duplicateStrategy][value=CREATE]");
    if (await dupCreate.count()) {
      await dupCreate.check();
      await pace(1000);
    }
    await page.locator("[data-testid=bc-review-next]").click();
    await pace(2200);                                   // 商機與任務畫面(商機名稱已預填)
    await page.locator("input[name=bc-opportunityAmount]").fill("1200000");
    await pace(600);
    await page.locator("input[name=bc-expectedCloseDate]").fill("2026-09-30");
    await pace(600);
    await page.locator("input[name=bc-callAt]").fill("2026-07-24T10:00");   // 必填:預定通話時間
    await pace(1500);
    await page.locator("[data-testid=bc-confirm-submit]").click();
    // 等待完成頁;若出現錯誤訊息則印出協助診斷
    const done = page.getByText(UI.cardDone);
    const err = page.locator("[data-testid=bc-confirm-error]");
    await Promise.race([
      done.waitFor({ timeout: 45000 }),
      err.waitFor({ timeout: 45000 }).then(async () => {
        throw new Error(`確認建檔失敗:${await err.textContent()}`);
      }),
    ]);
    await pace(3500);                                   // 完成頁:客戶/聯絡人/商機/任務 ID
  });
}

// ---------- 段落 6:AI 工作建議 → 一鍵建立商機 ----------
if (shouldRun("draft")) {
  console.log("=== 錄製:AI 建立商機 ===");
  await segment(browser, "07-ai-opportunity.webm", {}, async (page) => {
    await page.goto(`${BASE}/customers`, { waitUntil: "networkidle" });
    await pace(1200);
    await page.getByRole("button", { name: UI.aiSuggestions }).click();
    await pace(2500);                                   // 今日待辦清單
    await page.getByRole("button", { name: UI.generateSuggestions }).click();
    await page.getByText(UI.draftsTitle).waitFor({ timeout: 120000 }); // 等 AI 產生摘要與商機草稿
    await pace(3000);
    // 若有草稿則一鍵全部建立(把 AI 建議直接轉成正式商機)
    const createAll = page.getByRole("button", { name: UI.createAll });
    if (await createAll.count()) {
      await createAll.click();
      await page.getByText(UI.created).first().waitFor({ timeout: 45000 });
      await pace(2500);
    }
  });
}

// ---------- 段落 7:系統設定(展示 gpt-5.6-sol 模型) ----------
if (shouldRun("admin")) {
  console.log("=== 錄製:AI 模型設定 ===");
  await segment(browser, "05-admin-model.webm", { auth: false }, async (page) => {
    await page.goto(`${BASE}/login`, { waitUntil: "networkidle" });
    await pace(1500);
    // 清掉預填帳號,逐字輸入管理員帳號(呈現登入過程)
    const user = page.locator("input[name=username]");
    await user.fill("");
    await user.pressSequentially("admin@aurora.local", { delay: 45 });
    await pace(600);
    await page.click(".login-form button[type=submit]");
    await page.waitForURL("**/dashboard");
    await page.waitForLoadState("networkidle");
    await pace(1500);
    await page.getByRole("link", { name: UI.navAdmin }).click();      // 管理員限定頁
    await page.waitForLoadState("networkidle");
    await pace(3200);                                   // 顯示目前聊天模型 gpt-5.6-sol
    await scrollBy(page, 400, 2500);
  });
}

await browser.close();
console.log(`全部錄製完成,輸出目錄:${REC}`);
