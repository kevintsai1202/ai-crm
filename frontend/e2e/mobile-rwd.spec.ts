import { test, expect, type Page } from "@playwright/test";

/**
 * RWD 版面檢測：驗證登入頁、Dashboard、客戶工作台與管理頁在手機／平板無整頁水平溢出。
 * 函式級註解：Phase 2 i18n 遷移後英文字串通常比中文長，容易在窄螢幕撐破原本針對中文設計的
 * 按鈕/徽章寬度；本測試分別以 en / zh-TW 兩種語言在手機寬度下檢查，找出實際溢出的元素。
 * 前置：後端需已啟動（127.0.0.1:18080），seed 帳號 admin@aurora.local / password123。
 */

/** 手機視窗尺寸，涵蓋 320px 最小支援寬度與兩種常見窄機。 */
const MOBILE_VIEWPORTS = [
  { name: "Minimum mobile (320x700)", width: 320, height: 700 },
  { name: "iPhone SE (375x667)", width: 375, height: 667 },
  { name: "Android 常見窄機 (360x740)", width: 360, height: 740 }
];

/** 平板與小型筆電視窗，覆蓋側欄轉為緊湊頂部導覽的斷點。 */
const TABLET_VIEWPORTS = [
  { name: "Tablet (768x844)", width: 768, height: 844 },
  { name: "Small laptop (1024x844)", width: 1024, height: 844 }
];

/**
 * 檢查目前頁面是否有水平溢出，並回傳溢出元素清單（含 class/尺寸），方便定位問題。
 * 用 scrollWidth > clientWidth 判斷整頁是否可橫向捲動；再逐一檢查所有元素的
 * getBoundingClientRect().right 是否超出視窗寬度，找出實際撐破版面的元素。
 *
 * @param page Playwright 頁面
 * @returns 溢出元素描述陣列（空陣列表示無溢出）
 */
async function findHorizontalOverflow(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const viewportWidth = window.innerWidth;
    const offenders: string[] = [];
    const pageWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);
    // 表格容器可局部捲動是刻意設計；整頁寬度未超出時不應把其內容誤判為頁面溢出。
    if (pageWidth <= document.documentElement.clientWidth + 1) return offenders;
    const all = document.querySelectorAll("body *");
    all.forEach((el) => {
      const rect = el.getBoundingClientRect();
      // 容許 1px 誤差（次像素捲動軸等瀏覽器差異）；只記錄「確實看得見」且會撐開版面的元素
      if (rect.right > viewportWidth + 1 && rect.width > 0) {
        const tag = el.tagName.toLowerCase();
        const cls = el.className ? `.${String(el.className).split(" ").join(".")}` : "";
        offenders.push(`${tag}${cls} (right=${Math.round(rect.right)}px, viewport=${viewportWidth}px)`);
      }
    });
    return offenders;
  });
}

/** 登入並等待進入 Dashboard。 */
async function login(page: Page) {
  await page.goto("/");
  await page.fill('input[name="username"]', "admin@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
}

for (const viewport of MOBILE_VIEWPORTS) {
  for (const lang of ["en", "zh-TW"] as const) {
    test(`${viewport.name} · ${lang}：登入頁與主要頁面無水平溢出`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      // 進頁前先寫入語言選擇，讓 detectLanguage 偵測到已儲存語言，避免受瀏覽器/OS locale 影響
      await page.addInitScript((l) => localStorage.setItem("ai-crm-lang", l), lang);

      // ① 登入頁
      await page.goto("/");
      await expect(page.locator(".login-panel")).toBeVisible();
      let offenders = await findHorizontalOverflow(page);
      expect(offenders, `登入頁（${lang}）溢出元素：\n${offenders.join("\n")}`).toEqual([]);

      // ② Dashboard
      await login(page);
      await expect(page.locator(".dashboard-grid")).toBeVisible();
      offenders = await findHorizontalOverflow(page);
      expect(offenders, `Dashboard（${lang}）溢出元素：\n${offenders.join("\n")}`).toEqual([]);

      // 手機列首預設只佔一列；完整導覽由按鈕開啟為浮動面板。
      const sidebarHeight = await page.locator(".sidebar").evaluate((element) => element.getBoundingClientRect().height);
      expect(sidebarHeight).toBeLessThanOrEqual(68);
      const mobileMenuToggle = page.locator(".mobile-menu-toggle");
      await expect(mobileMenuToggle).toHaveAttribute("aria-expanded", "false");
      await mobileMenuToggle.click();
      await expect(page.locator(".sidebar-content")).toBeVisible();
      await expect(mobileMenuToggle).toHaveAttribute("aria-expanded", "true");
      offenders = await findHorizontalOverflow(page);
      expect(offenders, `手機浮動選單（${lang}）溢出元素：\n${offenders.join("\n")}`).toEqual([]);

      // ③ 客戶工作台
      await page.locator('a[href="/customers"], .side-nav-link[href="/customers"]').first().click();
      await expect(page).toHaveURL(/\/customers/);
      await expect(page.locator(".workspace-grid")).toBeVisible();
      await expect(mobileMenuToggle).toHaveAttribute("aria-expanded", "false");
      offenders = await findHorizontalOverflow(page);
      expect(offenders, `客戶工作台（${lang}）溢出元素：\n${offenders.join("\n")}`).toEqual([]);

      // ④ 主管與管理頁：寬表格僅允許在自身容器內捲動。
      for (const route of ["/team", "/admin/users", "/admin/settings"]) {
        await page.goto(route);
        await expect(page.locator(".main")).toBeVisible();
        offenders = await findHorizontalOverflow(page);
        expect(offenders, `${route}（${lang}）溢出元素：\n${offenders.join("\n")}`).toEqual([]);
      }
    });
  }
}

for (const viewport of TABLET_VIEWPORTS) {
  test(`${viewport.name}：真實資料主要頁面無整頁水平溢出`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.addInitScript(() => localStorage.setItem("ai-crm-lang", "en"));
    await login(page);

    for (const route of ["/dashboard", "/customers", "/team", "/admin/users", "/admin/settings"]) {
      await page.goto(route);
      await expect(page.locator(".main")).toBeVisible();
      const offenders = await findHorizontalOverflow(page);
      expect(offenders, `${route} 溢出元素：\n${offenders.join("\n")}`).toEqual([]);
    }
  });
}
