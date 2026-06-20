# SP7 儀表板版面 UX 強化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓儀表板各區塊固定高度（清單分頁、圖表固定高）、下鑽客戶有可返回的麵包屑、儀表板區塊可拖拉排序/關閉並用抽屜還原，偏好以個人帳號存後端。

**Architecture:** 三段獨立交付。第 1、2 段純前端（新增 PaginatedList、Breadcrumb 元件 + CSS 固定高度 + React Router location state 傳遞下鑽來源）。第 3 段全端：後端泛用 `user_preferences` 表 + `/api/me/preferences/dashboard-layout` API，前端把儀表板區塊抽成 catalog、存「可見有序陣列」、原生 HTML5 DnD 拖拉 + ✕ 關閉 + 右側抽屜加回。

**Tech Stack:** 後端 Spring Boot 4.1（Jackson 3 `tools.jackson`）、Flyway、JPA、Spring Security、JUnit5 + Testcontainers + MockMvc。前端 React 19 + TypeScript + Vite + react-router-dom v7，驗證用 `pnpm exec tsc --noEmit` + `pnpm build` + Playwright（無前端單元測試框架）。

**前端驗證慣例：** 本前端專案無單元測試框架，前端任務的「測試」= `pnpm exec tsc --noEmit` 通過（exit 0）+ `pnpm build` 成功；端到端行為在 Task 13 以 Playwright 一次驗證。Git Bash 環境需先 `export PATH="/d/nodejs:$PATH"`。

**參考依據：** 規格 `docs/superpowers/specs/2026-06-19-sp7-dashboard-layout-ux-design.md`。

---

## 檔案結構

| 檔案 | 動作 | 職責 |
|---|---|---|
| `frontend/src/components/common/PaginatedList.tsx` | 建立 | 通用前端分頁清單（固定每頁筆數） |
| `frontend/src/components/common/Breadcrumb.tsx` | 建立 | 純呈現麵包屑 |
| `frontend/src/features/dashboard/components/LayoutDrawer.tsx` | 建立 | 隱藏區塊抽屜 |
| `frontend/src/types.ts` | 修改 | 新增 `DrilldownSource`、`DashboardLayoutResponse` |
| `frontend/src/api.ts` | 修改 | 新增 `fetchDashboardLayout`、`saveDashboardLayout` |
| `frontend/src/styles.css` | 修改 | 固定高度系統 + 區塊工具列 + 抽屜樣式 + 麵包屑樣式 |
| `frontend/src/features/dashboard/components/SentimentRadarSection.tsx` | 修改 | 三清單改用 PaginatedList、帶下鑽 source |
| `frontend/src/features/dashboard/components/RfmSection.tsx` | 修改 | 帶下鑽 source |
| `frontend/src/features/dashboard/DashboardPage.tsx` | 修改 | 區塊 catalog + 拖拉/關閉 + scrollTo 返回定位 |
| `frontend/src/features/customers/CustomersPage.tsx` | 修改 | 顯示麵包屑 |
| `backend/src/main/resources/db/migration/V9__user_preferences.sql` | 建立 | user_preferences 表 |
| `backend/src/main/java/com/aicrm/crm/domain/UserPreference.java` | 建立 | 偏好實體 |
| `backend/src/main/java/com/aicrm/crm/repository/UserPreferenceRepository.java` | 建立 | 偏好查詢 |
| `backend/src/main/java/com/aicrm/crm/service/UserPreferenceService.java` | 建立 | upsert/get（依 username 解析 userId） |
| `backend/src/main/java/com/aicrm/crm/api/MeController.java` | 建立 | `/api/me/preferences/dashboard-layout` |
| `backend/src/main/java/com/aicrm/crm/api/Dtos.java` | 修改 | 新增 layout DTO |
| `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java` | 修改 | `/api/me/**` authenticated |
| `backend/src/test/java/com/aicrm/crm/service/UserPreferenceIntegrationTest.java` | 建立 | service upsert/get 測試 |
| `backend/src/test/java/com/aicrm/crm/api/MeControllerIntegrationTest.java` | 建立 | API 行為 + RBAC 測試 |
| `frontend/e2e/sp7-layout.spec.ts` | 建立 | 分頁 / 麵包屑 / 關閉+抽屜 E2E |

---

## 第 1 段：固定高度 + 清單分頁

### Task 1: PaginatedList 通用分頁清單元件

**Files:**
- Create: `frontend/src/components/common/PaginatedList.tsx`

- [ ] **Step 1: 建立元件**

```tsx
import { useEffect, useState } from "react";

/**
 * 通用前端分頁清單：把超量資料切成固定每頁筆數，底部提供上一頁/下一頁。
 * 函式級註解：純前端分頁（資料已在記憶體），用於儀表板清單卡固定高度；
 * 資料量小於等於 pageSize 時不顯示分頁列。
 */
interface PaginatedListProps<T> {
  /** 全部資料 */
  items: T[];
  /** 每頁筆數，預設 5 */
  pageSize?: number;
  /** 單列渲染 */
  renderRow: (item: T, index: number) => React.ReactNode;
  /** React key 產生器 */
  rowKey: (item: T, index: number) => string;
  /** 無資料時顯示文字 */
  emptyText?: string;
}

export function PaginatedList<T>({ items, pageSize = 5, renderRow, rowKey, emptyText = "尚無資料" }: PaginatedListProps<T>) {
  // 當前頁碼（0 起算）
  const [page, setPage] = useState(0);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));

  // 資料量變動導致當前頁超界時，夾回最後一頁，避免顯示空白頁
  useEffect(() => {
    if (page > totalPages - 1) setPage(totalPages - 1);
  }, [page, totalPages]);

  if (items.length === 0) {
    return <div className="sr-empty">{emptyText}</div>;
  }

  const start = page * pageSize;
  const pageItems = items.slice(start, start + pageSize);

  return (
    <div className="paginated-list">
      <div className="sr-list">
        {pageItems.map((item, i) => (
          <div key={rowKey(item, start + i)}>{renderRow(item, start + i)}</div>
        ))}
      </div>
      {items.length > pageSize ? (
        <div className="pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>上一頁</button>
          <span>第 {page + 1} / {totalPages} 頁</span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}>下一頁</button>
        </div>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0，無錯誤。

- [ ] **Step 3: Commit**

本專案非 git repo；若已初始化則 `git add frontend/src/components/common/PaginatedList.tsx && git commit -m "feat(sp7): add PaginatedList component"`，否則跳過 commit。

---

### Task 2: CSS 固定高度系統

**Files:**
- Modify: `frontend/src/styles.css`（新增於檔尾，避免動到既有規則）

- [ ] **Step 1: 追加固定高度與分頁清單樣式**

於 `frontend/src/styles.css` 檔尾追加：

```css
/* ===== SP7 固定高度系統 ===== */
/* 數據卡統一高度，四張齊高 */
.metric-card { min-height: 104px; }

