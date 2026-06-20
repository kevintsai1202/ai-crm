# SP1 設計規格：前端架構重構（側邊欄 + React Router 多頁、儀表板↔操作分頁）

> 子專案：SP1（路線圖見 `docs/roadmap-progress.md`）
> 建立日期：2026-06-19
> 依據：`docs/consulting-review.md`（梯次一：拆 App.tsx + 上路由）、`docs/crm-ai-consultant-analysis.md`（第八點：前端單檔負擔過重）
> 架構方案：**A — React Router + Context 放全域態 + 各頁區域 state**（不引入 Zustand）

---

## 1. 目標與成功標準

**目標**：把 1337 行的單檔 `App.tsx` 重構成以 React Router 驅動的多頁應用，將「儀表板（看數據）」與「操作（做事）」拆到不同路由頁，並把元件依 `features/` 切分。

**成功標準**：
1. 儀表板與操作分屬不同路由頁（`/dashboard` 與 `/customers`），由側邊欄切換，不再同頁堆疊。
2. **行為不變**：登入、客戶列表/篩選/分頁、客戶詳情、互動時間線、商機看板拖拽、AI 聊天串流、整體評估、圖表下鑽、Agent Trace、角色按鈕全部維持原行為。
3. `pnpm run build`（tsc 型別檢查）通過。
4. Playwright 煙霧測試通過（涵蓋主動線）。
5. `App.tsx` 瘦身為僅含路由表（~50 行）。

**非目標（明確排除，避免範圍蔓延）**：
- 不導入單元測試框架 vitest（屬 SP2）。
- 不引入 Zustand 或其他狀態管理庫。
- 不新增任何功能、不改後端、不改 API 合約。
- 不重寫 `styles.css`（沿用現有 class 名稱）。

---

## 2. 路由表（react-router-dom v7）

| 路徑 | 頁面 | 內容 | 守衛 |
|------|------|------|------|
| `/login` | 登入頁 | `LoginPanel`；已登入自動導向 `/dashboard` | 公開 |
| `/` | — | redirect → `/dashboard` | — |
| `/dashboard` | 儀表板頁（看數據） | 統計卡 + 7 張圖表 + 下鑽 Modal + Portfolio 整體評估 | 需登入 |
| `/customers` | 操作頁（做事） | 搜尋/篩選 + 客戶列表（master）+ 空白詳情提示 | 需登入 |
| `/customers/:id` | 操作頁（選中客戶） | 同上 + 詳情（hero/時間線/商機看板/Trace）+ 新增互動 + 評估 + AI 聊天 | 需登入 |

選取客戶以 URL `:id` 為單一真實來源（重整不失憶、可分享連結）。

---

## 3. 頁面歸屬

- **儀表板頁**：`DashboardCards`、`ReportsSection`（含 `PipelineFunnel`、`MonthlyForecastChart`、`IndustryBreakdown`、`RiskBreakdown`、`RenewalForecast`、`OwnerLeaderboard`、`ActivityReportList`）、`DrilldownModal`、Portfolio 整體評估鈕 + `ReportModal`。
- **操作頁**：搜尋列、`CustomerList`、`Pagination`、`CustomerDetailPanel`、`Timeline`、`OpportunityBoard`（含 `KanbanColumn`/`KanbanCard`）、`AddCustomerModal`、`AddInteractionModal`、客戶 360° 評估鈕 + `ReportModal`、AI 浮動聊天（`ChatLauncher`/`ChatWindow`/`ChatBubble`，綁選取客戶）、`TracePanel`。
- **共用**：`AiBadge`、`ReportModal`、`DrilldownModal`、`HealthBadge`、`lib/format.ts` 格式化函式。

---

## 4. 目錄結構

```
frontend/src/
  main.tsx                 # 掛 <BrowserRouter>
  App.tsx                  # 瘦身：只剩 <Routes> 路由表（~50 行）
  api.ts                   # 僅補 401 導向事件
  types.ts / styles.css    # 不動
  context/
    AuthContext.tsx        # user/health/login/logout/refreshHealth（跨頁全域態）
  app/
    AppShell.tsx           # 側邊欄(品牌 + HealthBadge + user 卡 + 導覽連結) + <Outlet/>
    ProtectedRoute.tsx     # 未登入 → /login
  features/
    auth/
      LoginPage.tsx        # 包 LoginPanel
    dashboard/
      DashboardPage.tsx
      components/          # DashboardCards, ReportsSection, PipelineFunnel,
                          # MonthlyForecastChart, IndustryBreakdown, RiskBreakdown,
                          # RenewalForecast, OwnerLeaderboard, ActivityReportList
    customers/
      CustomersPage.tsx
      components/          # CustomerList, CustomerDetailPanel, Timeline,
                          # OpportunityBoard, KanbanColumn, KanbanCard,
                          # Pagination, AddCustomerModal, AddInteractionModal
    ai-assistant/
      useAiChat.ts         # 對話狀態 + SSE 串流邏輯
      components/          # ChatLauncher, ChatWindow, ChatBubble
    agent-trace/
      TracePanel.tsx
  components/common/
    AiBadge.tsx
    ReportModal.tsx
    DrilldownModal.tsx
  lib/
    format.ts              # formatMoney/formatCompactMoney/formatDateTime/riskLabel/stageLabel
```

