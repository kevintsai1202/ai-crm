import { expect, test, type Page, type APIRequestContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { newPhaseDataPrefix } from "./fixtures/phase-data";
import type { AiSettingsResponse, MeetingCopilotSessionResponse } from "../src/types";

const FIXTURE_WAV = join(dirname(fileURLToPath(import.meta.url)), "fixtures", "meeting-audio.wav");

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

/** 建立具 AUDIO_TRANSCRIPTION 能力的模型並設為轉錄用途。 */
async function setupTranscription(request: APIRequestContext, headers: Record<string, string>, prefix: string) {
  let settings = await readSettings(request, headers);
  if (settings.providers.length === 0) {
    const created = await request.post("/api/admin/settings/ai/providers", { headers, data: {
      name: `${prefix}provider`, baseUrl: "http://127.0.0.1:19000", apiKey: "fake-key",
    }});
    expect(created.ok(), await created.text()).toBeTruthy();
    settings = await readSettings(request, headers);
  }
  const providerId = settings.providers[0]!.id;
  const audioModel = `${prefix}audio`;
  const putModel = await request.put("/api/admin/settings/ai", { headers, data: {
    model: settings.currentModel, providerId: settings.currentProviderId,
    modelOptions: [...settings.modelOptions, { model: audioModel, providerId, capabilities: [], capabilitySource: "UNKNOWN" }],
    temperature: settings.temperature, maxCompletionTokens: settings.maxCompletionTokens, reasoningEffort: settings.reasoningEffort,
  }});
  expect(putModel.ok(), await putModel.text()).toBeTruthy();
  const putCap = await request.put(`/api/admin/settings/ai/models/${encodeURIComponent(audioModel)}/capabilities`,
    { headers, data: { providerId, capabilities: ["AUDIO_TRANSCRIPTION"] } });
  expect(putCap.ok(), await putCap.text()).toBeTruthy();
  const putAssign = await request.put("/api/admin/settings/ai/assignments", { headers, data: {
    chatModel: settings.currentModel, chatProviderId: settings.currentProviderId,
    ocrModel: null, ocrProviderId: null, transcriptionModel: audioModel, transcriptionProviderId: providerId,
  }});
  expect(putAssign.ok(), await putAssign.text()).toBeTruthy();
  return { providerId, audioModel };
}

/** 還原轉錄 assignment 並移除前綴模型與供應商。 */
async function teardownTranscription(request: APIRequestContext, headers: Record<string, string>, prefix: string) {
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
  for (const provider of settings.providers.filter((p) => p.name.startsWith(prefix))) {
    await request.delete(`/api/admin/settings/ai/providers/${provider.id}`, { headers });
  }
}

/** 查詢暫存媒體狀態，驗證確認後音訊已刪除。 */
function mediaStatus(mediaId: number): string {
  return execFileSync("docker", [
    "exec", "ai-crm-postgres", "psql", "-U", "aicrm", "-d", "aicrm", "-tA",
    "-c", `select status from temporary_media where id=${mediaId};`,
  ], { encoding: "utf-8" }).trim();
}

test("V24 會議 Copilot：上傳轉錄、選擇性套用、原音訊刪除、逐字稿保留", async ({ page }) => {
  const prefix = newPhaseDataPrefix("V24");
  const uniqueDigits = Date.now().toString().slice(-8);
  const headers = await loginAsAdmin(page);
  const user = await page.evaluate(() => JSON.parse(sessionStorage.getItem("ai-crm-user") ?? "{}") as { id: number });
  await setupTranscription(page.request, headers, prefix);

  let customerId: number | null = null;
  try {
    const createCustomer = await page.request.post("/api/customers", { headers, data: {
      name: `${prefix}會議客戶`, email: `${prefix.toLowerCase()}@example.com`, phone: `09${uniqueDigits}`,
      taxId: uniqueDigits, industry: "軟體服務", ownerId: user.id,
      contractStartDate: null, contractEndDate: null, renewalDueDate: null,
    }});
    expect(createCustomer.ok(), await createCustomer.text()).toBeTruthy();
    customerId = (await createCustomer.json() as { id: number }).id;

    await page.goto(`/customers/${customerId}/meeting-copilot`);
    await expect(page.getByRole("heading", { name: "會議 Copilot" })).toBeVisible();

    const createResponse = page.waitForResponse((r) => r.url().includes("/api/meeting-copilot/sessions") && r.request().method() === "POST");
    await page.locator('input[name="meetingAudio"]').setInputFiles(FIXTURE_WAV);
    await page.getByTestId("mc-upload-submit").click();
    const created = await (await createResponse).json() as MeetingCopilotSessionResponse;
    const sessionId = created.id;

    // 轉錄完成後進入並排審核。
    await expect(page.getByTestId("mc-change-pane")).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId("mc-transcript")).toContainText("客戶表示對方案有高度興趣");
    await expect(page.getByTestId("mc-summary")).not.toBeEmpty();

    // 低信心 stakeholder 建議預設不勾選。
    await expect(page.getByTestId("mc-lowconf-stakeholder-1")).toBeVisible();
    await expect(page.locator('[data-testid="mc-change-stakeholder-1"] input[type="checkbox"]')).not.toBeChecked();
    // interaction 與 task 預設勾選。
    await expect(page.locator('[data-testid="mc-change-interaction-1"] input[type="checkbox"]')).toBeChecked();
    await expect(page.locator('[data-testid="mc-change-task-1"] input[type="checkbox"]')).toBeChecked();

    await expect(page.getByTestId("mc-confirm-submit")).toContainText("確認套用 2 項變更");
    await page.getByTestId("mc-confirm-submit").click();

    // 完成頁只套用選定項（互動＋任務），未選的 stakeholder 不套用。
    await expect(page.getByTestId("mc-summary-done")).toBeVisible();
    await expect(page.getByTestId("mc-done-interaction")).toBeVisible();
    await expect(page.getByTestId("mc-done-tasks")).toBeVisible();
    await expect(page.getByTestId("mc-done-stakeholder")).toHaveCount(0);

    // 原音訊確認後刪除。
    expect(created.mediaId).not.toBeNull();
    await expect.poll(() => mediaStatus(created.mediaId!), { timeout: 10000 }).toBe("DELETED");

    // 逐字稿在確認後保留為互動依據。
    const after = await page.request.get(`/api/meeting-copilot/sessions/${sessionId}`, { headers });
    expect(after.ok()).toBeTruthy();
    const afterBody = await after.json() as MeetingCopilotSessionResponse;
    expect(afterBody.status).toBe("CONFIRMED");
    expect(afterBody.transcript ?? "").toContain("客戶表示對方案有高度興趣");
  } finally {
    // 會議 session 對 customer 有 NOT NULL FK，刪客戶前先移除本客戶的 session 列。
    if (customerId != null) {
      execFileSync("docker", ["exec", "ai-crm-postgres", "psql", "-U", "aicrm", "-d", "aicrm",
        "-c", `delete from meeting_copilot_sessions where customer_id=${customerId};`]);
      await page.request.delete(`/api/customers/${customerId}`, { headers });
    }
    await teardownTranscription(page.request, headers, prefix);
  }
});