/* 報表/圖表/清單卡統一固定高度，標題列固定、內容區填滿 */
.report-card.sr-card,
.report-card {
  height: 320px;
  display: flex;
  flex-direction: column;
}
.report-card .panel-title { flex: none; }
/* 內容主體填滿剩餘高度，內部需要時自行捲動 */
.report-card > .bar-list,
.report-card > .leaderboard,
.report-card > .activity-report,
.report-card > .sr-intent-list,
.report-card > .rfm-table,
.report-card .paginated-list,
.report-card .sr-list { min-height: 0; }

/* 分頁清單：清單區填滿、分頁列固定在卡片底部 */
.paginated-list { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.paginated-list .sr-list { flex: 1; overflow-y: auto; }
.paginated-list .pagination { flex: none; padding-top: 8px; }

/* 客戶列表欄固定高度，避免切換客戶時整頁抖動 */
.customer-col { max-height: calc(100vh - 220px); }
.customer-col .customer-list { overflow-y: auto; }
```

- [ ] **Step 2: 型別/建置檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm build`
Expected: build 成功（CSS 無語法錯誤）。

- [ ] **Step 3: Commit**

`git add frontend/src/styles.css && git commit -m "feat(sp7): fixed-height card system"`（非 git repo 則跳過）。

---

### Task 3: SentimentRadarSection 三清單改用 PaginatedList

**Files:**
- Modify: `frontend/src/features/dashboard/components/SentimentRadarSection.tsx`

- [ ] **Step 1: 匯入 PaginatedList 並把三個 `.sr-list` 改為分頁**

在檔首 import 區加入：

```tsx
import { PaginatedList } from "../../../components/common/PaginatedList";
```

將「視覺 3：高風險互動」的 `<div className="sr-list">...</div>`（含 length===0 判斷）整段替換為：

```tsx
        <PaginatedList
          items={data.highRiskInteractions}
          pageSize={5}
          emptyText="目前無高風險互動"
          rowKey={(item, i) => `${item.customerId}-${i}`}
          renderRow={(item) => (
            <button type="button" className="sr-row clickable" onClick={() => onSelectCustomer(item.customerId)}>
              <span className={`sr-dot ${sentimentClass(item.sentiment)}`} />
              <strong className="sr-row-name">{item.customerName}</strong>
              {intentLabel(item.intent) ? <span className="sr-tag">{intentLabel(item.intent)}</span> : null}
              <span className="sr-row-content">{item.content}</span>
              <em className="sr-row-time">{formatDateTime(item.occurredAt)}</em>
            </button>
          )}
        />
```

將「視覺 4：流失雷達」的 `<div className="sr-list">...</div>` 整段替換為：

```tsx
        <PaginatedList
          items={data.churnRadar}
          pageSize={5}
          emptyText="尚無流失風險客戶"
          rowKey={(row) => String(row.customerId)}
          renderRow={(row) => (
            <button type="button" className="sr-row sr-churn-row clickable" onClick={() => onSelectCustomer(row.customerId)}>
              <strong className="sr-row-name">{row.name}</strong>
              <span className="sr-churn-meta" title={`負面 ${row.negativeCount} · 流失 ${row.churnSignalCount} · 客訴 ${row.complaintCount}`}>
                負 {row.negativeCount} · 流 {row.churnSignalCount} · 訴 {row.complaintCount}
              </span>
              <b className="sr-score">{row.score}</b>
            </button>
          )}
        />
```

將「視覺 5：優先關懷」的 `<div className="sr-list">...</div>` 整段替換為：

```tsx
        <PaginatedList
          items={data.priorityCare}
          pageSize={5}
          emptyText="尚無優先關懷對象"
          rowKey={(row) => String(row.customerId)}
          renderRow={(row) => (
            <button type="button" className="sr-row sr-care-row clickable" onClick={() => onSelectCustomer(row.customerId)}>
              <strong className="sr-row-name">{row.name}</strong>
              <span className="sr-row-content">{row.reason}</span>
            </button>
          )}
        />
```

- [ ] **Step 2: 型別檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 3: Commit**

`git add frontend/src/features/dashboard/components/SentimentRadarSection.tsx && git commit -m "feat(sp7): paginate sentiment radar lists"`（非 git repo 則跳過）。

---

## 第 2 段：下鑽麵包屑

### Task 4: DrilldownSource 型別 + Breadcrumb 元件

**Files:**
- Modify: `frontend/src/types.ts`
- Create: `frontend/src/components/common/Breadcrumb.tsx`

- [ ] **Step 1: types.ts 新增 DrilldownSource**

於 `frontend/src/types.ts` 檔尾追加：

```ts
/** 下鑽來源（由儀表板 navigate 帶入 location state），供客戶頁麵包屑與返回定位使用。 */
export interface DrilldownSource {
  from: "dashboard";
  /** 來源區塊中文名，如「流失雷達」 */
  section: string;
  /** 來源區塊 id，返回時用於捲動定位（對應 DashboardPage 區塊容器 id="block-{blockId}"） */
  blockId: string;
}
```

- [ ] **Step 2: 建立 Breadcrumb 元件**

```tsx
/**
 * 純呈現麵包屑：以 › 分隔逐層渲染；有 onClick 的層級為可點按鈕，無 onClick 為當前頁文字。
 */
export interface Crumb {
  label: string;
  onClick?: () => void;
}

export function Breadcrumb({ crumbs }: { crumbs: Crumb[] }) {
  return (
    <nav className="breadcrumb" aria-label="麵包屑">
      {crumbs.map((c, i) => (
        <span key={i}>
          {i > 0 ? <span className="breadcrumb-sep">›</span> : null}
          {c.onClick ? (
            <button type="button" className="breadcrumb-link" onClick={c.onClick}>{c.label}</button>
          ) : (
            <span className="breadcrumb-current">{c.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
```

- [ ] **Step 3: 追加麵包屑 CSS（styles.css 檔尾）**

```css
/* ===== SP7 麵包屑 ===== */
.breadcrumb { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; font-size: 13px; }
.breadcrumb-sep { color: #9bb0bb; margin: 0 2px; }
.breadcrumb-link { border: none; background: none; color: #0f766e; font: inherit; cursor: pointer; padding: 0; }
.breadcrumb-link:hover { text-decoration: underline; }
.breadcrumb-current { color: #5e7280; }
```

- [ ] **Step 4: 型別檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 5: Commit**

`git add frontend/src/types.ts frontend/src/components/common/Breadcrumb.tsx frontend/src/styles.css && git commit -m "feat(sp7): breadcrumb component and DrilldownSource type"`（非 git repo 則跳過）。

---

### Task 5: 儀表板下鑽入口帶 source state

**Files:**
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`（`jumpToCustomer` 與三個區塊的 onSelectCustomer 簽章）
- Modify: `frontend/src/features/dashboard/components/SentimentRadarSection.tsx`
- Modify: `frontend/src/features/dashboard/components/RfmSection.tsx`

**設計**：`onSelectCustomer` 簽章擴充為 `(id: number, source: DrilldownSource) => void`，各區塊呼叫時自行帶上 section/blockId；DashboardPage 的 `jumpToCustomer` 收 source 後 `navigate` 帶 state。

- [ ] **Step 1: DashboardPage.jumpToCustomer 收 source 並帶入 navigate state**

在 `frontend/src/features/dashboard/DashboardPage.tsx`，import 區加入型別：

```tsx
import type { DashboardReports, DashboardSummary, DrilldownResponse, DrilldownSource, RfmResponse, SentimentRadarResponse, UsageSummaryResponse } from "../../types";
```

將 `jumpToCustomer` 改為：

```tsx
  /** 從下鑽/區塊跳到操作頁指定客戶，帶上來源區塊供麵包屑與返回定位。 */
  function jumpToCustomer(id: number, source: DrilldownSource) {
    setDrilldown(null);
    navigate(`/customers/${id}`, { state: source });
  }
```

DrilldownModal 的下鑽來源固定為報表區塊，將其 `onSelectCustomer` 包一層：在 render 區找到 `<DrilldownModal ... onSelectCustomer={jumpToCustomer} ... />`，改為：

```tsx
      {drilldown?.open ? <DrilldownModal state={drilldown} onSelectCustomer={(id) => jumpToCustomer(id, { from: "dashboard", section: drilldown.title, blockId: "reports" })} onClose={() => setDrilldown(null)} /> : null}
```

- [ ] **Step 2: SentimentRadarSection onSelectCustomer 簽章帶 source**

在 `SentimentRadarSection.tsx`，import 區加入：

```tsx
import type { DrilldownSource, SentimentRadarResponse } from "../../../types";
```

把元件 props 型別改為：

```tsx
export function SentimentRadarSection({ data, onSelectCustomer }: { data: SentimentRadarResponse | null; onSelectCustomer: (id: number, source: DrilldownSource) => void }) {
```

三個 PaginatedList 的 `onClick` 改為帶 source（blockId 統一 `sentiment-radar`，section 各自）：
- 高風險互動：`onClick={() => onSelectCustomer(item.customerId, { from: "dashboard", section: "高風險互動", blockId: "sentiment-radar" })}`
- 流失雷達：`onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: "流失雷達", blockId: "sentiment-radar" })}`
- 優先關懷：`onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: "優先關懷", blockId: "sentiment-radar" })}`

- [ ] **Step 3: RfmSection onSelectCustomer 簽章帶 source**

在 `RfmSection.tsx`，import 區加入：

```tsx
import type { DrilldownSource, RfmResponse } from "../../../types";
```

把元件 props 型別改為：

```tsx
export function RfmSection({ data, onSelectCustomer }: { data: RfmResponse[] | null; onSelectCustomer: (id: number, source: DrilldownSource) => void }) {
```

把 `.rfm-row` 的 `onClick` 改為：

```tsx
              onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: "RFM 客戶分群", blockId: "rfm" })}
