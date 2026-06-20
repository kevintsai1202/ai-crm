# SP6 情緒意圖雷達 Implementation Plan

> REQUIRED SUB-SKILL: subagent-driven-development。

**Goal:** 互動情緒+意圖分析落庫、ADMIN 大量示範資料生成、儀表板 5 雷達視覺、客戶時間線標情緒/意圖。

**環境：** Maven+JDK21（`export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"`）；repo 根 `mvn -pl backend ...`；前端 pnpm（Node `D:\nodejs`）；非 git repo 不 commit；整合測試 Testcontainers（沿用 `com.aicrm.crm.support.PostgresTestBase`），需 Docker。spec：`docs/superpowers/specs/2026-06-19-sp6-sentiment-intent-radar-design.md`。

**已知事實：**
- `Interaction(InteractionType, LocalDateTime occurredAt, content)`；`Customer.addInteraction`；`CustomerService.addInteraction`（writable @Transactional）；`CustomerService.getDetail` 組 `CustomerDetailResponse`（含 `List<InteractionResponse>`）。
- `Dtos.InteractionResponse(Long id, InteractionType type, LocalDateTime occurredAt, String content)`。
- enum 既有風格見 `domain/InteractionType.java`、`OpportunityStage.java`。
- DashboardService（SP5）已存在；DashboardController `/api/dashboard/**` authenticated。SecurityConfig 用 URL 規則（ADMIN 規則放 `/api/ai/**` 之前的模式可參考）。
- `InsightService` 有 `aiEnabled`、ChatClient 用法可參考；deterministic 分類用既有風險關鍵字風格。
- 前端：SP5 已建 `features/dashboard/components/RfmSection.tsx`、`AiUsageCard.tsx` 可當新區塊範本；`fetchRfm` 等在 `api.ts`；timeline 在 `features/customers/components/Timeline.tsx`。

---

## Task A：資料模型 + 分類服務 + 觸發 + 單元測試

**Files:** `domain/Sentiment.java`、`domain/Intent.java`、`domain/InteractionInsight.java`、`repository/InteractionInsightRepository.java`、`service/SentimentIntentService.java`、`db/migration/V8__add_interaction_insights.sql`、改 `service/CustomerService.java`、`test/.../service/SentimentClassifierTest.java`

- [ ] **A1 V8 migration**：`interaction_insights`(id, interaction_id bigint not null unique references interactions(id), customer_id bigint not null, sentiment varchar(20) not null, sentiment_score int not null, intent varchar(40) not null, analyzed_at timestamptz not null) + index(customer_id)、index(intent)、index(analyzed_at)。
- [ ] **A2 enums**：`Sentiment{POSITIVE,NEUTRAL,NEGATIVE}`、`Intent{ASK_PRICING,COMPARE_COMPETITOR,CHURN_SIGNAL,RENEWAL_INTEREST,UPSELL_SIGNAL,COMPLAINT,OTHER}`。`InteractionInsight` entity（自管 analyzed_at，時間型別對齊既有 entity，如 Instant/OffsetDateTime）。`InteractionInsightRepository extends JpaRepository`，加聚合查詢（intent count、月情緒、customer 聚合——可用 @Query native 或 derived，後續 Task C 也可補）。
- [ ] **A3 SentimentIntentService**：
  - `record Classification(Sentiment sentiment, int score, Intent intent)`。
  - `Classification classifyDeterministic(String content)`：中文關鍵字規則（客訴/不滿/退費/抱怨→NEGATIVE+COMPLAINT；取消/不續/轉單/流失→NEGATIVE+CHURN_SIGNAL；競品/他牌/比較/別家→NEUTRAL/NEGATIVE+COMPARE_COMPETITOR；報價/價格/折扣/費用→NEUTRAL+ASK_PRICING；續約/續訂/延長→POSITIVE+RENEWAL_INTEREST；加購/升級/擴充/加值→POSITIVE+UPSELL_SIGNAL；其餘→NEUTRAL+OTHER）。score 依情緒給（負面 -40~-80、正面 +40~+70、中性 0~20）。
  - `Classification classifyWithLlm(String content)`：有 `aiEnabled` 時 ChatClient 要求回嚴格 JSON 解析；任何失敗 → `classifyDeterministic`。建構子注入 `ObjectProvider<ChatModel>` + `@Value api-key`（同 InsightService 判 aiEnabled）。
  - `InteractionInsight analyzeAndSave(Long interactionId, Long customerId, String content, boolean useLlm)`：分類後 upsert（存在則更新）。
  - `int analyzeMissing(boolean useLlm)`：找尚無 insight 的互動批次分析（預設 useLlm=false）。**sentiment 分析不寫 ai_call_log**（不接 AiGovernanceService）。
