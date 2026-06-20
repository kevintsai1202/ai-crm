# SP1 前端架構重構 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 1337 行單檔 `frontend/src/App.tsx` 重構成以 React Router 驅動的多頁應用，將「儀表板（看數據）」與「操作（做事）」拆到不同路由頁，元件依 `features/` 切分，行為完全不變。

**Architecture:** 方案 A — React Router v7 多頁 + 一個 `AuthContext` 放跨頁全域態（user/health），各頁自行 fetch + 區域 state。選取客戶以 URL `:id` 為單一真實來源。所有既有元件從 `App.tsx` **原樣搬移**，僅調整 import 路徑與共用工具引用，不改內部邏輯。

**Tech Stack:** React 19、TypeScript、Vite 7、react-router-dom v7、axios（既有）、@dnd-kit（既有）、react-markdown（既有）、@playwright/test（煙霧測試）。

**重要環境限制：**
- 本專案**非 git repo** → 不使用 `git commit`；每個任務以 `pnpm run build` 通過為驗證關卡。
- 前端規範用 **pnpm**，Node 在 `D:\nodejs`。每個 build 指令前需設定 PATH：
  ```powershell
  $env:Path = "D:\nodejs;$env:Path"
  ```
- 工作目錄：`d:\GitHub\ai-crm\frontend`。
- 參考來源：所有待搬移元件目前都在 `frontend/src/App.tsx`（行號見各任務）。spec：`docs/superpowers/specs/2026-06-19-sp1-frontend-architecture-refactor-design.md`。

**搬移總原則（每個元件任務都適用）：**
1. 從 `App.tsx` 將指定元件函式**整段原樣剪下**貼到新檔。
2. 新檔頂部加上該元件實際用到的 import（React hooks、型別、第三方庫、`lib/format`、子元件）。
3. 元件改為 `export function X(...)`（具名匯出）。
4. 全部任務完成後，`App.tsx` 只剩路由表。

---

## 檔案結構總覽（先鎖定分解）

```
frontend/src/
  main.tsx                 # [修改] 包 <BrowserRouter>
  App.tsx                  # [修改] 最終只剩 <Routes> 路由表
  api.ts                   # [修改] 401 派發 auth:logout 事件
  types.ts                 # [不動]
  styles.css               # [不動]
  lib/
    format.ts              # [新] 純格式化函式
  components/common/
    AiBadge.tsx            # [新] 搬移
    ReportModal.tsx        # [新] 搬移
    DrilldownModal.tsx     # [新] 搬移
  context/
    AuthContext.tsx        # [新] 全域 auth/health
  app/
    ProtectedRoute.tsx     # [新] 守衛
    AppShell.tsx           # [新] 側邊欄 + <Outlet/>
  features/
    auth/LoginPage.tsx                 # [新] 包 LoginPanel
    agent-trace/TracePanel.tsx         # [新] 搬移
    ai-assistant/
      useAiChat.ts                     # [新] 對話狀態 + SSE
      components/ChatBubble.tsx        # [新] 搬移
      components/ChatWindow.tsx        # [新] 搬移
      components/ChatLauncher.tsx      # [新] 搬移
    dashboard/
      DashboardPage.tsx                # [新] 組裝
      components/DashboardCards.tsx    # [新] 搬移
      components/ReportsSection.tsx    # [新] 搬移（含 7 chart）
    customers/
      CustomersPage.tsx                # [新] 組裝，URL :id 驅動
      components/CustomerList.tsx      # [新] 搬移
      components/Pagination.tsx        # [新] 搬移
      components/Timeline.tsx          # [新] 搬移
      components/OpportunityBoard.tsx  # [新] 搬移（含 Kanban）
      components/CustomerDetailPanel.tsx # [新] 搬移
      components/AddCustomerModal.tsx  # [新] 搬移
      components/AddInteractionModal.tsx # [新] 搬移
  e2e/
    sp1-smoke.spec.ts                  # [新] Playwright 煙霧測試
```

---

## Task 0：安裝相依套件

**Files:**
- Modify: `frontend/package.json`（由 pnpm 自動更新）

- [ ] **Step 1: 安裝 react-router-dom**

工作目錄 `d:\GitHub\ai-crm\frontend`，執行：
```powershell
$env:Path = "D:\nodejs;$env:Path"
pnpm add react-router-dom@^7
```

- [ ] **Step 2: 安裝 Playwright（devDependency）**

```powershell
pnpm add -D @playwright/test
pnpm exec playwright install chromium
```

- [ ] **Step 3: 驗證安裝**

Run: `pnpm list react-router-dom @playwright/test`
Expected: 兩者皆顯示已安裝版本（react-router-dom 7.x、@playwright/test）。

---

## Task 1：抽出純格式化工具 `lib/format.ts`

**Files:**
- Create: `frontend/src/lib/format.ts`
- 來源：`App.tsx:65-104`（formatMoney / formatCompactMoney / formatDateTime / riskLabel / stageLabel）

- [ ] **Step 1: 建立 `frontend/src/lib/format.ts`**

```typescript
/**
 * 將金額格式化為台幣樣式。
 */
export function formatMoney(value: number) {
  return new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(value);
}

/**
 * 將金額縮短為報表圖表適合閱讀的單位。
 */
export function formatCompactMoney(value: number) {
  return new Intl.NumberFormat("zh-TW", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

/**
 * 將日期時間轉為本地可讀格式。
 */
export function formatDateTime(value: string | null) {
  if (!value) return "尚無資料";
  return new Intl.DateTimeFormat("zh-TW", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

/**
 * 將風險等級轉成中文標籤。
 */
export function riskLabel(level: string) {
  const labels: Record<string, string> = { LOW: "低風險", MEDIUM: "中風險", HIGH: "高風險" };
  return labels[level] || level;
}

/**
 * 將商機階段轉成中文圖表標籤。
 */
export function stageLabel(stage: string) {
  const labels: Record<string, string> = {
    QUALIFICATION: "資格評估",
    PROPOSAL: "提案",
    NEGOTIATION: "議價",
    CLOSED_WON: "已成交",
    CLOSED_LOST: "已流失"
  };
  return labels[stage] || stage;
}
```

