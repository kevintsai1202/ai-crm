# Admin Settings UI 改版實作計劃

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 為管理設定頁實作 4 項競速測試調整：默認模型點選即儲存、Checkbox 競速勾選、評分歷程按鈕常駐、個別模型歷程查看。

**Architecture:** 後端新增 MODEL_TEST enum 值 + 2 個 endpoint，InsightService 的 streamModelScore 接收 sessionId；前端 AdminSettingsPage 分 4 個獨立 UI 需求逐步改動，每個需求獨立 commit。

**Tech Stack:** Java 21 / Spring Boot 4 / Spring Security 6（後端）、React 19 / TypeScript / Vite（前端）、pnpm

---

## 檔案異動對照

| 路徑 | 動作 | 說明 |
|------|------|------|
| `backend/.../domain/AiCallType.java` | 修改 | 新增 MODEL_TEST |
| `backend/.../api/Dtos.java` | 修改 | 新增 AiTestLogRequest；AiScoreRequest 加 sessionId |
| `backend/.../repository/AiCallLogRepository.java` | 修改 | 新增 findByCallTypeAndModelOrderByCreatedAtDesc |
| `backend/.../service/AiGovernanceService.java` | 修改 | 新增 recordModelTest(), historyByModel() |
| `backend/.../service/InsightService.java` | 修改 | streamModelScore 使用 request.sessionId() 存 subject |
| `backend/.../api/AdminSettingController.java` | 修改 | 新增 POST /ai/test/log, GET /ai/test/calls |
| `frontend/src/types.ts` | 修改 | 新增 AiTestLogRequest interface |
| `frontend/src/api.ts` | 修改 | 新增 logModelTest(), fetchModelTestCalls()；streamModelScore 帶 sessionId |
| `frontend/src/features/admin/AdminSettingsPage.tsx` | 修改 | 4 項 UI 變更 |

---

## Task 1：後端 — AiCallType + Dtos + Repository

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/domain/AiCallType.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Modify: `backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java`

- [ ] **Step 1: 新增 MODEL_TEST 到 AiCallType**

開啟 `backend/src/main/java/com/aicrm/crm/domain/AiCallType.java`，在 `MODEL_EVAL` 後加：

```java
/** 多模型競速：單一模型的測試結果（儲存供歷程查詢）。 */
MODEL_TEST
```

完整檔案：
```java
public enum AiCallType {
    CHAT,
    ASSESSMENT,
    PORTFOLIO,
    TEAM_ANALYSIS,
    OWNER_COACHING,
    /** 多模型競速測試評分（claude-opus-4-8 評審）。 */
    MODEL_EVAL,
    /** 多模型競速：單一模型的測試結果（儲存供歷程查詢）。 */
    MODEL_TEST
}
```

- [ ] **Step 2: 在 Dtos.java 末尾（`}` 前）新增 AiTestLogRequest 並修改 AiScoreRequest**

找到末尾現有內容：
```java
/** 多模型評分請求：包含所有模型的測試結果。 */
public record AiScoreRequest(List<ModelResultItem> results) {}
```

替換為：
```java
/** 多模型評分請求：包含所有模型的測試結果與競速 session 識別碼。 */
public record AiScoreRequest(List<ModelResultItem> results, String sessionId) {}

/** 儲存單一模型競速測試結果的請求 DTO（前端在每個模型測試完成後呼叫）。 */
public record AiTestLogRequest(
    String model,
    String sessionId,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    String answer
) {}
```

- [ ] **Step 3: 在 AiCallLogRepository 新增查詢 method**

在 `findByCallTypeAndSubjectOrderByCreatedAtDesc` 之後加：

```java
/**
 * 查指定類型 + 模型名稱的歷程（新到舊），供個別模型競速歷程查詢。
 *
 * @param type 呼叫類型（MODEL_TEST）
 * @param model 模型名稱
 * @return 歷次呼叫清單
 */
List<AiCallLog> findByCallTypeAndModelOrderByCreatedAtDesc(AiCallType type, String model);
```