- [ ] **A4 觸發**：`CustomerService.addInteraction` 存互動後呼叫 `sentimentIntentService.analyzeAndSave(interaction.getId(), customerId, content, aiEnabled)`。注入 SentimentIntentService；aiEnabled 可由 service 內部判斷（讓 SentimentIntentService 自己決定 useLlm，addInteraction 傳 true 表「允許用 LLM」，service 內部無金鑰自動 fallback）。
- [ ] **A5 SentimentClassifierTest**（單元，純 deterministic）：各 intent 關鍵字 → 正確 intent/sentiment；無命中→OTHER/NEUTRAL；score 區間合理。≥7 測試。
- [ ] **A6 驗證**：`mvn -pl backend -q -DskipTests compile` SUCCESS；`mvn -pl backend -Dtest=SentimentClassifierTest test` 綠。

---

## Task B：示範資料生成器 + 端點 + 權限 + 整合測試

**Files:** `service/DemoDataService.java`、改 `api/`（新 `DevController.java`）、改 `security/SecurityConfig.java`、`api/Dtos.java`（DemoStats）、`test/.../service/DemoDataIntegrationTest.java`

- [ ] **B1 DemoDataService**（@Service @Transactional）：注入 CustomerRepository / SentimentIntentService（與既有 repo）。
  - `record DemoStats(int customers, int interactions, int insights)`。
  - `DemoStats generate(int customers)`：固定 `new java.util.Random(20260619L)`；客戶名/產業/業務取自常數池；每客戶 5–25 則互動，type 隨機，`occurredAt` = now 減隨機 0–365 天，content 取自**每 intent 一組中文範本陣列**（隨機挑 intent 再挑該 intent 範本，讓分布有變化）；部分客戶建 1–3 商機（金額隨機、stage 隨機）。批次存。
  - 生成後 `sentimentIntentService.analyzeMissing(false)`（deterministic 批次）。
  - 回 DemoStats。
- [ ] **B2 DevController**：`@RestController @RequestMapping("/api/dev")`，`POST /generate-demo-data`（`@RequestParam(defaultValue="200") int customers`）→ 回 DemoStats。
- [ ] **B3 SecurityConfig**：在 `/api/ai/**` 等規則旁加 `requestMatchers("/api/dev/**").hasRole("ADMIN")`（放 authenticated 萬用前）。
- [ ] **B4 DemoDataIntegrationTest**（extends PostgresTestBase）：`generate(5)` → interactions 與 interaction_insights 筆數成長、每新互動有 insight；intent 分布含多類。
- [ ] **B5 驗證**：`mvn -pl backend -Dtest=DemoDataIntegrationTest test` 綠。

---

## Task C：雷達查詢端點 + 客戶詳情情緒 + 整合測試

**Files:** `service/SentimentService.java`（或併入 DashboardService）、改 `api/DashboardController.java`、`api/Dtos.java`（SentimentRadarResponse + 子型別、InteractionResponse 加欄位）、改 `service/CustomerService.java`（getDetail join insights）、`service/CustomerMapper.java`（若互動映射在此）、`test/.../api/SentimentRadarIntegrationTest.java`