- [ ] **Step 2: 驗證型別**

Run（工作目錄 frontend）：
```powershell
$env:Path = "D:\nodejs;$env:Path"
pnpm exec tsc --noEmit
```
Expected: 不因 `lib/format.ts` 報錯（既有 App.tsx 仍有自己的副本，暫時並存，下個任務起逐步改引用）。

---

## Task 2：搬移共用元件 `components/common/`

**Files:**
- Create: `frontend/src/components/common/AiBadge.tsx`（來源 `App.tsx:999-1005`）
- Create: `frontend/src/components/common/ReportModal.tsx`（來源 `App.tsx:1179-1205`）
- Create: `frontend/src/components/common/DrilldownModal.tsx`（來源 `App.tsx:1210-1252`）

- [ ] **Step 1: 建立 `AiBadge.tsx`**

```typescript
/**
 * AI 功能徽章：統一標示「此功能由 AI 驅動」。
 * 函式級註解：onDark 用於深色/漸層底（白底徽章），預設用於淺色底（漸層徽章）。
 */
export function AiBadge({ onDark = false }: { onDark?: boolean }) {
  return (
    <span className={onDark ? "ai-badge on-dark" : "ai-badge"} title="此功能由 AI 驅動">
      <span aria-hidden="true">✨</span> AI
    </span>
  );
}
```

- [ ] **Step 2: 建立 `ReportModal.tsx`**

從 `App.tsx:1179-1205` 原樣搬移 `ReportModal` 函式，頂部加 import：
```typescript
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AiBadge } from "./AiBadge";
```
並把函式宣告改成 `export function ReportModal(...)`，內容（含 props 型別）不變。

- [ ] **Step 3: 建立 `DrilldownModal.tsx`**

從 `App.tsx:1210-1252` 原樣搬移 `DrilldownModal`，頂部加 import：
```typescript
import type { DrilldownResponse } from "../../types";
import { formatMoney, formatCompactMoney, riskLabel } from "../../lib/format";
```
改成 `export function DrilldownModal(...)`，內容不變。

- [ ] **Step 4: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: 新檔不報錯（App.tsx 副本仍在，並存正常）。

---

## Task 3：搬移 `agent-trace/TracePanel.tsx`

**Files:**
- Create: `frontend/src/features/agent-trace/TracePanel.tsx`（來源 `App.tsx:1154-1174`）

- [ ] **Step 1: 建立檔案**

從 `App.tsx:1154-1174` 原樣搬移 `TracePanel`，頂部加 import：
```typescript
import type { AgentTraceResponse } from "../../types";
import { AiBadge } from "../../components/common/AiBadge";
```
改成 `export function TracePanel(...)`，內容不變。

- [ ] **Step 2: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: 無新錯誤。

---

## Task 4：搬移 AI 助理 `features/ai-assistant/`

**Files:**
- Create: `frontend/src/features/ai-assistant/components/ChatBubble.tsx`（來源 `App.tsx:1110-1149`）
- Create: `frontend/src/features/ai-assistant/components/ChatWindow.tsx`（來源 `App.tsx:1024-1105`，含 `CHAT_SUGGESTIONS`）
- Create: `frontend/src/features/ai-assistant/components/ChatLauncher.tsx`（來源 `App.tsx:1010-1019`）
- Create: `frontend/src/features/ai-assistant/useAiChat.ts`（來源 `App.tsx` 的 `ChatMessage` 介面 49-60、`handleAiChat` 邏輯 258-303、`messages/chatSending/chatOpen` 狀態）

- [ ] **Step 1: 建立 `ChatBubble.tsx`**

頂部 import：
```typescript
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AiBadge } from "../../../components/common/AiBadge";
import type { ChatMessage } from "../useAiChat";
```
從 `App.tsx:1110-1149` 搬移 `ChatBubble`，改 `export function ChatBubble(...)`。注意 `ChatMessage` 型別改從 `../useAiChat` 匯入。

- [ ] **Step 2: 建立 `ChatWindow.tsx`**

頂部 import：
```typescript
import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import type { CustomerSummary } from "../../../types";
import { AiBadge } from "../../../components/common/AiBadge";
import { ChatBubble } from "./ChatBubble";
import type { ChatMessage } from "../useAiChat";
```
搬移 `CHAT_SUGGESTIONS` 常數（`App.tsx:1024-1028`）與 `ChatWindow` 函式（`App.tsx:1034-1105`），`ChatWindow` 改 `export function`。`messages` 參數型別用匯入的 `ChatMessage`。

- [ ] **Step 3: 建立 `ChatLauncher.tsx`**

從 `App.tsx:1010-1019` 搬移，無額外 import 需求，改 `export function ChatLauncher(...)`。

- [ ] **Step 4: 建立 `useAiChat.ts`（hook）**

