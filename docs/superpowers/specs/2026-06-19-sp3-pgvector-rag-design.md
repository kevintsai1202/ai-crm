# SP3 設計規格：真 pgvector RAG（Voyage embedding + 向量檢索）

> 子專案：SP3（路線圖見 `docs/roadmap-progress.md`）
> 建立日期：2026-06-19
> 依據：`docs/consulting-review.md`（高風險 2：RAG 是假的）、`docs/crm-ai-consultant-analysis.md`（缺點 5 + 第三階段）
> 經驗參照：`skill-spring-boot-testing.md`（Flyway/Testcontainers）、`skill-embabel-spring-ai-dev.md`（Spring AI 2.0 / Boot 4）

---

## 1. 目標與成功標準

**目標**：把假 RAG（`findTop3ByOrderBySimilarityHintDesc`，與提問無關）換成真向量檢索：用 Voyage embedding 將知識文件與使用者查詢轉成向量，存進 pgvector，以 cosine 距離取回**與提問相關**的 top-K 引用。

**成功標準**：
1. 知識文件有 embedding 欄位（pgvector `vector(1024)`），由 Voyage（或 fallback）產生。
2. AI 對話/評估的引用改為「依查詢語意」的向量檢索結果，不同提問取回不同引用。
3. 無 `VOYAGE_API_KEY` 時走 deterministic fallback embedding，demo / 測試不依賴外部 API。
4. 整合測試改用 Testcontainers Postgres（pgvector 映像），SP2 既有測試全數遷移後仍綠。
5. `mvn -pl backend test` 全綠；向量檢索的相關性有測試驗證。

**非目標**：
- 不做 chunking（文件級 embedding；文件本就是短片段）。
- 不換掉 chat LLM（仍用既有 OpenAI/aihub 設定）。
- 不做知識文件 CRUD UI（僅後端檢索 + 重建索引端點）。
- 不保留 H2 測試路徑（整合測試一律 Postgres）。
- **不做對話（chat）的落庫與向量化**：對話記憶（chat 歷史落庫 + 語意向量記憶）屬 SP5，將複用本 SP 建立的 `EmbeddingClient` + pgvector 基礎。SP3 的向量化範圍僅限「知識庫文件」。

---

## 2. 架構總覽

```
使用者提問 ──embed(query)──┐
                          ▼
知識文件 ──embed(document)──► knowledge_documents.embedding (vector 1024)
                          │
                          ▼  ORDER BY embedding <=> queryVec  LIMIT k
                     top-K 引用（cosine 相似度）──► grounding context ──► LLM
```

- **EmbeddingClient 介面**：`List<float[]> embed(List<String> texts, InputType type)`（type = QUERY / DOCUMENT）。
- **VoyageEmbeddingClient**（@Primary, production）：有 `VOYAGE_API_KEY` 時呼叫 Voyage REST（`voyage-4-lite`，1024 維）；無 key 時委派 deterministic fallback。
- **DeterministicEmbedding**（fallback）：以文字 hash 產生穩定的 1024 維正規化向量，讓無金鑰時仍可運作（相似度非語意級，但流程完整、可重現）。
- **KnowledgeVectorRepository**（JdbcTemplate）：`updateEmbedding(id, vec)`、`searchTopK(queryVec, k)`。
- **KnowledgeIndexer**（ApplicationReadyEvent）：啟動時為 embedding 為 null 的文件補算向量（idempotent）；另提供 `POST /api/ai/knowledge/reindex`（ADMIN）強制重建。
- **InsightService.loadCitations(query)**：embed 查詢 → 向量檢索 → 引用；若無任何 embedding（極端情況）graceful fallback 回舊的 similarityHint 排序。

---

## 3. 資料庫變更

**Flyway `V5__add_knowledge_embedding.sql`**（放正常 `db/migration`，prod 與 Testcontainers 皆 Postgres）：
```sql
-- 啟用 pgvector 並為知識文件加 1024 維 embedding 欄位（Voyage voyage-4-lite 預設維度）
create extension if not exists vector;
alter table knowledge_documents add column embedding vector(1024);
-- HNSW cosine 索引，加速近似最近鄰檢索
create index idx_knowledge_embedding on knowledge_documents using hnsw (embedding vector_cosine_ops);
```

