# SP5 Service 拆分 + RFM + 對話記憶 Implementation Plan

> REQUIRED SUB-SKILL: subagent-driven-development。

**Goal:** 抽 `DashboardService`；加 RFM 分群端點；對話歷史落庫並以時序+語意向量注入 AI context。

**環境：** Maven+JDK21；Git Bash `export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"`；repo 根 `mvn -pl backend ...`；非 git repo 不 commit；整合測試需 Docker（沿用 `com.aicrm.crm.support.PostgresTestBase`）。spec：`docs/superpowers/specs/2026-06-19-sp5-service-split-rfm-chat-memory-design.md`。

**已知事實：**
- `CustomerService` 含 `dashboardSummary()`(113)、`dashboardReports()`(133)、`drilldown(type,key)`(229) + 私有 helper；另有 search/getDetail/create/updateStatus/addInteraction/findAllWithDetail/findDetail。
- `DashboardController` 注入 CustomerService，呼叫 dashboardSummary/dashboardReports/drilldown。
- SP3 模式可複用：`EmbeddingClient`（embed QUERY/DOCUMENT）、`KnowledgeVectorRepository`（JdbcTemplate `cast(? as vector)` + `<=>`）為對話向量的範本。
- `InsightService` chat/streamChat/customerAssessment 用 `buildAnswer`/`buildGroundingContext`；class 級 `@Transactional(readOnly=true)`；已注入 EmbeddingClient、KnowledgeVectorRepository、AiGovernanceService。
- `PiiMasker.mask(String)` 已存在（SP4）。
- `OpportunityStage.CLOSED_LOST` 用於排除。

---

## Task A1：抽出 DashboardService（行為不變）

**Files:** `service/DashboardService.java`(新)、`service/CustomerService.java`(改)、`api/DashboardController.java`(改)

