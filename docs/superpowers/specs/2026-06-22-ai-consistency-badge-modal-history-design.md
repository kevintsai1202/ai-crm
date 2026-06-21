# AI 功能一致化（標示 + 彈窗 + AI 歷程）— 設計文件

- 日期：2026-06-22
- 狀態：已通過腦力激盪、待實作計畫
- 範圍：把 Manager 業務分析（團隊診斷 / 業務 coaching）與儀表板全公司評估的 AI 體驗，對齊客戶工作區既有的「AI 標示 + 點擊彈窗 + AI 歷程」三件套。

---

## 1. 背景與目標

### 1.1 需求
1. 所有 AI 功能都要像客戶工作區一樣有 **AI 標示**（`AiBadge`），點了以後以**彈窗**顯示。
2. 所有 AI 功能都要能看到**歷史 AI 歷程**。
3. **團隊分析**比照儀表板放最上方（topbar 按鈕），也要有 AI 歷程。
4. **儀表板的全公司整體評估**（Portfolio）也要加上 AI 歷程。

### 1.2 既有地基（沿用）
- `AiBadge`（`components/common/AiBadge.tsx`）：`✨ AI` 徽章，`onDark` 用於深色底。
- 客戶 AI 歷程：`AiHistoryModal`（含 Agent trace）+ `GET /api/ai/customers/{id}/calls`（`findByCustomerIdOrderByCreatedAtDesc`）。
- 儀表板全公司評估：`DashboardPage` 的 `📊 整體評估（全公司）<AiBadge onDark/>` 按鈕 → `ReportModal`（唯讀 markdown）。**已有徽章+彈窗，缺歷程。**
- `Dtos.AiCallHistoryItem(id, callType, model, aiEnabled, totalTokens, answer, createdAt)`。
- `ai_call_log`：`customerId, type, model, aiEnabled, totalTokens, answer, createdAt, masked`。**無「subject/業務」欄位**——TEAM/OWNER/PORTFOLIO 呼叫的 `customerId` 皆為 null。
- 模組 C 的 `ManagerInsightService`（同步 JSON、`manager_insight` 快取、fallback）與 `ManagerInsightController`。
- 前端 `/team` 目前為 inline `InsightPanel`（讀快取 + 重新分析）。

### 1.3 成功標準
- 團隊診斷改為 topbar 按鈕（含 AiBadge）→ 點擊彈窗；業務 coaching 由表格列按鈕 → 彈窗。
- 團隊診斷、業務 coaching（分業務）、Portfolio 三者都能看到各自的 AI 歷程。
- 客戶工作區的 AI 體驗不受影響；`InsightService` 不被改動。

---

## 2. 後端

### 2.1 `ai_call_log` 新增 `subject` 欄（V15 migration）
- `V15__add_ai_call_log_subject.sql`：
  ```sql
  ALTER TABLE ai_call_log ADD COLUMN subject VARCHAR(255);
  CREATE INDEX idx_ai_call_log_type_subject ON ai_call_log (type, subject);
  ```
- 語意：`OWNER_COACHING` 存 ownerName；`TEAM_ANALYSIS` / `PORTFOLIO` / 客戶呼叫皆 null（客戶維度用既有 `customer_id`）。
- `AiCallLog` entity 加 `subject` 欄位 + getter；新增一個帶 subject 的建構子，既有建構子委派為 `subject=null`（不破壞既有呼叫）。

### 2.2 `AiGovernanceService.record(...)` 多載
- 保留既有 9 參數 `record(type, customerId, model, pt, ct, tt, aiEnabled, masked, answer)`（內部以 `subject=null` 呼叫新版）。
- 新增 10 參數多載 `record(type, customerId, subject, model, pt, ct, tt, aiEnabled, masked, answer)`。
- 只有 `ManagerInsightService` 的 `OWNER_COACHING` 改呼叫帶 subject 版本（傳 ownerName）；其餘呼叫端不變。

### 2.3 AI 歷程查詢
- `AiCallLogRepository` 新增：
  - `List<AiCallLog> findByTypeAndCustomerIdIsNullAndSubjectIsNullOrderByCreatedAtDesc(AiCallType type)` — 供 TEAM / PORTFOLIO。
  - `List<AiCallLog> findByTypeAndSubjectOrderByCreatedAtDesc(AiCallType type, String subject)` — 供 OWNER。
- `AiGovernanceService` 新增（回 `List<Dtos.AiCallHistoryItem>`，沿用既有 AiCallLog→AiCallHistoryItem 映射邏輯）：
  - `historyByType(AiCallType type)`
  - `historyByOwner(String ownerName)`（type 固定 OWNER_COACHING）

