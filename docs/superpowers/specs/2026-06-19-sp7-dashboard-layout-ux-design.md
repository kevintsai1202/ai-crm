# SP7 儀表板版面 UX 強化 — 設計規格

> 依據使用者需求：①固定高度 + 清單分頁、②下鑽麵包屑、③儀表板區塊拖拉排序 + 關閉 + 抽屜還原（個人帳號儲存）。
> 日期：2026-06-19

## 目標

讓儀表板與操作頁的版面穩定、可導覽、可個人化：各區塊有固定高度（資料多時清單分頁、圖表內部處理），下鑽到客戶時有麵包屑可回到來源區塊，且使用者可像 portal 一樣拖拉排序、關閉區塊，並透過抽屜把隱藏的區塊加回——偏好以個人帳號層級儲存於後端，跨裝置一致。

## 範圍與決策（已與使用者確認）

| 項目 | 決策 |
|---|---|
| 固定高度策略 | 混合：清單類用前端分頁（每頁固定 5 筆），純圖表用固定高度 |
| 麵包屑 | 來源區塊 + 返回（記憶下鑽來源區塊，可點回並捲到原區塊） |
| 自訂排序範圍 | 僅儀表板大區塊；順序與顯隱存後端（個人帳號） |
| 排序操作 | 原生 HTML5 拖拉拖曳（drag handle，不加套件） |
| 關閉/抽屜 | 區塊可關閉；右側抽屜列出隱藏區塊並可加回；附「還原預設」 |
| 後端儲存 | 方案 A：泛用 `user_preferences` 表，本次 key=`dashboard_layout` |

非目標（YAGNI）：操作頁（CustomersPage）區塊不納入拖拉排序；隱藏區塊不記憶原本位置（加回一律 append 尾端）；主題/其他偏好不在本次實作（但表結構需可擴充）。

---

## 第 1 段：固定高度 + 清單分頁

### 1.1 CSS 高度系統（`frontend/src/styles.css`）

- **數據卡 `.metric-card`**：補 `min-height` 統一為固定高度，四張齊高。
- **純圖表卡**（CRM 報表 `.report-card`、意圖分布 `.sr-card`、情緒趨勢）：改為固定高度 `height: 320px` + `display:flex; flex-direction:column`；標題列固定，圖表/內容區 `flex:1; min-height:0`，必要時 `overflow:hidden`。寬卡 `.report-card.wide` 同高度。
- **清單卡**（高風險互動、流失雷達、優先關懷）：固定高度 = 標題列 + 5 列固定高度 + 分頁列；資料超過 5 筆用前端分頁（見 1.2），不撐高卡片。

### 1.2 新元件 `frontend/src/components/common/PaginatedList.tsx`

通用前端分頁清單，供 SP6 三個清單與未來清單共用。

```tsx
interface PaginatedListProps<T> {
  items: T[];                      // 全部資料
  pageSize?: number;               // 每頁筆數，預設 5
  renderRow: (item: T, index: number) => React.ReactNode;  // 單列渲染
  emptyText?: string;              // 無資料時顯示文字
  rowKey: (item: T, index: number) => string;              // React key
}
```

行為：
- 內部 `page` state（0 起算）。`totalPages = Math.ceil(items.length / pageSize)`。
- 顯示當頁切片 `items.slice(page*pageSize, (page+1)*pageSize)`。
- 底部分頁列（沿用既有 `.pagination` 樣式）：「上一頁 / 第 X / N 頁 / 下一頁」，邊界禁用。`items.length <= pageSize` 時不顯示分頁列。
- `items` 長度變動且當前 `page` 超界時，自動夾回最後一頁（`useEffect` 修正）。
- 無資料時顯示 `emptyText`（預設「尚無資料」），沿用 `.sr-empty`。

### 1.3 套用點

- `SentimentRadarSection.tsx` 的高風險互動、流失雷達、優先關懷三個 `.sr-list` 改用 `<PaginatedList>`（pageSize=5）。意圖分布、情緒趨勢維持原樣（圖表固定高度）。
- 客戶列表（`CustomersPage`）已有後端分頁（size:10），保留；僅在 `.customer-col` 補固定高度避免切換時抖動（CSS）。

---

## 第 2 段：下鑽麵包屑（來源區塊 + 返回）

### 2.1 新元件 `frontend/src/components/common/Breadcrumb.tsx`

```tsx
interface Crumb {
  label: string;                   // 顯示文字
  onClick?: () => void;            // 有 onClick 即為可點層級；最後一層通常無
}
interface BreadcrumbProps { crumbs: Crumb[]; }
```

純呈現元件：以 `›` 分隔逐層渲染；有 `onClick` 的層級為按鈕樣式（可點），無 `onClick` 為當前頁文字。新增 CSS class `.breadcrumb`、`.breadcrumb-link`、`.breadcrumb-sep`、`.breadcrumb-current`。