```typescript
import { useState } from "react";
import { askAssistantStream } from "../../api";
import type { CitationResponse, RiskResponse } from "../../types";

/**
 * 聊天訊息模型：一則使用者或 AI 助理的訊息，AI 訊息可附帶引用與風險。
 */
export interface ChatMessage {
  /** 發話角色。 */
  role: "user" | "assistant";
  /** 訊息內容（AI 為 Markdown 文字）。 */
  content: string;
  /** AI 引用來源（選填）。 */
  citations?: CitationResponse[];
  /** AI 風險評分（選填）。 */
  risk?: RiskResponse;
  /** 是否仍在串流產生中。 */
  pending?: boolean;
}

/**
 * AI 助理對話 hook：管理對話歷史、開關、送出與 SSE 串流。
 * 函式級註解：把原 App.tsx 的 messages/chatSending/chatOpen 狀態與 handleAiChat 邏輯集中於此。
 */
export function useAiChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatSending, setChatSending] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);

  /** 清空對話（切換客戶時呼叫）。 */
  function resetChat() {
    setMessages([]);
  }

  /**
   * 送出問題並以 SSE 串流逐字填入最後一則 AI 訊息。
   * @param customerId 目標客戶 ID
   * @param message 使用者輸入
   */
  function sendChat(customerId: number, message: string) {
    if (chatSending) return;
    setMessages((prev) => [
      ...prev,
      { role: "user", content: message },
      { role: "assistant", content: "", citations: [], pending: true }
    ]);
    setChatSending(true);

    const patchLastAssistant = (patch: (m: ChatMessage) => ChatMessage) => {
      setMessages((prev) => {
        const copy = [...prev];
        const lastIndex = copy.length - 1;
        if (lastIndex >= 0 && copy[lastIndex].role === "assistant") {
          copy[lastIndex] = patch(copy[lastIndex]);
        }
        return copy;
      });
    };

    askAssistantStream(
      customerId,
      message,
      (chunk) => {
        if (chunk.type === "content" && chunk.delta !== undefined) {
          patchLastAssistant((m) => ({ ...m, content: m.content + chunk.delta, pending: false }));
        } else if (chunk.type === "citations" && chunk.citations !== undefined) {
          patchLastAssistant((m) => ({ ...m, citations: chunk.citations }));
        } else if (chunk.type === "risk" && chunk.risk !== undefined) {
          patchLastAssistant((m) => ({ ...m, risk: chunk.risk }));
        }
      },
      () => {
        patchLastAssistant((m) => ({ ...m, pending: false }));
        setChatSending(false);
      },
      (err) => {
        console.error("AI 助理串流失敗:", err);
        patchLastAssistant((m) => ({ ...m, content: m.content || "⚠️ AI 助理連線失敗，請稍後再試。", pending: false }));
        setChatSending(false);
      }
    );
  }

  return { messages, chatSending, chatOpen, setChatOpen, sendChat, resetChat };
}
```

- [ ] **Step 5: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: ai-assistant 新檔不報錯。

---

## Task 5：搬移儀表板元件 `features/dashboard/components/`

**Files:**
- Create: `frontend/src/features/dashboard/components/DashboardCards.tsx`（來源 `App.tsx:796-813`）
- Create: `frontend/src/features/dashboard/components/ReportsSection.tsx`（來源 `App.tsx:515-746`：`DrillFn` 型別 + `ReportsSection` + 7 個 chart 元件 `PipelineFunnel`/`MonthlyForecastChart`/`IndustryBreakdown`/`RiskBreakdown`/`RenewalForecast`/`OwnerLeaderboard`/`ActivityReportList`）

- [ ] **Step 1: 建立 `DashboardCards.tsx`**

頂部 import：
```typescript
import type { DashboardSummary } from "../../../types";
import { formatMoney } from "../../../lib/format";
```
從 `App.tsx:796-813` 搬移 `DashboardCards`，改 `export function`。

- [ ] **Step 2: 建立 `ReportsSection.tsx`**

頂部 import：
```typescript
import type { DashboardReports } from "../../../types";
import { formatCompactMoney, formatDateTime, riskLabel, stageLabel } from "../../../lib/format";

/** 圖表下鑽回呼型別。 */
export type DrillFn = (type: string, key: string, title: string) => void;
```
從 `App.tsx:522-746` 將 `ReportsSection` 及其 7 個 chart 子元件**整段原樣搬移**到本檔（它們只被 `ReportsSection` 使用，維持同檔即可）。`ReportsSection` 改 `export function`，其餘 chart 維持檔內 `function`（不需匯出）。移除原本檔內重複的 `DrillFn` 定義（已在頂部匯出）。

- [ ] **Step 3: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: dashboard 元件不報錯。

---

## Task 6：搬移客戶操作元件 `features/customers/components/`

**Files:**
- Create: `frontend/src/features/customers/components/CustomerList.tsx`（來源 `App.tsx:818-848`）
- Create: `frontend/src/features/customers/components/Pagination.tsx`（來源 `App.tsx:1257-1266`）
- Create: `frontend/src/features/customers/components/Timeline.tsx`（來源 `App.tsx:888-903`）
- Create: `frontend/src/features/customers/components/OpportunityBoard.tsx`（來源 `App.tsx:908-993`：`OpportunityBoard`+`KanbanColumn`+`KanbanCard`）
- Create: `frontend/src/features/customers/components/CustomerDetailPanel.tsx`（來源 `App.tsx:853-883`）
- Create: `frontend/src/features/customers/components/AddCustomerModal.tsx`（來源 `App.tsx:1271-1301`）
- Create: `frontend/src/features/customers/components/AddInteractionModal.tsx`（來源 `App.tsx:1306-1337`）

- [ ] **Step 1: 建立 `CustomerList.tsx`**

import：
```typescript
import type { CustomerSummary } from "../../../types";
import { riskLabel } from "../../../lib/format";
```
搬移 `CustomerList`（`App.tsx:818-848`），改 `export function`。

- [ ] **Step 2: 建立 `Pagination.tsx`**

無額外 import。搬移 `Pagination`（`App.tsx:1257-1266`），改 `export function`。

- [ ] **Step 3: 建立 `Timeline.tsx`**

import：
```typescript
import type { CustomerDetail } from "../../../types";
import { formatDateTime } from "../../../lib/format";
```
搬移 `Timeline`（`App.tsx:888-903`），改 `export function`。

- [ ] **Step 4: 建立 `OpportunityBoard.tsx`**

import：
```typescript
import { useState } from "react";
import {
  DndContext, DragOverlay, PointerSensor, useSensor, useSensors,
  useDraggable, useDroppable,
  type DragEndEvent, type DragStartEvent
} from "@dnd-kit/core";
import type { CustomerDetail } from "../../../types";
import { formatMoney } from "../../../lib/format";
import { updateOpportunityStage } from "../../../api";
```
搬移 `OpportunityBoard`+`KanbanColumn`+`KanbanCard`（`App.tsx:908-993`）。`OpportunityBoard` 改 `export function`，另兩個維持檔內 `function`。