- [ ] **Step 4: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/AiCallType.java
git add backend/src/main/java/com/aicrm/crm/api/Dtos.java
git add backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java
git commit -m "feat(backend): 新增 MODEL_TEST 類型、AiTestLogRequest DTO、repository 查詢 method"
```

---

## Task 2：後端 — AiGovernanceService 新增方法

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java`

- [ ] **Step 1: 在 historyByOwner() 之後新增兩個 method**

```java
/**
 * 記錄單一模型競速測試結果（MODEL_TEST），以 sessionId 作為 subject 關聯同批次的評分記錄。
 *
 * @param req 測試結果請求 DTO
 */
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordModelTest(Dtos.AiTestLogRequest req) {
    var entry = new AiCallLog(
        null, AiCallType.MODEL_TEST, req.sessionId(), req.model(),
        req.promptTokens(), req.completionTokens(), req.totalTokens(),
        true, false, req.answer());
    callLogRepository.save(entry);
}

/**
 * 查詢指定模型的 MODEL_TEST 歷程（新到舊）。
 *
 * @param model 模型名稱
 * @return 歷次測試紀錄 DTO 清單
 */
@Transactional(readOnly = true)
public java.util.List<Dtos.AiCallHistoryItem> historyByModel(String model) {
    return callLogRepository.findByCallTypeAndModelOrderByCreatedAtDesc(AiCallType.MODEL_TEST, model).stream()
        .map(c -> new Dtos.AiCallHistoryItem(c.getId(), c.getCallType().name(), c.getModel(),
            c.isAiEnabled(), c.getTotalTokens(), c.getAnswer(), c.getCreatedAt()))
        .toList();
}
```

- [ ] **Step 2: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java
git commit -m "feat(backend): AiGovernanceService 新增 recordModelTest/historyByModel"
```

---

## Task 3：後端 — AdminSettingController 新增 2 個 endpoint

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java`

- [ ] **Step 1: 在 scoreCalls() 之後新增兩個 endpoint**

```java
/**
 * 儲存單一模型競速測試結果（前端測試完成後非同步呼叫）。
 *
 * @param request 測試結果 DTO
 */
@PostMapping("/ai/test/log")
@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
public void logModelTest(@RequestBody Dtos.AiTestLogRequest request) {
    aiGovernance.recordModelTest(request);
}

/**
 * 取得指定模型的 MODEL_TEST 歷程（新到舊）。
 *
 * @param model 模型名稱（query param）
 * @return AI 呼叫歷史清單
 */
@GetMapping("/ai/test/calls")
public java.util.List<Dtos.AiCallHistoryItem> modelTestCalls(
        @org.springframework.web.bind.annotation.RequestParam String model) {
    return aiGovernance.historyByModel(model);
}
```

- [ ] **Step 2: 執行相關測試**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test "-Dspring.profiles.active=h2" "-Dtest=SecurityIntegrationTest,AuthIntegrationTest,AiFallbackIntegrationTest,InsightServiceFallbackTest" "-Dsurefire.failIfNoSpecifiedTests=false" -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java
git commit -m "feat(backend): 新增 POST /ai/test/log 與 GET /ai/test/calls endpoint"
```

---

## Task 4：後端 — InsightService streamModelScore 帶入 sessionId

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/InsightService.java`

- [ ] **Step 1: 修改 line 1037 的 record 呼叫，將 null subject 改為 sessionId**

找到：
```java
var saved = aiGovernance.record(AiCallType.MODEL_EVAL, null, null, usedModel, pt, ct, tt, true, true, answer);
```

改為：
```java
// subject 存 sessionId，供未來 ZIP 重組關聯同批次 MODEL_TEST 記錄
var saved = aiGovernance.record(AiCallType.MODEL_EVAL, null, request.sessionId(), usedModel, pt, ct, tt, true, true, answer);
```