```

- [ ] **Step 4: 型別檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（DashboardPage 對 `<RfmSection>`/`<SentimentRadarSection>` 的 `onSelectCustomer={jumpToCustomer}` 仍相容，因 jumpToCustomer 簽章已是 `(id, source)`）。

- [ ] **Step 5: Commit**

`git add frontend/src/features/dashboard/ && git commit -m "feat(sp7): pass drilldown source on customer navigation"`（非 git repo 則跳過）。

---

### Task 6: CustomersPage 顯示麵包屑 + DashboardPage 返回捲動定位

**Files:**
- Modify: `frontend/src/features/customers/CustomersPage.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

- [ ] **Step 1: CustomersPage 讀 location state 顯示麵包屑**

在 `CustomersPage.tsx`，把 `import { useNavigate, useParams } from "react-router-dom";` 改為：

```tsx
import { useLocation, useNavigate, useParams } from "react-router-dom";
```

加入 import：

```tsx
import { Breadcrumb } from "../../components/common/Breadcrumb";
import type { AgentTraceResponse, CustomerDetail, CustomerSummary, DrilldownSource } from "../../types";
```
（移除原本只 import 三型別的那行，合併為上面這行。）

在元件內 `const { id } = useParams();` 下方加入：

```tsx
  // 下鑽來源（自儀表板帶入）；重整後 state 消失即視為無來源，不顯示麵包屑
  const location = useLocation();
  const source = location.state as DrilldownSource | null;
```

在 `return (` 的第一個 `<section className="topbar">` 之前插入麵包屑（僅當有來源且已載入客戶名）：

```tsx
      {source?.from === "dashboard" ? (
        <Breadcrumb
          crumbs={[
            { label: "儀表板", onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: source.section, onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: selected?.customer.name ?? "客戶" }
          ]}
        />
      ) : null}
```

- [ ] **Step 2: DashboardPage 依 scrollTo 返回定位**

在 `DashboardPage.tsx`，把 `import { useNavigate } from "react-router-dom";` 改為：

```tsx
import { useLocation, useNavigate } from "react-router-dom";
```

在元件內加入 location 與返回定位 effect（放在既有 `useEffect` 之後）：

