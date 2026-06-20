# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

本目錄為 AI CRM 智慧業務助理前端，對應教學站 Unit 5 到 Unit 8 的 React 工作台實作。

## 指令

```powershell
# 開發伺服器（預設綁定 127.0.0.1:5173）
pnpm dev

# TypeScript 型別檢查（無產出，任何變更後先確認 exit 0）
pnpm exec tsc --noEmit
# Git Bash 環境需先設定 PATH
export PATH="/d/nodejs:$PATH"; pnpm exec tsc --noEmit

# 建置產出
pnpm build
```

無 lint script（`package.json` 未配置）。E2E 煙霧測試：`pnpm exec playwright test`（需後端在 18080）。

## 架構概覽

React 19 + TypeScript + Vite **多頁應用（react-router-dom v7）**。`src/App.tsx` 僅為路由表；全域認證/健康狀態集中於 `src/context/AuthContext.tsx`，各頁自管區域狀態。儀表板（看數據）與操作（做事）分屬不同路由頁。

### 路由

| 路徑 | 頁面 | 說明 |
|------|------|------|
| `/login` | LoginPage | 公開；已登入導向 /dashboard |
| `/dashboard` | DashboardPage | 統計卡 + 7 圖表 + 下鑽 + Portfolio 評估（唯讀） |
| `/customers`、`/customers/:id` | CustomersPage | 搜尋/列表/詳情/商機看板/互動/AI 助理；選取客戶以 URL `:id` 為單一真實來源 |

`App.tsx` 結構：`/login` 公開；其餘經 `app/ProtectedRoute`（用 `useAuth().isAuthed` 守衛）+ `app/AppShell`（側邊欄 + `<Outlet/>`）。`main.tsx` 包裹順序：`BrowserRouter > AuthProvider > App`。

### 目錄結構意圖

```text
src/
  api.ts          # 所有後端呼叫（axios client + 原始 fetch SSE）
  types.ts        # 共用 TypeScript 介面（從後端 DTO 對應）
  lib/
    format.ts     # 純函式：formatMoney / formatCompactMoney / formatDateTime / riskLabel / stageLabel
  components/
    common/       # 跨 feature 共用 UI：AiBadge、ReportModal、DrilldownModal
  features/
    agent-trace/  # TracePanel：呈現 GOAP Agent 執行步驟
    ai-assistant/ # useAiChat（hook）+ ChatBubble / ChatWindow / ChatLauncher
    dashboard/    # DashboardCards、ReportsSection（含 7 個內部圖表元件）
    customers/    # CustomerList、Pagination、Timeline、OpportunityBoard、
                  # CustomerDetailPanel、AddCustomerModal、AddInteractionModal
```

### 資料流

`App.tsx` 持有全部頁面狀態（登入、dashboard、客戶列表、選取客戶、報告 modal、下鑽 modal）。所有異步操作在 `App.tsx` 的 handler 函式中直接呼叫 `src/api.ts`，結果透過 setState 更新，再以 props 傳入子元件。

**例外**：`OpportunityBoard` 在拖拽結束時直接呼叫 `updateOpportunityStage`，並在失敗時以 `onStageChange` rollback 至舊階段（樂觀更新模式）。

### AI 對話 SSE 串流

`src/api.ts` 的 `askAssistantStream` 使用原生 `fetch` + `ReadableStream`（非 axios），解析 `text/event-stream`。串流片段分三種 `type`：`content`（delta 文字）、`citations`（引用來源）、`risk`（風險分數）。`useAiChat.ts` hook 封裝訊息狀態與 `patchLastAssistant` 不可變更新邏輯；`App.tsx` 呼叫 `sendChat(customerId, message)` 即可。

### ChatMessage 型別

`ChatMessage` interface 定義並 **export** 於 `src/features/ai-assistant/useAiChat.ts`。`ChatBubble` 與 `ChatWindow` 必須從 `../useAiChat` import，不可重複定義。

### 圖表元件（ReportsSection 內部）

`PipelineFunnel`、`MonthlyForecastChart`、`IndustryBreakdown`、`RiskBreakdown`、`RenewalForecast`、`OwnerLeaderboard`、`ActivityReportList` 均為 `ReportsSection.tsx` 檔內的非 export function，不應獨立 export。`ReportsSection` 同檔 export `DrillFn` 型別供 `App.tsx` 使用。

### Kanban 子元件（OpportunityBoard 內部）

`KanbanColumn` 與 `KanbanCard` 為 `OpportunityBoard.tsx` 檔內的非 export function。

### API 與後端連線

- Axios client 的 `baseURL` 預設為 `import.meta.env.VITE_API_BASE_URL || "/api"`。
- 本機開發時 Vite proxy 將 `/api` 轉發至 `http://127.0.0.1:18080`（Spring Boot 後端）。
- JWT token 儲存於 `localStorage`，key 為 `"ai-crm-token"`；401 response 自動清除 token。
- 後端健康檢查採 fail-closed：連線失敗時顯示紅燈，不阻斷渲染。

## 開發規範

- 所有函式需有中文函式級註解（`/** ... */`），重要變數或物件也需加上中文行內註解。
- 單一任務原則，勿過度開發（YAGNI）。
- 任何修改前先確認 `pnpm exec tsc --noEmit` exit 0，修改後再次確認。