- [ ] **Step 5: 建立 `CustomerDetailPanel.tsx`**

import：
```typescript
import type { AgentTraceResponse, CustomerDetail } from "../../../types";
import { riskLabel } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";
import { Timeline } from "./Timeline";
import { OpportunityBoard } from "./OpportunityBoard";
import { TracePanel } from "../../agent-trace/TracePanel";
```
搬移 `CustomerDetailPanel`（`App.tsx:853-883`），改 `export function`。

- [ ] **Step 6: 建立 `AddCustomerModal.tsx` 與 `AddInteractionModal.tsx`**

兩者皆 import `FormEvent`：
```typescript
import { FormEvent } from "react";
```
分別搬移 `AddCustomerModal`（`App.tsx:1271-1301`）與 `AddInteractionModal`（`App.tsx:1306-1337`），改 `export function`。

- [ ] **Step 7: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: customers 元件不報錯。

---

## Task 7：建立 `AuthContext` 與 api.ts 401 事件

**Files:**
- Modify: `frontend/src/api.ts:36-44`（401 攔截器加派發事件）
- Modify: `frontend/src/api.ts:159-164`（SSE 401 同步派發）
- Create: `frontend/src/context/AuthContext.tsx`

- [ ] **Step 1: api.ts 401 攔截器派發事件**

將 `api.ts:36-44` 的 response 攔截器改為：
```typescript
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event("auth:logout"));
    }
    return Promise.reject(error);
  }
);
```

- [ ] **Step 2: api.ts SSE 401 同步派發**

將 `askAssistantStream` 內 `App.tsx`（實為 api.ts）的 401 區塊（`api.ts:159-164`）改為：
```typescript
    if (!response.ok) {
      if (response.status === 401) {
        clearToken();
        window.dispatchEvent(new Event("auth:logout"));
      }
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
```

- [ ] **Step 3: 建立 `AuthContext.tsx`**

```typescript
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { clearToken, fetchHealth, getToken, login as apiLogin, saveToken } from "../api";
import type { HealthResponse, UserResponse } from "../types";

/** Auth 與 health 全域狀態介面。 */
interface AuthContextValue {
  user: UserResponse | null;
  health: HealthResponse | null;
  healthError: boolean;
  isAuthed: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshHealth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 全域 Auth/Health Provider：集中管理登入態與後端健康檢查。
 * 函式級註解：監聽 api.ts 派發的 auth:logout 事件，401 時自動清除使用者狀態。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthError, setHealthError] = useState(false);

  /** 重新讀取健康檢查，失敗採 fail-closed 紅燈。 */
  async function refreshHealth() {
    try {
      const result = await fetchHealth();
      setHealth(result);
      setHealthError(result.status !== "UP");
    } catch {
      setHealth(null);
      setHealthError(true);
    }
  }

  /** 登入並保存 token。 */
  async function login(username: string, password: string) {
    const result = await apiLogin(username, password);
    saveToken(result.token);
    setUser(result.user);
  }

  /** 登出並清除全部 auth 狀態。 */
  function logout() {
    clearToken();
    setUser(null);
  }

  // 啟動時測健康；監聽 401 事件以自動登出
  useEffect(() => {
    void refreshHealth();
    const onLogout = () => setUser(null);
    window.addEventListener("auth:logout", onLogout);
    return () => window.removeEventListener("auth:logout", onLogout);
  }, []);

  const value: AuthContextValue = {
    user,
    health,
    healthError,
    isAuthed: !!getToken(),
    login,
    logout,
    refreshHealth
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** 取用 Auth context 的 hook。 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth 必須在 AuthProvider 內使用");
  return ctx;
}
```

- [ ] **Step 4: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: AuthContext 不報錯。

---

## Task 8：建立 `ProtectedRoute` 與 `AppShell`

**Files:**
- Create: `frontend/src/app/ProtectedRoute.tsx`
- Create: `frontend/src/app/AppShell.tsx`
- 來源參考：側邊欄結構 `App.tsx:422-438`、HealthBadge `App.tsx:751-762`

- [ ] **Step 1: 建立 `ProtectedRoute.tsx`**

```typescript
import { Navigate, Outlet } from "react-router-dom";
import { getToken } from "../api";

/**
 * 路由守衛：未登入（無 token）導向 /login。
 */
export function ProtectedRoute() {
  return getToken() ? <Outlet /> : <Navigate to="/login" replace />;
}
```

- [ ] **Step 2: 在 `AppShell.tsx` 內建立 HealthBadge（搬移）與側邊欄**

```typescript
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type { HealthResponse } from "../types";
import { formatDateTime } from "../lib/format";

/**
 * 顯示後端連線狀態，任何錯誤都以紅燈呈現。
 */
function HealthBadge({ health, error, onRefresh }: { health: HealthResponse | null; error: boolean; onRefresh: () => void }) {
  const ok = !!health && !error;
  return (
    <div className={`health-card ${ok ? "ok" : "fail"}`}>
      <span className="pulse" />
      <div>
        <strong>{ok ? "後端連線正常" : "後端無法連線"}</strong>
        <small>{health?.timestamp ? formatDateTime(health.timestamp) : "尚未取得健康資訊"}</small>
      </div>
      <button type="button" onClick={onRefresh}>重測</button>
    </div>
  );
}

/**
 * 應用外殼：左側邊欄（品牌 + 健康狀態 + 使用者卡 + 導覽）+ 右側 <Outlet/>。
 * 函式級註解：側邊欄導覽提供「儀表板」與「客戶」兩個主入口，達成儀表板與操作分頁。
 */
export function AppShell() {
  const { user, health, healthError, refreshHealth, logout } = useAuth();
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <img src="/crm-hero.svg" alt="AI CRM 工作台視覺" />
          <div>
            <span>AI CRM</span>
            <h1>智慧業務助理</h1>
          </div>
        </div>
        <nav className="side-nav">
          <NavLink to="/dashboard" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>📊 儀表板</NavLink>
          <NavLink to="/customers" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>👥 客戶工作台</NavLink>
        </nav>
        <HealthBadge health={health} error={healthError} onRefresh={refreshHealth} />
        {user ? (
          <div className="user-card">
            <strong>{user.displayName}</strong>
            <span>{user.role}</span>
            <button type="button" onClick={logout}>登出</button>
          </div>
        ) : null}
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
```

