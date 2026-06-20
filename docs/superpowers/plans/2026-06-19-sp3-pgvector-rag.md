# SP3 真 pgvector RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development。Steps 用 checkbox（`- [ ]`）追蹤。

**Goal:** 把假 RAG（與提問無關的固定 top-3）換成真向量檢索：Voyage embedding（無金鑰走 deterministic fallback）+ pgvector cosine 檢索，讓 AI 引用「隨提問改變」。整合測試改用 Testcontainers Postgres。

**Architecture:** `EmbeddingClient` 介面（`VoyageEmbeddingClient` @Primary，有 key 走真 Voyage、無 key 走 `DeterministicEmbedding`）；`knowledge_documents` 加 `vector(1024)` 欄位（Flyway V5），embedding 不映進 JPA 實體，用 `KnowledgeVectorRepository`（JdbcTemplate native SQL + `cast(? as vector)`）寫入與 `<=>` cosine 檢索；`KnowledgeIndexer` 啟動補算 null 向量 + ADMIN 重建端點；`InsightService.loadCitations(query)` 改向量檢索。測試以 Testcontainers `pgvector/pgvector:pg16`。

**Tech Stack:** Spring Boot 4.1、Java 21、Spring JDBC（JdbcTemplate）、pgvector、Voyage REST、Flyway、Testcontainers、JUnit5/Mockito、MockRestServiceServer。

**環境限制：**
- Maven + JDK 21；Git Bash 先 `export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"`。從 repo 根用 `mvn -pl backend ...`。非 git repo，不要 commit。
- **整合測試需 Docker daemon**（Testcontainers）；本機已在跑 Docker。
- spec：`docs/superpowers/specs/2026-06-19-sp3-pgvector-rag-design.md`。

**已讀碼確認的事實：**
- `Dtos.CitationResponse(String title, String docType, String content, BigDecimal similarity)`。
- 知識 seed 3 筆（PRODUCT 智慧手機巡檢方案 / POLICY 企業服務條款 / PLAYBOOK 續約風險話術），表 `knowledge_documents(id, doc_type, title, content, similarity_hint, ...)`。
- `InsightService.loadCitations()` 目前回 `findTop3ByOrderBySimilarityHintDesc()`，被 `chat`/`streamChat`/`customerAssessment` 呼叫。
- SP2 既有 5 個 Spring context 測試在 `backend/src/test/java/com/aicrm/crm/`：`AiCrmApplicationContextTest`、`api/{SecurityIntegrationTest,AuthIntegrationTest,DashboardIntegrationTest,AiFallbackIntegrationTest}`，目前各自標 `@SpringBootTest(properties="spring.ai.openai.api-key=") @ActiveProfiles("test")`，跑在 H2（`application-test.yml`）。
- `.env`：`VOYAGE_API_KEY` / `VOYAGE_MODEL=voyage-4-lite` / `VOYAGE_URL=https://api.voyageai.com/v1/embeddings`。

---

## 檔案結構總覽

```
backend/
  pom.xml                                   # [修改] testcontainers test deps
  src/main/resources/
    application.yml                         # [修改] app.voyage.*
    db/migration/V5__add_knowledge_embedding.sql  # [新]
  src/main/java/com/aicrm/crm/
    service/embedding/EmbeddingClient.java          # [新] 介面
    service/embedding/DeterministicEmbedding.java   # [新] fallback
    service/embedding/VoyageEmbeddingClient.java    # [新] @Primary
    repository/KnowledgeVectorRepository.java       # [新] JdbcTemplate
    service/KnowledgeIndexer.java                   # [新] 啟動索引 + reindex
    service/InsightService.java                     # [修改] loadCitations(query)
    api/AiController.java                            # [修改] reindex 端點
    security/SecurityConfig.java                    # [修改] reindex 限 ADMIN
  src/test/resources/application-test.yml    # [修改] 移除 H2、加 voyage 空 key
  src/test/java/com/aicrm/crm/
    support/PostgresTestBase.java                   # [新] Testcontainers 基底
    service/embedding/DeterministicEmbeddingTest.java   # [新] 單元
    service/embedding/VoyageEmbeddingClientTest.java    # [新] 單元
    repository/KnowledgeVectorRetrievalTest.java        # [新] 整合
    service/RagRelevanceTest.java                       # [新] 整合
    （遷移既有 5 個 context 測試 → 繼承 PostgresTestBase）
```