- [ ] **C1 DTOs**：`Dtos.InteractionResponse` 加 nullable `String sentiment, String intent`（最後欄位，更新所有建構點，含 CustomerMapper / DemoData / addInteraction 回傳）。新增 `SentimentRadarResponse(List<IntentCount> intentDistribution, List<SentimentTrendPoint> sentimentTrend, List<HighRiskInteraction> highRiskInteractions, List<ChurnRadarItem> churnRadar, List<PriorityCareItem> priorityCare)` 與子 record（`IntentCount(String intent, long count)`、`SentimentTrendPoint(String month, long positive, long neutral, long negative)`、`HighRiskInteraction(Long customerId, String customerName, java.time.LocalDateTime occurredAt, String type, String intent, String sentiment, String content)`、`ChurnRadarItem(Long customerId, String name, long negativeCount, long churnSignalCount, long complaintCount, int score)`、`PriorityCareItem(Long customerId, String name, String reason)`）。
- [ ] **C2 SentimentService**：以 `InteractionInsightRepository` 聚合產出 5 區塊（intent 分布、近 12 月情緒趨勢補空月、高風險互動 top 20、流失雷達 top 20 加權排序、優先關懷 top 10 附理由）。可用 native @Query。
- [ ] **C3 端點**：`DashboardController` 加 `GET /api/dashboard/sentiment`（authenticated）→ SentimentService.radar()。
- [ ] **C4 客戶詳情**：`CustomerService.getDetail` 查該客戶各互動的 insight（一次撈 map by interactionId），填入 InteractionResponse 的 sentiment/intent（無則 null）。
- [ ] **C5 SentimentRadarIntegrationTest**（extends PostgresTestBase）：`DemoDataService.generate(8)` → `GET /api/dashboard/sentiment` 帶 token → 5 區塊存在且 intentDistribution 非空；`GET /api/customers/{id}` 互動含 sentiment/intent（至少部分非空）。
- [ ] **C6 驗證**：`mvn -pl backend test` 全量綠（既有 44 + SP6 新增）。

---

## Task D：前端（5 視覺 + 時間線標籤 + 生成鈕）

**Files:** `frontend/src/types.ts`、`api.ts`、`features/dashboard/components/SentimentRadarSection.tsx`(新)、改 `features/dashboard/DashboardPage.tsx`、改 `features/customers/components/Timeline.tsx`、styles.css、`e2e/sp1-smoke.spec.ts`

- [ ] **D1 types**：`SentimentRadarResponse` 與子型別；`InteractionResponse`（若前端有對應型別/CustomerDetail.interactions）加 `sentiment?`/`intent?`。
- [ ] **D2 api**：`fetchSentimentRadar()`、`generateDemoData(customers)`（POST /api/dev/generate-demo-data）。
- [ ] **D3 SentimentRadarSection**：5 視覺（意圖分布長條、情緒趨勢、高風險互動清單、流失雷達排序、優先關懷清單），點客戶跳操作頁（沿用 RfmSection 模式 onSelectCustomer）。
- [ ] **D4 DashboardPage**：進頁載入 `fetchSentimentRadar`，渲染 SentimentRadarSection；ADMIN 顯示「產生示範資料」鈕（呼叫 generateDemoData(200) 後重載）。
- [ ] **D5 Timeline**：每則互動顯示情緒色點 + 意圖中文標籤（intent→中文 label map；sentiment→色）。
- [ ] **D6 styles** + **D7 build**：`pnpm run build` 綠。
- [ ] **D8 smoke**：擴充 `sp1-smoke.spec.ts` 斷言儀表板出現情緒雷達區塊（`[data-promo-chart="sentiment-intent"]` 或相應 selector）。

---

## Task E：部署 dev + 端到端驗證

- [ ] **E1**：全量後端測試綠；前端 build 綠。
- [ ] **E2**：重啟 dev 後端（套 V8）；以 `POST /api/dev/generate-demo-data?customers=200`（ADMIN token）灌資料。
- [ ] **E3**：Playwright 煙霧測試綠（含情緒雷達區塊）。
- [ ] **E4**：更新 `docs/roadmap-progress.md`（SP6 ✅）。

---

## 自我審查
- spec §3.1 資料模型→A；§3.2 分析→A；§3.3 生成器→B；§3.4 雷達→C；§3.5 客戶詳情→C；§4 前端→D；§5 測試→A5/B4/C5/D8。
- sentiment 不接 governance（spec §1 非目標）→ A3。
- 批次 deterministic 避免大量真 LLM→A3/B1。
- 無 git commit。
