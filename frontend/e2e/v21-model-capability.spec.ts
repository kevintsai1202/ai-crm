import { expect, test, type Page } from "@playwright/test";
import { newPhaseDataPrefix } from "./fixtures/phase-data";

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
    await expect(page.getByTestId("ocr-model-select").locator(`option[value="${visionModel}"]`)).toHaveCount(1);
    await page.getByTestId("ocr-model-select").selectOption(visionModel);
    await page.getByTestId("save-model-assignments").click();
    await expect(page.getByText("OCR 與語音轉錄模型已儲存。")).toBeVisible();

    await visionRow.getByRole("checkbox", { name: `${visionModel} Vision` }).click();
    await expect(visionRow.getByLabel("Vision capability")).toHaveCount(0);
    await expect(page.getByTestId("ocr-model-select").locator(`option[value="${visionModel}"]`)).toHaveCount(0);
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
    await expect(page.getByTestId("transcription-model-select").locator(`option[value="${audioModel}"]`)).toHaveCount(1);
    await page.getByTestId("transcription-model-select").selectOption(audioModel);
    await page.getByTestId("save-model-assignments").click();

    await audioRow.getByRole("checkbox", { name: `${audioModel} Audio transcription` }).click();
    await expect(audioRow.getByLabel("Audio transcription capability")).toHaveCount(0);
    await expect(page.getByTestId("transcription-model-select").locator(`option[value="${audioModel}"]`)).toHaveCount(0);
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
    for (const model of [visionModel, audioModel]) {
      const row = page.locator(`[data-model-name="${model}"]`);
      if (await row.count()) await row.getByRole("button", { name: "刪除" }).click();
    }
    await page.getByRole("button", { name: "儲存參數" }).click();
  }
});