```tsx
  const location = useLocation();

  // 自客戶頁返回時，依 location.state.scrollTo 捲到原區塊，並清掉 state 避免重複觸發
  useEffect(() => {
    const scrollTo = (location.state as { scrollTo?: string } | null)?.scrollTo;
    if (!scrollTo) return;
    // 等資料載入後再捲，確保區塊已渲染（dashboard 為依賴）
    if (!dashboard) return;
    const el = document.getElementById(`block-${scrollTo}`);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    navigate(location.pathname, { replace: true, state: null });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.state, dashboard]);
```

> 注意：區塊容器的 `id="block-{blockId}"` 由 Task 11 的 catalog 包裹層提供（`metrics`/`reports`/`rfm`/`sentiment-radar`/`usage`）。在 Task 11 完成前，此 effect 找不到元素僅為 no-op，不報錯。

- [ ] **Step 3: 型別檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 4: Commit**

`git add frontend/src/features/ && git commit -m "feat(sp7): breadcrumb on customer page + scroll-back on dashboard"`（非 git repo 則跳過）。

---

## 第 3 段：後端偏好 + 拖拉排序/關閉/抽屜

### Task 7: V9 migration + UserPreference 實體 + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__user_preferences.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/UserPreference.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/UserPreferenceRepository.java`

- [ ] **Step 1: 建立 migration**

```sql
-- SP7：泛用個人偏好表（本次用於 dashboard_layout，未來可擴充其他偏好）
create table user_preferences (
    id bigint generated by default as identity primary key,
    user_id bigint not null references app_user(id),
    pref_key varchar(64) not null,
    pref_value text not null,
    updated_at timestamp with time zone not null,
    constraint uq_user_pref unique (user_id, pref_key)
);
create index idx_user_pref_user on user_preferences(user_id);
```

> 確認外鍵表名：seed/V1 的使用者表名。先 `grep -i "create table" backend/src/main/resources/db/migration/V1__init_schema.sql` 找實際表名（可能為 `app_user` 或 `users`），把上面 `references app_user(id)` 改為實際表名。`AppUserRepository` 對應 entity `AppUser`。

- [ ] **Step 2: 建立 UserPreference 實體**

```java
package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 個人偏好設定（泛用 key-value）：對應 user_preferences 表。
 * 函式級註解：本次用於 dashboard_layout（存可見區塊有序 id 陣列 JSON）；自行管理 updated_at。
 */
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬使用者 ID。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 偏好鍵，如 dashboard_layout。 */
    @Column(name = "pref_key", nullable = false, length = 64)
    private String prefKey;

    /** 偏好值（JSON 字串）。 */
    @Column(name = "pref_value", nullable = false, columnDefinition = "text")
    private String prefValue;

    /** 最後更新時間（自行管理）。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {
    }

    /**
     * 建立個人偏好。
     *
     * @param userId 使用者 ID
     * @param prefKey 偏好鍵
     * @param prefValue 偏好值 JSON 字串
     */
    public UserPreference(Long userId, String prefKey, String prefValue) {
        this.userId = userId;
        this.prefKey = prefKey;
        this.prefValue = prefValue;
        this.updatedAt = Instant.now();
    }

    /** 更新偏好值並刷新更新時間。 */
    public void updateValue(String prefValue) {
        this.prefValue = prefValue;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPrefKey() { return prefKey; }
    public String getPrefValue() { return prefValue; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 3: 建立 repository**

```java
package com.aicrm.crm.repository;

import com.aicrm.crm.domain.UserPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 個人偏好資料存取：依 (userId, prefKey) 查詢唯一偏好。
 */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * 依使用者與偏好鍵查詢。
     *
     * @param userId 使用者 ID
     * @param prefKey 偏好鍵
     * @return 偏好（可能不存在）
     */
    Optional<UserPreference> findByUserIdAndPrefKey(Long userId, String prefKey);
}
```

- [ ] **Step 4: 編譯**

Run（PowerShell）:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test-compile -q
```
Expected: BUILD SUCCESS（編譯通過）。

- [ ] **Step 5: Commit**

`git add backend/src/main/resources/db/migration/V9__user_preferences.sql backend/src/main/java/com/aicrm/crm/domain/UserPreference.java backend/src/main/java/com/aicrm/crm/repository/UserPreferenceRepository.java && git commit -m "feat(sp7): user_preferences table, entity, repository"`（非 git repo 則跳過）。

---

### Task 8: UserPreferenceService（TDD）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/UserPreferenceService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/UserPreferenceIntegrationTest.java`

**設計**：service 對外以 `username` 操作（呼叫端只有認證 username），內部用 `AppUserRepository.findByUsername` 解析 userId。提供 `getDashboardLayout(username): List<String>`（無則回 null）與 `saveDashboardLayout(username, List<String>)`（upsert）。layout 以 Jackson 3 `tools.jackson.databind.ObjectMapper` 序列化為 JSON 存 `pref_value`。

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UserPreferenceService 整合測試：dashboard layout 的 upsert 與讀取。
 * 以 seed 帳號 sales@aurora.local 驗證；DB 由 PostgresTestBase 提供。
 */
class UserPreferenceIntegrationTest extends PostgresTestBase {

    @Autowired UserPreferenceService service;

    @Test
    void getDashboardLayout_whenNoPreference_returnsNull() {
        // manager@aurora.local 尚未設定 layout，應回 null（讓前端套預設）
        assertThat(service.getDashboardLayout("manager@aurora.local")).isNull();
    }

    @Test
    void saveThenGet_returnsSameOrder() {
        var order = List.of("metrics", "rfm", "reports");
        service.saveDashboardLayout("sales@aurora.local", order);
        assertThat(service.getDashboardLayout("sales@aurora.local")).containsExactly("metrics", "rfm", "reports");
    }

    @Test
    void save_isUpsert_overwritesPrevious() {
        service.saveDashboardLayout("sales@aurora.local", List.of("metrics"));
        service.saveDashboardLayout("sales@aurora.local", List.of("reports", "metrics"));
        assertThat(service.getDashboardLayout("sales@aurora.local")).containsExactly("reports", "metrics");
    }
}
```

> seed 帳號以 `SecurityIntegrationTest` 使用的 `sales@aurora.local` / `manager@aurora.local` 為準；若 manager 帳號不存在，改用任一未被其他測試寫入 layout 的 seed 帳號。

- [ ] **Step 2: 跑測試確認失敗**

Run（PowerShell）:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -Dtest=UserPreferenceIntegrationTest -q
```
Expected: 編譯失敗（`UserPreferenceService` 不存在）或測試紅燈。