---

## 5. 狀態歸位（方案 A）

- **AuthContext（全域）**：`user`、`health`、`healthError`、`login()`、`logout()`、`refreshHealth()`。被 `AppShell` 側邊欄與 `ProtectedRoute` 消費。token 仍透過 `api.ts` 的 `saveToken/getToken/clearToken`。
- **DashboardPage（區域）**：`dashboard`、`reports`、`drilldown`、portfolio `report`，進頁時 fetch。
- **CustomersPage（區域）**：`customers`、`filters(keyword/industry/owner)`、`page/totalPages/totalElements`、`selected(CustomerDetail)`、`trace`、各 Modal 開關、客戶 `report`。`selected` 由 `useParams().id` 驅動載入。
- **AI 對話**：`messages`、`chatSending`、`chatOpen` 抽進 `useAiChat()` hook，掛在操作頁。

---

## 6. 資料流與 401 處理

- **登入**：`LoginPage` → `AuthContext.login()` → `saveToken` → `navigate('/dashboard')`。
- **401 導向（新增）**：`api.ts` response 攔截器在 401 時除 `clearToken()` 外，`window.dispatchEvent(new Event('auth:logout'))`；`AuthContext` 以 `useEffect` 監聽該事件 → `setUser(null)`，`ProtectedRoute` 下次 render 導向 `/login`。SSE 版（`askAssistantStream`）401 時同樣 `clearToken()` 並派發事件。不在攔截器硬寫 `window.location`，保持 SPA 行為。
- **儀表板進頁**：`Promise.all([fetchDashboard(), fetchDashboardReports()])`。
- **操作頁**：`fetchCustomers(filters,page)`；`:id` 變動 → `Promise.all([fetchCustomerDetail(id), fetchAgentTrace(id)])`。
- **商機拖拽 / SSE 聊天 / 下鑽 / 評估**：邏輯不變，僅搬移位置。

---

## 7. 錯誤處理

- health 維持 **fail-closed**：無法連線顯示紅燈（沿用 `HealthBadge`）。
- 各頁各自 loading 與空狀態（沿用 skeleton-list / empty-state-box）。
- 401 → 導 `/login`。
- 登入失敗顯示錯誤框（沿用）。

---

## 8. 測試與驗證

- **行為不變** 為核心驗收標準（純結構重構）。
- `pnpm run build` 通過（含 tsc -b 型別檢查）。
- **Playwright 煙霧測試**（`frontend/e2e/sp1-smoke.spec.ts`，加中文註解，可重跑）驗證主動線：
  1. 開 `/` → 未登入導向 `/login`。
  2. 用 `sales@aurora.local / password123` 登入 → 導向 `/dashboard`，統計卡與圖表出現。
  3. 圖表下鑽 Modal 可開可關。
  4. 側邊欄切到「客戶」→ URL 變 `/customers`，列表出現。
  5. 點客戶 → URL 變 `/customers/:id`，詳情/時間線/商機看板/Trace/AI 入口顯示。
  6. 開 AI 聊天視窗、開新增互動 Modal。
  7. 登出 → 回 `/login`。
- 單元測試（vitest）留待 SP2。

---

## 9. 套件異動

- 新增：`react-router-dom@^7`（runtime 相依）。
- 新增（devDependencies，僅煙霧測試用）：`@playwright/test`（若專案尚未具備）。
- 移除：無。

---

## 10. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 拆檔過程悄悄改變行為 | Playwright 煙霧測試 + 逐元件原樣搬移（不改內部邏輯） |
| 共享狀態切錯造成資料不同步 | selected 一律以 URL `:id` 為準；全域態僅 auth/health |
| `styles.css` class 名稱對不上 | 沿用原 class，不改樣式 |
| SSE 串流搬移後失效 | `askAssistantStream` 與 `useAiChat` 行為對拍測試 |

---

## 11. 完成後

- 更新 `docs/roadmap-progress.md`：SP1 → ✅ 完成。
- 以 writing-plans 技能展開實作計畫。