- [ ] **Step 3: 補側邊欄導覽樣式（styles.css）**

在 `frontend/src/styles.css` 末端追加（沿用既有色系，僅新增導覽連結樣式）：
```css
/* 側邊欄主導覽：儀表板 / 客戶工作台 */
.side-nav { display: flex; flex-direction: column; gap: 8px; margin: 16px 0; }
.side-nav-link {
  display: block; padding: 10px 14px; border-radius: 10px;
  color: inherit; text-decoration: none; font-weight: 600;
  background: rgba(255, 255, 255, 0.04);
}
.side-nav-link:hover { background: rgba(255, 255, 255, 0.10); }
.side-nav-link.active { background: rgba(20, 184, 166, 0.22); }
```

- [ ] **Step 4: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: app/ 兩檔不報錯。

---

## Task 9：建立登入頁 `features/auth/LoginPage.tsx`

**Files:**
- Create: `frontend/src/features/auth/LoginPage.tsx`（來源 LoginPanel `App.tsx:768-791`）

- [ ] **Step 1: 建立 `LoginPage.tsx`**

```typescript
import { FormEvent, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { getToken } from "../../api";
import { useAuth } from "../../context/AuthContext";

/**
 * 登入頁：已登入自動導向儀表板，否則顯示教學帳號登入表單。
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");

  // 已登入則直接導向儀表板
  if (getToken()) return <Navigate to="/dashboard" replace />;

  /** 處理登入表單送出。 */
  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await login(String(form.get("username")), String(form.get("password")));
      navigate("/dashboard");
    } catch {
      setError("登入失敗，請確認帳號與密碼。");
    }
  }

  return (
    <section className="login-panel">
      <div className="login-copy">
        <span>Unit 4 + Unit 5</span>
        <h2>登入 AI CRM 工作台</h2>
        <p>使用教學 seed 帳號進入完整工作台，驗證 JWT、角色權限、Dashboard、客戶資料、AI 助理與 Agent Trace。</p>
      </div>
      <form className="login-form" onSubmit={handleLogin}>
        <label>
          帳號
          <input name="username" defaultValue="sales@aurora.local" autoComplete="username" />
        </label>
        <label>
          密碼
          <input name="password" type="password" defaultValue="password123" autoComplete="current-password" />
        </label>
        {error ? <div className="error-box">{error}</div> : null}
        <button type="submit">登入</button>
        <small>可用帳號：sales@aurora.local / manager@aurora.local / admin@aurora.local，密碼皆為 password123。</small>
      </form>
    </section>
  );
}
```

- [ ] **Step 2: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: LoginPage 不報錯。

---

## Task 10：組裝儀表板頁 `features/dashboard/DashboardPage.tsx`

**Files:**
- Create: `frontend/src/features/dashboard/DashboardPage.tsx`
- 來源參考：dashboard 載入 `App.tsx:168-190`、portfolio 評估 `App.tsx:366-381`、drilldown `App.tsx:342-361`、topbar portfolio 鈕 `App.tsx:445-450`

- [ ] **Step 1: 建立 `DashboardPage.tsx`**

```typescript
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchDashboard, fetchDashboardReports, fetchDrilldown, fetchPortfolioAssessment } from "../../api";
import type { DashboardReports, DashboardSummary, DrilldownResponse } from "../../types";
import { formatMoney } from "../../lib/format";
import { AiBadge } from "../../components/common/AiBadge";
import { ReportModal } from "../../components/common/ReportModal";
import { DrilldownModal } from "../../components/common/DrilldownModal";
import { DashboardCards } from "./components/DashboardCards";
import { ReportsSection } from "./components/ReportsSection";

/**
 * 儀表板頁（看數據）：統計卡 + 7 張 CRM 圖表 + 圖表下鑽 + 全公司 Portfolio 整體評估。
 * 函式級註解：本頁為唯讀的管理層視角，所有資料進頁時載入；點客戶會跳到操作頁。
 */
export function DashboardPage() {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null);
  const [reports, setReports] = useState<DashboardReports | null>(null);
  const [drilldown, setDrilldown] = useState<{ open: boolean; loading: boolean; title: string; data: DrilldownResponse | null } | null>(null);
  const [report, setReport] = useState<{ open: boolean; title: string; loading: boolean; markdown: string; meta?: string } | null>(null);

  // 進頁載入摘要與報表
  useEffect(() => {
    void (async () => {
      const [summary, reportResult] = await Promise.all([fetchDashboard(), fetchDashboardReports()]);
      setDashboard(summary);
      setReports(reportResult);
    })();
  }, []);

  const riskCounts = useMemo<Record<string, number>>(() => ({}), []);

  /** 開啟圖表下鑽明細 Modal。 */
  async function openDrilldown(type: string, key: string, title: string) {
    setDrilldown({ open: true, loading: true, title, data: null });
    try {
      const data = await fetchDrilldown(type, key);
      setDrilldown({ open: true, loading: false, title, data });
    } catch (e) {
      console.error("下鑽明細載入失敗:", e);
      setDrilldown({ open: true, loading: false, title, data: null });
    }
  }

  /** 從下鑽明細跳到操作頁的指定客戶。 */
  function jumpToCustomer(id: number) {
    setDrilldown(null);
    navigate(`/customers/${id}`);
  }

  /** 開啟 Portfolio 全公司整體評估報告。 */
  async function openPortfolioAssessment() {
    setReport({ open: true, title: "Portfolio 整體評估（全公司）", loading: true, markdown: "" });
    try {
      const result = await fetchPortfolioAssessment();
      setReport({
        open: true,
        title: "Portfolio 整體評估（全公司）",
        loading: false,
        markdown: result.assessment,
        meta: `客戶 ${result.customerCount} · 高風險 ${result.highRiskCount} · 商機總額 ${formatMoney(result.totalPipeline)} · 活躍商機 ${result.activeOpportunityCount}`
      });
    } catch (e) {
      console.error("Portfolio 整體評估失敗:", e);
      setReport({ open: true, title: "Portfolio 整體評估（全公司）", loading: false, markdown: "⚠️ 產生評估失敗，請稍後再試。" });
    }
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>儀表板</h2>
        </div>
        <button type="button" className="btn-assess topbar-assess" onClick={openPortfolioAssessment}>📊 整體評估（全公司）<AiBadge onDark /></button>
      </section>

      <DashboardCards dashboard={dashboard} riskCounts={riskCounts} />
      <ReportsSection reports={reports} onDrill={openDrilldown} onSelectCustomer={jumpToCustomer} />

      {report?.open ? <ReportModal report={report} onClose={() => setReport(null)} /> : null}
      {drilldown?.open ? <DrilldownModal state={drilldown} onSelectCustomer={jumpToCustomer} onClose={() => setDrilldown(null)} /> : null}
    </>
  );
}
```