- [ ] **Step 3: 實作 service**

```java
package com.aicrm.crm.service;

import com.aicrm.crm.domain.UserPreference;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.UserPreferenceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 個人偏好服務：本次提供儀表板版面（可見區塊有序 id 陣列）的讀取與 upsert。
 * 函式級註解：對外以登入 username 操作，內部解析為 userId；layout 以 JSON 字串存 pref_value。
 */
@Service
public class UserPreferenceService {

    /** 儀表板版面偏好鍵。 */
    private static final String KEY_DASHBOARD_LAYOUT = "dashboard_layout";

    /** 偏好資料存取。 */
    private final UserPreferenceRepository preferenceRepository;
    /** 使用者查詢（username → id）。 */
    private final AppUserRepository userRepository;
    /** Jackson 3 ObjectMapper（Spring Boot 4.1 受管 bean）。 */
    private final ObjectMapper objectMapper;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository, AppUserRepository userRepository, ObjectMapper objectMapper) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 讀取使用者的儀表板版面（可見區塊有序 id 陣列）。
     *
     * @param username 登入帳號
     * @return 區塊 id 陣列；尚未設定時回 null
     */
    @Transactional(readOnly = true)
    public List<String> getDashboardLayout(String username) {
        var user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("使用者不存在: " + username));
        return preferenceRepository.findByUserIdAndPrefKey(user.getId(), KEY_DASHBOARD_LAYOUT)
                .map(pref -> deserialize(pref.getPrefValue()))
                .orElse(null);
    }

    /**
     * upsert 使用者的儀表板版面。
     *
     * @param username 登入帳號
     * @param visibleOrder 可見區塊有序 id 陣列
     */
    @Transactional
    public void saveDashboardLayout(String username, List<String> visibleOrder) {
        var user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("使用者不存在: " + username));
        var json = serialize(visibleOrder);
        preferenceRepository.findByUserIdAndPrefKey(user.getId(), KEY_DASHBOARD_LAYOUT)
                .ifPresentOrElse(
                        existing -> { existing.updateValue(json); preferenceRepository.save(existing); },
                        () -> preferenceRepository.save(new UserPreference(user.getId(), KEY_DASHBOARD_LAYOUT, json))
                );
    }

    /** 將區塊 id 陣列序列化為 JSON 字串。 */
    private String serialize(List<String> visibleOrder) {
        return objectMapper.writeValueAsString(visibleOrder);
    }

    /** 將 JSON 字串反序列化為區塊 id 陣列。 */
    private List<String> deserialize(String json) {
        return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<List<String>>() {});
    }
}
```

> Jackson 3 的 `writeValueAsString` / `readValue` 在 `tools.jackson` 為非受檢例外（不需 try/catch）。`AppUser` getter 名稱以實際 entity 為準（`getId()`、`getUsername()`）。

- [ ] **Step 4: 跑測試確認通過**

Run（PowerShell）:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -Dtest=UserPreferenceIntegrationTest -q
```
Expected: 3 個測試全綠。

- [ ] **Step 5: Commit**

`git add backend/src/main/java/com/aicrm/crm/service/UserPreferenceService.java backend/src/test/java/com/aicrm/crm/service/UserPreferenceIntegrationTest.java && git commit -m "feat(sp7): UserPreferenceService with dashboard layout upsert"`（非 git repo 則跳過）。

---

### Task 9: MeController + DTO + Security（TDD）

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/MeController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/MeControllerIntegrationTest.java`

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * /api/me/preferences/dashboard-layout 整合測試：未登入 401、PUT 後 GET 取回、無偏好回 null。
 */
class MeControllerIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    private String loginPost(String username) throws Exception {
        var body = "{\"username\":\"" + username + "\",\"password\":\"password123\"}";
        var json = mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void getLayout_withoutToken_returns401() throws Exception {
        mockMvc().perform(get("/api/me/preferences/dashboard-layout")).andExpect(status().isUnauthorized());
    }

    @Test
    void putThenGet_returnsSavedOrder() throws Exception {
        var token = loginPost("manager@aurora.local");
        mockMvc().perform(put("/api/me/preferences/dashboard-layout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibleOrder\":[\"reports\",\"metrics\"]}"))
                .andExpect(status().isOk());
        mockMvc().perform(get("/api/me/preferences/dashboard-layout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleOrder[0]").value("reports"))
                .andExpect(jsonPath("$.visibleOrder[1]").value("metrics"));
    }
}
```

> 移除上面誤植的 `login(...)` helper，只保留 `loginPost`。最終測試只用 `loginPost`。（撰寫時直接寫對：單一 `loginPost` helper。）

- [ ] **Step 2: 跑測試確認失敗**

Run（PowerShell）:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -Dtest=MeControllerIntegrationTest -q
```
Expected: 編譯失敗（MeController 不存在）或 GET 因無 security 規則行為不符。

- [ ] **Step 3: Dtos.java 新增 layout DTO**

在 `backend/src/main/java/com/aicrm/crm/api/Dtos.java` 內新增兩個 record（與既有 record 同層）：

```java
    /** 儀表板版面回應：可見區塊有序 id 陣列；尚未設定時 visibleOrder 為 null。 */
    public record DashboardLayoutResponse(java.util.List<String> visibleOrder) {}

    /** 儀表板版面請求：可見區塊有序 id 陣列。 */
    public record DashboardLayoutRequest(java.util.List<String> visibleOrder) {}
```

- [ ] **Step 4: 建立 MeController**

```java
package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 個人化 API：目前提供儀表板版面偏好的讀取與儲存（限本人，任何已登入角色）。
 */
@RestController
@RequestMapping("/api/me/preferences")
public class MeController {

    /** 個人偏好服務。 */
    private final UserPreferenceService preferenceService;

    public MeController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * 取得本人儀表板版面（可見區塊有序 id 陣列）。
     *
     * @param authentication 登入認證（principal 為 JwtService.AuthPrincipal）
     * @return 版面回應；未設定時 visibleOrder 為 null
     */
    @GetMapping("/dashboard-layout")
    public Dtos.DashboardLayoutResponse getDashboardLayout(Authentication authentication) {
        var order = preferenceService.getDashboardLayout(resolveUsername(authentication));
        return new Dtos.DashboardLayoutResponse(order);
    }

    /**
     * 儲存本人儀表板版面（upsert）。
     *
     * @param request 含可見區塊有序 id 陣列
     * @param authentication 登入認證
     */
    @PutMapping("/dashboard-layout")
    public void saveDashboardLayout(@RequestBody Dtos.DashboardLayoutRequest request, Authentication authentication) {
        preferenceService.saveDashboardLayout(resolveUsername(authentication), request.visibleOrder());
    }

    /** 從認證主體解析登入帳號。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未取得登入身分");
    }
}
```