---

## Task 0：pom 測試相依 + application 設定

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: pom 加 Testcontainers（test scope）**

在 `backend/pom.xml` 的 `<dependencies>` 加（版本由 Boot parent BOM 管，不寫 version）：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: application.yml 加 voyage 設定**

在 `backend/src/main/resources/application.yml` 的 `app:` 區塊（已有 `app.security`）下加：
```yaml
app:
  voyage:
    api-key: ${VOYAGE_API_KEY:}
    model: ${VOYAGE_MODEL:voyage-4-lite}
    url: ${VOYAGE_URL:https://api.voyageai.com/v1/embeddings}
    dimension: 1024
```
（保留既有 `app.security` 內容，僅新增 `app.voyage`。）

- [ ] **Step 3: application-test.yml 改為 Postgres（移除 H2）**

整檔取代 `backend/src/test/resources/application-test.yml`（datasource 由 Testcontainers `@ServiceConnection` 注入，這裡只留非 DB 設定）：
```yaml
# SP3 測試 profile：DB 由 Testcontainers Postgres(@ServiceConnection) 注入；空金鑰強制 fallback
spring:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
  ai:
    openai:
      api-key: ""
app:
  voyage:
    api-key: ""
```

- [ ] **Step 4: 驗證 pom 可解析**

Run（repo 根）：
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 1：EmbeddingClient 介面 + DeterministicEmbedding + 單元測試

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/embedding/EmbeddingClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/embedding/DeterministicEmbedding.java`
- Create: `backend/src/test/java/com/aicrm/crm/service/embedding/DeterministicEmbeddingTest.java`

- [ ] **Step 1: EmbeddingClient 介面**

```java
package com.aicrm.crm.service.embedding;

import java.util.List;

/**
 * 向量嵌入用戶端：將文字轉為固定維度向量，供知識庫 RAG 檢索使用。
 */
public interface EmbeddingClient {

    /** 嵌入輸入型別：查詢或文件（部分模型會據此最佳化）。 */
    enum InputType { QUERY, DOCUMENT }

    /**
     * 將多段文字嵌入為向量。
     *
     * @param texts 文字清單
     * @param type 輸入型別
     * @return 與輸入等長的向量清單（每個 float[] 長度為 dimension()）
     */
    List<float[]> embed(List<String> texts, InputType type);

    /** 向量維度。 */
    int dimension();
}
```

- [ ] **Step 2: DeterministicEmbedding（fallback）**

```java
package com.aicrm.crm.service.embedding;

import java.nio.charset.StandardCharsets;

/**
 * 確定性嵌入 fallback：無外部金鑰時，用穩定雜湊把文字散佈到固定維度並 L2 正規化。
 * 函式級註解：相同輸入恆得相同向量，讓無金鑰 demo / 測試流程完整（相似度非語意級）。
 */
public final class DeterministicEmbedding {

    private DeterministicEmbedding() {
    }