> 注意：`request.sessionId()` 可能為 null（舊版前端未帶）；`record()` 方法接受 null subject，無需額外 null check。

- [ ] **Step 2: 執行全量測試**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test "-Dspring.profiles.active=h2" "-Dsurefire.failIfNoSpecifiedTests=false" -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/InsightService.java
git commit -m "feat(backend): streamModelScore 保存 sessionId 至 MODEL_EVAL subject 欄位"
```

---

## Task 5：前端 — types.ts + api.ts 新增函式

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`

- [ ] **Step 1: 在 types.ts 末尾（`ModelResultItem` 之後）新增**

```typescript
/** 儲存單一模型測試結果的請求（對應後端 Dtos.AiTestLogRequest）。 */
export interface AiTestLogRequest {
  model: string;
  sessionId: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  answer: string;
}
```

- [ ] **Step 2: 在 api.ts 修改 streamModelScore 加入 sessionId 參數**

找到：
```typescript
export async function streamModelScore(
  results: import("./types").ModelResultItem[],
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
```

改為：
```typescript
export async function streamModelScore(
  results: import("./types").ModelResultItem[],
  sessionId: string,
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
```

找到：
```typescript
      body: JSON.stringify({ results })
```

改為：
```typescript
      body: JSON.stringify({ results, sessionId })
```

- [ ] **Step 3: 在 api.ts 末尾（fetchModelScoreCalls 之後）新增兩個函式**

```typescript
/** 儲存單一模型競速測試結果到後端（MODEL_TEST 紀錄）。 */
export async function logModelTest(req: import("./types").AiTestLogRequest): Promise<void> {
  await apiClient.post("/admin/settings/ai/test/log", req);
}

/** 取得指定模型的 MODEL_TEST 歷程（新到舊）。 */
export async function fetchModelTestCalls(model: string): Promise<AiCallHistoryItem[]> {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/admin/settings/ai/test/calls", {
    params: { model }
  });
  return data;
}
```

- [ ] **Step 4: TypeScript 型別確認**

```powershell
Set-Location frontend
pnpm exec tsc --noEmit
```

期望：exit 0，無錯誤

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types.ts frontend/src/api.ts
git commit -m "feat(frontend): 新增 AiTestLogRequest type 與 logModelTest/fetchModelTestCalls API"
```

---

## Task 6：前端 — 需求 1：默認模型點選即儲存

**Files:**
- Modify: `frontend/src/features/admin/AdminSettingsPage.tsx`

- [ ] **Step 1: 將 selectModel() 改為 async 並直接呼叫 save()**

找到並替換（lines 98-111）：

```typescript
/**
 * 選取模型，同時更新對應的 provider ID，並立即儲存至後端。
 * 若再次點擊已選模型則取消選取（model 設為空字串）。
 */
async function selectModel(m: string, pid: number | null) {
  const newModel = currentModel === m ? "" : m;
  const newPid = currentModel === m ? null : pid;
  setCurrentModel(newModel);
  setCurrentProviderId(newPid);
  setActionMsg(null);
  // 立即儲存，不需使用者另按按鈕
  setSaving(true);
  setSettingError(null);
  try {
    const data = await saveAiSettings(newModel, newPid, options);
    setSettings(data);
    setCurrentModel(data.currentModel);
    setCurrentProviderId(data.currentProviderId);
    setOptions(data.modelOptions);
    setProviders(data.providers);
    const msg = data.currentModel
      ? `✓ 已設定 ${data.currentModel} 為默認模型`
      : "✓ 已清除默認模型，改用環境變數預設";
    setActionMsg(msg);
    setTimeout(() => setActionMsg(null), 3000);
  } catch (e) {
    setSettingError(e instanceof Error ? e.message : "儲存失敗");
  } finally {
    setSaving(false);
  }
}
```

- [ ] **Step 2: 移除「儲存設定」按鈕（lines 559-568）**

找到並刪除：
```typescript
            {/* 儲存 */}
            <button
              type="button"
              className="btn-primary"
              disabled={saving}
              onClick={save}
              style={{ width: "100%", padding: "11px", fontSize: 15, fontWeight: 700, borderRadius: 10 }}
            >
              {saving ? "儲存中…" : "儲存設定"}
            </button>