- [ ] **Step 1:** 讀 `CustomerService` 的 `dashboardSummary`/`dashboardReports`/`drilldown` 及其只被這三者使用的私有 helper。建 `DashboardService`（@Service @Transactional(readOnly=true)），把這三個 public 方法與其專用 helper **整段原樣搬移**過去；注入所需 repository（與 CustomerService 相同來源，通常 `CustomerRepository`；若需 findAllWithDetail 等，注入 CustomerService 或 repository，擇行為等價者）。
- [ ] **Step 2:** 從 CustomerService 移除已搬走的方法（若 helper 仍被 CustomerService 其他方法使用則保留）。`DashboardController` 改注入 `DashboardService`。
- [ ] **Step 3:** Run：
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=DashboardIntegrationTest test
```
Expected: 綠（行為不變，SP2 dashboard 測試護航）。

---

## Task A2：RfmService + 端點

**Files:** `service/RfmService.java`(新)、`api/Dtos.java`(改：RfmResponse)、`api/DashboardController.java`(改：/rfm)、`test/.../service/RfmServiceTest.java`(新)

- [ ] **Step 1:** `Dtos.RfmResponse(Long customerId, String name, long recencyDays, long frequency, java.math.BigDecimal monetary, int rScore, int fScore, int mScore, String segment)`。
- [ ] **Step 2:** `RfmService`（@Service @Transactional(readOnly=true)，注入 CustomerService.findAllWithDetail 或 CustomerRepository）：
  - 對每位客戶算 recencyDays（距今最後互動天數；無互動給很大值如 9999）、frequency（互動數）、monetary（非 CLOSED_LOST 商機 amount 合計）。
  - 評分 1–5：用簡單門檻（教學版，可解釋）。R 反向（recency 越小 rScore 越高）。在類別內定義門檻常數並加中文註解。
  - segment 規則（依 r/f/m 高低組合）：例如 r&f&m 高→「冠軍客戶」；r 低（久未互動）&曾高 m→「瀕危流失」；其餘給「忠誠客戶/具潛力/需關注」。規則需可解釋、有中文註解。
  - 回 `List<RfmResponse>`。
- [ ] **Step 3:** DashboardController 加 `@GetMapping("/rfm") public List<Dtos.RfmResponse> rfm()`（注入 RfmService）。`/api/dashboard/**` 已是 authenticated（SecurityConfig），無需改權限。
- [ ] **Step 4:** `RfmServiceTest`（Mockito，自建 Customer+Interaction+Opportunity fixture，參考 SP2 InsightServiceRiskTest 建構方式）：驗 R/F/M 分數與 segment；含邊界（無互動→recency 大、rScore 低；無商機→monetary 0）。≥3 測試。
- [ ] **Step 5:** Run `mvn -pl backend -Dtest=RfmServiceTest test` → 綠；再 `mvn -pl backend -q -DskipTests compile` → SUCCESS。

---

## Task B1：V7 + ChatMessage entity + repositories

**Files:** `db/migration/V7__add_chat_messages.sql`、`domain/ChatMessage.java`、`domain/ChatRole.java`、`repository/ChatMessageRepository.java`、`repository/ChatMessageVectorRepository.java`

- [ ] **Step 1:** V7 migration（見 spec §3：chat_messages 含 embedding vector(1024) + hnsw + (customer_id,created_at) 索引）。
- [ ] **Step 2:** enum `ChatRole { USER, ASSISTANT }`。`ChatMessage` entity（id, customerId(Long, 用欄位非關聯以簡化), role(enum string), content(text), createdAt）——**embedding 不映進實體**（同 KnowledgeDocument 做法），讀既有 entity 對齊時間型別與 AuditableEntity 需求（本表自管 created_at，可不繼承 AuditableEntity）。
- [ ] **Step 3:** `ChatMessageRepository extends JpaRepository<ChatMessage, Long>`：`List<ChatMessage> findTop6ByCustomerIdOrderByCreatedAtDesc(Long customerId)`。
- [ ] **Step 4:** `ChatMessageVectorRepository`（JdbcTemplate，仿 KnowledgeVectorRepository）：
  - `long save(Long customerId, String role, String content, float[] vec, java.time.OffsetDateTime createdAt)`：insert 回傳 id（embedding 用 `cast(? as vector)`；createdAt 型別對齊既有）。
  - `List<String> searchTopK(Long customerId, float[] queryVec, int k)`：`select content from chat_messages where customer_id = ? and embedding is not null order by embedding <=> cast(? as vector) limit ?`，回 content 清單。
- [ ] **Step 5:** Run `mvn -pl backend -q -DskipTests compile` → SUCCESS。

---

## Task B2：ChatMemoryService + InsightService 注入記憶

**Files:** `service/ChatMemoryService.java`(新)、`service/InsightService.java`(改)、`test/.../service/ChatMemoryIntegrationTest.java`(新)

- [ ] **Step 1:** `ChatMemoryService`（@Service，**可寫交易** `@Transactional`，與 InsightService 的 readOnly 隔離）：
  - 注入 `EmbeddingClient`、`ChatMessageVectorRepository`、`ChatMessageRepository`。
  - `void save(Long customerId, ChatRole role, String content)`：embed(DOCUMENT) 後 `vectorRepo.save(...)`（createdAt 用 `OffsetDateTime.now()` 或對齊既有時間來源）。
  - `String recall(Long customerId, String query)`：取「最近 6 則（時序，`findTop6...`，反轉成舊→新）」+「語意相似 3 則（`searchTopK`）」，去重、各截斷（如每則 ≤200 字），組成「# 對話記憶」Markdown 區塊字串；無記憶回空字串。
- [ ] **Step 2:** 修改 `InsightService`：注入 `ChatMemoryService`。
  - `chat(request)` 與 `streamChat(request)`：在組 grounding 前先 `var memory = chatMemory.recall(customerId, message)`，把 memory（**過 `PiiMasker.mask`**）併入 `buildGroundingContext` 或 buildAnswer 的 prompt（在「# 對話記憶」段）。產生 answer 後，`chatMemory.save(customerId, USER, message)` 與 `chatMemory.save(customerId, ASSISTANT, answer)`。
  - 注意順序：先 recall（不含本則）→ 產生 answer → 再 save 本輪 user+assistant，避免把本則當記憶。
  - customerAssessment 可不接記憶（單次報告），保持簡單；如接亦可，但非必須。
- [ ] **Step 3:** `ChatMemoryIntegrationTest`（extends PostgresTestBase，注入 InsightService + ChatMessageRepository + ChatMessageVectorRepository）：
  - 對 customerId=1 連續呼叫 `insightService.chat(new ChatRequest(1L,"第一個問題：續約計畫"))` 與 `chat(new ChatRequest(1L,"第二個問題"))`。
  - 斷言 `chatMessageRepository.count() >= 4`（2 輪 × user+assistant）。
  - 斷言 `chatMessageVectorRepository.searchTopK(1L, <任意向量>, 5)` 回傳非空（訊息有 embedding）。
  - （可選）斷言第二次的記憶 recall 含第一輪內容。
- [ ] **Step 4:** Run：
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=ChatMemoryIntegrationTest,AiFallbackIntegrationTest,RagRelevanceTest test
```
Expected: 全綠（記憶不破壞既有 RAG/fallback）。

---

## Task C：全量測試

- [ ] **Step 1:** Run `mvn -pl backend test` → BUILD SUCCESS，全部測試綠（SP4 後 38 + SP5 新增）。
- [ ] **Step 2:** 回報數字。

---

## 自我審查
- spec §2.1 DashboardService 抽離 → Task A1 ✅
- spec §2.2 RFM → Task A2 ✅
- spec §2.3 對話記憶（時序+語意+PII 遮罩+可寫交易隔離）→ Task B1/B2 ✅
- spec §3 V7 → Task B1 ✅
- spec §4 測試 → Task A2/B2/C ✅
- 行為不變（dashboard）靠 SP2 測試 ✅
- 無 git commit ✅