- [ ] **Step 5: SecurityConfig 加 `/api/me/**` authenticated**

在 `SecurityConfig.java` 的 `authorizeHttpRequests` 中，於既有 `.requestMatchers("/api/customers/**", ... , "/api/agent/**").authenticated()` 那行**之前或同時**加入 `/api/me/**`。最簡作法：把該行的字串清單加上 `"/api/me/**"`：

```java
                        .requestMatchers("/api/customers/**", "/api/opportunities/**", "/api/dashboard/**", "/api/ai/**", "/api/agent/**", "/api/me/**").authenticated()
```

- [ ] **Step 6: 跑測試確認通過**

Run（PowerShell）:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -Dtest=MeControllerIntegrationTest -q
```
Expected: 2 個測試全綠（401 + putThenGet）。

- [ ] **Step 7: 跑全量後端測試確保無回歸**

Run（PowerShell）:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -q
```
Expected: 既有 58 + 新增 5（Task8 三 + Task9 二）= 63 全綠。

- [ ] **Step 8: Commit**

`git add backend/src/main/java/com/aicrm/crm/api/ backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java backend/src/test/java/com/aicrm/crm/api/MeControllerIntegrationTest.java && git commit -m "feat(sp7): /api/me/preferences/dashboard-layout endpoint"`（非 git repo 則跳過）。

---

### Task 10: 前端 api + types（layout）

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`

- [ ] **Step 1: types.ts 新增 DashboardLayoutResponse**

於 `frontend/src/types.ts` 檔尾追加：

```ts
/** 儀表板版面回應（SP7）：可見區塊有序 id 陣列；尚未設定時 visibleOrder 為 null。 */
export interface DashboardLayoutResponse {
  visibleOrder: string[] | null;
}
```

- [ ] **Step 2: api.ts 新增 fetch/save**

於 `frontend/src/api.ts` 的 import 型別清單加入 `DashboardLayoutResponse`，並於檔尾追加：

```ts
/**
 * 取得本人儀表板版面（可見區塊有序 id 陣列）；未設定時回 null。
 */
export async function fetchDashboardLayout() {
  const { data } = await apiClient.get<DashboardLayoutResponse>("/me/preferences/dashboard-layout");
  return data.visibleOrder;
}

/**
 * 儲存本人儀表板版面（可見區塊有序 id 陣列）。
 *
 * @param visibleOrder 區塊 id 有序陣列
 */
export async function saveDashboardLayout(visibleOrder: string[]) {
  await apiClient.put("/me/preferences/dashboard-layout", { visibleOrder });
}
```

- [ ] **Step 3: 型別檢查**

Run（Git Bash）: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 4: Commit**

`git add frontend/src/types.ts frontend/src/api.ts && git commit -m "feat(sp7): dashboard layout api client"`（非 git repo 則跳過）。

---

### Task 11: DashboardPage 區塊 catalog + 拖拉排序 + 關閉

**Files:**
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

**設計**：把目前寫死的 5 個區塊 JSX 抽成 `catalog`（依角色過濾），以 `visibleOrder` 控制渲染順序與顯隱。每個區塊以 `BlockWrapper` 包裹，提供 `id="block-{id}"`、drag handle、✕ 關閉。拖拉用原生 HTML5 DnD。所有變更「先更新 state，再 fire-and-forget PUT」。

- [ ] **Step 1: 加入 import 與狀態**

在 import 區加入：

```tsx
import { fetchDashboardLayout, saveDashboardLayout } from "../../api";
import { LayoutDrawer } from "./components/LayoutDrawer";
```

在元件內既有 state 之後加入版面狀態：

```tsx
  // 預設區塊順序（含全部區塊；usage 會在 catalog 依角色過濾）
  const DEFAULT_ORDER = ["metrics", "reports", "rfm", "sentiment-radar", "usage"];
  // 目前可見區塊有序 id 陣列（單一真實來源）
  const [visibleOrder, setVisibleOrder] = useState<string[]>(DEFAULT_ORDER);
  // 抽屜開關
  const [drawerOpen, setDrawerOpen] = useState(false);
  // 拖拉來源索引（拖拉中）
  const [dragIndex, setDragIndex] = useState<number | null>(null);
```

- [ ] **Step 2: 進頁載入版面偏好**

在既有 `useEffect(() => { void loadDashboardData(); }, [])` 之後加入：

```tsx
  // 進頁載入個人版面偏好；無偏好用預設順序
  useEffect(() => {
    void (async () => {
      try {
        const saved = await fetchDashboardLayout();
        if (saved && saved.length > 0) setVisibleOrder(saved);
      } catch (e) {
        console.error("載入版面偏好失敗:", e);
      }
    })();
  }, []);