```

- [ ] **Step 3: 在模型清單項目上加 pointer-events: none 當 saving 時**

在模型清單 div 的 onClick 行（約 line 491）加保護：
```typescript
onClick={() => { if (!saving) selectModel(opt.model, opt.providerId ?? null); }}
style={{
  ...（原有 style）
  cursor: saving ? "not-allowed" : "pointer",
  opacity: saving ? 0.7 : 1,
}}
```

- [ ] **Step 4: TypeScript 型別確認**

```powershell
Set-Location frontend
pnpm exec tsc --noEmit
```

期望：exit 0

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/admin/AdminSettingsPage.tsx
git commit -m "feat(frontend): 需求1 - 默認模型點選即儲存，移除儲存設定按鈕"
```

---

## Task 7：前端 — 需求 2：Checkbox 控制競速參與

**Files:**
- Modify: `frontend/src/features/admin/AdminSettingsPage.tsx`

- [ ] **Step 1: 新增 raceModels state（放在現有 racing state 之後）**

在 `const [racing, setRacing] = useState(false);` 之後加：

```typescript
/** 勾選加入競速的模型名稱集合（初始為全部勾選）。 */
const [raceModels, setRaceModels] = useState<Set<string>>(
  () => new Set(options.map(o => o.model))
);
```

- [ ] **Step 2: 在 addModel() 同步加入 raceModels**

找到 `addModel()` 函式中 `setOptions(prev => [...prev, {...}])` 這行，在其後加：
```typescript
setRaceModels(prev => new Set([...prev, m]));
```

在 `removeModel()` 函式中 `setOptions(prev => prev.filter(...))` 之後加：
```typescript
setRaceModels(prev => { const s = new Set(prev); s.delete(m); return s; });
```

- [ ] **Step 3: 修改 startRace() 只跑勾選的模型**

找到：
```typescript
  if (options.length === 0 || racing) return;
```
在下方找 `options.forEach` 第一次出現（init 建立），將兩處 `options.forEach` 和 `options.length` 改用 `activeOptions`：

在 `startRace()` 頂部（`const init:` 之前）加：
```typescript
const activeOptions = options.filter(o => raceModels.has(o.model));
if (activeOptions.length === 0) return;
```

然後將函式內所有 `options.forEach(` 改為 `activeOptions.forEach(`，`const total = options.length` 改為 `const total = activeOptions.length`。

- [ ] **Step 4: 修改競速結果 grid 只顯示 activeOptions**

找到：
```typescript
                      {options.map((opt) => {
                        const r = raceResults[opt.model];
                        if (!r) return null;
```

改為（注意：此處 `options` 是完整清單，需根據 raceResults 有無鍵值來判斷）：
```typescript
                      {options.filter(o => raceResults[o.model] !== undefined).map((opt) => {
                        const r = raceResults[opt.model];
                        if (!r) return null;
```

- [ ] **Step 5: 修改模型清單 UI 加 checkbox**

找到模型清單每個項目的 render（約 line 484-532）。在 `<div style={{ display: "flex", alignItems: "center", gap: 10 }}>` 內的最前方加 checkbox：

```typescript
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      {/* checkbox 控制是否加入競速 */}
                      <input
                        type="checkbox"
                        checked={raceModels.has(opt.model)}
                        onChange={e => {
                          e.stopPropagation();
                          setRaceModels(prev => {
                            const s = new Set(prev);
                            e.target.checked ? s.add(opt.model) : s.delete(opt.model);
                            return s;
                          });
                        }}
                        onClick={e => e.stopPropagation()}
                        style={{ width: 16, height: 16, cursor: "pointer", flexShrink: 0 }}
                        title="勾選加入競速比較"
                      />
                      {/* 原有 radio 圓點改為只在 isSelected 時顯示 badge */}
```

