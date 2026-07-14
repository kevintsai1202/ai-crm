import { expect, test, type Page } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { newPhaseDataPrefix } from "./fixtures/phase-data";

interface LoginUser { id: number; }
interface CustomerResponse { id: number; }

/** 以 seed ADMIN 登入真實前後端並回傳登入者資料。 */
async function loginAsAdmin(page: Page): Promise<LoginUser> {
  await page.goto("/login");
  await page.fill('input[name="username"]', "admin@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
  return page.evaluate(() => JSON.parse(sessionStorage.getItem("ai-crm-user") ?? "{}") as LoginUser);
}

/** 取得前端 session token，供真實 fixture API 與 finally cleanup 使用。 */
async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem("ai-crm-token"));
  if (!token) throw new Error("登入後未取得 E2E session token");
  return { Authorization: `Bearer ${token}` };
}

test("V22 建立、延期、下載 ICS 並完成電話任務", async ({ page }) => {
  const prefix = newPhaseDataPrefix("V22");
  const uniqueDigits = Date.now().toString().slice(-8);
  let customer: CustomerResponse | null = null;
  let taskId: string | null = null;
  const user = await loginAsAdmin(page);
  const headers = await authHeaders(page);

  try {
    const createCustomer = await page.request.post("/api/customers", { headers, data: {
      name: `${prefix}calendar-customer`, email: `${prefix.toLowerCase()}@example.com`,
      phone: `09${uniqueDigits}`, taxId: uniqueDigits, industry: "軟體服務", ownerId: user.id,
      contractStartDate: null, contractEndDate: null, renewalDueDate: null,
    }});
    expect(createCustomer.ok(), await createCustomer.text()).toBeTruthy();
    customer = await createCustomer.json() as CustomerResponse;

    await page.goto(`/customers/${customer.id}`);
    await expect(page.getByRole("heading", { name: "客戶工作台" })).toBeVisible();
    await page.getByRole("button", { name: "☎ 安排電話" }).click();
    await page.locator('input[name="taskTitle"]').fill(`${prefix}電話追蹤`);
    await page.locator('input[name="taskStart"]').fill("2030-07-16T14:00");
    await page.locator('input[name="taskEnd"]').fill("2030-07-16T14:30");
    await page.getByRole("button", { name: "建立電話任務" }).click();

    const row = page.locator("[data-task-id]", { hasText: `${prefix}電話追蹤` });
    await expect(row).toBeVisible();
    taskId = await row.getAttribute("data-task-id");
    expect(taskId).toMatch(/^\d+$/);

    await row.getByRole("button", { name: "延期一天" }).click();
    await expect(row).toContainText("2030/7/17");

    const downloadPromise = page.waitForEvent("download");
    await row.getByRole("button", { name: "下載行事曆" }).click();
    const download = await downloadPromise;
    const downloadPath = await download.path();
    expect(downloadPath).not.toBeNull();
    const bytes = await readFile(downloadPath!);
    const calendar = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    expect(calendar).toContain("BEGIN:VCALENDAR\r\n");
    expect(calendar).toContain(`UID:crm-task-${taskId}@ai-crm\r\n`);
    expect(calendar).toContain("DTSTART;TZID=Asia/Taipei:20300717T140000\r\n");
    expect(calendar).toContain("DTEND;TZID=Asia/Taipei:20300717T143000\r\n");
    expect(calendar.endsWith("END:VCALENDAR\r\n")).toBeTruthy();

    await row.getByRole("button", { name: "完成" }).click();
    await expect(row).toHaveCount(0);
  } finally {
    const cleanupErrors: string[] = [];
    if (taskId) {
      const taskResponse = await page.request.get(`/api/tasks/${taskId}`, { headers });
      if (taskResponse.ok()) {
        const task = await taskResponse.json() as { version: number };
        const deleteTask = await page.request.delete(`/api/tasks/${taskId}`, { headers, params: { version: task.version } });
        if (!deleteTask.ok()) cleanupErrors.push(`task ${taskId}: HTTP ${deleteTask.status()}`);
      } else if (taskResponse.status() !== 404) {
        cleanupErrors.push(`read task ${taskId}: HTTP ${taskResponse.status()}`);
      }
    }
    if (customer) {
      const cleanup = await page.request.delete(`/api/customers/${customer.id}`, { headers });
      if (!cleanup.ok()) cleanupErrors.push(`customer ${customer.id}: HTTP ${cleanup.status()}`);
    }
    if (cleanupErrors.length > 0) throw new Error(`V22 cleanup 失敗：${cleanupErrors.join("；")}`);
  }
});
