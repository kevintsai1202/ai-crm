import { test, expect } from "@playwright/test";

/**
 * 登入診斷 e2e：截圖、攔截 login API、驗證導向儀表板。
 * 前置：後端 18080、前端 5173（或 playwright webServer）。
 */
test("登入 API 與 UI 診斷", async ({ page }) => {
  const loginResponses: { status: number; body: string }[] = [];

  page.on("console", (msg) => {
    // 收集前端 console 錯誤供失敗時閱讀
    if (msg.type() === "error") {
      console.log("[browser console error]", msg.text());
    }
  });

  page.on("response", async (res) => {
    if (res.url().includes("/api/auth/login")) {
      let body = "";
      try {
        body = await res.text();
      } catch {
        body = "(unreadable)";
      }
      loginResponses.push({ status: res.status(), body: body.slice(0, 500) });
      console.log("[login response]", res.status(), body.slice(0, 300));
    }
  });

  page.on("requestfailed", (req) => {
    if (req.url().includes("/api/")) {
      console.log("[request failed]", req.url(), req.failure()?.errorText);
    }
  });

  await page.goto("/login");
  await expect(page.locator('input[name="username"]')).toBeVisible();

  // 健康檢查
  const health = await page.request.get("http://127.0.0.1:5173/api/health");
  console.log("[proxy health]", health.status(), await health.text());

  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');

  // 等待導向或錯誤訊息
  const errorBox = page.locator(".error-box");
  try {
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
    console.log("[result] login success → dashboard");
  } catch {
    const errText = (await errorBox.count()) > 0 ? await errorBox.innerText() : "(no error box)";
    console.log("[result] login failed UI error:", errText);
    console.log("[result] login API responses:", JSON.stringify(loginResponses));
    await page.screenshot({ path: "test-results/login-diag-fail.png", fullPage: true });
    throw new Error(`登入未進入 dashboard。UI=${errText} API=${JSON.stringify(loginResponses)}`);
  }

  await expect(page.locator(".dashboard-grid")).toBeVisible({ timeout: 20000 });
  await page.screenshot({ path: "test-results/login-diag-ok.png", fullPage: true });
});