> 註：`DashboardCards` 的 `highRiskCount` 在原碼可由 `dashboard.highRiskCount` 提供（見 `App.tsx:801`），故 `riskCounts` 傳空物件即可維持行為（fallback 用），不需在儀表板頁再算客戶風險分佈。

- [ ] **Step 2: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: DashboardPage 不報錯。

---

## Task 11：組裝操作頁 `features/customers/CustomersPage.tsx`

**Files:**
- Create: `frontend/src/features/customers/CustomersPage.tsx`
- 來源參考：載入/篩選/分頁 `App.tsx:168-190,226-231`、selectCustomer `App.tsx:405-418`、新增客戶/互動 `App.tsx:236-250`、客戶評估 `App.tsx:316-333`、搜尋列 `App.tsx:451-467`、action-bar `App.tsx:472-475`、workspace-grid `App.tsx:477-483`、AI 聊天掛載 `App.tsx:489-505`

- [ ] **Step 1: 建立 `CustomersPage.tsx`**

```typescript
import { FormEvent, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  addInteraction, createCustomer, fetchAgentTrace, fetchCustomerAssessment,
  fetchCustomerDetail, fetchCustomers
} from "../../api";
import type { AgentTraceResponse, CustomerDetail, CustomerSummary } from "../../types";
import { useAuth } from "../../context/AuthContext";
import { useAiChat } from "../ai-assistant/useAiChat";
import { ReportModal } from "../../components/common/ReportModal";
import { CustomerList } from "./components/CustomerList";
import { Pagination } from "./components/Pagination";
import { CustomerDetailPanel } from "./components/CustomerDetailPanel";
import { AddCustomerModal } from "./components/AddCustomerModal";
import { AddInteractionModal } from "./components/AddInteractionModal";
import { ChatLauncher } from "../ai-assistant/components/ChatLauncher";
import { ChatWindow } from "../ai-assistant/components/ChatWindow";

/**
 * 操作頁（做事）：搜尋/篩選 + 客戶列表 + 詳情 + 商機看板 + 互動 + AI 助理。
 * 函式級註解：選取客戶以 URL :id 為單一真實來源；切換 :id 時載入詳情與 Agent Trace。
 */
export function CustomersPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { id } = useParams();
  const selectedId = id ? Number(id) : undefined;

  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [selected, setSelected] = useState<CustomerDetail | null>(null);
  const [trace, setTrace] = useState<AgentTraceResponse | null>(null);
  const [keyword, setKeyword] = useState("");
  const [industry, setIndustry] = useState("");
  const [owner, setOwner] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [showAddCustomer, setShowAddCustomer] = useState(false);
  const [showAddInteraction, setShowAddInteraction] = useState(false);
  const [report, setReport] = useState<{ open: boolean; title: string; loading: boolean; markdown: string; meta?: string } | null>(null);

  const { messages, chatSending, chatOpen, setChatOpen, sendChat, resetChat } = useAiChat();

  /** 載入客戶列表（支援篩選與分頁）。 */
  async function loadCustomers(overrides?: { keyword?: string; industry?: string; owner?: string; page?: number }) {
    setLoading(true);
    try {
      const list = await fetchCustomers({
        keyword: overrides?.keyword ?? keyword,
        industry: overrides?.industry ?? industry,
        owner: overrides?.owner ?? owner,
        page: overrides?.page ?? page,
        size: 10
      });
      setCustomers(list.items);
      setTotalPages(list.totalPages);
      setTotalElements(list.totalElements);
      // 若尚未選任何客戶且有第一筆，導向第一筆詳情
      if (!selectedId && list.items[0]) {
        navigate(`/customers/${list.items[0].id}`, { replace: true });
      }
    } finally {
      setLoading(false);
    }
  }

  // 進頁載入列表（一次）
  useEffect(() => {
    void loadCustomers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // :id 變動 → 載入詳情與 Trace、重置對話
  useEffect(() => {
    if (!selectedId) {
      setSelected(null);
      setTrace(null);
      return;
    }
    setLoading(true);
    resetChat();
    void (async () => {
      try {
        const [detail, traceResult] = await Promise.all([fetchCustomerDetail(selectedId), fetchAgentTrace(selectedId)]);
        setSelected(detail);
        setTrace(traceResult);
      } catch (e) {
        console.error("載入客戶詳情或 Trace 失敗:", e);
      } finally {
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  /** 搜尋（重置頁碼）。 */
  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    await loadCustomers({ keyword, industry, owner, page: 0 });
  }

  /** 選取客戶（改 URL）。 */
  function selectCustomer(cid: number) {
    navigate(`/customers/${cid}`);
  }

  /** 新增客戶後重載列表。 */
  async function handleCreateCustomer(data: { name: string; email: string; phone: string; taxId: string; industry: string; ownerName: string }) {
    await createCustomer(data);
    setShowAddCustomer(false);
    await loadCustomers();
  }

  /** 新增互動後重載詳情。 */
  async function handleAddInteraction(data: { type: string; occurredAt: string; content: string }) {
    if (!selected) return;
    await addInteraction(selected.customer.id, data);
    setShowAddInteraction(false);
    // 重新載入目前客戶詳情
    const detail = await fetchCustomerDetail(selected.customer.id);
    setSelected(detail);
  }

  /** 商機階段變更（樂觀更新本地）。 */
  function handleStageChange(opportunityId: number, newStage: string) {
    setSelected((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        opportunities: prev.opportunities.map((opp) =>
          opp.id === opportunityId ? { ...opp, stage: newStage as any } : opp
        )
      };
    });
  }

  /** 開啟目前客戶 360° 整體評估。 */
  async function openCustomerAssessment() {
    if (!selected) return;
    const name = selected.customer.name;
    setReport({ open: true, title: `整體評估 — ${name}`, loading: true, markdown: "" });
    try {
      const result = await fetchCustomerAssessment(selected.customer.id);
      setReport({
        open: true,
        title: `整體評估 — ${name}`,
        loading: false,
        markdown: result.answer,
        meta: `流失風險 ${result.risk.churnRisk} · 續約延遲 ${result.risk.renewalDelayRisk}`
      });
    } catch (e) {
      console.error("客戶整體評估失敗:", e);
      setReport({ open: true, title: `整體評估 — ${name}`, loading: false, markdown: "⚠️ 產生評估失敗，請稍後再試。" });
    }
  }

  /** 開啟 AI 聊天（需先選客戶）。 */
  function openChat() {
    if (!selected) return;
    setChatOpen(true);
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>客戶工作台</h2>
        </div>
        <form className="search-box" onSubmit={handleSearch}>
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜尋客戶名稱" />
          <select value={industry} onChange={(e) => setIndustry(e.target.value)}>
            <option value="">全部產業</option>
            <option value="雲端服務">雲端服務</option>
            <option value="物聯網">物聯網</option>
            <option value="金融科技">金融科技</option>
            <option value="醫療科技">醫療科技</option>
          </select>
          <select value={owner} onChange={(e) => setOwner(e.target.value)}>
            <option value="">全部業務</option>
            {[...new Set(customers.map((c) => c.ownerName))].map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
          <button type="submit">搜尋</button>
        </form>
      </section>

      <div className="action-bar">
        <button type="button" onClick={() => setShowAddCustomer(true)}>+ 新增客戶</button>
        {selected ? <button type="button" onClick={() => setShowAddInteraction(true)}>+ 新增互動</button> : null}
      </div>

      <div className="workspace-grid">
        <div className="customer-col">
          <CustomerList customers={customers} selectedId={selectedId} onSelect={selectCustomer} loading={loading} />
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={(p) => { setPage(p); void loadCustomers({ page: p }); }} />
        </div>
        <CustomerDetailPanel detail={selected} loading={loading} trace={trace} onStageChange={handleStageChange} onOpenChat={openChat} onAssess={openCustomerAssessment} userRole={user?.role} />
      </div>

      <ChatLauncher open={chatOpen} unread={messages.length} customerName={selected?.customer.name} onOpen={openChat} />
      {chatOpen ? (
        <ChatWindow
          customer={selected?.customer ?? null}
          messages={messages}
          sending={chatSending}
          onSend={(msg) => { if (selected) sendChat(selected.customer.id, msg); }}
          onClose={() => setChatOpen(false)}
        />
      ) : null}
      {report?.open ? <ReportModal report={report} onClose={() => setReport(null)} /> : null}
      {showAddCustomer ? <AddCustomerModal onSubmit={handleCreateCustomer} onClose={() => setShowAddCustomer(false)} /> : null}
      {showAddInteraction && selected ? <AddInteractionModal customerName={selected.customer.name} onSubmit={handleAddInteraction} onClose={() => setShowAddInteraction(false)} /> : null}
    </>
  );
}
```