```

- [ ] **Step 3: 定義區塊 catalog 與輔助函式**

在 `return (` 之前加入：

```tsx
  // 區塊目錄：id → 標題與渲染；usage 僅 MANAGER/ADMIN
  const catalog: { id: string; title: string; render: () => React.ReactNode; visibleToRole: boolean }[] = [
    { id: "metrics", title: "統計卡", visibleToRole: true, render: () => <DashboardCards dashboard={dashboard} riskCounts={riskCounts} /> },
    { id: "reports", title: "CRM 報表", visibleToRole: true, render: () => <ReportsSection reports={reports} onDrill={openDrilldown} onSelectCustomer={(cid) => jumpToCustomer(cid, { from: "dashboard", section: "CRM 報表", blockId: "reports" })} /> },
    { id: "rfm", title: "RFM 客戶分群", visibleToRole: true, render: () => <RfmSection data={rfm} onSelectCustomer={jumpToCustomer} /> },
    { id: "sentiment-radar", title: "情緒意圖雷達", visibleToRole: true, render: () => <SentimentRadarSection data={sentiment} onSelectCustomer={jumpToCustomer} /> },
    { id: "usage", title: "AI 用量治理", visibleToRole: canSeeUsage, render: () => <AiUsageCard usage={usage} /> }
  ];
  // 依角色可用的區塊 id 集合
  const allowedIds = new Set(catalog.filter((b) => b.visibleToRole).map((b) => b.id));
  // 實際渲染順序：過濾掉不存在/無權限的 id
  const renderOrder = visibleOrder.filter((id) => allowedIds.has(id));
  // 隱藏區塊（可加回）：允許但不在 renderOrder 中
  const hiddenBlocks = catalog.filter((b) => b.visibleToRole && !renderOrder.includes(b.id));

  /** 套用新順序並 fire-and-forget 存後端。 */
  function applyOrder(next: string[]) {
    setVisibleOrder(next);
    void saveDashboardLayout(next).catch((e) => console.error("儲存版面失敗:", e));
  }
  /** 關閉區塊。 */
  function closeBlock(id: string) { applyOrder(renderOrder.filter((x) => x !== id)); }
  /** 加回區塊（append 尾端）。 */
  function addBlock(id: string) { applyOrder([...renderOrder, id]); }
  /** 還原預設順序（依角色過濾）。 */
  function resetOrder() { applyOrder(DEFAULT_ORDER.filter((id) => allowedIds.has(id))); }
  /** 拖放結束：把 dragIndex 區塊移到 targetIndex 位置。 */
  function handleDrop(targetIndex: number) {
    if (dragIndex === null || dragIndex === targetIndex) { setDragIndex(null); return; }
    const next = [...renderOrder];
    const [moved] = next.splice(dragIndex, 1);
    next.splice(targetIndex, 0, moved);
    setDragIndex(null);
    applyOrder(next);
  }
```

> `riskCounts`、`dashboard`、`reports`、`rfm`、`sentiment`、`usage`、`canSeeUsage`、`openDrilldown`、`jumpToCustomer` 均為既有變數/函式（Task 5 已更新 jumpToCustomer 簽章）。

- [ ] **Step 4: 改寫 return 的區塊渲染區**

把原本固定的：

```tsx
      <DashboardCards .../>
      <ReportsSection .../>
      <RfmSection .../>
      <SentimentRadarSection .../>
      {canSeeUsage ? <AiUsageCard usage={usage} /> : null}
```

替換為依 renderOrder 動態渲染（每塊含 id 容器 + 工具列）：

```tsx
      {renderOrder.map((id, index) => {
        const block = catalog.find((b) => b.id === id);
        if (!block) return null;
        return (
          <div
            key={id}
            id={`block-${id}`}
            className={`block-wrapper${dragIndex === index ? " dragging" : ""}`}
            draggable
            onDragStart={() => setDragIndex(index)}
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => handleDrop(index)}
          >
            <div className="block-toolbar">
              <span className="block-drag-handle" title="拖拉排序">⠿</span>
              <button type="button" className="block-close" title="關閉區塊" onClick={() => closeBlock(id)}>✕</button>
            </div>
            {block.render()}
          </div>
        );
      })}
```

- [ ] **Step 5: topbar 加抽屜按鈕 + 掛載抽屜**

在 `<div className="topbar-actions">` 內，`isAdmin` 按鈕之前加入版面按鈕：

```tsx
          <button type="button" className="layout-btn" onClick={() => setDrawerOpen(true)}>⊞ 版面（隱藏 {hiddenBlocks.length}）</button>
```

在 return 結尾（`{drilldown?.open ...}` 之後）掛載抽屜：

```tsx
      {drawerOpen ? (
        <LayoutDrawer hiddenBlocks={hiddenBlocks.map((b) => ({ id: b.id, title: b.title }))} onAdd={addBlock} onReset={resetOrder} onClose={() => setDrawerOpen(false)} />
      ) : null}
```

- [ ] **Step 6: 型別檢查（此時 LayoutDrawer 尚未建立，預期 import 報錯）**

> 本 Task 與 Task 12 相依（互相引用）。先完成 Task 12 後再一起檢查。若分開執行，Step 6 改為跳過、於 Task 12 Step 結尾一起跑 tsc。

- [ ] **Step 7: Commit（與 Task 12 一起）**

待 Task 12 完成後一起 commit。

---

### Task 12: LayoutDrawer 抽屜元件 + CSS

**Files:**
- Create: `frontend/src/features/dashboard/components/LayoutDrawer.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 建立 LayoutDrawer**

```tsx
/**
 * 版面抽屜：右側滑入，列出目前隱藏的儀表板區塊，可逐一加回，並提供還原預設順序。
 * 函式級註解：加回與還原都委派給父層（DashboardPage）統一走存檔邏輯，本元件只負責呈現與觸發。
 */
interface HiddenBlock { id: string; title: string; }

export function LayoutDrawer({ hiddenBlocks, onAdd, onReset, onClose }: {
  hiddenBlocks: HiddenBlock[];
  onAdd: (id: string) => void;
  onReset: () => void;
  onClose: () => void;
}) {
  return (
    <div className="drawer-overlay" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header">
          <strong>版面設定</strong>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>
        <div className="drawer-body">
          <p className="drawer-hint">隱藏的區塊（點＋加回儀表板）</p>
          {hiddenBlocks.length === 0 ? (
            <div className="sr-empty">目前所有區塊都已顯示</div>
          ) : (
            hiddenBlocks.map((b) => (
              <div className="drawer-item" key={b.id}>
                <span>{b.title}</span>
                <button type="button" className="btn-secondary" onClick={() => onAdd(b.id)}>＋ 加回</button>
              </div>
            ))
          )}
        </div>
        <div className="drawer-footer">
          <button type="button" className="btn-secondary" onClick={onReset}>還原預設順序</button>
        </div>
      </aside>
    </div>
  );
}
```

- [ ] **Step 2: 追加區塊工具列與抽屜 CSS（styles.css 檔尾）**