並將原有的 radio 樣式 div（`width:16, height:16, borderRadius:"50%", border...`）整個移除，改用上方 checkbox。

`使用中` badge 保留，顯示條件不變（`isSelected`）。

- [ ] **Step 6: TypeScript 型別確認**

```powershell
Set-Location frontend
pnpm exec tsc --noEmit
```

期望：exit 0

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/admin/AdminSettingsPage.tsx
git commit -m "feat(frontend): 需求2 - Checkbox 控制競速參與，與默認模型選取分離"
```

---

## Task 8：前端 — 需求 3+4：評分歷程按鈕常駐 + 個別模型歷程

**Files:**
- Modify: `frontend/src/features/admin/AdminSettingsPage.tsx`

### Part A — 評分歷程按鈕常駐（需求 3）

- [ ] **Step 1: 新增 state 管理個別模型歷程 Modal**

在 `scoreCallsLoading` state 之後加：

```typescript
/** 個別模型歷程 Modal 狀態（null 代表關閉）。 */
const [modelHistoryState, setModelHistoryState] = useState<{
  open: boolean; model: string; calls: AiCallHistoryItem[]; loading: boolean;
} | null>(null);
```

在 import 區加（若尚未引入）：
```typescript
import { logModelTest, fetchModelTestCalls } from "../../api";
import type { AiCallHistoryItem } from "../../types";
```

- [ ] **Step 2: 修改 startRace() 在每個模型完成後 logModelTest 並帶入 sessionId**

在 `startRace()` 頂部（`const activeOptions = ...` 之後）加：

```typescript
/** 本批次競速的 sessionId，關聯 MODEL_TEST 與 MODEL_EVAL 記錄。 */
const sessionId = crypto.randomUUID();
```

在 `doneCount++; if (doneCount >= total) setRacing(false);` 前（onDone callback 裡）加：

```typescript
          // 非同步儲存模型測試結果（不阻斷 UI）
          const cur = Object.values(raceResults).find(() => true); // 從 closure 取
