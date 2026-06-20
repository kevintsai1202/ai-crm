# SP6 設計規格：情緒意圖雷達（Sentiment & Intent Radar）

> 子專案：SP6（路線圖見 `docs/roadmap-progress.md`）
> 建立日期：2026-06-19
> 依據：`docs/consulting-review.md`（模組 2 🥇 投報比最高）、`docs/crm-ai-consultant-analysis.md`（第五階段）

---

## 1. 目標與成功標準

**目標**：對互動紀錄做情緒+意圖分析並落庫；儀表板呈現 5 個雷達視覺；客戶詳情每則互動標情緒/意圖；提供 ADMIN 大量示範資料生成端點。

**成功標準**：
1. `interaction_insights` 落庫每則互動的 sentiment / score / intent。
2. 新增互動時自動分析；ADMIN 可批次重建。
3. 儀表板雷達 5 視覺：意圖分布、情緒趨勢（月）、高風險互動、流失雷達、優先關懷清單。
4. 客戶詳情時間線每則互動顯示情緒色點 + 意圖標籤。
5. ADMIN `POST /api/dev/generate-demo-data?customers=N` 生成 ~N 客戶、跨 12 個月互動並完成分析。
6. 後端 `mvn -pl backend test` 全綠；前端 build + 煙霧測試綠。

**非目標**：
- 不接外部情緒 API（用既有可切換 LLM + deterministic 規則）。
- 不做即時串流分析（落庫後查詢）。
- sentiment 分析不納入 SP4 治理日誌（它是分類器非對話 LLM，避免大量雜訊；單筆若用真 LLM 亦不記，保持 governance 專注 chat/assessment）。

---

## 2. 分類維度

- **Sentiment**：`POSITIVE` / `NEUTRAL` / `NEGATIVE`，附 `score`（-100..100，負值越大越負面）。
- **Intent**（enum）：`ASK_PRICING`(詢價) / `COMPARE_COMPETITOR`(競品比較) / `CHURN_SIGNAL`(流失信號) / `RENEWAL_INTEREST`(續約意願) / `UPSELL_SIGNAL`(加購) / `COMPLAINT`(客訴) / `OTHER`(其他)。

---

## 3. 元件設計

### 3.1 資料模型（V8 migration）
`interaction_insights`：
- id, interaction_id (FK→interactions, unique), customer_id (冗餘供聚合), sentiment varchar, sentiment_score int, intent varchar, analyzed_at timestamptz。
- index：customer_id、intent、analyzed_at。
- enum：`Sentiment`、`Intent`；entity `InteractionInsight`；`InteractionInsightRepository`（JPA + 聚合 @Query）。

### 3.2 SentimentIntentService
- `Classification classify(String content, boolean useLlm)`：
  - `useLlm=true`（單筆、有金鑰）：LLM 回 JSON {sentiment, score, intent}（解析失敗 → fallback）。
  - 否則：**deterministic 關鍵字規則**分類器（依中文關鍵字對應 intent 與情緒；例如「客訴/不滿/退費」→ NEGATIVE+COMPLAINT、「報價/價格/折扣」→ ASK_PRICING、「競品/他牌/比較」→ COMPARE_COMPETITOR、「續約/續訂」→ RENEWAL_INTEREST、「加購/升級/擴充」→ UPSELL_SIGNAL、「考慮取消/不續/轉單」→ CHURN_SIGNAL；無命中→NEUTRAL+OTHER）。
- `InteractionInsight analyzeAndSave(interaction, useLlm)`：分類後 upsert interaction_insights。
- `int analyzeMissing(boolean useLlm)`：批次分析尚無 insight 的互動（**預設 deterministic**，供 reindex 與生成器，避免大量真 LLM）。
- 觸發：`CustomerService.addInteraction` 後呼叫 `analyzeAndSave(interaction, aiEnabled)`（單筆，有金鑰走真 LLM）。

