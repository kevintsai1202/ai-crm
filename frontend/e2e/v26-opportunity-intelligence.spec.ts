import { expect, test, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { newPhaseDataPrefix } from "./fixtures/phase-data";

interface Opportunity { id: number; stage: string; probability?: number; }

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

test("V26 商機智能：健康度、可解釋依據、重算趨勢，且不改動商機階段/機率", async ({ page }) => {
  const prefix = newPhaseDataPrefix("V26");
  const uniqueDigits = Date.now().toString().slice(-8);
  const headers = await loginAsAdmin(page);
  const user = await page.evaluate(() => JSON.parse(sessionStorage.getItem("ai-crm-user") ?? "{}") as { id: number });

  let customerId: number | null = null;
  try {
    const createCustomer = await page.request.post("/api/customers", { headers, data: {
      name: `${prefix}商機客戶`, email: `${prefix.toLowerCase()}@example.com`, phone: `09${uniqueDigits}`,
      taxId: uniqueDigits, industry: "軟體服務", ownerId: user.id,
      contractStartDate: null, contractEndDate: null, renewalDueDate: null,
    }});
    expect(createCustomer.ok(), await createCustomer.text()).toBeTruthy();
    customerId = (await createCustomer.json() as { id: number }).id;

    const createOpp = await page.request.post("/api/opportunities", { headers, data: {
      customerId, name: `${prefix}商機`, stage: "QUALIFICATION", amount: 500000,
      expectedCloseDate: null, type: "NEW_BUSINESS", ownerId: user.id,
    }});
    expect(createOpp.ok(), await createOpp.text()).toBeTruthy();
    const opp = await createOpp.json() as Opportunity;
    const stageBefore = opp.stage;

    await page.goto(`/opportunities/${opp.id}/intelligence?customerId=${customerId}`);
    await expect(page.getByRole("heading", { name: "商機智能" })).toBeVisible();

    // 總分、分級、可解釋分項與下一最佳行動。
    await expect(page.getByTestId("oi-total")).toContainText(/\d/, { timeout: 15000 });
    await expect(page.getByTestId("oi-tier")).toBeVisible();
    await expect(page.getByTestId("oi-next-action")).toBeVisible();
    // 至少一個分項含可解釋依據（引用聯絡人數的決策鏈分項一定存在）。
    const stageComponent = page.getByTestId("oi-component-DECISION_CHAIN");
    await expect(stageComponent).toBeVisible();
    await expect(stageComponent).toContainText("決策鏈");

    // 重算後保留歷史，趨勢出現 2 點（以 → 串接）。
    await page.getByTestId("oi-recalculate").click();
    await expect(page.locator("text=/\\d+ → \\d+/")).toBeVisible();

    // 重算不得改動商機階段/機率。
    const after = await page.request.get(`/api/customers/${customerId}`, { headers });
    const detail = await after.json() as { opportunities: Opportunity[] };
    const oppAfter = detail.opportunities.find((o) => o.id === opp.id)!;
    expect(oppAfter.stage).toBe(stageBefore);
    if (opp.probability != null) expect(oppAfter.probability).toBe(opp.probability);

    // 由建議產生跟進信。
    await page.getByTestId("oi-follow-up").click();
    await expect(page.getByRole("heading", { name: "AI 跟進信" })).toBeVisible();
  } finally {
    if (customerId != null) {
      // opportunity_health_snapshots 對商機有 FK，刪客戶前先清。
      execFileSync("docker", ["exec", "ai-crm-postgres", "psql", "-U", "aicrm", "-d", "aicrm", "-c",
        `delete from opportunity_health_snapshots where opportunity_id in (select id from opportunities where customer_id=${customerId}); delete from follow_up_drafts where customer_id=${customerId};`]);
      await page.request.delete(`/api/customers/${customerId}`, { headers });
    }
  }
});