- embedding 欄位**不映進 `KnowledgeDocument` JPA 實體**（避免 Hibernate vector 型別問題）；`ddl-auto:validate` 不會因 DB 多欄位而失敗。
- 向量寫入/檢索一律走 `KnowledgeVectorRepository`（JdbcTemplate native SQL，含 `cast(? as vector)`）。

---

## 4. 元件設計

### 4.1 EmbeddingClient（介面）
```
enum InputType { QUERY, DOCUMENT }
List<float[]> embed(List<String> texts, InputType type)
```

### 4.2 VoyageEmbeddingClient（@Primary）
- 設定：`app.voyage.api-key` `${VOYAGE_API_KEY:}`、`app.voyage.model` `${VOYAGE_MODEL:voyage-4-lite}`、`app.voyage.url` `${VOYAGE_URL:https://api.voyageai.com/v1/embeddings}`、`app.voyage.dimension` 1024。
- 有 key：`RestClient` POST `{ input: texts, model, input_type: "query"|"document" }`，Header `Authorization: Bearer <key>`；解析 `data[].embedding` → `float[]`。逾時/錯誤 → log + fallback。
- 無 key 或失敗：委派 `DeterministicEmbedding`。
- 維度防呆：回傳向量長度需為 1024，否則 fallback。

### 4.3 DeterministicEmbedding（fallback）
- 對每段文字以穩定雜湊（如逐 token hash 散佈到 1024 維 bucket）產生向量並 L2 正規化。
- 同輸入恆得同向量（可重現），讓無金鑰 demo / 測試流程完整。

### 4.4 KnowledgeVectorRepository（JdbcTemplate）
- `void updateEmbedding(long id, float[] vec)`：`update knowledge_documents set embedding = cast(? as vector) where id = ?`（vec 轉成 `[0.1,0.2,...]` 字串）。
- `List<Citation> searchTopK(float[] queryVec, int k)`：
  `select id, doc_type, title, content, 1 - (embedding <=> cast(? as vector)) as similarity from knowledge_documents where embedding is not null order by embedding <=> cast(? as vector) limit ?`。

### 4.5 KnowledgeIndexer
- `@EventListener(ApplicationReadyEvent)`：查 embedding 為 null 的文件，批次 embed(DOCUMENT) 後 updateEmbedding。idempotent（已索引的略過）。
- `reindexAll()`：重算全部（供 ADMIN 端點）。

### 4.6 端點
- `POST /api/ai/knowledge/reindex`（ADMIN）→ 觸發 `reindexAll()`，回傳處理筆數。SecurityConfig 加 `POST /api/ai/knowledge/** → hasRole("ADMIN")`（或沿用 `/api/ai/**` authenticated + 端點內檢查；優先用 SecurityConfig URL 規則）。

### 4.7 InsightService 串接
- `loadCitations()` → `loadCitations(String query)`：embed(QUERY) → `searchTopK(vec, 3)` → `CitationResponse`（similarity 用 cosine 相似度）。
- `chat`/`streamChat`：以 `request.message()` 當 query。
- `customerAssessment`：以評估指令或客戶名稱+產業當 query。
- graceful fallback：searchTopK 回空（無任何 embedding）時，退回 `findTop3ByOrderBySimilarityHintDesc()`，確保不致無引用。

---

## 5. 測試 DB：H2 → Testcontainers Postgres

- 新增測試相依：`org.springframework.boot:spring-boot-testcontainers`、`org.testcontainers:postgresql`、`org.testcontainers:junit-jupiter`（test scope，版本由 Boot BOM）。
- 新增 `PostgresTestBase`（抽象基底）：
  ```
  @SpringBootTest(properties = "spring.ai.openai.api-key=")
  @ActiveProfiles("test")
  @Testcontainers
  abstract class PostgresTestBase {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres"));
  }
  ```