### 3.3 DemoDataService（ADMIN 生成器）
- `DemoStats generate(int customers)`：以**固定 seed Random** 生成 N 客戶（名稱/產業/業務取自池），每客戶 5–25 則互動，`occurredAt` 散佈過去 365 天，內容取自**每個 intent 一組中文範本**（讓 deterministic 分類能標對、情緒趨勢/分布有變化）；部分客戶給商機。生成後對新互動 `analyzeMissing(false)`（deterministic 批次）。回傳 {customers, interactions, insights}。
- 端點 `POST /api/dev/generate-demo-data?customers=N`（ADMIN）。SecurityConfig：`/api/dev/**` → ADMIN。
- 量：預設 N=200（~3000 互動）；可由參數調。批次 insert。

### 3.4 雷達查詢（DashboardService 或新 SentimentService）
`GET /api/dashboard/sentiment` → `SentimentRadarResponse`：
- `intentDistribution`: List<{intent, count}>。
- `sentimentTrend`: List<{month(yyyy-MM), positive, neutral, negative}>（近 12 月）。
- `highRiskInteractions`: List<{customerId, customerName, occurredAt, type, intent, sentiment, content}>（NEGATIVE 且 intent∈{CHURN_SIGNAL,COMPLAINT}，近期 top 20）。
- `churnRadar`: List<{customerId, name, negativeCount, churnSignalCount, complaintCount, score}>（依負面+流失訊號加權排序 top 20）。
- `priorityCare`: List<{customerId, name, reason}>（最該本週聯繫 top 10，理由如「3 則負面 + 1 則客訴」）。
- 以 SQL/JPA 聚合 interaction_insights join interactions/customers。

### 3.5 客戶詳情
- `Dtos.InteractionResponse` 加 nullable `sentiment` / `intent`；`CustomerService.getDetail` 一併帶出（join insights）。

---

## 4. 前端（串接，全 5 視覺）

- types：`SentimentRadarResponse` 與子型別；`InteractionResponse` 加 sentiment/intent。
- api：`fetchSentimentRadar()`、`generateDemoData(customers)`（ADMIN）。
- 儀表板：`SentimentRadarSection`（5 視覺：意圖分布長條、情緒趨勢堆疊/折線、高風險互動清單、流失雷達排序、優先關懷清單）。
- 客戶時間線：每則互動加情緒色點 + 意圖標籤（沿用既有 timeline UI）。
- （便利）ADMIN 在儀表板可見「產生示範資料」鈕（呼叫生成端點後重載）。

---

## 5. 測試

**單元**：
- `SentimentClassifierTest`：deterministic 分類器各 intent 關鍵字 → 正確 intent/sentiment；無命中 → OTHER/NEUTRAL。
**整合（PostgresTestBase）**：
- `SentimentRadarIntegrationTest`：用 DemoDataService 生成小量（如 customers=8）→ `GET /api/dashboard/sentiment` 回 5 區塊且非空、intentDistribution 含多類；客戶 getDetail 互動帶 sentiment/intent。
- `DemoDataIntegrationTest`：generate(5) 後 interactions 與 interaction_insights 筆數成長且每互動有 insight。
- 權限：`/api/dev/**` 與 sentiment 端點權限（dev 限 ADMIN）。

---

## 6. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 3000 互動若走真 LLM 分析 → 慢/貴 | 批次與生成器一律 deterministic；僅 UI 單筆新增走真 LLM |
| deterministic 分類器準度 | 生成器用「每意圖範本」確保關鍵字命中；真實資料準度由真 LLM 補 |
| 生成大量資料拖慢/重複 | 固定 seed + 批次 insert；端點可調量；生成為附加（不清既有） |
| InteractionResponse 加欄位影響前端 | nullable，前端舊欄位不受影響 |
| 時區/月份聚合 | 以 created/occurred_at 的 yyyy-MM 聚合，補滿近 12 月空月 |

---

## 7. 完成後
- 更新進度（SP6 ✅）；視情況回寫經驗。
- 部署 dev：重啟後端套 V8、用生成端點灌入示範資料、Playwright 看雷達。
