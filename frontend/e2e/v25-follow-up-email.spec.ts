import { expect, test, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { newPhaseDataPrefix } from "./fixtures/phase-data";

/** 以 seed ADMIN 登入並回傳 Bearer headers。 */
async function loginAsAdmin(page: Page): Promise<Record<string, string>> {
  await page.goto("/login");
  await page.fill('input[name="username"]', "admin@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
  const token = await page.evaluate(() => sessionStorage.getItem("ai-crm-token"));
  if (!token) throw new Error("登入後未取得 E2E session token");
  return { Authorization: `Bearer ${token}` };
}

test("V25 AI 跟進信：草擬、存新版本、經 Zeabur Sendmail 寄送並顯示寄送資訊", async ({ page }) => {
  const prefix = newPhaseDataPrefix("V25");
  const uniqueDigits = Date.now().toString().slice(-8);
  const headers = await loginAsAdmin(page);
  const user = await page.evaluate(() => JSON.parse(sessionStorage.getItem("ai-crm-user") ?? "{}") as { id: number });

  let customerId: number | null = null;
  try {
    const createCustomer = await page.request.post("/api/customers", { headers, data: {
      name: `${prefix}跟進客戶`, email: `${prefix.toLowerCase()}@example.com`, phone: `09${uniqueDigits}`,
      taxId: uniqueDigits, industry: "軟體服務", ownerId: user.id,
      contractStartDate: null, contractEndDate: null, renewalDueDate: null,
    }});
    expect(createCustomer.ok(), await createCustomer.text()).toBeTruthy();
    customerId = (await createCustomer.json() as { id: number }).id;

    await page.goto(`/customers/${customerId}/follow-up`);
    await expect(page.getByRole("heading", { name: "AI 跟進信" })).toBeVisible();

    // 草稿產生後顯示引用依據與可編輯內容。
    await expect(page.getByTestId("fu-grounding")).toBeVisible({ timeout: 15000 });
    await expect(page.locator('input[name="fu-subject"]')).not.toHaveValue("");
    await expect(page.locator('textarea[name="fu-body"]')).not.toHaveValue("");

    // 人工修改主旨後，寄送需先存為新版本。
    await page.locator('input[name="fu-subject"]').fill(`${prefix}重要跟進`);
    await expect(page.getByTestId("fu-send")).toBeDisabled();
    await page.getByTestId("fu-save-version").click();
    await expect(page.getByTestId("fu-grounding")).toContainText("v2");
    await expect(page.getByTestId("fu-send")).toBeEnabled();

    // 核准並寄送。
    await page.getByTestId("fu-send").click();
    await expect(page.getByTestId("fu-result")).toBeVisible();
    await expect(page.getByTestId("fu-status")).toHaveText("已寄出");
    // Reply-To 為負責業務（admin）Email；收件者為客戶 Email。
    await expect(page.getByTestId("fu-replyto")).toHaveText("admin@aurora.local");
    await expect(page.getByTestId("fu-to")).toHaveText(`${prefix.toLowerCase()}@example.com`);
    await expect(page.getByTestId("fu-from")).not.toBeEmpty();
  } finally {
    // follow_up_drafts / outbound_emails 對 customer/draft 有 NOT NULL FK，刪客戶前先清。
    if (customerId != null) {
      execFileSync("docker", ["exec", "ai-crm-postgres", "psql", "-U", "aicrm", "-d", "aicrm", "-c",
        `delete from outbound_emails where draft_id in (select id from follow_up_drafts where customer_id=${customerId}); delete from follow_up_drafts where customer_id=${customerId};`]);
      await page.request.delete(`/api/customers/${customerId}`, { headers });
    }
  }
});