```

等一下，這裡有 closure 問題。改成：在 `onDone` callback 取得 `raceResults` 最新值有困難，因為 `setRaceResults` 是非同步的。需改用 ref 或在 onDone 時直接帶 content。

修改方式：在 `streamModelTest` 的 onChunk 處理中用 `contentRef` 累積：

在 `startRace()` 的每個 `opt.forEach` 迴圈，改為：

```typescript
activeOptions.forEach((opt) => {
  const t0 = performance.now();
  startTimeRef.current[opt.model] = t0;
  /** 累積此模型的串流內容，供測試完成後存入後端 */
  let accContent = "";
  let accPromptTokens = 0;
  let accCompletionTokens = 0;
  let accTotalTokens = 0;

  streamModelTest(
    "", opt.model, opt.providerId ?? null,
    (chunk: any) => {
      if (chunk.type === "content" && chunk.delta) {
        accContent += chunk.delta;
        // ...原有的 setRaceResults 邏輯不變...
      } else if (chunk.type === "tokens") {
        accPromptTokens = chunk.promptTokens ?? 0;
        accCompletionTokens = chunk.completionTokens ?? 0;
        accTotalTokens = chunk.totalTokens ?? 0;
        // ...原有 setRaceResults token 邏輯不變...
      }
    },
    () => {
      const totalMs = Math.round(performance.now() - (startTimeRef.current[opt.model] ?? 0));
      setRaceResults((prev) => ({
        ...prev,
        [opt.model]: { ...prev[opt.model], status: "done", totalMs }
      }));
      // 非同步儲存至後端（fire-and-forget）
      logModelTest({
        model: opt.model,
        sessionId,
        promptTokens: accPromptTokens,
        completionTokens: accCompletionTokens,
        totalTokens: accTotalTokens,
        answer: accContent,
      }).catch(err => console.warn("logModelTest 失敗：", err));
      doneCount++;
      if (doneCount >= total) setRacing(false);
    },
    // ...onError 不變...
  );
});
```

> 完整 startRace() 改寫：保留原有邏輯，只是 `options.forEach` 改 `activeOptions.forEach`，加入 `accContent` 累積變數，在 onDone 時呼叫 logModelTest。

- [ ] **Step 3: 修改 startScore() 傳入 sessionId**

需要把 sessionId 從 startRace 傳出去。用 state：

在 state 區加：
```typescript
/** 當前競速批次的 sessionId（startRace 時產生，startScore 時傳遞）。 */
const [currentSessionId, setCurrentSessionId] = useState<string>("");
```

在 `startRace()` 的 `const sessionId = crypto.randomUUID();` 之後加：
```typescript
setCurrentSessionId(sessionId);
```

在 `startScore()` 的 `streamModelScore(` 呼叫改為：
```typescript
streamModelScore(
  doneResults,
  currentSessionId,
  (chunk: any) => { ... },
  ...
)
```

- [ ] **Step 4: 將評分/ZIP 按鈕移出條件區塊（常駐顯示）**

找到（約 line 680-716）：
```typescript
                    {/* 評分按鈕（全部完成後顯示） */}
                    {allDone && hasDoneResults && (
                      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", ... }}>
                        <button type="button" className="btn-secondary" onClick={openScoreHistory}>🕘 評分歷程</button>
                        {/* ZIP 下載 ... */}
                        <button ... onClick={() => { ...downloadZip... }}>📦 下載全部 ZIP</button>
                        <button ... onClick={startScore}>🏆 claude-opus-4-8 評分</button>
                      </div>
                    )}
```

改為（移到 `{hasRaceResults && (...)}` 外，緊接在競速 grid 下方，conditions 改為 disabled）：

```typescript
              {/* 評分相關按鈕列（永遠顯示，條件控制 disabled） */}
              {options.length > 0 && (
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end",
                  paddingTop: 8, borderTop: "1px solid #f1f5f9", flexWrap: "wrap", marginTop: 8 }}>
                  <button type="button" className="btn-secondary" onClick={openScoreHistory}>📊 評分歷程</button>
                  <button
                    type="button"
                    className="btn-secondary"
                    disabled={!hasDoneResults}
                    onClick={() => {
                      const ts = new Date().toISOString().slice(0, 16).replace("T", "_").replace(":", "-");
                      const files: Record<string, string> = {};
                      Object.entries(raceResults).forEach(([model, r]) => {
                        if (r.content) {
                          const safeName = model.replace(/[/\\:*?"<>|]/g, "_");
                          files[`${safeName}.md`] = r.content;
                        }
                      });
                      if (scoreReport?.markdown) files["00_評分報告.md"] = scoreReport.markdown;
                      downloadZip(`競速測試_${ts}`, files);
                    }}
                  >
                    📦 下載全部 ZIP
                  </button>
                  <button
                    type="button"
                    className="btn-assess"
                    disabled={scoring || !allDone || !hasDoneResults}
                    onClick={startScore}
                    style={{ fontWeight: 700, padding: "9px 20px", borderRadius: 8 }}
                  >
                    {scoring ? "評分中…" : "🏆 claude-opus-4-8 評分"}
                  </button>
                </div>
              )}
```

### Part B — 個別模型歷程（需求 4）

- [ ] **Step 5: 新增 openModelHistory() 函式**

```typescript
/** 開啟指定模型的歷程 Modal。 */
async function openModelHistory(model: string) {
  setModelHistoryState({ open: true, model, calls: [], loading: true });
  try {
    const calls = await fetchModelTestCalls(model);
    setModelHistoryState(prev => prev ? { ...prev, calls, loading: false } : null);
  } catch {
    setModelHistoryState(prev => prev ? { ...prev, calls: [], loading: false } : null);
  }
}
```

- [ ] **Step 6: 在競速結果 card 底部加「歷程」按鈕**

找到（約 line 662-674）競速 card 底部的「⬇ 下載 MD」按鈕區：
```typescript
                            {r.status === "done" && r.content && (
                              <div style={{ padding: "6px 12px", borderTop: "1px solid #f1f5f9", textAlign: "right" }}>
                                <button ... onClick={() => downloadMarkdown(...)}>⬇ 下載 MD</button>
                              </div>
                            )}
```

改為加入歷程按鈕：
```typescript
                            {r.status === "done" && r.content && (
                              <div style={{ padding: "6px 12px", borderTop: "1px solid #f1f5f9",
                                display: "flex", justifyContent: "flex-end", gap: 6 }}>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ fontSize: 12, padding: "3px 10px" }}
                                  onClick={() => openModelHistory(opt.model)}
                                >
                                  🕘 歷程
                                </button>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ fontSize: 12, padding: "3px 10px" }}
                                  onClick={() => downloadMarkdown(`${opt.model}-回答`, r.content)}
                                >
                                  ⬇ 下載 MD
                                </button>
                              </div>
                            )}