### 2.4 端點
- `ManagerInsightController`（`/api/manager/**`，MANAGER/ADMIN）：
  - `GET /api/manager/insights/team/calls` → `historyByType(TEAM_ANALYSIS)`
  - `GET /api/manager/insights/owner/calls?owner=NAME` → `historyByOwner(NAME)`
- `AiController`（`/api/ai/**`，已登入）：
  - `GET /api/ai/portfolio/calls` → `historyByType(PORTFOLIO)`

### 2.5 `ManagerInsightService`
- `generateOwnerInsight(owner)` 的 `callLlm` 改走帶 subject 的 record（subject=ownerName）；`generateTeamInsight` 維持 subject=null。

---

## 3. 前端

### 3.1 新增共用元件 `AiCallHistoryModal`（`components/common/`）
- props：`{ title: string; calls: AiCallHistoryItem[]; loading: boolean; onClose: () => void }`。
- 沿用客戶 `AiHistoryModal` 的 `ai-history-list` 樣式（可展開、模型/fallback 標記、token 用量、`react-markdown`），**不含** Agent trace 區段。
- `CALL_TYPE_LABELS` 補 `TEAM_ANALYSIS:"團隊診斷"`、`OWNER_COACHING:"業務輔導"`、`PORTFOLIO:"全公司評估"`（沿用既有 CHAT/ASSESSMENT/PORTFOLIO）。

### 3.2 新增 `ManagerInsightModal`（`features/team/`）
- props：`{ scope: "TEAM" | "OWNER"; owner?: string; onClose: () => void }`。
- 內含原 `InsightPanel` 的邏輯：開啟先讀快取（`fetchTeamInsight` / `fetchOwnerInsight(owner)`）顯示報告 + 「上次分析」時間 + 模型/教學版摘要；「重新分析」呼叫 `generateTeamInsight` / `generateOwnerInsight(owner)`。
- 標題含 `AiBadge onDark`；含一顆「🕘 AI 歷程」→ 開 `AiCallHistoryModal`（team → `fetchTeamInsightCalls()`；owner → `fetchOwnerInsightCalls(owner)`）。
- 樣式沿用 `ReportModal`（`modal-overlay` / `report-modal` / `report-header` / `report-body`）。

### 3.3 `/team` 改版（`TeamAnalyticsPage`）
- 移除兩個 inline `InsightPanel`（`InsightPanel` 元件刪除，邏輯併入 `ManagerInsightModal`）。
- topbar 右側加按鈕 `🤖 團隊整體診斷 <AiBadge onDark/>` → 開 `ManagerInsightModal(scope=TEAM)`。
- 業務績效表每列「輔導報告」按鈕 → 開 `ManagerInsightModal(scope=OWNER, owner=該業務)`。
- KPI 列、可排序業務表維持不變。

### 3.4 儀表板（`DashboardPage`）
- 現有 `📊 整體評估（全公司）` 按鈕旁加 `🕘 AI 歷程` 按鈕 → 開 `AiCallHistoryModal`（`fetchPortfolioCalls()`，標題「全公司評估 AI 歷程」）。
- 既有 ReportModal 評估流程不變。

### 3.5 `api.ts` 新增
- `fetchTeamInsightCalls(): Promise<AiCallHistoryItem[]>` → `GET /manager/insights/team/calls`
- `fetchOwnerInsightCalls(owner): Promise<AiCallHistoryItem[]>` → `GET /manager/insights/owner/calls?owner=`
- `fetchPortfolioCalls(): Promise<AiCallHistoryItem[]>` → `GET /ai/portfolio/calls`

---

## 4. 測試策略
- **後端**：
  - `historyByType` / `historyByOwner` 正確性：產生 TEAM 與兩個不同 owner 的 OWNER_COACHING 後，各自歷程筆數與 subject 隔離正確。
  - 端點權限：SALES 打 `/api/manager/insights/team/calls`、`/owner/calls` → 403；MANAGER → 200。
  - `record` 多載：OWNER_COACHING 寫入的 `ai_call_log.subject == ownerName`；TEAM/PORTFOLIO 為 null。
- **前端**：`tsc --noEmit` 與 `pnpm build` exit 0。
- **E2E**（延伸 `team-analytics.spec.ts` 或新增）：MANAGER 點「團隊整體診斷」→ 彈窗顯示報告 → 點「AI 歷程」→ 顯示歷程清單；點某業務「輔導報告」→ 彈窗。

---

## 5. 範圍外（YAGNI）
- 不改客戶工作區的 `AiHistoryModal`（含 trace）行為。
- 不為歷程做分頁（資料量小，一次撈回）。
- 不改 `InsightService`（CHAT/ASSESSMENT/PORTFOLIO 既有流程）。
- Portfolio 歷程沿用現有權限（任何登入者可見，與既有評估按鈕一致）；不另設角色限制。
