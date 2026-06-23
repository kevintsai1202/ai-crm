# Dashboard 快取與逐一渲染 — 設計規格

**日期：** 2026-06-23  
**狀態：** 已核准，待實作  
**問題：** 儀表板每次載入（登入後或 F5）都凍結數秒，原因是 3 個並行 API 各自獨立做全量客戶掃描，加上前端 `Promise.all` 封鎖全部渲染直到最慢的請求回來。

---

## 根本原因摘要

1. `/api/dashboard/summary`、`/api/dashboard/reports`、`/api/dashboard/rfm` 三個 API 各自呼叫 `allCustomersWithDetail()` → `customers.findAll()`，在同一個 `Promise.all` 內並行打出，產生 ~15 條 DB 查詢同時打遠端 PostgreSQL。
2. `SentimentService.radar()` 內部 `aggregateCustomerRisk()` 被呼叫兩次（`churnRadar()` + `priorityCare()` 各叫一次）。
3. 前端 `loadDashboardData()` 使用 `Promise.all`，全部 4 個 API 都回來才 `setState`，任何一個慢就凍住整個儀表板。

---

## 解法架構

### 後端：Caffeine Cache（TTL 2 分鐘 + 主要寫入點 evict）

**選擇 Caffeine 的原因：** Spring Boot 原生支援，不需額外 infrastructure（Redis），TTL 設定簡單，in-memory 回應為微秒級。

#### 4 個 Cache 定義

| Cache 名稱 | 快取方法 | TTL |
|---|---|---|
| `dashboard-summary` | `DashboardService.dashboardSummary()` | 2 分鐘 |
| `dashboard-reports` | `DashboardService.dashboardReports()` | 2 分鐘 |
| `dashboard-rfm` | `RfmService.computeRfm()` | 2 分鐘 |
| `dashboard-sentiment` | `SentimentService.radar()` | 2 分鐘 |

#### Cache Evict 規則（混合策略）

| 寫入點 | 清除 cache | 理由 |
|---|---|---|
| `DemoDataService.generateDemoData()` | 全部 4 個 | 寫入客戶、互動、商機、insights，全部資料改變 |
| `SentimentIntentService.analyzeMissing()` | 只清 `dashboard-sentiment` | 只改 `interaction_insights`，客戶/商機資料不受影響 |
| TTL 2 分鐘到期 | 自動失效 | 小型日常操作（單筆客戶更新）靠 TTL 自然過期 |

#### 附帶修正：消除重複查詢

`SentimentService.radar()` 改為計算 `churnRadar()` 一次後傳入 `priorityCareFrom(churn)`，`aggregateCustomerRisk()` 只執行一次。

#### 異動檔案（後端）

- `backend/pom.xml` — 新增 `com.github.ben-manes.caffeine:caffeine` dependency
- `backend/src/main/resources/application.yml` — 新增 `spring.cache` 設定區塊
- `backend/src/main/java/com/aicrm/crm/config/CacheConfig.java`（新建）— `@EnableCaching` + Caffeine CacheManager，定義 4 個 cache 各 TTL 2 分鐘
- `backend/src/main/java/com/aicrm/crm/service/DashboardService.java` — `dashboardSummary()` 加 `@Cacheable("dashboard-summary")`；`dashboardReports()` 加 `@Cacheable("dashboard-reports")`
- `backend/src/main/java/com/aicrm/crm/service/RfmService.java` — `computeRfm()` 加 `@Cacheable("dashboard-rfm")`
- `backend/src/main/java/com/aicrm/crm/service/SentimentService.java` — `radar()` 加 `@Cacheable("dashboard-sentiment")`；修正 `churnRadar()` 雙重呼叫
- `backend/src/main/java/com/aicrm/crm/service/DemoDataService.java` — `generateDemoData()` 加 `@CacheEvict` 清除全部 4 個 cache
- `backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java` — `analyzeMissing()` 加 `@CacheEvict("dashboard-sentiment")`

---

### 前端：Promise.all → 獨立非同步渲染

**目標：** 哪個 API 先回來就先顯示該區塊，使用者不再等最慢的那個。

#### 改動位置

`frontend/src/features/dashboard/DashboardPage.tsx`，`loadDashboardData()` 函式：

```typescript
// 改前：全部等齊才渲染
async function loadDashboardData() {
    const [summary, reportResult, rfmResult, sentimentResult] = await Promise.all([
        fetchDashboard(),
        fetchDashboardReports(),
        fetchRfm(),
        fetchSentimentRadar()
    ]);
    setDashboard(summary);
    setReports(reportResult);
    setRfm(rfmResult);
    setSentiment(sentimentResult);
}

// 改後：各自回來各自渲染
function loadDashboardData() {
    fetchDashboard()
        .then(setDashboard)
        .catch(e => console.error("摘要載入失敗:", e));
    fetchDashboardReports()
        .then(setReports)
        .catch(e => console.error("報表載入失敗:", e));
    fetchRfm()
        .then(setRfm)
        .catch(e => console.error("RFM 載入失敗:", e));
    fetchSentimentRadar()
        .then(setSentiment)
        .catch(e => console.error("情緒雷達載入失敗:", e));
}
```

`handleGenerateDemo()` 呼叫 `loadDashboardData()` 後不再 `await`（各 API 獨立回來後更新 UI）；`setGeneratingDemo(false)` 在 demo data API 完成後立刻執行。

#### 現有 loading state

各區塊的 `dashboard === null`、`reports === null`、`rfm === null`、`sentiment === null` 已對應空態 UI（skeleton 或佔位），無需額外修改。

---

## 預期效果

| 情境 | 改前 | 改後 |
|---|---|---|
| 第一次開啟儀表板 | 凍結 N 秒後全部一起出現 | 各區塊陸續出現（快的先顯示） |
| F5 重載（後端 cache 熱） | 凍結 N 秒 | 幾乎即時（cache hit，微秒回應） |
| 產生示範資料後重載 | 凍結 N 秒 | cache evict 後同「第一次」情境，之後 F5 即時 |
| AI 批次補算後重載 | 凍結 N 秒 | sentiment cache evict，其餘 cache 仍有效 |

---

## 不在本次範圍

- 客戶列表頁、客戶詳情頁的效能優化（另立 task）
- Redis 分散式快取（Caffeine in-memory 已足夠單節點部署）
- 前端區塊級 loading skeleton 的視覺優化（現有 null 佔位已可接受）
