# AI 模型系統設定 — 設計文件

**日期：** 2026-06-22
**狀態：** 已確認，待實作

## 目標

在系統設定中新增「AI 對話模型 (model)」設定，AI 呼叫時**優先使用系統設定的 model，未設定時才回退讀環境變數**（`OPENAI_CHAT_MODEL` / `application.yml` 預設）。主要用途：ADMIN 可快速切換模型，測試不同模型的價格與速度。

## 範圍界定（YAGNI）

- **只設定 `model`**。`base-url` 與 `api-key` 維持固定，繼續由環境變數提供，不在本次範圍。
- model 由**下拉選單**選擇；選單候選清單由 ADMIN 自訂（可新增/刪除模型名）。
- 即時生效（免重啟）。
- 限 ADMIN 操作，前端新增 `/admin/settings` 頁。

## 關鍵技術決策

因為只改 model，**不需**拆除 Spring AI 自動配置的 `ChatModel` bean，也不需自建 `OpenAiApi`。bean 仍承載 base-url 與 api-key；只在每次呼叫時用 `OpenAiChatOptions.builder().model(x).build()` 覆蓋 request 層的模型。

回退邏輯由單一解析方法收斂：`SystemSettingService.getAiChatModel()` 回傳設定值或空。呼叫端在值為空時**不覆蓋 options**，自動沿用 bean 的環境變數預設值。fallback 不散落各處。

### 替代方案與不採用理由

- **自建 OpenAiApi / 動態 ChatModel factory**：可同時動態切 base-url/api-key，但本次不需要，過度設計。
- **需重啟生效**：實作最簡，但體驗差，不符「即時生效」需求。

## 資料模型（Flyway V16）

新增通用 key-value 設定表 `system_settings`（全域單列，非 per-user）：

```sql
create table system_settings (
    setting_key   varchar(64) primary key,
    setting_value text not null,
    updated_at    timestamp with time zone not null,
    updated_by    varchar(64)
);
```

種子兩個 key：
- `ai.chat.model` → 目前選用模型名；空字串代表「用環境變數預設」。初始種子：空字串。
- `ai.chat.model_options` → 可選模型清單 JSON 陣列字串。初始種子：`["gemini-3.1-flash-lite-preview","gpt-4o-mini"]`。

## 後端元件

- `SystemSetting`（domain entity）+ `SystemSettingRepository`（Spring Data JPA）
- `SystemSettingService`：
  - `getAiChatModel()`：回 `Optional<String>`（空字串視為 empty）
  - `getModelOptions()`：回 `List<String>`（解析 JSON）
  - `getAiSettingsView()`：組裝 currentModel、modelOptions、envDefaultModel、來源標示
  - `updateAiSettings(model, options, username)`：upsert 兩個 key，更新 updated_at/updated_by
- `AdminSettingController`：
  - `GET /api/admin/settings/ai` → 回 currentModel、modelOptions、envDefaultModel、source（DB/ENV）
  - `PUT /api/admin/settings/ai` → 接收 model、modelOptions，驗證 model 在 options 內（或為空），儲存
  - 權限：沿用現有 `/api/admin/**` → `hasRole("ADMIN")`，無需改 SecurityConfig
- **AI 呼叫接線**：在以下三處 `ChatClient.create(chatModel).prompt()` 加 `.options(...)`，model 取自 `SystemSettingService.getAiChatModel()`，空則不覆蓋：
  - `InsightService`（同步 `callLlm` + 串流）
  - `ManagerInsightService`
  - `SentimentIntentService`
- 效能：每次 AI 呼叫前讀一次 DB（單列、PK 查詢），相對 LLM 延遲可忽略，確保即時生效。

## 前端

- 新增路由 `/admin/settings`（ADMIN 守衛，沿用現有 role 機制）。
- 頁面 `AdminSettingsPage`：
  - 下拉選 currentModel（選項 = modelOptions）
  - 模型清單編輯區：新增 / 刪除模型名
  - 唯讀顯示 envDefaultModel 與目前來源（DB/ENV），讓 ADMIN 知道留空會用哪個
  - 儲存按鈕
- `api.ts` 新增 `fetchAiSettings()` / `saveAiSettings(model, modelOptions)` 與對應型別。
- 導覽：在 ADMIN 可見處加入「系統設定」入口（與帳號管理並列）。

## 錯誤處理

- PUT 時 model 不在 options 內且非空 → 400 ProblemDetail。
- model_options JSON 解析失敗 → 視為空清單並記 log，不中斷。
- DB 無設定列（理論上種子已建）→ getAiChatModel 回 empty，走環境變數。

## 測試

後端：
- `SystemSettingService`：DB 有值回該值；空字串回 empty（觸發回退）；options JSON 解析。
- `AdminSettingController`：非 ADMIN 403；ADMIN PUT 後 GET 一致；model 不在 options 回 400。
- 既有 AI fallback 測試（api-key 為空走 deterministic）不受影響。

前端：
- 既有 e2e 煙霧測試不破壞；視情況補 /admin/settings 基本載入與儲存測試。

## 不在本次範圍

- base-url / api-key 的 DB 設定
- temperature 等進階參數
- 多模型路由 / 多租戶
- per-user 模型偏好