```css
/* ===== SP7 區塊工具列（拖拉/關閉） ===== */
.block-wrapper { position: relative; }
.block-wrapper.dragging { opacity: 0.5; }
.block-toolbar {
  position: absolute; top: 6px; right: 10px; z-index: 5;
  display: flex; align-items: center; gap: 8px;
}
.block-drag-handle { cursor: grab; color: #9bb0bb; font-size: 16px; user-select: none; }
.block-close {
  border: none; background: rgba(15, 34, 55, 0.06); color: #5e7280;
  width: 22px; height: 22px; border-radius: 6px; font-size: 12px; line-height: 1;
}
.block-close:hover { background: #ffe2e2; color: #b42318; }

/* ===== SP7 版面抽屜 ===== */
.layout-btn {
  border: 1px solid #c7d7d3; border-radius: 8px; padding: 9px 14px;
  background: #fff; color: #0f5f57; font-weight: 600; white-space: nowrap;
}
.layout-btn:hover { background: #f0faf6; }
.drawer-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.35); z-index: 110; display: flex; justify-content: flex-end; }
.drawer {
  width: 320px; max-width: 90vw; height: 100vh; background: #fff;
  display: flex; flex-direction: column; box-shadow: -8px 0 32px rgba(16,43,64,.2);
  animation: drawer-in .18s ease;
}
@keyframes drawer-in { from { transform: translateX(100%); } to { transform: translateX(0); } }
.drawer-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; background: linear-gradient(135deg, #0f766e, #14b8a6); color: #fff; }
.drawer-body { flex: 1; overflow-y: auto; padding: 16px 18px; display: flex; flex-direction: column; gap: 10px; }
.drawer-hint { margin: 0 0 4px; font-size: 13px; color: #5e7280; }
.drawer-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; border: 1px solid #e1ece9; border-radius: 10px; padding: 10px 12px; }
.drawer-footer { padding: 12px 18px; border-top: 1px solid #e1ece9; text-align: right; }
```

- [ ] **Step 3: 型別檢查 + 建置（Task 11 + 12 一起驗證）**

Run（Git Bash）:
```bash
export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit && pnpm build
```
Expected: tsc exit 0、build 成功。

- [ ] **Step 4: Commit（Task 11 + 12）**

`git add frontend/src/features/dashboard/ frontend/src/styles.css && git commit -m "feat(sp7): dashboard block catalog, drag reorder, close, layout drawer"`（非 git repo 則跳過）。

---

### Task 13: E2E 煙霧測試

**Files:**
- Create: `frontend/e2e/sp7-layout.spec.ts`

**前置**：dev 後端跑在 127.0.0.1:18080（已套 V9），dev 前端可由 Playwright config 啟動或已啟動；seed 帳號 `sales@aurora.local` / `password123`。先確認 dev 後端已重啟套用 V9：

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend spring-boot:run
```
（背景啟動；確認 log 顯示 Flyway migrate 到 V9。）

- [ ] **Step 1: 撰寫 E2E**

```ts
import { test, expect } from "@playwright/test";

/**
 * SP7 煙霧測試：清單分頁、下鑽麵包屑返回、區塊關閉 + 抽屜加回。
 * 前置：後端 127.0.0.1:18080（已套 V9）、seed sales@aurora.local / password123。
 */
test("SP7 版面：分頁 / 麵包屑 / 關閉加回", async ({ page }) => {
  // 登入
  await page.goto("/");
  await page.fill('input[name="username"]', "sales@aurora.local");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/);

  // 情緒意圖雷達區塊出現
  const radar = page.locator('#block-sentiment-radar');
  await expect(radar).toBeVisible();

  // 清單分頁：若高風險互動超過 5 筆，分頁列出現且可下一頁
  const pager = radar.locator('.paginated-list .pagination').first();
  if (await pager.count() > 0) {
    await pager.getByRole("button", { name: "下一頁" }).click();
    await expect(pager.getByText(/第 2 \/ /)).toBeVisible();
  }

  // 下鑽麵包屑：點流失雷達任一列 → 客戶頁出現麵包屑 → 點「儀表板」返回
  const churnRow = radar.locator('.sr-churn-row').first();
  if (await churnRow.count() > 0) {
    await churnRow.click();
    await expect(page).toHaveURL(/\/customers\/\d+/);
    await expect(page.locator('.breadcrumb')).toContainText("流失雷達");
    await page.locator('.breadcrumb-link', { hasText: "儀表板" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  }

  // 關閉區塊 + 抽屜加回：關掉 RFM 區塊 → 抽屜計數 +1 → 加回
  const layoutBtn = page.locator('.layout-btn');
  const before = await layoutBtn.textContent();
  await page.locator('#block-rfm .block-close').click();
  await expect(page.locator('#block-rfm')).toHaveCount(0);
  await layoutBtn.click();
  await expect(page.locator('.drawer')).toBeVisible();
  await page.locator('.drawer-item', { hasText: "RFM" }).getByRole("button", { name: /加回/ }).click();
  await expect(page.locator('#block-rfm')).toBeVisible();
});
```

- [ ] **Step 2: 跑 E2E**

Run（Git Bash）:
```bash
export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec playwright test sp7-layout
```
Expected: 1 passed。（DnD 拖拉排序因 Playwright 原生 DnD 不穩，未納入斷言；核心存取邏輯由關閉/加回涵蓋。）

- [ ] **Step 3: Commit**

`git add frontend/e2e/sp7-layout.spec.ts && git commit -m "test(sp7): e2e for pagination, breadcrumb, close+drawer"`（非 git repo 則跳過）。

---

## 收尾

- [ ] 更新 `docs/roadmap-progress.md`：新增 SP7 列與變更紀錄。
- [ ] 全量後端測試 `mvn -pl backend test -q` 綠燈（63 測試）。
- [ ] 前端 `pnpm exec tsc --noEmit` + `pnpm build` 綠燈、`pnpm exec playwright test` 綠燈。

## 自我檢查（撰寫者已執行）

**Spec 覆蓋：** ①固定高度→Task 1-3；②麵包屑→Task 4-6；③拖拉/關閉/抽屜/後端→Task 7-12；測試→Task 8/9/13。全部對應。

**型別一致性：** `DrilldownSource{from,section,blockId}`（Task 4 定義，Task 5/6 使用一致）；`onSelectCustomer:(id,source)`（Task 5 三區塊與 DashboardPage 一致）；`visibleOrder:string[]`（後端 DTO ↔ 前端 api ↔ DashboardPage 一致）；blockId 集合 `metrics/reports/rfm/sentiment-radar/usage`（Task 11 catalog 與 Task 6 容器 id、Task 5 source 一致）。

**已知相依：** Task 11↔12 互相引用，需一起完成再 tsc；Task 6 的 scrollTo 依賴 Task 11 的 `id="block-*"` 容器（先做為 no-op，後生效）。Task 9 測試的 `loginPost` helper 撰寫時只保留單一版本（計畫範例含說明，實作勿複製誤植的 `login` 方法）。外鍵表名（app_user vs users）實作前以 grep V1 確認。
