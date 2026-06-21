# Manager 業務分析頁 + 客戶列表效能修復 — 設計文件

- 日期：2026-06-21
- 狀態：已通過腦力激盪、待實作計畫
- 範圍：① 客戶列表分頁效能修復 ② Manager 業務統計頁 ③ 兩個 AI 分析功能

---

## 1. 背景與目標

### 1.1 問題與需求
1. **客戶列表切換頁很慢**：帶「風險等級」篩選時，後端走「整表載入記憶體再分頁」，每切一頁都全表重算，資料量一大即線性惡化。
2. **Manager 需要業務分析能力**：銷售主管(MANAGER)需要一個專屬頁面，綜觀全公司各業務(SALES)的績效，並有 AI 協助分析。

### 1.2 成功標準
- 風險篩選的分頁查詢從「O(全表) 掃描 + 全表 toSummary」降為固定的分頁查詢。
- MANAGER / ADMIN 可進入業務分析頁；SALES 不可(API 403、前端導回)。
- 兩個 AI 功能可點按生成、結果快取，無金鑰時走 fallback 仍可用。

### 1.3 既有地基(沿用)
- 角色：`SALES / MANAGER / ADMIN`（`domain/Role.java`），JWT 帶 role，`SecurityConfig` 用 `hasAnyRole`，前端 `AdminRoute` 守衛模式。
- 儀表板已有業務排行榜 `ownerLeaderboard`（`DashboardService.java:100`，純數字）。
- AI 基礎設施：`ChatClient` + SSE 串流(`InsightService.streamAnswer`) + deterministic fallback + `AiCallLog`(token/governance 計量) + `PiiMasker`。
- 衍生表回填慣例：`InteractionInsightBackfillRunner`(啟動補算)、`interaction_insights` 表。

---

## 2. 整體架構

三個模組獨立，可分開實作與測試。建議實作順序 A → B → C（C 依賴 B 的統計資料）。

| 模組 | 後端 | 前端 |
|---|---|---|
| **A. 列表效能修復** | `customers.risk_level` 欄位 + 維護機制；`CustomerService.search` 回走高效路徑 | 無(透明) |
| **B. 業務統計頁** | `ManagerAnalyticsController` + `ManagerAnalyticsService`(純統計) | 新頁 `/team`，`ManagerRoute` 守衛 |
| **C. 兩個 AI 功能** | `ManagerInsightService`(打 LLM) + `manager_insight` 快取表 | 同頁兩個區塊 + SSE 串流 |

**權限**：後端 `/api/manager/**` 由 `SecurityConfig` 以 `hasAnyRole("MANAGER","ADMIN")` 保護；前端新增 `ManagerRoute`(`role === "MANAGER" || role === "ADMIN"`，否則導回 `/dashboard`)，側邊欄對 MANAGER/ADMIN 顯示「📈 業務分析」。

---

## 3. 模組 A：客戶列表效能修復

### 3.1 根因
`riskLevel` 是衍生值（依「最後互動日」與「今天日期」即時計算，`CustomerMapper.calculateRiskLevel`），SQL 無法篩選。帶 `riskLevel` 參數時 `CustomerService.search`(約 L103) 走路徑 2：`findAll(spec)` 撈全部 → 每筆 `toSummary`(觸發 interactions/opportunities 載入) → 記憶體 filter + subList。每次切頁重跑。

### 3.2 修法：風險等級落地為 DB 欄位
- 新增欄位 `customers.risk_level`(varchar，存 `HIGH/MEDIUM/LOW`)與 `customers.risk_computed_at`(timestamp，記錄最後重算時間)。
- `CustomerService.search` 移除路徑 2；風險篩選改為 Specification 的 `cb.equal(root.get("riskLevel"), ...)`，統一走 `findAll(spec, pageable)` 的 DB 分頁。
- `CustomerMapper.toSummary` 直接讀 `risk_level` 欄位，不再掃描 interactions 計算風險。

### 3.3 維護機制（落地衍生值的關鍵）
`riskLevel` 同時依賴「最後互動日」與「今天日期」，光靠事件觸發不夠：
1. **事件觸發**：新增/編輯互動、變更客戶續約日時，即時重算該客戶 `risk_level`。
2. **每日重算**：`@Scheduled` 每日凌晨重算全表（時間流逝會使等級自然變化）。
3. **啟動補算**：仿 `InteractionInsightBackfillRunner`，啟動時補算 `risk_level` 為 null 的客戶。

> **決策**：每日重算排程做成設定開關 `app.risk.daily-recompute.enabled`，**預設 `true`**（風險不每日更新會失準），但保留可關閉，避免「隱性啟用」反模式。