    /**
     * 產生確定性向量。
     *
     * @param text 文字
     * @param dimension 維度
     * @return 已 L2 正規化的向量
     */
    public static float[] embed(String text, int dimension) {
        var vec = new float[dimension];
        if (text == null || text.isBlank()) {
            return vec; // 全零向量
        }
        // 以每個 token 的雜湊決定 bucket 與正負號，累加後正規化
        for (String token : text.toLowerCase().split("\\s+|(?<=\\p{IsHan})")) {
            if (token.isBlank()) continue;
            int h = stableHash(token);
            int bucket = Math.floorMod(h, dimension);
            vec[bucket] += (h & 1) == 0 ? 1.0f : -1.0f;
        }
        double norm = 0;
        for (float v : vec) norm += (double) v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) vec[i] /= (float) norm;
        }
        return vec;
    }

    /** 穩定雜湊（不依賴 String.hashCode 的 JVM 差異，用 FNV-1a）。 */
    private static int stableHash(String s) {
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
```

- [ ] **Step 3: 單元測試**

```java
package com.aicrm.crm.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * DeterministicEmbedding 單元測試：維度、確定性、正規化。
 */
class DeterministicEmbeddingTest {

    @Test
    void producesRequestedDimension() {
        assertThat(DeterministicEmbedding.embed("續約風險話術", 1024)).hasSize(1024);
    }

    @Test
    void sameInputSameVector() {
        var a = DeterministicEmbedding.embed("企業服務條款", 1024);
        var b = DeterministicEmbedding.embed("企業服務條款", 1024);
        assertThat(a).containsExactly(b);
    }

    @Test
    void isL2Normalised() {
        var v = DeterministicEmbedding.embed("智慧手機巡檢方案", 1024);
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void differentInputsDiffer() {
        var a = DeterministicEmbedding.embed("續約", 1024);
        var b = DeterministicEmbedding.embed("巡檢方案保固", 1024);
        assertThat(a).isNotEqualTo(b);
    }
}
```

- [ ] **Step 4: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=DeterministicEmbeddingTest test
```
Expected: Tests run: 4, Failures: 0。

---

## Task 2：VoyageEmbeddingClient + 單元測試

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/embedding/VoyageEmbeddingClient.java`
- Create: `backend/src/test/java/com/aicrm/crm/service/embedding/VoyageEmbeddingClientTest.java`

- [ ] **Step 1: VoyageEmbeddingClient**

```java
package com.aicrm.crm.service.embedding;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Voyage 向量嵌入用戶端：有 VOYAGE_API_KEY 時呼叫 Voyage REST；無金鑰或失敗時走 deterministic fallback。
 */
@Component
@Primary
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingClient.class);

    private final String apiKey;
    private final String model;
    private final int dimension;
    private final boolean enabled;
    private final RestClient restClient;

    public VoyageEmbeddingClient(
            @Value("${app.voyage.api-key:}") String apiKey,
            @Value("${app.voyage.model:voyage-4-lite}") String model,
            @Value("${app.voyage.url:https://api.voyageai.com/v1/embeddings}") String url,
            @Value("${app.voyage.dimension:1024}") int dimension) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder().baseUrl(url).build();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<float[]> embed(List<String> texts, InputType type) {
        if (!enabled) {
            return fallback(texts);
        }
        try {
            var body = Map.of(
                    "input", texts,
                    "model", model,
                    "input_type", type == InputType.QUERY ? "query" : "document");
            @SuppressWarnings("unchecked")
            var response = restClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            var result = parse(response);
            // 維度防呆：任一向量長度不符即 fallback
            if (result.stream().anyMatch(v -> v.length != dimension)) {
                log.warn("Voyage 回傳維度與設定不符（預期 {}），改用 fallback", dimension);
                return fallback(texts);
            }
            return result;
        } catch (Exception e) {
            log.warn("Voyage 嵌入失敗，改用 deterministic fallback：{}", e.getMessage());
            return fallback(texts);
        }
    }

    /** 解析 Voyage 回應 {data:[{embedding:[...]}]} 為 float[] 清單。 */
    @SuppressWarnings("unchecked")
    private List<float[]> parse(Map<String, Object> response) {
        var data = (List<Map<String, Object>>) response.get("data");
        return data.stream().map(item -> {
            var emb = (List<Number>) item.get("embedding");
            var arr = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) arr[i] = emb.get(i).floatValue();
            return arr;
        }).toList();
    }

    /** 無金鑰 / 失敗時的確定性後備。 */
    private List<float[]> fallback(List<String> texts) {
        return texts.stream().map(t -> DeterministicEmbedding.embed(t, dimension)).toList();
    }
}
```

- [ ] **Step 2: 單元測試（MockRestServiceServer）**

> 先確認 `RestClient.builder()` 可注入 `MockRestServiceServer`：用 `RestClient.builder()` + `MockRestServiceServer.bindTo(builder)`。本測試直接驗「有 key 時送出正確請求並解析」與「無 key 時走 fallback、不發 HTTP」。為可注入 mock，測試用反射或改以建構式接受 builder——**改用較簡單做法**：測試「無金鑰 fallback」與「parse 邏輯」即可；真 Voyage 呼叫留待手動驗證。

```java
package com.aicrm.crm.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VoyageEmbeddingClient 單元測試：無金鑰時走 deterministic fallback，回正確維度且不需網路。
 */