- `application-test.yml`：移除 H2 datasource（改由 `@ServiceConnection` 注入容器），保留 `flyway.enabled`、`ddl-auto:validate`、`ai.openai.api-key:""`、`app.voyage.api-key:""`。
- **SP2 既有 5 個 Spring context 測試**（SecurityIntegrationTest / AuthIntegrationTest / DashboardIntegrationTest / AiFallbackIntegrationTest / AiCrmApplicationContextTest）改為**繼承 `PostgresTestBase`**、移除各自的 `@SpringBootTest`/`@ActiveProfiles`（由基底提供）。功能斷言不變。
- 測試以 `@TestConfiguration` 提供 `@Primary` 的 fake `EmbeddingClient`，把已知文字映射到已知向量 → 向量檢索測試可重現、零外部呼叫；KnowledgeIndexer 啟動時也用此 fake 對 seed 文件建索引。

---

## 6. 測試清單

**單元**：
- `DeterministicEmbeddingTest`：同輸入同向量、維度 1024、已正規化。
- `VoyageEmbeddingClientTest`：用 `MockRestServiceServer` 驗請求 body（input/model/input_type）與解析 `data[].embedding` → float[1024]；無 key 時走 fallback（回 1024 維、不發 HTTP）。

**整合（Testcontainers）**：
- `KnowledgeVectorRetrievalTest`：用 repository 寫入數筆已知向量，`searchTopK` 回傳依 cosine 距離排序的最近鄰；相似度欄位合理（0~1）。
- `RagRelevanceTest`：注入 fake EmbeddingClient（query「續約」↦ 接近「續約風險話術」文件的向量），經 `InsightService.chat` 取回的引用 top1 是續約文件 → 證明「引用隨提問改變」。
- SP2 遷移後測試：Security/Auth/Dashboard/AiFallback/ContextLoads 全綠（繼承 PostgresTestBase）。

---

## 7. 設定與 .env

- `.env` 既有：`VOYAGE_API_KEY` / `VOYAGE_MODEL=voyage-4-lite` / `VOYAGE_URL`。
- `application.yml` 加：
  ```yaml
  app:
    voyage:
      api-key: ${VOYAGE_API_KEY:}
      model: ${VOYAGE_MODEL:voyage-4-lite}
      url: ${VOYAGE_URL:https://api.voyageai.com/v1/embeddings}
      dimension: 1024
  ```

---

## 8. 套件異動（pom.xml）

- 新增（test scope）：`spring-boot-testcontainers`、`testcontainers-postgresql`、`testcontainers-junit-jupiter`。
- 既有 `flyway-database-postgresql`、JdbcTemplate（spring-boot-starter-data-jpa 已含 spring-jdbc）足夠；**無需** pgvector 的 Java client 套件（用 native SQL + cast）。
- 不加 Spring AI vector store starter（採手動方案）。

---

## 9. 風險與緩解

| 風險 | 緩解 |
|------|------|
| Testcontainers 需 Docker，CI/本機無 Docker 則整合測試失敗 | 本機已跑 Docker；單元測試不需 Docker 仍可獨立驗證核心邏輯。文件註明整合測試前置需 Docker |
| Voyage voyage-4-lite 實際維度 ≠ 1024 | 啟動/索引時驗證向量長度，不符則 log error 並 fallback；維度集中設定，必要時改一處 |
| pgvector HNSW 索引語法在映像版本不支援 | `pgvector/pgvector:pg16` 內建新版 pgvector 支援 hnsw；若失敗改 ivfflat 或先不建索引（資料量小，順序掃描可接受） |
| 啟動索引對 seed 文件呼叫真 Voyage（本機有 key）耗時/花費 | 文件級、僅數筆、僅 null 才算、idempotent；測試用 fake client 不外呼 |
| Hibernate 對 vector 欄位報錯 | embedding 不映進實體；只用 JdbcTemplate 存取 |
| .env 真 Voyage key 滲入測試 | 測試 profile `app.voyage.api-key:""` + 注入 fake EmbeddingClient（@Primary）雙保險 |

---

## 10. 完成後

- 更新 `docs/roadmap-progress.md`：SP3 → ✅，目前在 SP4。
- 視情況回寫 `skill-spring-boot-testing.md`（Testcontainers + pgvector）與新增 RAG 經驗。
- 以 writing-plans 展開實作計畫。
