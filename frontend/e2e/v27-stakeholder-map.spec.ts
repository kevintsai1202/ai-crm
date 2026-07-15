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

test("V27 決策鏈：AI 建議待確認、確認成為事實、拒絕不顯示", async ({ page }) => {
  const prefix = newPhaseDataPrefix("V27");
  const uniqueDigits = Date.now().toString().slice(-8);
  const headers = await loginAsAdmin(page);
  const user = await page.evaluate(() => JSON.parse(sessionStorage.getItem("ai-crm-user") ?? "{}") as { id: number });

  let customerId: number | null = null;
  try {
    const createCustomer = await page.request.post("/api/customers", { headers, data: {
      name: `${prefix}決策鏈客戶`, email: `${prefix.toLowerCase()}@example.com`, phone: `09${uniqueDigits}`,
      taxId: uniqueDigits, industry: "軟體服務", ownerId: user.id,
      contractStartDate: null, contractEndDate: null, renewalDueDate: null,
    }});
    expect(createCustomer.ok(), await createCustomer.text()).toBeTruthy();
    customerId = (await createCustomer.json() as { id: number }).id;

    // 兩位聯絡人（不同職稱），供 deterministic 建議產生角色與關係。
    for (const [name, title] of [["陳採購經理", "採購經理"], ["林工程師", "工程師"]]) {
      const c = await page.request.post(`/api/customers/${customerId}/contacts`, { headers, data: {
        name: `${prefix}${name}`, title, email: `${prefix.toLowerCase()}.${title}@example.com`,
      }});
      expect(c.ok(), await c.text()).toBeTruthy();
    }

    await page.goto(`/customers/${customerId}/stakeholder-map`);
    await expect(page.getByRole("heading", { name: "決策鏈" })).toBeVisible();

    // 初始無已確認角色。
    await expect(page.locator('[data-testid^="sm-role-"]')).toHaveCount(0);

    // 產生 AI 建議 → 出現待確認建議（且不進已確認圖）。
    await page.getByTestId("sm-suggest").click();
    await expect(page.locator('[data-testid^="sm-suggestion-"]').first()).toBeVisible();
    const suggestionCount = await page.locator('[data-testid^="sm-suggestion-"]').count();
    expect(suggestionCount).toBeGreaterThan(0);
    await expect(page.locator('[data-testid^="sm-role-"]')).toHaveCount(0); // 建議尚未成為事實

    // 確認第一則角色建議 → 成為已確認事實。
    const firstRoleConfirm = page.locator('[data-testid^="sm-confirm-role-"]').first();
    await firstRoleConfirm.click();
    await expect(page.locator('[data-testid^="sm-role-"]').first()).toBeVisible();

    // 拒絕第一則關係建議 → 從待確認消失且不成為事實。
    const relReject = page.locator('[data-testid^="sm-reject-relation-"]').first();
    if (await relReject.count()) {
      const rejectedId = await relReject.getAttribute("data-testid");
      const suggestionId = rejectedId!.replace("sm-reject-", "");
      await relReject.click();
      await expect(page.getByTestId(`sm-suggestion-${suggestionId}`)).toHaveCount(0);
    }

    // 重新載入後已確認角色仍在（事實持久）。
    await page.reload();
    await expect(page.locator('[data-testid^="sm-role-"]').first()).toBeVisible();
  } finally {
    if (customerId != null) {
      // stakeholder_roles/relations 對 contacts 有 FK，刪客戶前先清。
      execFileSync("docker", ["exec", "ai-crm-postgres", "psql", "-U", "aicrm", "-d", "aicrm", "-c",
        `delete from stakeholder_relations where from_contact_id in (select id from contacts where customer_id=${customerId}); delete from stakeholder_roles where contact_id in (select id from contacts where customer_id=${customerId});`]);
      await page.request.delete(`/api/customers/${customerId}`, { headers });
    }
  }
});