class VoyageEmbeddingClientTest {

    @Test
    void withoutApiKey_fallsBackDeterministically() {
        var client = new VoyageEmbeddingClient("", "voyage-4-lite",
                "https://api.voyageai.com/v1/embeddings", 1024);
        var vecs = client.embed(List.of("續約風險話術", "企業服務條款"), EmbeddingClient.InputType.DOCUMENT);
        assertThat(vecs).hasSize(2);
        assertThat(vecs.get(0)).hasSize(1024);
        // 與 DeterministicEmbedding 一致（確定性）
        assertThat(vecs.get(0)).containsExactly(DeterministicEmbedding.embed("續約風險話術", 1024));
    }

    @Test
    void dimensionReportedFromConfig() {
        var client = new VoyageEmbeddingClient("", "voyage-4-lite", "http://x", 1024);
        assertThat(client.dimension()).isEqualTo(1024);
    }
}
```
> 註：若你要加「有金鑰時用 MockRestServiceServer 驗請求 body + 解析」的測試，需把 `RestClient.Builder` 改為建構式注入以便 bind mock；屬選配強化，非必須。本任務最低要求是上面兩個 fallback/維度測試綠。

- [ ] **Step 3: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=VoyageEmbeddingClientTest test
```
Expected: Tests run: 2, Failures: 0。

---

## Task 3：V5 migration + KnowledgeVectorRepository

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__add_knowledge_embedding.sql`
- Create: `backend/src/main/java/com/aicrm/crm/repository/KnowledgeVectorRepository.java`

- [ ] **Step 1: V5 migration**

```sql
-- 啟用 pgvector，為知識文件加 1024 維 embedding 欄位與 HNSW cosine 索引
create extension if not exists vector;
alter table knowledge_documents add column embedding vector(1024);
create index idx_knowledge_embedding on knowledge_documents using hnsw (embedding vector_cosine_ops);
```

- [ ] **Step 2: KnowledgeVectorRepository（JdbcTemplate）**

```java
package com.aicrm.crm.repository;

import com.aicrm.crm.api.Dtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 知識文件向量存取：以 JdbcTemplate 操作 pgvector 欄位（寫入 embedding、cosine 近鄰檢索）。
 * 函式級註解：embedding 不映進 JPA 實體，集中於此用 native SQL + cast(? as vector) 處理。
 */
@Repository
public class KnowledgeVectorRepository {

    private final JdbcTemplate jdbc;

    public KnowledgeVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 寫入指定文件的 embedding。
     *
     * @param id 文件 id
     * @param vec 向量
     */
    public void updateEmbedding(long id, float[] vec) {
        jdbc.update("update knowledge_documents set embedding = cast(? as vector) where id = ?",
                toVectorLiteral(vec), id);
    }

