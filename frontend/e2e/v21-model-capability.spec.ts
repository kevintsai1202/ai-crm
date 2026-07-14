import { expect, test, type Page } from "@playwright/test";
import { newPhaseDataPrefix } from "./fixtures/phase-data";
import type { AiSettingsResponse } from "../src/types";

/** 以 seed ADMIN 登入真實前後端。 */
async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto("/login");
  await page.fill('input[name="username"]', "admin@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
}

/** 從 Admin UI 新增 UNKNOWN 模型，確保測試不直接偽造設定 API。 */
async function addUnknownModel(page: Page, model: string): Promise<void> {
  const providerSelect = page.locator('select').filter({ has: page.locator('option', { hasText: "選擇供應商" }) });
  await providerSelect.selectOption({ index: 1 });
  await page.getByPlaceholder("輸入模型名，如 claude-sonnet-4-6").fill(model);
  await page.getByRole("button", { name: "+ 新增" }).click();
  await expect(page.locator(`[data-model-name="${model}"]`)).toContainText("UNKNOWN");
}

/** 先清空用途 assignment，再以精確 prefix 移除測試模型；各步失敗仍繼續後續清理。 */
async function cleanupV21Models(page: Page, prefix: string): Promise<void> {
  const cleanupErrors: string[] = [];
  const token = await page.evaluate(() => localStorage.getItem("ai-crm-token")).catch(() => null);
  let settings: AiSettingsResponse | null = null;

  try {
    const response = await page.request.get("/api/admin/settings/ai", {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok()) throw new Error(`讀取設定 HTTP ${response.status()}`);
    settings = await response.json() as AiSettingsResponse;
  } catch (error) {
    cleanupErrors.push(error instanceof Error ? error.message : String(error));
  }

  try {
    if (!settings) throw new Error("缺少設定，無法安全保留 Chat assignment");
    const response = await page.request.put("/api/admin/settings/ai/assignments", {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        chatModel: settings.currentModel,
        chatProviderId: settings.currentProviderId,
        ocrModel: null,
        ocrProviderId: null,
        transcriptionModel: null,
        transcriptionProviderId: null,
      },
    });
    if (!response.ok()) throw new Error(`清空用途 assignment HTTP ${response.status()}`);
  } catch (error) {
    cleanupErrors.push(error instanceof Error ? error.message : String(error));
  }

  try {
    if (!settings) throw new Error("缺少設定，無法以 API 移除 prefix 模型");
    const currentIsTestModel = settings.currentModel.startsWith(prefix);
    const response = await page.request.put("/api/admin/settings/ai", {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        model: currentIsTestModel ? "" : settings.currentModel,
        providerId: currentIsTestModel ? null : settings.currentProviderId,
        modelOptions: settings.modelOptions.filter((option) => !option.model.startsWith(prefix)),
        temperature: settings.temperature,
        maxCompletionTokens: settings.maxCompletionTokens,
        reasoningEffort: settings.reasoningEffort,
      },
    });
    if (!response.ok()) throw new Error(`移除 prefix 模型 HTTP ${response.status()}`);
  } catch (error) {
    cleanupErrors.push(error instanceof Error ? error.message : String(error));
  }

  if (cleanupErrors.length > 0) {
    throw new Error(`V21 E2E cleanup 失敗：${cleanupErrors.join("；")}`);
  }
}

test("ADMIN 以真實 UI 治理 Vision／Transcription 能力與用途模型", async ({ page }) => {
  const prefix = newPhaseDataPrefix("V21");
  const visionModel = `${prefix}vision`;
  const audioModel = `${prefix}audio`;

  await loginAsAdmin(page);
  await page.goto("/admin/settings");
  await expect(page.getByRole("heading", { name: "系統設定" })).toBeVisible();

  try {
    await addUnknownModel(page, visionModel);
    await addUnknownModel(page, audioModel);
    await page.getByRole("button", { name: "儲存參數" }).click();
    await expect(page.getByText("已儲存，AI 呼叫即時生效。")).toBeVisible();

    const visionRow = page.locator(`[data-model-name="${visionModel}"]`);
    await visionRow.getByRole("checkbox", { name: `${visionModel} Vision` }).click();
    await expect(visionRow.getByLabel("Vision capability")).toBeVisible();
    await expect(visionRow).toContainText("MANUAL");
    const visionProviderId = Number(await visionRow.getAttribute("data-provider-id"));
    const visionPairKey = JSON.stringify([visionModel, visionProviderId]);
    await expect(page.getByTestId("ocr-model-select").locator("option", { hasText: visionModel })).toHaveCount(1);
    await page.getByTestId("ocr-model-select").selectOption(visionPairKey);
    await page.getByTestId("save-model-assignments").click();
    await expect(page.getByText("OCR 與語音轉錄模型已儲存。")).toBeVisible();

    await page.reload();
    await expect(page.getByTestId("ocr-model-select")).toHaveValue(visionPairKey);

    await visionRow.getByRole("checkbox", { name: `${visionModel} Vision` }).click();
    await expect(visionRow.getByLabel("Vision capability")).toHaveCount(0);
    await expect(page.getByTestId("ocr-model-select").locator("option", { hasText: visionModel })).toHaveCount(0);
    await expect(page.getByTestId("save-model-assignments")).toBeDisabled();

    const token = await page.evaluate(() => localStorage.getItem("ai-crm-token"));
    const backendVisionResponse = await page.request.put("/api/admin/settings/ai/assignments", {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        chatModel: "", chatProviderId: null,
        ocrModel: visionModel, ocrProviderId: Number(await visionRow.getAttribute("data-provider-id")),
        transcriptionModel: null, transcriptionProviderId: null,
      },
    });
    expect(backendVisionResponse.status()).toBe(400);
    await page.getByTestId("ocr-model-select").selectOption("");

    const audioRow = page.locator(`[data-model-name="${audioModel}"]`);
    await audioRow.getByRole("checkbox", { name: `${audioModel} Audio transcription` }).click();
    await expect(audioRow.getByLabel("Audio transcription capability")).toBeVisible();
    const audioProviderId = Number(await audioRow.getAttribute("data-provider-id"));
    await expect(page.getByTestId("transcription-model-select").locator("option", { hasText: audioModel })).toHaveCount(1);
    await page.getByTestId("transcription-model-select").selectOption(JSON.stringify([audioModel, audioProviderId]));
    await page.getByTestId("save-model-assignments").click();

    await audioRow.getByRole("checkbox", { name: `${audioModel} Audio transcription` }).click();
    await expect(audioRow.getByLabel("Audio transcription capability")).toHaveCount(0);
    await expect(page.getByTestId("transcription-model-select").locator("option", { hasText: audioModel })).toHaveCount(0);
    await expect(page.getByTestId("save-model-assignments")).toBeDisabled();
    const backendAudioResponse = await page.request.put("/api/admin/settings/ai/assignments", {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        chatModel: "", chatProviderId: null,
        ocrModel: null, ocrProviderId: null,
        transcriptionModel: audioModel,
        transcriptionProviderId: Number(await audioRow.getAttribute("data-provider-id")),
      },
    });
    expect(backendAudioResponse.status()).toBe(400);
    await page.getByTestId("transcription-model-select").selectOption("");
    await page.getByTestId("save-model-assignments").click();
  } finally {
    await cleanupV21Models(page, prefix);
  }
});