### 2.2 來源傳遞（React Router location state）

下鑽來源資訊型別（定義於 `frontend/src/types.ts`）：

```ts
export interface DrilldownSource {
  from: "dashboard";               // 來源頁
  section: string;                 // 來源區塊中文名，如「流失雷達」
  blockId: string;                 // 來源區塊 id，用於返回時捲動定位
}
```

所有儀表板下鑽到客戶的入口，`navigate` 改帶 state：

```tsx
navigate(`/customers/${id}`, { state: { from: "dashboard", section: "流失雷達", blockId: "sentiment-radar" } as DrilldownSource });
```

涉及檔案與區塊名/blockId：
- `SentimentRadarSection.tsx`：高風險互動 / 流失雷達 / 優先關懷三清單 → section 各為「高風險互動」「流失雷達」「優先關懷」，blockId 統一 `sentiment-radar`（同一大區塊）。
- `RfmSection.tsx`：section「RFM 客戶分群」，blockId `rfm`。
- `DrilldownModal.tsx`（經 `DashboardPage.jumpToCustomer`）：section 用下鑽標題（drilldown.label），blockId `reports`。
- `ReportsSection`：經由 DrilldownModal，故同上。

`onSelectCustomer`/`jumpToCustomer` 簽章擴充為可帶 `DrilldownSource`，由各區塊提供。

### 2.3 麵包屑呈現與返回（`CustomersPage.tsx`）

- 以 `useLocation().state` 取得 `DrilldownSource`。
- **有來源**：CustomersPage 頂部（topbar 之上）顯示 `儀表板 › {section} › {客戶名}`。
  - 「儀表板」與「{section}」皆可點 → `navigate('/dashboard', { state: { scrollTo: blockId } })`。
  - 最後一層「{客戶名}」為當前，不可點。
- **無來源**（直接進 /customers 或重整）：不顯示麵包屑（state 在重整後消失即視為無來源，可接受）。

### 2.4 返回後捲動定位（`DashboardPage.tsx`）

- 每個大區塊容器加 `id`（對應 blockId，如 `id="block-sentiment-radar"`）。
- DashboardPage 掛載時讀 `useLocation().state?.scrollTo`，若有則於資料載入後 `document.getElementById('block-'+scrollTo)?.scrollIntoView({ behavior:'smooth', block:'start' })`，並清掉 state 避免重複觸發。

---

## 第 3 段：儀表板區塊拖拉排序 + 關閉 + 抽屜還原

### 3.1 區塊目錄（catalog）

DashboardPage 內定義固定的區塊目錄（單一真實來源），每項：

```ts
interface DashboardBlock {
  id: string;                      // 穩定識別，如 'metrics' | 'reports' | 'rfm' | 'sentiment-radar' | 'usage'
  title: string;                   // 抽屜顯示用中文名
  render: () => React.ReactNode;   // 渲染該區塊
  requireUsageRole?: boolean;      // usage 區塊僅 MANAGER/ADMIN
}
```

預設順序：`['metrics', 'reports', 'rfm', 'sentiment-radar', 'usage']`。

### 3.2 資料模型：可見有序陣列

- 個人偏好只存「**可見區塊 id 的有序陣列**」`visibleOrder: string[]`。
- 隱藏區塊 = catalog 全部 id（依角色過濾後）扣掉 `visibleOrder`。
- 關閉 = 從 `visibleOrder` 移除；加回 = append 到 `visibleOrder` 尾端；拖拉 = 重排 `visibleOrder`。
- 角色過濾：非 MANAGER/ADMIN 的使用者，catalog 不含 `usage`，故其 visibleOrder 與隱藏清單都不會出現 usage（後端存的 id 若含 usage，前端渲染時按 catalog 過濾即可，不報錯）。

### 3.3 後端

**Migration**（`backend/src/main/resources/db/migration/V9__user_preferences.sql`）：

```sql
create table user_preferences (
  id          bigserial primary key,
  user_id     bigint      not null references users(id),
  pref_key    varchar(64) not null,
  pref_value  text        not null,
  updated_at  timestamptz not null default now(),
  constraint uq_user_pref unique (user_id, pref_key)
);
```

**Entity**：`domain/UserPreference.java`（id, userId, prefKey, prefValue, updatedAt）。

**Repository**：`repository/UserPreferenceRepository.java`，`Optional<UserPreference> findByUserIdAndPrefKey(Long userId, String prefKey)`。

**Service**：`service/UserPreferenceService.java`
- `String get(Long userId, String key)`：回 prefValue 或 null。
- `void put(Long userId, String key, String value)`：upsert（存在則更新 value+updatedAt，否則新建）。

