import { expect, test, type Page, type APIRequestContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { newPhaseDataPrefix } from "./fixtures/phase-data";
import type { AiSettingsResponse, BusinessCardIntakeResponse } from "../src/types";

const FIXTURE_PNG = join(dirname(fileURLToPath(import.meta.url)), "fixtures", "business-card.png");
// FakeBusinessCardRecognitionClient 回傳的固定 email，用於觸發重複合併候選。
const FAKE_EMAIL = "card-fake@example.com";

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

/** 讀取目前 AI 設定。 */
async function readSettings(request: APIRequestContext, headers: Record<string, string>): Promise<AiSettingsResponse> {
  const response = await request.get("/api/admin/settings/ai", { headers });
  if (!response.ok()) throw new Error(`讀取 AI 設定失敗 HTTP ${response.status()}`);
  return response.json() as Promise<AiSettingsResponse>;
}

/** 建立具 VISION 能力的模型並設為 OCR 用途；回傳選定 providerId 與模型名。 */
async function setupVisionOcr(request: APIRequestContext, headers: Record<string, string>, prefix: string) {
  let settings = await readSettings(request, headers);
  // 環境無供應商時自建一個（fake Vision 會忽略其連線資訊）。
  if (settings.providers.length === 0) {
    const created = await request.post("/api/admin/settings/ai/providers", { headers, data: {
      name: `${prefix}provider`, baseUrl: "http://127.0.0.1:19000", apiKey: "fake-key",
    }});
    expect(created.ok(), await created.text()).toBeTruthy();
    settings = await readSettings(request, headers);
  }
  const providerId = settings.providers[0]?.id;
  if (providerId == null) throw new Error("環境缺少可用的 AI 供應商，無法設定 OCR");
  const visionModel = `${prefix}vision`;

  const putModel = await request.put("/api/admin/settings/ai", { headers, data: {
    model: settings.currentModel, providerId: settings.currentProviderId,
    modelOptions: [...settings.modelOptions, { model: visionModel, providerId, capabilities: [], capabilitySource: "UNKNOWN" }],
    temperature: settings.temperature, maxCompletionTokens: settings.maxCompletionTokens, reasoningEffort: settings.reasoningEffort,
  }});
  expect(putModel.ok(), await putModel.text()).toBeTruthy();

  const putCap = await request.put(`/api/admin/settings/ai/models/${encodeURIComponent(visionModel)}/capabilities`,
    { headers, data: { providerId, capabilities: ["VISION"] } });
  expect(putCap.ok(), await putCap.text()).toBeTruthy();

  const putAssign = await request.put("/api/admin/settings/ai/assignments", { headers, data: {
    chatModel: settings.currentModel, chatProviderId: settings.currentProviderId,
    ocrModel: visionModel, ocrProviderId: providerId, transcriptionModel: null, transcriptionProviderId: null,
  }});
  expect(putAssign.ok(), await putAssign.text()).toBeTruthy();
  return { providerId, visionModel };
}

/** 還原 OCR assignment 並移除本次前綴模型。 */
async function teardownVisionOcr(request: APIRequestContext, headers: Record<string, string>, prefix: string) {
  const settings = await readSettings(request, headers);
  await request.put("/api/admin/settings/ai/assignments", { headers, data: {
    chatModel: settings.currentModel, chatProviderId: settings.currentProviderId,
    ocrModel: null, ocrProviderId: null, transcriptionModel: null, transcriptionProviderId: null,
  }});
  await request.put("/api/admin/settings/ai", { headers, data: {
    model: settings.currentModel, providerId: settings.currentProviderId,
    modelOptions: settings.modelOptions.filter((o) => !o.model.startsWith(prefix)),
    temperature: settings.temperature, maxCompletionTokens: settings.maxCompletionTokens, reasoningEffort: settings.reasoningEffort,
  }});
  // 移除本次自建的前綴供應商，避免累積。
  for (const provider of settings.providers.filter((p) => p.name.startsWith(prefix))) {
    await request.delete(`/api/admin/settings/ai/providers/${provider.id}`, { headers });
  }
}

/** 刪除指定前綴建立的客戶。 */
async function cleanupCustomers(request: APIRequestContext, headers: Record<string, string>, prefix: string) {
  const response = await request.get("/api/customers", { headers, params: { keyword: prefix, page: 0, size: 100 } });
  if (!response.ok()) return;
  const pageData = await response.json() as { items: { id: number; name: string }[] };
  for (const customer of pageData.items) {
    if (customer.name.startsWith(prefix)) await request.delete(`/api/customers/${customer.id}`, { headers });
  }
}

/** 以 docker exec psql 查詢暫存媒體狀態，驗證確認後原圖已刪除。 */
function mediaStatus(mediaId: number): string {
  const out = execFileSync("docker", [
    "exec", "ai-crm-postgres", "psql", "-U", "aicrm", "-d", "aicrm", "-tA",
    "-c", `select status from temporary_media where id=${mediaId};`,
  ], { encoding: "utf-8" });
  return out.trim();
}

/** 上傳 fixture 名片並回傳建立回應（含 intake 與 mediaId）。 */
async function uploadCard(page: Page): Promise<BusinessCardIntakeResponse> {
  await page.goto("/business-cards/new");
  await expect(page.getByRole("heading", { name: "名片建檔精靈" })).toBeVisible();
  const createResponse = page.waitForResponse((r) => r.url().includes("/api/business-card-intakes") && r.request().method() === "POST");
  await page.locator('input[name="businessCardFile"]').setInputFiles(FIXTURE_PNG);
  await page.getByRole("button", { name: "開始辨識" }).click();
  const created = await (await createResponse).json() as BusinessCardIntakeResponse;
  await expect(page.getByTestId("bc-review-step")).toBeVisible({ timeout: 15000 });
  return created;
}

test.describe.serial("V23 名片建檔精靈", () => {
  test("上傳名片辨識後校正並建立全新客戶，確認後原圖刪除", async ({ page }) => {
    const prefix = newPhaseDataPrefix("V23");
    const headers = await loginAsAdmin(page);
    await setupVisionOcr(page.request, headers, prefix);

    try {
      const created = await uploadCard(page);

      // 低信心欄位（title=0.42）應顯示校正提示。
      await expect(page.getByTestId("bc-lowconf-contactTitle")).toBeVisible();

      // 以前綴覆寫客戶識別欄位，確保測試資料可被清理。
      await page.locator('input[name="bc-customerName"]').fill(`${prefix}公司`);
      await page.locator('input[name="bc-customerEmail"]').fill(`${prefix.toLowerCase()}@example.com`);
      await page.locator('input[name="bc-customerPhone"]').fill(`09${Date.now().toString().slice(-8)}`);
      await page.locator('input[name="bc-taxId"]').fill(Date.now().toString().slice(-8));
      await page.locator('input[name="bc-industry"]').fill("軟體服務");
      await page.locator('input[name="bc-contactEmail"]').fill(`${prefix.toLowerCase()}c@example.com`);

      // 若因固定 email 殘留而出現重複候選，明確選擇仍建立新客戶。
      const createRadio = page.locator('input[name="duplicateStrategy"][value="CREATE"]');
      if (await createRadio.count()) await createRadio.check();

      await page.getByTestId("bc-review-next").click();
      await expect(page.getByTestId("bc-confirm-step")).toBeVisible();
      await page.locator('input[name="bc-opportunityName"]').fill(`${prefix}商機`);
      await page.locator('input[name="bc-callAt"]').fill("2030-08-01T10:00");
      await page.getByTestId("bc-confirm-submit").click();

      await expect(page.getByTestId("bc-summary")).toBeVisible();
      for (const entity of ["customer", "contact", "opportunity", "task"]) {
        await expect(page.getByTestId(`bc-summary-${entity}-id`)).toContainText(/#\d+/);
      }

      // 確認後原圖應為 DELETED。
      expect(created.mediaId).not.toBeNull();
      await expect.poll(() => mediaStatus(created.mediaId!), { timeout: 10000 }).toBe("DELETED");
    } finally {
      await cleanupCustomers(page.request, headers, prefix);
      await teardownVisionOcr(page.request, headers, prefix);
    }
  });

  test("辨識命中既有客戶時可選擇合併，不建立重複客戶", async ({ page }) => {
    const prefix = newPhaseDataPrefix("V23");
    const headers = await loginAsAdmin(page);
    const user = await page.evaluate(() => JSON.parse(sessionStorage.getItem("ai-crm-user") ?? "{}") as { id: number });
    await setupVisionOcr(page.request, headers, prefix);

    let existingCustomerId: number | null = null;
    try {
      // 預建一筆 email 與 fake 辨識相同的既有客戶，用於觸發合併候選。
      const createCustomer = await page.request.post("/api/customers", { headers, data: {
        name: `${prefix}既有客戶`, email: FAKE_EMAIL, phone: `09${Date.now().toString().slice(-8)}`,
        taxId: Date.now().toString().slice(-8), industry: "軟體服務", ownerId: user.id,
        contractStartDate: null, contractEndDate: null, renewalDueDate: null,
      }});
      expect(createCustomer.ok(), await createCustomer.text()).toBeTruthy();
      existingCustomerId = (await createCustomer.json() as { id: number }).id;

      await uploadCard(page);
      await expect(page.getByTestId("bc-duplicates")).toBeVisible();
      await page.locator(`input[name="duplicateStrategy"][value="MERGE-${existingCustomerId}"]`).check();

      await page.locator('input[name="bc-taxId"]').fill(Date.now().toString().slice(-8));
      await page.locator('input[name="bc-industry"]').fill("軟體服務");
      await page.getByTestId("bc-review-next").click();

      await expect(page.getByTestId("bc-confirm-step")).toBeVisible();
      await page.locator('input[name="bc-opportunityName"]').fill(`${prefix}合併商機`);
      await page.locator('input[name="bc-callAt"]').fill("2030-08-02T10:00");
      const confirmResponse = page.waitForResponse((r) => r.url().includes("/confirm") && r.request().method() === "POST");
      await page.getByTestId("bc-confirm-submit").click();
      const confirmed = await (await confirmResponse).json() as { customerId: number };

      await expect(page.getByTestId("bc-summary")).toBeVisible();
      // 合併路徑應沿用既有客戶，不新建。
      expect(confirmed.customerId).toBe(existingCustomerId);
    } finally {
      await cleanupCustomers(page.request, headers, prefix);
      await teardownVisionOcr(page.request, headers, prefix);
    }
  });
});