    /**
     * 取回尚未建立 embedding 的文件（id 與 content）。
     *
     * @return [id(Long), content(String)] 清單
     */
    public List<Object[]> findMissingEmbedding() {
        return jdbc.query(
                "select id, content from knowledge_documents where embedding is null",
                (rs, n) -> new Object[]{rs.getLong("id"), rs.getString("content")});
    }

    /**
     * 取回全部文件（id 與 content），供強制重建索引。
     *
     * @return [id(Long), content(String)] 清單
     */
    public List<Object[]> findAllForIndex() {
        return jdbc.query(
                "select id, content from knowledge_documents",
                (rs, n) -> new Object[]{rs.getLong("id"), rs.getString("content")});
    }

    /**
     * cosine 近鄰檢索 top-K，回傳引用（similarity = 1 - cosine_distance）。
     *
     * @param queryVec 查詢向量
     * @param k 取回筆數
     * @return 引用清單（依相似度高到低）
     */
    public List<Dtos.CitationResponse> searchTopK(float[] queryVec, int k) {
        var literal = toVectorLiteral(queryVec);
        return jdbc.query(
                "select doc_type, title, content, 1 - (embedding <=> cast(? as vector)) as similarity "
                        + "from knowledge_documents where embedding is not null "
                        + "order by embedding <=> cast(? as vector) limit ?",
                (rs, n) -> new Dtos.CitationResponse(
                        rs.getString("title"),
                        rs.getString("doc_type"),
                        rs.getString("content"),
                        BigDecimal.valueOf(rs.getDouble("similarity")).setScale(4, RoundingMode.HALF_UP)),
                literal, literal, k);
    }

