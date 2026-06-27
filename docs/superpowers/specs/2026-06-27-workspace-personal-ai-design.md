# 我的工作檯個人 AI — 設計文件

- 日期：2026-06-27
- 子專案代號：SP9-B
- 狀態：設計已與開發者確認，待出實作計畫

## 1. 背景與目標

「我的工作檯」（`/my-work`，`MyWorkspacePage`）目前只有個人 KPI 與客戶列表，沒有 AI 功能。
本子專案為工作檯加入「個人 AI 助理」，讓業務在自己的客戶範圍內，得到 AI 的工作優先建議、可對自己的客戶組合問答、並能查既往 AI 歷程。

現況約 80% 可複用既有架構（`ManagerInsightService`、`InsightService` 串流、`AiGovernanceService`、SALES 隔離、前端 `useAiChat`/`readSseStream`/`AiCallHistoryModal`/`AddOpportunityModal`）。

## 2. 範圍（4 項能力）

### A. 個人工作推薦
點「產生我的工作建議」後：
1. **可點待辦清單**：由 DB 規則即時算出（高風險客戶 / 即將續約 / 停滯商機），每筆可點跳 `/customers/{id}`。不依賴 AI，永遠可用。
2. **AI 總結**：以待辦為 grounding，LLM 逐字串流產生優先排序建議。
3. **AI 建議商機草稿**：LLM 產生草稿（客戶、名稱、建議階段、理由），使用者按「建立」開既有 `AddOpportunityModal` 預填，確認後才真正建立。

### B. 個人問答
複用既有對話 UI，預設範圍為「我的客戶組合總覽」，可選某客戶深入問。SSE 逐字串流。

### C. AI 歷程
複用既有歷程彈窗，列出本人工作檯的 AI 呼叫紀錄（全部保留，不刪除）。

### D. 範圍隔離
- SALES：後端強制只能存取自己負責的客戶（`ownerName = principal.displayName()`）。
- MANAGER / ADMIN：預設看自己，可切換看「全部」。

## 3. 關鍵設計原則

- **防幻覺接地**：待辦清單與商機草稿的「對象、數字」一律由 Java/DB 計算後當 grounding 餵給 LLM，prompt 明示不可竄改數字（沿用專案既有 RAG grounding 紀律）。
- **AI 建商機走確認流**：後端只「產生草稿」；真正建立沿用既有 `POST /api/opportunities`（已驗證、有權限控管），不另開寫入路徑，降低風險。
- **優雅降級**：無金鑰 / LLM 呼叫失敗時，AI 文字走 deterministic fallback；待辦清單為純規則計算，不受影響。
- **越權零信任**：所有 scope 解析在後端；SALES 即使帶 `scope=all` 也被強制回 self；單客戶深入問答前先驗證該 customerId 在呼叫者可見範圍內。

## 4. 後端設計

### 4.1 新增端點 `WorkspaceController`（`/api/workspace/**`，任何登入角色）

| 端點 | 方法 | 用途 |
|---|---|---|
| `/api/workspace/recommendation` | `POST`（SSE） | 產生工作建議：先送結構化待辦 + 商機草稿，AI 總結逐字串流 |
| `/api/workspace/recommendation` | `GET` | 讀上次快取的 AI 總結 + 即時重算的待辦（無快取則總結為 null） |
| `/api/workspace/chat` | `POST`（SSE） | 個人問答；可選 `customerId` 深入單客戶 |
| `/api/workspace/history` | `GET` | 本人工作檯 AI 歷程 |

> AI 建商機不另開端點：草稿在 recommendation 回應內，前端拿去預填既有新增商機 Modal → 既有 `POST /api/opportunities`。

### 4.2 新增服務 `WorkspaceAiService`

- `resolveScope(principal, requestedScope)`：SALES 一律回 self（強制 `ownerName=displayName`）；MANAGER/ADMIN 依請求回 self 或 all。回傳要套用的 owner 過濾條件。
- `computeTodos(scope)`：純 DB 規則算出待辦。每筆含 `{type, customerId, customerName, reason, severity}`。規則：
  - 高風險：`riskLevel = HIGH`
  - 即將續約：`renewalDueDate` 落在未來 N 天內（N 預設 14）
  - 停滯商機：商機開放中（非 CLOSED_WON / CLOSED_LOST）且 `expectedCloseDate` 早於今日（逾期未結案）
