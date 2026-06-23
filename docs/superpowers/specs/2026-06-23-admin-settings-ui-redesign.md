# Admin Settings UI 改版設計規格

**日期**：2026-06-23
**範圍**：`frontend/src/features/admin/AdminSettingsPage.tsx` + 後端新增 3 個 endpoint

---

## 需求對應

| # | 需求 | 摘要 |
|---|------|------|
| 1 | 默認模型點選即儲存 | 移除「儲存設定」按鈕，selectModel() 內部直接儲存 |
| 2 | Checkbox 勾選競速參與模型 | checkbox ≠ 默認模型選取，兩功能分離 |
| 3 | 評分歷程按鈕常駐 | 評分歷程 + ZIP 按鈕常駐；ZIP 下載當前 session 的模型結果 |
| 4 | 個別模型歷程 | MODEL_TEST 類型儲存到 ai_call_log；card 加歷程按鈕 |

---

## 1. 默認模型點選即儲存

### 前端

- `selectModel(m, pid)` 改為 `async` function，選取後立即呼叫 `saveAiSettings(m, pid, options)`；再次點擊同一模型取消選取後也儲存（model=""）。
- 儲存期間 `saving=true`，模型清單項目加 `pointer-events: none`。
- 成功後 `actionMsg` 顯示「✓ 已設定 {model} 為默認模型」，3 秒後以 `setTimeout` 清除。
- **移除** lines 560-568 的「儲存設定」`<button>`。

### 後端

無變更。

---

## 2. Checkbox 勾選競速參與模型

### 前端 state

```ts
// 初始值從 options 全勾；options 增減時同步
const [raceModels, setRaceModels] = useState<Set<string>>(
  () => new Set(options.map(o => o.model))
);
```

- 新增模型（addModel）時：`setRaceModels(prev => new Set([...prev, m]))`
- 刪除模型（removeModel）時：從 raceModels 移除

### UI 佈局（模型清單每行）

```
[☑]  gpt-5.4-mini   [使用中]  [OpenAI]  [刪除]
```

- 左側 `<input type="checkbox">` 控制 raceModels，stopPropagation
- 整行 div onClick 仍保留，點擊觸發 selectModel（默認模型切換）
- `使用中` badge 顯示條件不變（currentModel === opt.model）

### startRace() 變更

```ts
// 只跑 raceModels 勾選的選項
const activeOptions = options.filter(o => raceModels.has(o.model));
```

- 競速結果 grid：`activeOptions.map(...)` 取代 `options.map(...)`

### 後端

無變更。

---

## 3. 評分歷程按鈕常駐顯示

### 佈局調整

**原本**：整個按鈕區塊在 `{allDone && hasDoneResults && (...)}` 內。

**新設計**：

```
/* 測試區下方固定按鈕列（永遠顯示，options.length > 0 即可） */
[📊 評分歷程]  [📦 下載全部 ZIP（hasDoneResults 才啟用）]  [🏆 Claude 評分（allDone && hasDoneResults 才啟用）]
```

- 「📊 評分歷程」：永遠顯示，點擊開啟 AiCallHistoryModal（現有行為不變）
- 「📦 下載全部 ZIP」：永遠顯示；`hasDoneResults` 時才可點擊，下載**當前 session** 的所有模型結果 + 評分報告（ZIP 邏輯與現有相同，只是從條件內移出）
- 「🏆 Claude 評分」：永遠顯示；條件 `allDone && hasDoneResults` 才 **非 disabled**

---

## 4. 個別模型歷程 + ZIP 下載

### 後端新增

#### AiCallType.java

新增 enum 值：
```java
/** 多模型競速：單一模型的測試結果（儲存供歷程查詢）。 */
MODEL_TEST
```

> `AiCallType` 以 `EnumType.STRING` 儲存，新增 enum 值不需 Flyway migration（DB 僅存字串）。

#### Dtos.java 新增

```java
/** 儲存單一模型測試結果的請求 DTO。 */
record AiTestLogRequest(
    String model,
    Integer providerId,
    String sessionId,          // UUID，前端 race 開始時產生
    int promptTokens,
    int completionTokens,
    int totalTokens,
    String answer
) {}
```

> `AiScoreRequest` 新增 `String sessionId` 欄位（MODEL_EVAL 儲存時寫入 subject）。

#### AiCallLogRepository.java 新增

```java
/** 查指定類型 + 模型的歷程（新到舊）。 */
List<AiCallLog> findByCallTypeAndModelOrderByCreatedAtDesc(AiCallType type, String model);
```

（`findByCallTypeAndSubjectOrderByCreatedAtDesc` 已存在，可直接用於 sessionId 查詢）

#### AdminSettingController.java 新增端點

| Method | Path                                          | 說明                  |
|--------|-----------------------------------------------|-----------------------|
| `POST` | `/api/admin/settings/ai/test/log`             | 儲存 MODEL_TEST 記錄  |
| `GET`  | `/api/admin/settings/ai/test/calls?model=xxx` | 查該模型歷程          |

#### sessionId 流程（用於 MODEL_TEST 關聯，無 ZIP 端點需求）

```
race 開始 → 前端生成 sessionId（crypto.randomUUID()）
    每個模型完成 → POST /ai/test/log（含 sessionId, answer, tokens）
    MODEL_TEST 儲存時 subject = sessionId（供未來擴展，MVP 不查詢 sessionId）
ZIP 下載 → 純前端，組裝當前 raceResults + scoreReport（現有邏輯不變）
```

### 前端新增

#### api.ts

```ts
export function logModelTest(req: AiTestLogRequest): Promise<void>
export function fetchModelTestCalls(model: string): Promise<AiCallHistoryItem[]>
export function downloadModelScoreZip(callId: number): Promise<Blob>
```

#### AdminSettingsPage.tsx 模型 card 底部

status === "done" 時顯示：
```
[⬇ 下載 MD]  [🕘 歷程]
```

「歷程」按鈕 onClick：
1. 呼叫 `fetchModelTestCalls(opt.model)`
2. 開啟 `AiCallHistoryModal`（per-model 歷程）

#### AiCallHistoryModal ZIP 按鈕

傳入 `onDownloadZip` callback，每筆 MODEL_EVAL 歷史旁顯示「📦 ZIP」，觸發時呼叫 `downloadModelScoreZip(call.id)` 並以 `URL.createObjectURL` 觸發下載。

---

## 受影響檔案清單

### 後端
- `domain/AiCallType.java` — 新增 MODEL_TEST
- `api/Dtos.java` — 新增 AiTestLogRequest；AiScoreRequest 加 sessionId
- `repository/AiCallLogRepository.java` — 新增查詢 method
- `service/InsightService.java` — streamModelScore 接受 sessionId
- `api/AdminSettingController.java` — 新增 3 個 endpoint

### 前端
- `src/api.ts` — 新增 3 個 API 函式
- `src/types.ts` — 新增 AiTestLogRequest type（若需要）
- `src/features/admin/AdminSettingsPage.tsx` — 主要改動
- `src/components/common/AiCallHistoryModal.tsx` — 加 onDownloadZip prop

---

## 不在此規格範圍

- 競速歷程 sessionId 跨 race 的關聯分析（MVP 只存、查、下載）
- Checkbox 狀態持久化（localStorage）
- 模型測試個別歷程的分頁（初期全量回傳，資料量小可接受）