**Controller**：`api/MeController.java`，base `/api/me/preferences`
- `GET /dashboard-layout` → 回 `DashboardLayoutResponse { visibleOrder: string[] }`；無偏好時回 `null`（前端套預設）。
- `PUT /dashboard-layout`，body `DashboardLayoutRequest { visibleOrder: string[] }` → upsert，回 200。
- userId 取自 SecurityContext（限本人；任何登入角色皆可）。
- prefValue 以 Jackson 3（`tools.jackson`）序列化 `visibleOrder` 為 JSON 字串存 text 欄。

**Security**：`/api/me/**` 設為 authenticated（置於 `/api/ai/**` 等既有規則相容位置；任何已登入角色可用）。

### 3.4 前端

**api.ts**：
- `fetchDashboardLayout(): Promise<string[] | null>` → GET，回 `visibleOrder` 或 null。
- `saveDashboardLayout(visibleOrder: string[]): Promise<void>` → PUT。

**types.ts**：`DashboardLayoutResponse { visibleOrder: string[] }`。

**DashboardPage.tsx**：
- 進頁 `fetchDashboardLayout()`：有值用之（再經 catalog 過濾移除不存在/無權限 id），無值用預設順序。
- 依 `visibleOrder` 對映 catalog 渲染各區塊；每個區塊包一層含 `id="block-{id}"` 的容器 + 區塊頭工具列（drag handle ⠿ + 關閉鈕 ✕）。
- **拖拉**：原生 HTML5 DnD。容器 `draggable`，`onDragStart` 記來源 index、`onDragOver` preventDefault、`onDrop` 計算目標 index 重排 `visibleOrder`，更新 state 後 `saveDashboardLayout`。拖拉中加 `.dragging` 視覺。
- **關閉**：✕ → 從 `visibleOrder` 移除 → state + 存檔。
- 任何變更採「先更新本地 state，再 fire-and-forget PUT」；PUT 失敗僅 console.error（不回滾，下次進頁以後端為準），符合既有專案模式。

**抽屜元件** `features/dashboard/components/LayoutDrawer.tsx`：
- 觸發：topbar 加按鈕「⊞ 版面（隱藏 {n}）」（n=隱藏數）。
- 開啟：右側滑入抽屜（`position:fixed` + 遮罩，沿用 `.modal-overlay` 概念，新增 `.drawer` 樣式）。
- 內容：列出所有隱藏區塊為卡片（title），各帶「＋ 加回」鈕 → append 到 `visibleOrder` + 存檔。
- 底部「還原預設順序」鈕 → `visibleOrder` 設回預設（依角色過濾）+ 存檔。
- 無隱藏區塊時顯示「目前所有區塊都已顯示」。

### 3.5 CSS（styles.css）

新增：`.block-toolbar`（區塊頭工具列）、`.block-drag-handle`、`.block-close`、`.block-wrapper.dragging`、`.drawer`、`.drawer-overlay`、`.drawer-item`、`.layout-btn`。

---

## 元件邊界總覽

| 檔案 | 職責 | 相依 |
|---|---|---|
| `components/common/PaginatedList.tsx` | 通用前端分頁清單 | 無（純 UI） |
| `components/common/Breadcrumb.tsx` | 純呈現麵包屑 | 無（純 UI） |
| `features/dashboard/components/LayoutDrawer.tsx` | 隱藏區塊抽屜 | api（save） |
| `domain/UserPreference.java` | 偏好實體 | JPA |
| `repository/UserPreferenceRepository.java` | 偏好查詢 | Spring Data |
| `service/UserPreferenceService.java` | upsert/get | repository |
| `api/MeController.java` | `/api/me/preferences/dashboard-layout` | service, security |

## 測試策略

- **後端**：`UserPreferenceServiceTest`（upsert 新建/更新、get 命中/未命中）；`MeControllerTest`（GET 無偏好回 null、PUT 後 GET 取回、未登入 401、A 使用者不可讀 B 偏好）。沿用既有 Testcontainers/MockMvc 模式。
- **前端**：`pnpm exec tsc --noEmit` + `pnpm build` 綠燈。
- **E2E（擴充 `e2e/sp1-smoke.spec.ts` 或新增 sp7 spec）**：
  - 清單分頁：高風險互動超過 5 筆時出現分頁列、可翻頁。
  - 麵包屑：儀表板流失雷達點客戶 → 客戶頁出現「儀表板 › 流失雷達 › 客戶名」→ 點「儀表板」回到並捲到該區塊。
  - 拖拉/關閉/抽屜：關閉一個區塊 → 消失且抽屜計數 +1 → 開抽屜加回 → 重新出現。（拖拉排序若 DnD 在 Playwright 不穩，改以「還原預設」與關閉/加回覆蓋核心存取邏輯。）

## 實作順序

1. 第 1 段（固定高度 + PaginatedList）— 純前端，獨立可測。
2. 第 2 段（麵包屑 + 返回捲動）— 純前端。
3. 第 3 段（後端 user_preferences → API → 前端拖拉/關閉/抽屜）— 全端。

每段獨立可上線，互不阻擋。