- [ ] **Step 2: 驗證型別**

Run: `$env:Path = "D:\nodejs;$env:Path"; pnpm exec tsc --noEmit`
Expected: CustomersPage 不報錯。

---

## Task 12：改寫 `App.tsx` 路由表與 `main.tsx`

**Files:**
- Modify: `frontend/src/App.tsx`（整檔取代為路由表）
- Modify: `frontend/src/main.tsx`（包 BrowserRouter + AuthProvider）

- [ ] **Step 1: 整檔取代 `App.tsx`**

```typescript
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./app/AppShell";
import { ProtectedRoute } from "./app/ProtectedRoute";
import { LoginPage } from "./features/auth/LoginPage";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { CustomersPage } from "./features/customers/CustomersPage";

/**
 * 應用路由表：登入頁公開；其餘頁面需登入並套用 AppShell（側邊欄 + Outlet）。
 * 函式級註解：儀表板（/dashboard）與操作（/customers）為兩個獨立路由頁，達成儀表板與操作分頁。
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route path="/customers/:id" element={<CustomersPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
```

- [ ] **Step 2: 改寫 `main.tsx`**

讀現有 `main.tsx`（14 行），將 `<App />` 包進 `BrowserRouter` 與 `AuthProvider`：
```typescript
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>
);
```
> 註：若現有 `main.tsx` 的 root 取得或 CSS import 路徑不同，沿用現有寫法，只新增 `BrowserRouter` 與 `AuthProvider` 包裹。