```

- [ ] **Step 7: 在 JSX 末尾加個別模型歷程 Modal**

在現有 `評分歷程 Modal` 之後（約 line 744 之後）加：

```typescript
      {/* 個別模型歷程 Modal */}
      {modelHistoryState?.open && (
        <AiCallHistoryModal
          title={`${modelHistoryState.model} 測試歷程`}
          calls={modelHistoryState.calls}
          loading={modelHistoryState.loading}
          onClose={() => setModelHistoryState(null)}
        />
      )}
```

- [ ] **Step 8: TypeScript 型別確認**

```powershell
Set-Location frontend
pnpm exec tsc --noEmit
```

期望：exit 0

- [ ] **Step 9: Commit**

```bash
git add frontend/src/features/admin/AdminSettingsPage.tsx
git commit -m "feat(frontend): 需求3+4 - 評分歷程按鈕常駐、個別模型歷程 Modal"
```

---

## Task 9：整合推送與 Zeabur 部署確認

- [ ] **Step 1: 執行後端全量測試**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location "d:/GitHub/ai-crm"
mvn -pl backend test "-Dspring.profiles.active=h2" -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 2: 推送至 GitHub（觸發 Zeabur CI/CD）**

```bash
git push
```

- [ ] **Step 3: 確認 Zeabur build 成功**

```bash
npx zeabur@latest deployment log --service-id 6a36197b558aac447d435d2d -t build --project-id 6a361651558aac447d435cdc -i=false 2>&1 | grep -E "(DONE|ERROR|success|fail)" | head -5
```

期望：`DONE ✅ build completed`

---

## 自我審查

**Spec 覆蓋確認：**
- ✅ 需求 1（默認模型點選即儲存）→ Task 6
- ✅ 需求 2（Checkbox 競速）→ Task 7
- ✅ 需求 3（歷程按鈕常駐）→ Task 8 Part A
- ✅ 需求 4（個別模型歷程）→ Task 8 Part B + Task 1-4（後端）
- ✅ sessionId 流程 → Task 4（backend）+ Task 8 Step 2-3（frontend）
- ✅ fetchModelTestCalls + logModelTest API → Task 5

**型別一致性：**
- `AiTestLogRequest`：Task 1（後端 DTO） = Task 5（前端 interface）✅
- `streamModelScore(results, sessionId, ...)` signature → Task 5 定義，Task 8 Step 3 使用 ✅
- `currentSessionId` state → Task 8 Step 3 定義，Task 8 Step 2 set ✅