- `streamRecommendation(principal, scope)`：先算 todos 當 grounding → `ChatClient.stream()` 產生總結 + 商機草稿 → 逐字串流 → `AiGovernanceService.record(WORKSPACE_RECOMMENDATION, subject=username)`；失敗 fallback。
- `streamChat(principal, scope, customerId?, message)`：
  - `customerId` 有值：先驗證該客戶在呼叫者可見 scope 內，否則 403/404；通過後複用 `InsightService.streamChat`（單客戶）。
  - `customerId` 無值：以「我的客戶組合摘要」當 grounding 做總覽問答；`AiGovernanceService.record(WORKSPACE_CHAT, subject=username)`。

### 4.3 重用既有

- SALES 隔離過濾：`CustomerService` 既有 `search(principal,…)` / `buildSpec` 邏輯。
- 串流 / 治理 / fallback：`InsightService` 串流模板、`AiGovernanceService.record(...subject...)`、`PiiMasker`。
- 快取：沿用 `manager_insight` 表，新增 `scope=SELF`（`ownerName=username`），存 AI 總結文字 + generatedAt + model；待辦每次即時算，不快取。

### 4.4 新增列舉與查詢

- `AiCallType.WORKSPACE_RECOMMENDATION`、`AiCallType.WORKSPACE_CHAT`。
- `AiCallLogRepository`：加「依 subject + 這兩種類型查歷程」的查詢方法。

### 4.5 回應 DTO（草案）

- `WorkspaceTodoItem{ type, customerId, customerName, reason, severity }`
- `SuggestedOpportunityDraft{ customerId, customerName, name, suggestedStage, amount?, rationale }`
- recommendation SSE 串流：先送 `{type:"todos", items:[...]}` 與 `{type:"drafts", items:[...]}`，再逐塊送 `{type:"content", delta}`，尾段 `{type:"callId"}` 與 `[DONE]`（沿用既有 SSE chunk 協定）。

## 5. 前端設計

### 5.1 `MyWorkspacePage` 新增「我的 AI 助理」區塊（KPI 卡下方）

1. **工作建議卡**：「產生我的工作建議」按鈕觸發 SSE；即時顯示可點待辦清單；AI 總結 Markdown 逐字串流（`readSseStream` + `pending` + `AiThinkingIndicator`）；AI 建議商機草稿卡，按「建立」開 `AddOpportunityModal` 並預填。
2. **個人問答**：複用 `useAiChat` 模式對話框；上方「範圍」下拉（全部我的客戶 / 指定某客戶深入）。
3. **AI 歷程**：按鈕開 `AiCallHistoryModal`。
4. **MANAGER/ADMIN**：頂部「自己 / 全部」切換；SALES 不顯示。

### 5.2 新增 `api.ts` 函式

- `streamWorkspaceRecommendation(scope, onChunk, onDone, onError)`
- `fetchWorkspaceRecommendation(scope)`
- `streamWorkspaceChat(scope, customerId|null, message, onChunk, onDone, onError)`
- `fetchWorkspaceHistory(scope)`

### 5.3 複用既有元件

`readSseStream`、`AiThinkingIndicator`、`AiCallHistoryModal`、`AddOpportunityModal`、Markdown 渲染、`ChatMessage` 模型、`useAiChat` 模式。

## 6. 測試設計

### 後端（TDD，重點在隔離與接地）
- **隔離（最關鍵）**：SALES 呼叫 recommendation/chat，即使帶 `scope=all`，結果只含自己的客戶；深入問非自己客戶 → 403/404。
- **待辦正確性**：高風險 / 續約 / 停滯規則各命中正確客戶。
- **MANAGER scope 切換**：self vs all 客戶集合正確。
- **fallback**：無金鑰時 AI 總結走 deterministic，待辦照常。
- **歷程**：呼叫後 `ai_call_log` 有對應 subject 紀錄、查得到。
- 全程跑 `mvn -pl backend test`（CI 會驗）。

### 前端
以實際啟動後端 + 瀏覽器自動化腳本（放 `e2e/` 或 `scripts/`，加中文註解，依 CLAUDE.md 規約）登入 SALES，驗證待辦可點、AI 逐字、建商機預填流程。

## 7. 安全與正式環境注意

- scope 解析、客戶可見性驗證一律在後端；前端參數不可信任。
- 不新增任何繞過既有權限的寫入路徑；AI 建商機沿用既有建立端點。

## 8. 不在本子專案範圍（YAGNI）

- 不做跨業務的團隊層 AI（已有 `ManagerInsightService` 團隊診斷）。
- 不做 AI 自動建立商機（一律經使用者確認）。
- 不做推薦待辦的持久化排程 / 通知。