- [ ] **Step 3: 完整建置驗證**

Run（工作目錄 frontend）：
```powershell
$env:Path = "D:\nodejs;$env:Path"
pnpm run build
```
Expected: `tsc -b` 與 `vite build` 皆成功，無型別錯誤、無未使用匯入錯誤。若報「App.tsx 仍有殘留舊元件未使用」之類錯誤，確認 App.tsx 已整檔取代。

---

## Task 13：Playwright 煙霧測試

**Files:**
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/sp1-smoke.spec.ts`

- [ ] **Step 1: 建立 `playwright.config.ts`**

```typescript
import { defineConfig } from "@playwright/test";

/**
 * Playwright 設定：對本機 dev server 跑 SP1 煙霧測試。
 * 函式級註解：自動啟動 vite dev（127.0.0.1:5173），測試前等待就緒。
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 30000,
  use: { baseURL: "http://127.0.0.1:5173" },
  webServer: {
    command: "pnpm run dev",
    url: "http://127.0.0.1:5173",
    reuseExistingServer: true,
    timeout: 60000
  }
});
```
> 註：請確認 vite dev 實際 port（預設 5173）。若 `vite.config` 指定其他 port，兩處同步調整。後端需另行啟動（見專案根 `start-crm.ps1`）。

- [ ] **Step 2: 建立 `e2e/sp1-smoke.spec.ts`**

```typescript
import { test, expect } from "@playwright/test";

/**
 * SP1 煙霧測試：驗證重構後主動線行為不變。
 * 前置：後端需已啟動（start-crm.ps1），seed 帳號 sales@aurora.local / password123。
 */
test("未登入導向登入頁、登入後可在儀表板與客戶頁間切換並操作", async ({ page }) => {
  // 1. 未登入 → /login
  await page.goto("/");
  await expect(page).toHaveURL(/\/login/);

  // 2. 登入 → /dashboard
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.locator(".metric-grid")).toBeVisible();
  await expect(page.locator(".report-grid")).toBeVisible();

  // 3. 圖表下鑽 Modal 可開可關
  await page.locator('[data-promo-chart="industry"] .bar-row').first().click();
  await expect(page.locator(".modal-overlay")).toBeVisible();
  await page.locator(".report-footer button").click();
  await expect(page.locator(".modal-overlay")).toHaveCount(0);

  // 4. 側邊欄切到客戶頁
  await page.click('text=客戶工作台');
  await expect(page).toHaveURL(/\/customers/);
  await expect(page.locator(".customer-list")).toBeVisible();

  // 5. 點客戶 → URL 變 /customers/:id，詳情顯示
  await page.locator(".customer-row").first().click();
  await expect(page).toHaveURL(/\/customers\/\d+/);
  await expect(page.locator(".customer-hero")).toBeVisible();
  await expect(page.locator(".trace-panel")).toBeVisible();

  // 6. 開 AI 聊天視窗
  await page.locator(".hero-actions >> text=詢問 AI 助理").click();
  await expect(page.locator(".chat-window")).toBeVisible();
  await page.locator(".chat-close").click();

  // 7. 開新增互動 Modal
  await page.locator('text=+ 新增互動').click();
  await expect(page.locator(".modal-content")).toBeVisible();
  await page.locator('.modal-actions >> text=取消').click();

  // 8. 登出 → /login
  await page.locator('.user-card button').click();
  await expect(page).toHaveURL(/\/login/);
});
```

- [ ] **Step 3: 執行煙霧測試**

前置：另開終端啟動後端（專案根 `start-crm.ps1` 或既有方式）。
Run（工作目錄 frontend）：
```powershell
$env:Path = "D:\nodejs;$env:Path"
pnpm exec playwright test
```
Expected: 1 passed。若失敗，逐條對照 selector 與實際 DOM（class 名稱應與 styles.css 一致）。

---

## Task 14：更新進度追蹤

**Files:**
- Modify: `docs/roadmap-progress.md`

- [ ] **Step 1: 標記 SP1 完成**

將 `docs/roadmap-progress.md` 的「目前在」改為下一個 SP2，SP1 列狀態改 `✅ 完成`、plan 欄填本計畫路徑，並在變更紀錄追加完成日期。

- [ ] **Step 2: 最終驗證**

Run（工作目錄 frontend）：
```powershell
$env:Path = "D:\nodejs;$env:Path"
pnpm run build
pnpm exec playwright test
```
Expected: build 成功 + 煙霧測試 1 passed。

---

## 自我審查結果（spec 覆蓋對照）

- spec §2 路由表 → Task 12（App.tsx Routes）涵蓋全部 5 條路由 ✅
- spec §3 頁面歸屬 → Task 5（dashboard）/ Task 6（customers）/ Task 4（AI）/ Task 3（trace）✅
- spec §4 目錄結構 → Task 1–12 逐一建立，與結構總覽一致 ✅
- spec §5 狀態歸位 → AuthContext（Task 7）、DashboardPage 區域態（Task 10）、CustomersPage 區域態 + URL :id（Task 11）、useAiChat（Task 4）✅
- spec §6 資料流與 401 → api.ts 事件 + AuthContext 監聽（Task 7）、登入導向（Task 9）、:id 載入（Task 11）✅
- spec §7 錯誤處理 → HealthBadge fail-closed（Task 8）、各頁 loading/空狀態（沿用搬移元件）、401 導向（Task 7+ProtectedRoute Task 8）✅
- spec §8 測試驗證 → build 關卡（各任務）+ Playwright 煙霧（Task 13）✅
- spec §9 套件異動 → Task 0 ✅
- 型別一致性：`ChatMessage` 統一由 `useAiChat` 匯出、`DrillFn` 由 `ReportsSection` 匯出，跨檔引用一致 ✅
- 無 git commit 步驟（專案非 git repo），改以 build 為關卡 ✅
```