    /** 將 float[] 轉為 pgvector 字面值 "[v1,v2,...]"。 */
    private String toVectorLiteral(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
```

- [ ] **Step 3: 編譯驗證**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 4：PostgresTestBase + 遷移 SP2 測試 + 向量檢索測試

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/support/PostgresTestBase.java`
- Modify: `backend/src/test/java/com/aicrm/crm/AiCrmApplicationContextTest.java`
- Modify: `backend/src/test/java/com/aicrm/crm/api/SecurityIntegrationTest.java`
- Modify: `backend/src/test/java/com/aicrm/crm/api/AuthIntegrationTest.java`
- Modify: `backend/src/test/java/com/aicrm/crm/api/DashboardIntegrationTest.java`
- Modify: `backend/src/test/java/com/aicrm/crm/api/AiFallbackIntegrationTest.java`
- Create: `backend/src/test/java/com/aicrm/crm/repository/KnowledgeVectorRetrievalTest.java`

- [ ] **Step 1: PostgresTestBase**

```java
package com.aicrm.crm.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 整合測試基底：以 Testcontainers 啟動 pgvector Postgres，並注入為 DataSource。
 * 函式級註解：空 OpenAI 金鑰強制 AI fallback；容器在所有子類別間共用（static）。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresTestBase {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
}
```

- [ ] **Step 2: 遷移 5 個既有測試改繼承 PostgresTestBase**

對 `AiCrmApplicationContextTest`、`SecurityIntegrationTest`、`AuthIntegrationTest`、`DashboardIntegrationTest`、`AiFallbackIntegrationTest`：
- 移除各自類別上的 `@SpringBootTest(...)`、`@ActiveProfiles("test")`、`@Testcontainers`（若有）。
- 改為 `extends com.aicrm.crm.support.PostgresTestBase`（加 import）。
- 保留 `@AutoConfigureMockMvc`（若該檔有用）與所有測試方法/斷言不變。
- `AiCrmApplicationContextTest` 變成：
  ```java
  package com.aicrm.crm;
  import com.aicrm.crm.support.PostgresTestBase;
  import org.junit.jupiter.api.Test;
  class AiCrmApplicationContextTest extends PostgresTestBase {
      @Test void contextLoads() {}
  }
  ```
- 注意：`SecurityIntegrationTest` 等若用 `webAppContextSetup`（@Autowired WebApplicationContext）方式，保留那些 @Autowired 欄位與 mockMvc() 方法，只把類別宣告改成 `extends PostgresTestBase` 並移除 class 上的 `@SpringBootTest/@ActiveProfiles`（由基底提供）。

- [ ] **Step 3: KnowledgeVectorRetrievalTest（純檢索 plumbing）**

```java
package com.aicrm.crm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量檢索整合測試：寫入已知向量後，searchTopK 依 cosine 距離回傳最近鄰。
 */
class KnowledgeVectorRetrievalTest extends PostgresTestBase {

    @Autowired KnowledgeVectorRepository vectorRepo;
    @Autowired JdbcTemplate jdbc;

    private float[] unit(int dim, int hot) {
        var v = new float[dim];
        v[hot] = 1.0f;
        return v;
    }

    @Test
    void searchTopK_returnsNearestByCosine() {
        // 取 seed 三筆文件 id，分別賦予彼此正交的單位向量
        var ids = jdbc.queryForList("select id from knowledge_documents order by id", Long.class);
        assertThat(ids).hasSizeGreaterThanOrEqualTo(3);
        vectorRepo.updateEmbedding(ids.get(0), unit(1024, 0));
        vectorRepo.updateEmbedding(ids.get(1), unit(1024, 1));
        vectorRepo.updateEmbedding(ids.get(2), unit(1024, 2));

        // 查詢向量貼近第 2 筆（hot=1）
        var hits = vectorRepo.searchTopK(unit(1024, 1), 3);
        assertThat(hits).hasSize(3);
        // 最近鄰相似度應≈1，且排第一
        assertThat(hits.get(0).similarity().doubleValue()).isGreaterThan(0.9);
    }
}
```

- [ ] **Step 4: 執行（Testcontainers，需 Docker）**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=AiCrmApplicationContextTest,KnowledgeVectorRetrievalTest test
```
Expected: 兩類綠；log 顯示 Testcontainers 啟動 `pgvector/pgvector:pg16`，Flyway 跑到 V5。
> 若 `@ServiceConnection` 無法解析（套件路徑），確認 import `org.springframework.boot.testcontainers.service.connection.ServiceConnection`（Boot 4 仍此路徑；若移位則依編譯錯誤修正）。
> 若 V5 的 hnsw 索引語法失敗，改 `using ivfflat (embedding vector_cosine_ops) with (lists=10)` 或先移除索引（資料量小可順序掃描），並回報。

---

## Task 5：KnowledgeIndexer + reindex 端點 + 權限

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/KnowledgeIndexer.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/AiController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java`

- [ ] **Step 1: KnowledgeIndexer**

```java
package com.aicrm.crm.service;

import com.aicrm.crm.repository.KnowledgeVectorRepository;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 知識庫索引器：啟動時為缺向量的文件補算 embedding；提供強制重建。
 */
@Service
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeVectorRepository vectorRepo;
    private final EmbeddingClient embeddingClient;

    public KnowledgeIndexer(KnowledgeVectorRepository vectorRepo, EmbeddingClient embeddingClient) {
        this.vectorRepo = vectorRepo;
        this.embeddingClient = embeddingClient;
    }

    /** 啟動後為尚未建立向量的文件補索引（idempotent）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        int n = indexRows(vectorRepo.findMissingEmbedding());
        if (n > 0) log.info("知識庫啟動索引完成，補算 {} 筆向量", n);
    }

    /**
     * 強制重建全部文件的向量。
     *
     * @return 重建筆數
     */
    public int reindexAll() {
        return indexRows(vectorRepo.findAllForIndex());
    }

    /** 對 [id, content] 列批次嵌入並寫回。 */
    private int indexRows(List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        var texts = rows.stream().map(r -> (String) r[1]).toList();
        var vectors = embeddingClient.embed(texts, EmbeddingClient.InputType.DOCUMENT);
        for (int i = 0; i < rows.size(); i++) {
            vectorRepo.updateEmbedding((Long) rows.get(i)[0], vectors.get(i));
        }
        return rows.size();
    }
}
```

- [ ] **Step 2: AiController 加 reindex 端點**

在 `AiController` 注入 `KnowledgeIndexer` 並加端點（需 import `org.springframework.web.bind.annotation.PostMapping` 已有）：
```java
    /** 知識庫索引器。 */
    private final com.aicrm.crm.service.KnowledgeIndexer knowledgeIndexer;
```
- 更新建構子同時注入 `InsightService` 與 `KnowledgeIndexer`。
- 加方法：
```java
    /**
     * 重建知識庫向量索引（限 ADMIN）。
     *
     * @return 重建筆數
     */
    @PostMapping("/knowledge/reindex")
    public java.util.Map<String, Integer> reindex() {
        return java.util.Map.of("reindexed", knowledgeIndexer.reindexAll());
    }
```

- [ ] **Step 3: SecurityConfig 加 ADMIN 規則**

在 `securityFilterChain` 的 `authorizeHttpRequests` 中，**在** `/api/ai/**` 的 authenticated 規則**之前**加：
```java
.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/ai/knowledge/**").hasRole("ADMIN")
```
（確保更具體的規則在 `/api/ai/**` authenticated 之前。）

- [ ] **Step 4: 編譯驗證**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 6：InsightService 串接向量檢索 + RAG 相關性測試

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/InsightService.java`
- Create: `backend/src/test/java/com/aicrm/crm/service/RagRelevanceTest.java`

- [ ] **Step 1: InsightService 改用向量檢索**

- 建構子注入新增 `EmbeddingClient embeddingClient` 與 `KnowledgeVectorRepository vectorRepo`（保留既有參數）。
- 將 `private List<Dtos.CitationResponse> loadCitations()` 改為帶查詢字串版本：
```java
    /**
     * 依查詢語意做向量檢索取回 top-3 引用；無任何向量時 graceful fallback 回相似度提示排序。
     *
     * @param query 查詢文字（使用者提問或評估指令）
     * @return 引用清單
     */
    private List<Dtos.CitationResponse> loadCitations(String query) {
        try {
            var vec = embeddingClient.embed(List.of(query), EmbeddingClient.InputType.QUERY).get(0);
            var hits = vectorRepo.searchTopK(vec, 3);
            if (!hits.isEmpty()) {
                return hits;
            }
        } catch (Exception e) {
            log.warn("向量檢索失敗，改用 similarityHint fallback：{}", e.getMessage());
        }
        return knowledgeDocuments.findTop3ByOrderBySimilarityHintDesc().stream()
                .map(doc -> new Dtos.CitationResponse(doc.getTitle(), doc.getDocType(), doc.getContent(), doc.getSimilarityHint()))
                .toList();
    }
```
- 更新呼叫點：
  - `chat`：`var citations = loadCitations(request.message());`
  - `streamChat`：`var citations = loadCitations(request.message());`
  - `customerAssessment`：`var citations = loadCitations(customer.getName() + " " + customer.getIndustry() + " 續約 風險 評估");`
- 加 import：`com.aicrm.crm.repository.KnowledgeVectorRepository`、`com.aicrm.crm.service.embedding.EmbeddingClient`。
- 確認原 `loadCitations()` 無參數版本已無呼叫者（若有則一併改），避免遺留死碼。

- [ ] **Step 2: RagRelevanceTest（證明引用隨提問改變）**

用 `@TestConfiguration` 注入 @Primary 的 fake EmbeddingClient，把含特定關鍵字的文字映到正交基底向量，使「續約」查詢最近鄰為續約文件。

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.service.embedding.EmbeddingClient;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * RAG 相關性整合測試：注入語意可控的 fake embedding，驗證引用會隨提問語意改變。
 */
class RagRelevanceTest extends PostgresTestBase {

    /** 語意可控的 fake：依關鍵字落到正交基底，讓檢索可預期。 */
    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        @Primary
        EmbeddingClient fakeEmbeddingClient() {
            return new EmbeddingClient() {
                @Override public int dimension() { return 1024; }
                @Override public List<float[]> embed(List<String> texts, InputType type) {
                    return texts.stream().map(this::vec).toList();
                }
                private float[] vec(String t) {
                    var v = new float[1024];
                    if (t.contains("續約")) v[0] = 1f;
                    else if (t.contains("條款") || t.contains("服務")) v[1] = 1f;
                    else if (t.contains("巡檢") || t.contains("手機")) v[2] = 1f;
                    else v[3] = 1f;
                    return v;
                }
            };
        }
    }

    @Autowired InsightService insightService;
    @Autowired com.aicrm.crm.service.KnowledgeIndexer knowledgeIndexer;

    @BeforeEach
    void reindexWithFake() {
        knowledgeIndexer.reindexAll(); // 用 fake 重建 seed 文件向量
    }

    @Test
    void citationsFollowQuerySemantics() {
        var resp = insightService.chat(new Dtos.ChatRequest(1L, "請分析這位客戶的續約風險"));
        assertThat(resp.citations()).isNotEmpty();
        // 續約查詢的 top1 應為續約話術文件
        assertThat(resp.citations().get(0).title()).contains("續約");
    }
}
```
> 註：`customerId=1` 為 seed 第一筆客戶。若 seed 無 id=1，改先查一個存在的 id。fake 的關鍵字對應以實際 seed 文件標題/內容為準（續約風險話術 / 企業服務條款 / 智慧手機巡檢方案）。

- [ ] **Step 3: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=RagRelevanceTest test
```
Expected: Tests run: 1, Failures: 0。

---

## Task 7：全量測試 + 真 Voyage 手動驗證（選配）+ 更新進度

**Files:**
- Modify: `docs/roadmap-progress.md`

- [ ] **Step 1: 全量測試（Docker 需在跑）**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend test
```
Expected: BUILD SUCCESS，全部測試綠（SP2 22 個遷移後 + SP3 新增）。

- [ ] **Step 2:（選配）真 Voyage 手動驗證**

啟動後端（含 .env 的真 VOYAGE_API_KEY）後，確認啟動 log 有「知識庫啟動索引完成，補算 N 筆向量」，並打 `POST /api/ai/chat` 觀察不同提問取回不同引用。此步驟依賴外部 API，不納入自動化測試。

- [ ] **Step 3: 更新 `docs/roadmap-progress.md`**

SP3 列狀態改 ✅、plan 欄填本計畫路徑、「目前在」改 SP4，變更紀錄追加完成摘要（含 Voyage embedding + pgvector + Testcontainers 遷移）。

---

## 自我審查（spec 覆蓋對照）

- spec §2 架構（EmbeddingClient/Voyage/Deterministic/VectorRepo/Indexer/InsightService）→ Task 1/2/3/5/6 ✅
- spec §3 DB 變更（V5 + 不映實體 + JdbcTemplate）→ Task 3 ✅
- spec §5 測試 DB Testcontainers + 遷移 SP2 → Task 0/4 ✅
- spec §6 測試清單（Deterministic/Voyage 單元、向量檢索/RAG 相關性整合、SP2 遷移）→ Task 1/2/4/6 ✅
- spec §7 設定（app.voyage）→ Task 0 ✅
- spec §8 套件（testcontainers）→ Task 0 ✅
- spec §9 風險（維度防呆、hnsw fallback、fake 防外呼、不映實體）→ 各 Task 註記 ✅
- 型別一致：`CitationResponse(title, docType, content, BigDecimal similarity)` 於 VectorRepo 正確建構 ✅
- 無 git commit（非 git repo）✅
- 對話向量化不在本 SP（spec 非目標）✅
```