### 3.4 風險計算邏輯（不變，僅落地）
維持既有規則（`lastInteractionAt == null → MEDIUM`；`days>60 或 續約逾期 → HIGH`；`days>30 → MEDIUM`；否則 `LOW`），抽到可共用的計算方法供寫入時與排程共用。

---

## 4. 模組 B：業務統計頁

### 4.1 API
`GET /api/manager/analytics` → 每個業務(以 `owner_id` 聚合，非 ownerName，避免同名合併)一筆 `OwnerStats`：

```
OwnerStats {
  ownerId, ownerName,
  customerCount, highRiskCount,               // 客戶數、高風險數
  pipelineAmount, activeOpportunityCount,     // 活躍商機(非 CLOSED_LOST)
  wonAmount, wonCount, winRate,               // 成交績效 + 成交率
  avgDaysSinceInteraction, avgSentimentScore, // 互動活躍度 + 情緒(聚合 interaction_insights)
  renewalsThisMonth, renewalsThisQuarter      // 續約到期
}
```
另回傳團隊層級彙總(總成交、平均成交率等)供頂部 KPI 列。

### 4.2 指標來源
- 成交：opportunities 中 `stage = CLOSED_WON` 的金額/件數；winRate = wonCount / 總商機數。
- 活躍商機：非 `CLOSED_LOST` 的商機數與金額。
- 互動活躍度：該業務客戶的最後互動日距今天數平均。
- 客戶情緒：聚合 `interaction_insights.sentiment_score`。
- 續約：客戶 `renewalDueDate` 落在本月/本季的數量。

### 4.3 前端
- 路由 `/team`，`ManagerRoute` 守衛。
- 版面：頂部團隊總覽 KPI 列 + 一張可排序業務績效表(沿用既有表格樣式)。點業務列 → 帶入 AI-B 的對象。
- **純統計、不碰 LLM**，先完成此模組，AI 功能才有資料基礎。

---

## 5. 模組 C：兩個 AI 功能

### 5.1 共用設計：點按生成 + 快取
新增快取表 `manager_insight`：
```
manager_insight {
  id, scope (TEAM | OWNER), owner_id (TEAM 時 null),
  content (markdown), model, generated_at
}
```
- 每次真實 LLM 呼叫仍寫一筆 `AiCallLog`(沿用 token 計量/governance)。
- 新增 `AiCallType.TEAM_ANALYSIS`、`AiCallType.OWNER_COACHING`。
- 流程：進頁先讀 `manager_insight` 快取顯示(含「上次分析時間」)；按「重新分析」才 SSE 串流打 LLM，完成後 upsert 快取。
- 沿用 `InsightService.streamAnswer` 與 deterministic fallback(無金鑰/失敗時回退)。

### 5.2 AI-A：團隊整體診斷 + 逐人點評（scope=TEAM）
- Prompt 餵全體 `OwnerStats`。
- 產出：① 團隊整體診斷(top 表現者、落後者、共通問題與建議) ② 排行榜逐人一段「優勢 + 加強建議」。

### 5.3 AI-B：個別業務 coaching（scope=OWNER）
- Prompt 餵該業務名下所有客戶的商機/風險/近期互動摘要(沿用 `customerContext` + `PiiMasker` 遮罩)。
- 產出：主管輔導報告 — 該優先跟進哪些客戶、哪些風險要處理、具體輔導建議。

---

## 6. 資料庫遷移

- `V13__add_customer_risk_level.sql`：新增 `risk_level`、`risk_computed_at` 欄位 + `idx_customers_risk_level` 索引。(順帶補 status / renewal_due_date 索引)
- `V14__add_manager_insight.sql`：新增 `manager_insight` 表。
- **不可修改既有 migration**（Flyway checksum 不可變）。

---

## 7. 測試策略

- **後端單元/整合**：
  - `ManagerAnalyticsService` 各指標聚合正確性(成交率、情緒平均、續約計數)。
  - `search()` 風險篩選改走 SQL 後的分頁與總筆數正確性。
  - `risk_level` 維護：新增互動後該客戶等級更新；每日重算覆蓋。
  - 權限：SALES 打 `/api/manager/**` → 403；MANAGER/ADMIN → 200。
  - AI：無金鑰走 fallback；快取 upsert；`AiCallLog` 有記錄。
- **驗收**：風險篩選分頁查詢數不再隨資料量線性成長；MANAGER 可見頁、SALES 不可見。

---

## 8. 範圍外（YAGNI）

- 不建「上下屬/團隊」結構（Manager 看全公司所有業務即可）。
- 不做 AI 排程預生成(採點按生成)。
- 不改既有客戶 360 / Portfolio 評估功能。
