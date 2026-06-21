# Manager 業務分析頁 + AI 模組 C 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 Manager 業務分析的前端頁面（模組 B 統計頁）與模組 C 的兩個 AI 功能（團隊整體診斷 + 個別業務 coaching，含後端），讓 MANAGER/ADMIN 能綜觀全公司業務績效並取得 AI 輔導建議。

**Architecture:** 後端模組 B（`GET /api/manager/analytics`）已完成。本計畫補：① 模組 C 後端 AI insight（**同步 JSON**，非 SSE，對齊既有前端 stub 與 Portfolio 評估慣例）：新增 `manager_insight` 快取表、`ManagerInsightService`（點按生成 + upsert 快取 + deterministic fallback）、`ManagerInsightController`（4 個端點，落在 `/api/manager/**` 已受 RBAC 保護）；② 前端模組 B 統計頁 `/team`（KPI 列 + 可排序業務績效表）+ 模組 C 兩個 AI 區塊 + `ManagerRoute` 守衛 + 側邊欄入口；③ 修復 `api.ts` 既有殘缺 AI stub（目前參照未定義型別 `ManagerInsightResponse`，導致 `tsc` 失敗）。

**Tech Stack:** 後端 Java 21 / Spring Boot 4.1 / Spring AI 2.0 / JPA / Flyway / JUnit5 + Testcontainers(PostgresTestBase)。前端 React 19 / TypeScript / Vite / react-router-dom v7 / react-markdown + remark-gfm。

**快取設計決策**：`manager_insight` 以 `(scope, owner_name)` 為邏輯鍵；`scope=TEAM` 時 `owner_name` 存 null，查詢用 `findFirstByScope("TEAM")`；`scope=OWNER` 時用 `findFirstByScopeAndOwnerName("OWNER", ownerName)`。以 `ownerName` 為鍵與 `ManagerAnalyticsService` 分組一致（ownerId 可能為 null）。

---

## 檔案結構

### 後端（新增）
- `backend/src/main/resources/db/migration/V14__add_manager_insight.sql` — 快取表 migration
- `backend/src/main/java/com/aicrm/crm/domain/ManagerInsight.java` — 快取 entity
- `backend/src/main/java/com/aicrm/crm/repository/ManagerInsightRepository.java` — 快取存取
- `backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java` — AI 生成 + 快取 + fallback
- `backend/src/main/java/com/aicrm/crm/api/ManagerInsightController.java` — 4 個端點
- `backend/src/test/java/com/aicrm/crm/service/ManagerInsightServiceTest.java` — fallback + 快取測試

### 後端（修改）
- `backend/src/main/java/com/aicrm/crm/domain/AiCallType.java` — 加 `TEAM_ANALYSIS`、`OWNER_COACHING`
- `backend/src/main/java/com/aicrm/crm/api/Dtos.java` — 加 `ManagerInsightResponse`
- `backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java` — 補 insight 端點權限測試

### 前端（新增）
- `frontend/src/app/ManagerRoute.tsx` — 路由守衛（MANAGER/ADMIN）
- `frontend/src/features/team/TeamAnalyticsPage.tsx` — 統計頁 + 兩個 AI 區塊

### 前端（修改）
- `frontend/src/types.ts` — 加 `OwnerStats`、`TeamSummary`、`ManagerAnalyticsResponse`、`ManagerInsightResponse`
- `frontend/src/api.ts` — 加 `fetchManagerAnalytics`；修復 4 個 insight stub（import 型別 + GET null 強制）
- `frontend/src/App.tsx` — 加 `/team` 路由（ManagerRoute 守衛）
- `frontend/src/app/AppShell.tsx` — 側邊欄對 MANAGER/ADMIN 顯示「📈 業務分析」

---

## 後端：模組 C（AI insight）

### Task 1：V14 migration — manager_insight 快取表

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__add_manager_insight.sql`

- [ ] **Step 1: 建立 migration（不可修改既有 V1~V13）**

```sql
-- 模組 C：Manager AI 分析快取表。scope=TEAM 時 owner_name 為 null；scope=OWNER 時存業務顯示名稱。
CREATE TABLE manager_insight (
    id           BIGSERIAL PRIMARY KEY,
    scope        VARCHAR(16)  NOT NULL,          -- TEAM | OWNER
    owner_name   VARCHAR(255),                   -- OWNER 時的業務名；TEAM 為 null
    content      TEXT         NOT NULL,          -- 產出的 Markdown 報告
    model        VARCHAR(128),                   -- 模型名；fallback 時為 null
    generated_at TIMESTAMP    NOT NULL
);

-- 查快取用：TEAM 走 scope；OWNER 走 (scope, owner_name)
CREATE INDEX idx_manager_insight_scope ON manager_insight (scope);
CREATE INDEX idx_manager_insight_scope_owner ON manager_insight (scope, owner_name);
```

- [ ] **Step 2: 確認檔名與既有遷移連號**

Run: `git -C d:/GitHub/ai-crm status --porcelain backend/src/main/resources/db/migration/`
Expected: 只列出 `?? .../V14__add_manager_insight.sql`，V1~V13 未被修改。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V14__add_manager_insight.sql
git commit -m "feat(manager): V14 migration 新增 manager_insight AI 分析快取表"
```

---

### Task 2：ManagerInsight entity

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/domain/ManagerInsight.java`

- [ ] **Step 1: 建立 entity**

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
 * Manager AI 分析快取：團隊整體診斷（scope=TEAM）與個別業務 coaching（scope=OWNER）的最後一次產出。
 * 「點按生成」時 upsert 此表；進頁先讀此表顯示「上次分析時間」。
 */
@Entity
@Table(name = "manager_insight")
public class ManagerInsight {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 範圍：TEAM（團隊）或 OWNER（個別業務）。 */
    @Column(nullable = false, length = 16)
    private String scope;

    /** OWNER 時的業務顯示名稱；TEAM 時為 null。 */
    @Column(name = "owner_name")
    private String ownerName;

    /** 產出的 Markdown 報告。 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 產出模型名；deterministic fallback 時為 null。 */
    @Column(length = 128)
    private String model;

    /** 產出時間。 */
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ManagerInsight() {
    }

    /**
     * 建立快取列。
     *
     * @param scope 範圍（TEAM/OWNER）
     * @param ownerName 業務名（TEAM 傳 null）
     * @param content Markdown 報告
     * @param model 模型名（fallback 傳 null）
     * @param generatedAt 產出時間
     */
    public ManagerInsight(String scope, String ownerName, String content, String model, Instant generatedAt) {
        this.scope = scope;
        this.ownerName = ownerName;
        this.content = content;
        this.model = model;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getScope() {
        return scope;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /** 重新生成時更新內容、模型與時間（供 upsert 沿用同一列）。 */
    public void update(String content, String model, Instant generatedAt) {
        this.content = content;
        this.model = model;
        this.generatedAt = generatedAt;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/ManagerInsight.java
git commit -m "feat(manager): ManagerInsight 快取 entity"
```

---

### Task 3：ManagerInsightRepository

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/repository/ManagerInsightRepository.java`

- [ ] **Step 1: 建立 repository**

```java
package com.aicrm.crm.repository;

import com.aicrm.crm.domain.ManagerInsight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Manager AI 分析快取存取。
 */
public interface ManagerInsightRepository extends JpaRepository<ManagerInsight, Long> {

    /**
     * 取得團隊診斷快取（scope=TEAM 僅一列）。
     *
     * @param scope 固定傳 "TEAM"
     * @return 快取列（可空）
     */
    Optional<ManagerInsight> findFirstByScope(String scope);

    /**
     * 取得個別業務 coaching 快取。
     *
     * @param scope 固定傳 "OWNER"
     * @param ownerName 業務顯示名稱
     * @return 快取列（可空）
     */
    Optional<ManagerInsight> findFirstByScopeAndOwnerName(String scope, String ownerName);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/repository/ManagerInsightRepository.java
git commit -m "feat(manager): ManagerInsightRepository"
```

---

### Task 4：AiCallType 新增類型 + DTO

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/domain/AiCallType.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`

- [ ] **Step 1: AiCallType 加兩個類型**

將 `AiCallType.java` 整檔改為：

```java
package com.aicrm.crm.domain;

/**
 * AI 呼叫類型：對話、單客戶評估、Portfolio 評估、團隊分析、業務 coaching。
 */
public enum AiCallType {
    /** 一般對話（/api/ai/chat）。 */
    CHAT,
    /** 單一客戶 360 度評估。 */
    ASSESSMENT,
    /** 跨客戶 Portfolio 評估。 */
    PORTFOLIO,
    /** 團隊整體診斷（模組 C，scope=TEAM）。 */
    TEAM_ANALYSIS,
    /** 個別業務 coaching（模組 C，scope=OWNER）。 */
    OWNER_COACHING
}
```

- [ ] **Step 2: Dtos 加 ManagerInsightResponse**

在 `Dtos.java` 的 `ManagerAnalyticsResponse` record 之後（約 L276 後）插入：

```java
    /**
     * Manager AI 分析回應（模組 C）：團隊診斷或個別業務 coaching 的快取/生成結果。
     *
     * @param scope 範圍（TEAM / OWNER）
     * @param ownerName 業務名（TEAM 時為 null）
     * @param content Markdown 報告內容
     * @param model 產出模型名（deterministic fallback 時為 null）
     * @param generatedAt 產出時間
     */
    public record ManagerInsightResponse(
            String scope,
            String ownerName,
            String content,
            String model,
            Instant generatedAt
    ) {}
```

（`Instant` 已 import，無需新增。）

- [ ] **Step 3: 編譯確認**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend -q -o compile
```
Expected: BUILD SUCCESS（若 offline 套件不足改去掉 `-o`）。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/AiCallType.java backend/src/main/java/com/aicrm/crm/api/Dtos.java
git commit -m "feat(manager): AiCallType 加 TEAM_ANALYSIS/OWNER_COACHING + ManagerInsightResponse DTO"
```

---

### Task 5：ManagerInsightService（TDD — 先寫測試）

**Files:**
- Test: `backend/src/test/java/com/aicrm/crm/service/ManagerInsightServiceTest.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java`

> 說明：測試環境無 `OPENAI_API_KEY` → `aiEnabled=false` → 走 deterministic fallback。測試驗證 ①fallback 內容非空、model 為 null、寫入 ai_call_log；②快取 upsert（再次生成沿用同列、`generatedAt` 更新）；③`getTeamInsight` 未生成時回 null、生成後回快取。

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.repository.AiCallLogRepository;
import com.aicrm.crm.repository.ManagerInsightRepository;
import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ManagerInsightService 測試：無金鑰時走 deterministic fallback、寫 ai_call_log、快取 upsert。
 */
class ManagerInsightServiceTest extends PostgresTestBase {

    @Autowired ManagerInsightService service;
    @Autowired ManagerInsightRepository insightRepo;
    @Autowired AiCallLogRepository callLogRepo;

    @Test
    void teamInsight_noKey_fallback_andCached() {
        // 生成前無快取
        assertThat(service.getTeamInsight()).isNull();

        var before = callLogRepo.count();
        var first = service.generateTeamInsight();

        // fallback：content 非空、model 為 null（無金鑰）
        assertThat(first.content()).isNotBlank();
        assertThat(first.model()).isNull();
        assertThat(first.scope()).isEqualTo("TEAM");
        // 寫了一筆 ai_call_log
        assertThat(callLogRepo.count()).isEqualTo(before + 1);
        // 快取已建立
        assertThat(insightRepo.findFirstByScope("TEAM")).isPresent();
        assertThat(service.getTeamInsight()).isNotNull();

        // 再次生成 → upsert，不應新增第二列 TEAM 快取
        var firstGeneratedAt = first.generatedAt();
        var second = service.generateTeamInsight();
        assertThat(insightRepo.findAll().stream().filter(i -> "TEAM".equals(i.getScope())).count()).isEqualTo(1L);
        assertThat(second.generatedAt()).isAfterOrEqualTo(firstGeneratedAt);
    }

    @Test
    void ownerInsight_noKey_fallback_andCached() {
        // 種子資料含業務「林佩珊」（manager@aurora.local 之外的 SALES）；取任一存在的 ownerName
        var ownerName = service.firstOwnerNameForTest();
        assertThat(ownerName).isNotBlank();

        var result = service.generateOwnerInsight(ownerName);
        assertThat(result.content()).isNotBlank();
        assertThat(result.scope()).isEqualTo("OWNER");
        assertThat(result.ownerName()).isEqualTo(ownerName);
        assertThat(service.getOwnerInsight(ownerName)).isNotNull();
    }
}
```

> 註：`firstOwnerNameForTest()` 是 service 上的測試輔助方法（見 Step 3），避免測試硬編特定種子名稱導致脆弱。

- [ ] **Step 2: 執行測試確認失敗**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test -Dtest=ManagerInsightServiceTest
```
Expected: 編譯失敗 / 測試失敗（`ManagerInsightService` 尚不存在）。

- [ ] **Step 3: 實作 ManagerInsightService**

```java
package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.ManagerInsight;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.ManagerInsightRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manager AI 分析服務（模組 C）：團隊整體診斷與個別業務 coaching。
 *
 * <p>採可切換策略，與 {@link InsightService} 一致：設定 OpenAI api-key 時呼叫真實 LLM；
 * 未設定或失敗時 fallback 回 deterministic 摘要。統計數字一律由 {@link ManagerAnalyticsService}
 * 以 Java/DB 計算後餵給 LLM 當 grounding context，防止幻覺。每次呼叫（含 fallback）寫一筆 ai_call_log。
 * 「點按生成」時 upsert {@code manager_insight} 快取，進頁先讀快取顯示上次分析時間。</p>
 */
@Service
public class ManagerInsightService {

    private static final Logger log = LoggerFactory.getLogger(ManagerInsightService.class);

    /** 系統提示詞：約束角色（銷售主管教練）、語言與防幻覺邊界。 */
    private static final String SYSTEM_PROMPT = """
            你是一位資深 B2B 銷售主管教練。請僅根據提供的「團隊/業務統計數字」與「客戶摘要」回答，
            不得自行編造數字、價格或未經確認的承諾。回答需簡潔、條理清楚、可執行，並使用繁體中文。""";

    /** 業務統計聚合（模組 B），作為團隊診斷的資料來源。 */
    private final ManagerAnalyticsService analytics;

    /** 快取存取。 */
    private final ManagerInsightRepository insights;

    /** 客戶存取（個別 coaching 需各客戶的商機/互動）。 */
    private final CustomerRepository customers;

    /** AI 治理：寫入每次呼叫的用量與答案。 */
    private final AiGovernanceService aiGovernance;

    /** OpenAI ChatModel 提供者（Spring AI 2.0 即使無金鑰仍建立 bean）。 */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** 是否啟用真實 OpenAI：以 api-key 是否實際設定為準。 */
    private final boolean aiEnabled;

    public ManagerInsightService(ManagerAnalyticsService analytics,
                                 ManagerInsightRepository insights,
                                 CustomerRepository customers,
                                 AiGovernanceService aiGovernance,
                                 ObjectProvider<ChatModel> chatModelProvider,
                                 @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.analytics = analytics;
        this.insights = insights;
        this.customers = customers;
        this.aiGovernance = aiGovernance;
        this.chatModelProvider = chatModelProvider;
        this.aiEnabled = openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /** callLlm 回傳：答案與模型名（fallback 時 model 為 null）。 */
    private record LlmResult(String answer, String model) {}

    /**
     * 讀團隊診斷快取（未生成回 null）。
     *
     * @return 快取回應或 null
     */
    @Transactional(readOnly = true)
    public Dtos.ManagerInsightResponse getTeamInsight() {
        return insights.findFirstByScope("TEAM").map(this::toResponse).orElse(null);
    }

    /**
     * 讀個別業務 coaching 快取（未生成回 null）。
     *
     * @param owner 業務顯示名稱
     * @return 快取回應或 null
     */
    @Transactional(readOnly = true)
    public Dtos.ManagerInsightResponse getOwnerInsight(String owner) {
        return insights.findFirstByScopeAndOwnerName("OWNER", owner).map(this::toResponse).orElse(null);
    }

    /**
     * 生成團隊整體診斷並 upsert 快取（呼叫 LLM，無金鑰走 fallback）。
     *
     * @return 生成後的回應
     */
    @Transactional
    public Dtos.ManagerInsightResponse generateTeamInsight() {
        var data = analytics.analytics();
        var prompt = buildTeamPrompt(data);
        var fallback = deterministicTeam(data);
        var result = callLlm(AiCallType.TEAM_ANALYSIS, prompt, fallback);
        var saved = upsert("TEAM", null, result.answer(), result.model());
        return toResponse(saved);
    }

    /**
     * 生成個別業務 coaching 並 upsert 快取。
     *
     * @param owner 業務顯示名稱
     * @return 生成後的回應
     */
    @Transactional
    public Dtos.ManagerInsightResponse generateOwnerInsight(String owner) {
        var ownerCustomers = customers.findAll().stream()
                .filter(c -> owner.equals(c.getOwnerName()))
                .toList();
        if (ownerCustomers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此業務：" + owner);
        }
        var prompt = buildOwnerPrompt(owner, ownerCustomers);
        var fallback = deterministicOwner(owner, ownerCustomers);
        var result = callLlm(AiCallType.OWNER_COACHING, prompt, fallback);
        var saved = upsert("OWNER", owner, result.answer(), result.model());
        return toResponse(saved);
    }

    /** 測試輔助：取任一存在的業務名（避免測試硬編種子名稱）。 */
    @Transactional(readOnly = true)
    public String firstOwnerNameForTest() {
        return customers.findDistinctOwners().stream().findFirst().orElse("");
    }

    /**
     * 呼叫 LLM；無金鑰、回空白或例外皆 fallback。每次（含 fallback）寫 ai_call_log（customerId 為 null）。
     *
     * @param type 呼叫類型
     * @param userPrompt 使用者提示詞（已含 grounding context）
     * @param fallbackAnswer deterministic 保底答案
     * @return 答案與模型名
     */
    private LlmResult callLlm(AiCallType type, String userPrompt, String fallbackAnswer) {
        var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
        if (chatModel == null) {
            aiGovernance.record(type, null, null, 0, 0, 0, false, true, fallbackAnswer);
            return new LlmResult(fallbackAnswer, null);
        }
        try {
            var chatResponse = ChatClient.create(chatModel).prompt()
                    .system(SYSTEM_PROMPT).user(userPrompt).call().chatResponse();
            var content = chatResponse == null ? null : chatResponse.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                log.warn("OpenAI {} 回傳空白，改用 fallback", type);
                aiGovernance.record(type, null, null, 0, 0, 0, false, true, fallbackAnswer);
                return new LlmResult(fallbackAnswer, null);
            }
            var metadata = chatResponse.getMetadata();
            var usage = metadata == null ? null : metadata.getUsage();
            var model = metadata == null ? null : metadata.getModel();
            Integer pt = usage == null ? null : usage.getPromptTokens();
            Integer ct = usage == null ? null : usage.getCompletionTokens();
            Integer tt = usage == null ? null : usage.getTotalTokens();
            var answer = content.strip();
            aiGovernance.record(type, null, model, pt, ct, tt, true, true, answer);
            return new LlmResult(answer, model);
        } catch (Exception e) {
            log.warn("OpenAI {} 呼叫失敗，改用 fallback：{}", type, e.getMessage());
            aiGovernance.record(type, null, null, 0, 0, 0, false, true, fallbackAnswer);
            return new LlmResult(fallbackAnswer, null);
        }
    }

    /**
     * upsert 快取：存在則更新同列，否則新增。
     *
     * @param scope TEAM / OWNER
     * @param ownerName 業務名（TEAM 傳 null）
     * @param content Markdown
     * @param model 模型名（fallback 為 null）
     * @return 已存的快取列
     */
    private ManagerInsight upsert(String scope, String ownerName, String content, String model) {
        var now = Instant.now();
        var existing = ownerName == null
                ? insights.findFirstByScope(scope)
                : insights.findFirstByScopeAndOwnerName(scope, ownerName);
        var entity = existing.orElseGet(() -> new ManagerInsight(scope, ownerName, content, model, now));
        if (existing.isPresent()) {
            entity.update(content, model, now);
        }
        return insights.save(entity);
    }

    /** entity → DTO。 */
    private Dtos.ManagerInsightResponse toResponse(ManagerInsight m) {
        return new Dtos.ManagerInsightResponse(m.getScope(), m.getOwnerName(), m.getContent(), m.getModel(), m.getGeneratedAt());
    }

    /**
     * 組裝團隊診斷 prompt：餵全體 OwnerStats 表 + 團隊總覽。
     *
     * @param data 模組 B 分析結果
     * @return prompt 文字
     */
    private String buildTeamPrompt(Dtos.ManagerAnalyticsResponse data) {
        var rows = data.owners().stream().map(o -> String.format(
                "%s｜客戶%d｜高風險%d｜進行中商機%s(%d筆)｜已成交%s(%d筆)｜成交率%.0f%%｜平均互動間隔%s天｜情緒%s｜本月續約%d｜本季續約%d",
                o.ownerName(), o.customerCount(), o.highRiskCount(),
                o.pipelineAmount(), o.activeOpportunityCount(), o.wonAmount(), o.wonCount(), o.winRate() * 100,
                o.avgDaysSinceInteraction() == null ? "—" : String.format("%.0f", o.avgDaysSinceInteraction()),
                o.avgSentimentScore() == null ? "—" : String.format("%.1f", o.avgSentimentScore()),
                o.renewalsThisMonth(), o.renewalsThisQuarter()))
                .collect(java.util.stream.Collectors.joining("\n"));
        var t = data.team();
        return """
                以下是全公司各業務的績效統計（系統計算，請勿更改數字）。

                # 團隊總覽
                客戶總數：%d｜全團隊成交金額：%s｜進行中商機總額：%s｜高風險客戶數：%d｜平均成交率：%.0f%%｜業務人數：%d

                # 各業務統計（依成交金額降序）
                %s

                # 任務
                請產出「團隊業務分析報告」（Markdown 格式），務必涵蓋：
                1. **團隊整體診斷**：top 表現者、落後者、共通問題與建議。
                2. **逐業務點評**：依排行榜對每位業務各給一段「優勢 + 加強建議」。
                使用繁體中文、條理清楚、勿編造數字。
                """.formatted(t.totalCustomers(), t.totalWonAmount(), t.totalPipeline(), t.totalHighRisk(),
                t.avgWinRate() * 100, t.ownerCount(), rows.isBlank() ? "（無業務資料）" : rows);
    }

    /**
     * 組裝個別 coaching prompt：餵該業務名下客戶的商機/風險/近期互動摘要（PII 遮罩）。
     *
     * @param owner 業務名
     * @param ownerCustomers 該業務客戶
     * @return prompt 文字
     */
    private String buildOwnerPrompt(String owner, List<Customer> ownerCustomers) {
        var rows = ownerCustomers.stream().map(c -> {
            var amount = c.getOpportunities().stream().map(Opportunity::getAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            var lastDate = c.getInteractions().stream().map(Interaction::getOccurredAt)
                    .max(Comparator.naturalOrder()).map(d -> d.toLocalDate().toString()).orElse("無");
            return "- " + c.getName() + "｜產業" + c.getIndustry() + "｜風險" + c.getRiskLevel()
                    + "｜商機" + c.getOpportunities().size() + "筆/" + amount
                    + "｜最近互動" + lastDate
                    + "｜續約日" + (c.getRenewalDueDate() == null ? "未定" : c.getRenewalDueDate());
        }).collect(java.util.stream.Collectors.joining("\n"));
        return """
                以下是業務「%s」名下所有客戶的摘要（系統計算，請勿更改數字）。

                # 客戶清單（名稱｜產業｜風險｜商機｜最近互動｜續約日）
                %s

                # 任務
                請以銷售主管視角產出對「%s」的「輔導報告」（Markdown 格式），務必涵蓋：
                1. **該優先跟進的客戶**（附理由）。
                2. **需要立即處理的風險**（高風險 / 續約逾期 / 久未互動）。
                3. **給該業務的具體輔導建議**。
                使用繁體中文、條理清楚、勿編造數字。
                """.formatted(owner, PiiMasker.mask(rows.isBlank() ? "（無客戶）" : rows), owner);
    }

    /**
     * 團隊診斷 deterministic fallback：由統計數字直接彙總。
     */
    private String deterministicTeam(Dtos.ManagerAnalyticsResponse data) {
        var t = data.team();
        var top = data.owners().isEmpty() ? "—" : data.owners().get(0).ownerName();
        return """
                ## 團隊業務分析（教學版摘要）

                - **業務人數**：%d
                - **客戶總數**：%d
                - **全團隊成交金額**：%s
                - **進行中商機總額**：%s
                - **高風險客戶數**：%d
                - **平均成交率**：%.0f%%
                - **成交金額最高**：%s

                > 目前未設定 OpenAI 金鑰，顯示為系統彙總的 deterministic 摘要；設定金鑰後可取得 AI 團隊診斷與逐業務點評。
                """.formatted(t.ownerCount(), t.totalCustomers(), t.totalWonAmount(), t.totalPipeline(),
                t.totalHighRisk(), t.avgWinRate() * 100, top);
    }

    /**
     * 個別 coaching deterministic fallback。
     */
    private String deterministicOwner(String owner, List<Customer> ownerCustomers) {
        long highRisk = ownerCustomers.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        return """
                ## %s 的輔導摘要（教學版）

                - **負責客戶數**：%d
                - **高風險客戶數**：%d

                > 目前未設定 OpenAI 金鑰，顯示為系統彙總的 deterministic 摘要；設定金鑰後可取得 AI 個別輔導建議。
                """.formatted(owner, ownerCustomers.size(), highRisk);
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test -Dtest=ManagerInsightServiceTest
```
Expected: Tests run: 2, Failures: 0, Errors: 0。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java backend/src/test/java/com/aicrm/crm/service/ManagerInsightServiceTest.java
git commit -m "feat(manager): ManagerInsightService 團隊診斷/業務coaching + 快取 + fallback(含測試)"
```

---

### Task 6：ManagerInsightController（4 端點）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/api/ManagerInsightController.java`

> 端點落在 `/api/manager/insights/**`，已由 `SecurityConfig` 的 `/api/manager/**` → `hasAnyRole("MANAGER","ADMIN")` 保護（無需改 SecurityConfig）。GET 讀快取（未生成回 204 No Content）；POST 生成。

- [ ] **Step 1: 建立 controller**

```java
package com.aicrm.crm.api;

import com.aicrm.crm.service.ManagerInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager AI 分析 API（模組 C）：團隊整體診斷與個別業務 coaching。
 * GET 讀快取（未生成回 204）；POST 點按生成（呼叫 LLM，無金鑰走 fallback）。
 * 存取由 SecurityConfig 以 /api/manager/** → hasAnyRole("MANAGER","ADMIN") 保護。
 */
@RestController
@RequestMapping("/api/manager/insights")
public class ManagerInsightController {

    /** AI 分析服務。 */
    private final ManagerInsightService service;

    public ManagerInsightController(ManagerInsightService service) {
        this.service = service;
    }

    /**
     * 讀團隊診斷快取；未生成回 204。
     *
     * @return 快取回應或 204
     */
    @GetMapping("/team")
    public ResponseEntity<Dtos.ManagerInsightResponse> getTeam() {
        var cached = service.getTeamInsight();
        return cached == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(cached);
    }

    /**
     * 生成團隊整體診斷（upsert 快取）。
     *
     * @return 生成後的回應
     */
    @PostMapping("/team")
    public Dtos.ManagerInsightResponse generateTeam() {
        return service.generateTeamInsight();
    }

    /**
     * 讀個別業務 coaching 快取；未生成回 204。
     *
     * @param owner 業務顯示名稱
     * @return 快取回應或 204
     */
    @GetMapping("/owner")
    public ResponseEntity<Dtos.ManagerInsightResponse> getOwner(@RequestParam String owner) {
        var cached = service.getOwnerInsight(owner);
        return cached == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(cached);
    }

    /**
     * 生成個別業務 coaching（upsert 快取）。
     *
     * @param owner 業務顯示名稱
     * @return 生成後的回應
     */
    @PostMapping("/owner")
    public Dtos.ManagerInsightResponse generateOwner(@RequestParam String owner) {
        return service.generateOwnerInsight(owner);
    }
}
```

- [ ] **Step 2: 編譯確認**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend -q compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/ManagerInsightController.java
git commit -m "feat(manager): ManagerInsightController 4 端點(team/owner 讀快取+生成)"
```

---

### Task 7：補 insight 端點權限測試

**Files:**
- Modify: `backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java`

- [ ] **Step 1: 加 SALES 對 insight 端點 403 的測試**

在 `sales_isForbidden` 測試之後加入兩個測試方法（沿用既有 `login` 與 `mockMvc()`）：

```java
    @Test
    void sales_isForbidden_onTeamInsight() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/manager/insights/team").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void manager_canAccessTeamInsight() throws Exception {
        var token = login("manager@aurora.local");
        // 未生成快取時回 204（仍代表「可存取」，非 403）
        mockMvc().perform(get("/api/manager/insights/team").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
```

- [ ] **Step 2: 執行測試**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test -Dtest=ManagerAnalyticsSecurityTest
```
Expected: Tests run: 4, Failures: 0。

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java
git commit -m "test(manager): 補 insight 端點權限測試(SALES 403 / MANAGER 204)"
```

---

## 前端：模組 B 統計頁 + 模組 C AI 區塊

### Task 8：types.ts 新增型別

**Files:**
- Modify: `frontend/src/types.ts`

- [ ] **Step 1: 在 `DashboardLayoutResponse` 之後（檔尾）加入型別**

```typescript
/** 單一業務績效統計（模組 B，對應後端 Dtos.OwnerStats）。 */
export interface OwnerStats {
  ownerId: number | null;
  ownerName: string;
  customerCount: number;
  highRiskCount: number;
  pipelineAmount: number;
  activeOpportunityCount: number;
  wonAmount: number;
  wonCount: number;
  /** 成交率 0~1。 */
  winRate: number;
  /** 平均互動間隔天數（全無互動則 null）。 */
  avgDaysSinceInteraction: number | null;
  /** 平均情緒分數（無分析則 null）。 */
  avgSentimentScore: number | null;
  renewalsThisMonth: number;
  renewalsThisQuarter: number;
}

/** 團隊總覽（模組 B，對應後端 Dtos.TeamSummary）。 */
export interface TeamSummary {
  totalCustomers: number;
  totalWonAmount: number;
  totalPipeline: number;
  totalHighRisk: number;
  /** 各業務成交率平均 0~1。 */
  avgWinRate: number;
  ownerCount: number;
}

/** Manager 業務分析回應（模組 B）。 */
export interface ManagerAnalyticsResponse {
  team: TeamSummary;
  owners: OwnerStats[];
}

/** Manager AI 分析回應（模組 C，對應後端 Dtos.ManagerInsightResponse）。 */
export interface ManagerInsightResponse {
  scope: "TEAM" | "OWNER";
  ownerName: string | null;
  content: string;
  /** 產出模型；deterministic fallback 時為 null。 */
  model: string | null;
  generatedAt: string;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/types.ts
git commit -m "feat(team): types 新增 OwnerStats/TeamSummary/ManagerAnalytics/ManagerInsight"
```

---

### Task 9：api.ts 加分析 fetch + 修復 insight stub

**Files:**
- Modify: `frontend/src/api.ts`

> 現況問題：`fetchTeamInsight` 等 4 個函式參照 `ManagerInsightResponse` 但未 import → `tsc` 失敗；且缺模組 B 的 `fetchManagerAnalytics`。

- [ ] **Step 1: import 新型別**

將 import 區塊（L2-25）中加入 `ManagerAnalyticsResponse` 與 `ManagerInsightResponse`。把：

```typescript
  LoginResponse,
  OpportunityResponse,
```

改為：

```typescript
  LoginResponse,
  ManagerAnalyticsResponse,
  ManagerInsightResponse,
  OpportunityResponse,
```

- [ ] **Step 2: 修 GET insight stub 的 null 強制**

204 No Content 時 axios 的 `data` 為空字串，需強制成 null。將 `fetchTeamInsight`（L104-107）改為：

```typescript
export async function fetchTeamInsight() {
  const { data } = await apiClient.get<ManagerInsightResponse | "">("/manager/insights/team");
  return data || null;
}
```

將 `fetchOwnerInsight`（L122-125）改為：

```typescript
export async function fetchOwnerInsight(owner: string) {
  const { data } = await apiClient.get<ManagerInsightResponse | "">("/manager/insights/owner", { params: { owner } });
  return data || null;
}
```

（`generateTeamInsight` / `generateOwnerInsight` 既有實作正確，僅型別現在可解析，無需改動。）

- [ ] **Step 3: 加模組 B 分析 fetch**

在 `fetchOwnerInsight`/`generateOwnerInsight` 區塊之後加入：

```typescript
/**
 * 讀取 Manager 業務分析（團隊總覽 + 各業務統計，依成交金額降序）。
 */
export async function fetchManagerAnalytics() {
  const { data } = await apiClient.get<ManagerAnalyticsResponse>("/manager/analytics");
  return data;
}
```

- [ ] **Step 4: 型別檢查**

Run:
```powershell
cd frontend; pnpm exec tsc --noEmit
```
Expected: exit 0（修復後 `ManagerInsightResponse` 可解析）。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api.ts
git commit -m "feat(team): api 加 fetchManagerAnalytics + 修復 insight stub 型別與 204 null"
```

---

### Task 10：ManagerRoute 守衛

**Files:**
- Create: `frontend/src/app/ManagerRoute.tsx`

- [ ] **Step 1: 建立守衛（仿 AdminRoute）**

```tsx
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * 業務分析路由守衛：MANAGER 或 ADMIN 可進入，其餘導回儀表板。
 * 函式級註解：用於 /team 路由，前端先擋一層；後端 /api/manager/** 亦以 RBAC 限制（雙重防護）。
 */
export function ManagerRoute() {
  const { user } = useAuth();
  const allowed = user?.role === "MANAGER" || user?.role === "ADMIN";
  return allowed ? <Outlet /> : <Navigate to="/dashboard" replace />;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/ManagerRoute.tsx
git commit -m "feat(team): ManagerRoute 守衛(MANAGER/ADMIN)"
```

---

### Task 11：TeamAnalyticsPage（統計頁 + 兩個 AI 區塊）

**Files:**
- Create: `frontend/src/features/team/TeamAnalyticsPage.tsx`

> 版面：①頂部團隊 KPI 列（TeamSummary）②可排序業務績效表（OwnerStats）③AI-A 團隊診斷區塊（進頁讀快取、按鈕重新分析）④AI-B 個別 coaching（點業務列 → 載入該業務快取/生成）。沿用既有 className（`topbar`/`panel`/`admin-user-table`/`btn-assess`/`btn-secondary`/`sr-empty`），markdown 用 `react-markdown`+`remarkGfm`（與 ReportModal 一致）。

- [ ] **Step 1: 建立頁面**

```tsx
import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  fetchManagerAnalytics,
  fetchTeamInsight,
  generateTeamInsight,
  fetchOwnerInsight,
  generateOwnerInsight
} from "../../api";
import type { ManagerAnalyticsResponse, ManagerInsightResponse, OwnerStats } from "../../types";
import { formatCompactMoney, formatDateTime } from "../../lib/format";

/** 業務績效表可排序的欄位鍵。 */
type SortKey = "wonAmount" | "winRate" | "pipelineAmount" | "customerCount" | "highRiskCount";

/**
 * 業務分析頁（MANAGER/ADMIN）：團隊 KPI + 可排序業務績效表 + 兩個 AI 區塊。
 * 函式級註解：純統計來自 /api/manager/analytics；AI 區塊進頁先讀快取，按鈕才呼叫 LLM 生成並更新快取顯示。
 */
export function TeamAnalyticsPage() {
  const [data, setData] = useState<ManagerAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("wonAmount");
  // 被選取做 coaching 的業務名（null 表示未選）
  const [selectedOwner, setSelectedOwner] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(null);
      try {
        setData(await fetchManagerAnalytics());
      } catch (e) {
        console.error("載入業務分析失敗:", e);
        setError("載入業務分析失敗");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // 依選定欄位降序排序（不可變副本，避免改動原陣列）
  const sortedOwners = useMemo(() => {
    if (!data) return [];
    return [...data.owners].sort((a, b) => Number(b[sortKey]) - Number(a[sortKey]));
  }, [data, sortKey]);

  if (loading) return <div className="panel"><div className="sr-empty">載入中…</div></div>;
  if (error || !data) return <div className="panel"><div className="sr-empty">{error ?? "無資料"}</div></div>;

  const t = data.team;
  return (
    <>
      <section className="topbar">
        <div>
          <p>Manager Console</p>
          <h2>業務分析</h2>
        </div>
      </section>

      {/* 團隊 KPI 列 */}
      <div className="team-kpi-row">
        <Kpi label="客戶總數" value={String(t.totalCustomers)} />
        <Kpi label="全團隊成交金額" value={formatCompactMoney(t.totalWonAmount)} />
        <Kpi label="進行中商機" value={formatCompactMoney(t.totalPipeline)} />
        <Kpi label="高風險客戶" value={String(t.totalHighRisk)} />
        <Kpi label="平均成交率" value={`${Math.round(t.avgWinRate * 100)}%`} />
        <Kpi label="業務人數" value={String(t.ownerCount)} />
      </div>

      {/* 業務績效表 */}
      <div className="panel">
        <div className="panel-head">
          <h3>各業務績效</h3>
          <label className="sort-select">
            排序：
            <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)}>
              <option value="wonAmount">成交金額</option>
              <option value="winRate">成交率</option>
              <option value="pipelineAmount">進行中商機</option>
              <option value="customerCount">客戶數</option>
              <option value="highRiskCount">高風險數</option>
            </select>
          </label>
        </div>
        <table className="admin-user-table">
          <thead>
            <tr>
              <th>業務</th><th>客戶</th><th>高風險</th><th>進行中商機</th>
              <th>已成交</th><th>成交率</th><th>平均互動間隔</th><th>本季續約</th><th>Coaching</th>
            </tr>
          </thead>
          <tbody>
            {sortedOwners.map((o) => (
              <tr key={o.ownerName} className={selectedOwner === o.ownerName ? "row-selected" : ""}>
                <td>{o.ownerName}</td>
                <td>{o.customerCount}</td>
                <td>{o.highRiskCount}</td>
                <td>{formatCompactMoney(o.pipelineAmount)}（{o.activeOpportunityCount}）</td>
                <td>{formatCompactMoney(o.wonAmount)}（{o.wonCount}）</td>
                <td>{Math.round(o.winRate * 100)}%</td>
                <td>{o.avgDaysSinceInteraction == null ? "—" : `${Math.round(o.avgDaysSinceInteraction)} 天`}</td>
                <td>{o.renewalsThisQuarter}</td>
                <td>
                  <button type="button" className="btn-secondary" onClick={() => setSelectedOwner(o.ownerName)}>
                    輔導報告
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* AI-A 團隊整體診斷 */}
      <InsightPanel
        title="🤖 團隊整體診斷"
        emptyHint="尚未產生團隊診斷，點「重新分析」由 AI 產出團隊整體診斷與逐業務點評。"
        load={fetchTeamInsight}
        generate={generateTeamInsight}
        reloadKey="team"
      />

      {/* AI-B 個別業務 coaching */}
      {selectedOwner ? (
        <InsightPanel
          title={`🎯 ${selectedOwner} 的輔導報告`}
          emptyHint={`尚未產生「${selectedOwner}」的輔導報告，點「重新分析」由 AI 產出。`}
          load={() => fetchOwnerInsight(selectedOwner)}
          generate={() => generateOwnerInsight(selectedOwner)}
          reloadKey={`owner:${selectedOwner}`}
        />
      ) : (
        <div className="panel"><div className="sr-empty">點上方任一業務的「輔導報告」可查看 / 產生個別 AI 輔導建議。</div></div>
      )}
    </>
  );
}

/** 單一 KPI 卡。 */
function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi-card">
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value">{value}</strong>
    </div>
  );
}

/**
 * AI 區塊：進頁（或 reloadKey 變更）先讀快取顯示；按「重新分析」呼叫 LLM 生成後更新顯示。
 * 函式級註解：以 reloadKey 區分團隊 / 不同業務，切換業務時自動重讀對應快取。
 */
function InsightPanel({
  title, emptyHint, load, generate, reloadKey
}: {
  title: string;
  emptyHint: string;
  load: () => Promise<ManagerInsightResponse | null>;
  generate: () => Promise<ManagerInsightResponse>;
  reloadKey: string;
}) {
  const [insight, setInsight] = useState<ManagerInsightResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setErr(null);
    load()
      .then((r) => { if (alive) setInsight(r); })
      .catch((e) => { console.error("讀取 AI 分析快取失敗:", e); if (alive) setErr("讀取失敗"); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // reloadKey 變更（切換業務）時重讀；load/generate 為穩定引用之外的依賴刻意忽略
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadKey]);

  /** 點按生成：呼叫 LLM 並更新顯示。 */
  async function handleGenerate() {
    setGenerating(true);
    setErr(null);
    try {
      setInsight(await generate());
    } catch (e) {
      console.error("產生 AI 分析失敗:", e);
      setErr("產生失敗，請稍後再試");
    } finally {
      setGenerating(false);
    }
  }

  return (
    <div className="panel ai-insight-panel">
      <div className="panel-head">
        <h3>{title}</h3>
        <div className="ai-insight-actions">
          {insight ? <small className="ai-insight-meta">
            上次分析：{formatDateTime(insight.generatedAt)}{insight.model ? `（${insight.model}）` : "（教學版摘要）"}
          </small> : null}
          <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
            {generating ? "分析中…" : "重新分析"}
          </button>
        </div>
      </div>
      {loading ? (
        <div className="sr-empty">載入中…</div>
      ) : err ? (
        <div className="sr-empty">{err}</div>
      ) : insight ? (
        <div className="report-markdown">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{insight.content}</ReactMarkdown>
        </div>
      ) : (
        <div className="sr-empty">{emptyHint}</div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: 確認 format.ts 有 `formatCompactMoney`**

Run:
```powershell
cd frontend; pnpm exec tsc --noEmit
```
Expected: exit 0。若 `formatCompactMoney` 不存在則改用 `formatMoney`（檢查 `frontend/src/lib/format.ts` 的 export；CLAUDE.md 記載兩者皆有）。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/team/TeamAnalyticsPage.tsx
git commit -m "feat(team): TeamAnalyticsPage 統計頁(KPI+可排序表)+兩個AI區塊"
```

---

### Task 12：掛路由 + 側邊欄入口

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/app/AppShell.tsx`

- [ ] **Step 1: App.tsx 加 import 與路由**

加 import：

```tsx
import { ManagerRoute } from "./app/ManagerRoute";
import { TeamAnalyticsPage } from "./features/team/TeamAnalyticsPage";
```

在 `AdminRoute` 區塊之前（`<Route element={<AdminRoute />}>` 上方）加入：

```tsx
          <Route element={<ManagerRoute />}>
            <Route path="/team" element={<TeamAnalyticsPage />} />
          </Route>
```

- [ ] **Step 2: AppShell.tsx 側邊欄加入口**

在「客戶工作台」NavLink 之後、ADMIN 帳號管理之前加入（對 MANAGER/ADMIN 顯示）：

```tsx
          {user?.role === "MANAGER" || user?.role === "ADMIN" ? (
            <NavLink to="/team" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>📈 業務分析</NavLink>
          ) : null}
```

- [ ] **Step 3: 型別檢查 + 建置**

Run:
```powershell
cd frontend; pnpm exec tsc --noEmit; pnpm build
```
Expected: 兩者皆 exit 0。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/App.tsx frontend/src/app/AppShell.tsx
git commit -m "feat(team): 掛 /team 路由(ManagerRoute) + 側邊欄業務分析入口(MANAGER/ADMIN)"
```

---

### Task 13：樣式（最小必要）

**Files:**
- Modify: `frontend/src/index.css`（或專案既有全域樣式檔，依實際定位）

> 先確認既有全域樣式檔位置：`grep -rl "team-kpi-row\|kpi-card\|panel-head" frontend/src` 若無，於全域 CSS 末尾補。沿用既有設計變數/色票，不新增框架。

- [ ] **Step 1: 確認全域樣式檔**

Run: `git -C d:/GitHub/ai-crm ls-files frontend/src | grep -E "\.css$"`
Expected: 找到全域樣式檔（如 `frontend/src/index.css`）。以該檔為 target。

- [ ] **Step 2: 補必要樣式**

```css
/* 業務分析頁：團隊 KPI 列 */
.team-kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-card { background: var(--panel-bg, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 10px; padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; }
.kpi-label { font-size: 12px; color: var(--muted, #6b7280); }
.kpi-value { font-size: 22px; font-weight: 700; }
/* 區塊標題列 */
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.ai-insight-actions { display: flex; align-items: center; gap: 12px; }
.ai-insight-meta { color: var(--muted, #6b7280); }
.row-selected { background: var(--row-selected, #eef2ff); }
.report-markdown { line-height: 1.7; }
```

- [ ] **Step 3: 建置確認**

Run: `cd frontend; pnpm build`
Expected: exit 0。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/index.css
git commit -m "style(team): 業務分析頁 KPI 列 / 區塊標題 / 選取列樣式"
```

---

## 驗收（全部任務後）

- [ ] **後端全測試**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test
```
Expected: 全綠（含既有測試未被破壞）。

- [ ] **前端型別 + 建置**

Run: `cd frontend; pnpm exec tsc --noEmit; pnpm build`
Expected: 皆 exit 0。

- [ ] **手動煙霧（依 CLAUDE.md 用 agent-browser / Playwright 腳本，勿一次性互動指令）**
  - manager@aurora.local 登入 → 側邊欄見「📈 業務分析」→ 進 `/team` 見 KPI + 業務表。
  - 按「重新分析」（團隊）→ 顯示報告 + 上次分析時間（無金鑰為教學版摘要）。
  - 點某業務「輔導報告」→ AI-B 區塊載入 / 生成。
  - sales@aurora.local 登入 → 側邊欄無「業務分析」；直接打 `/team` 被導回 `/dashboard`；直接打 `GET /api/manager/analytics` 得 403。

---

## Self-Review 紀錄

- **Spec 覆蓋**：模組 B 前端（§4.3）= Task 8-13；模組 C 後端（§5.1-5.3、§6 V14）= Task 1-7；權限（§2）= ManagerRoute + 既有 SecurityConfig + Task 7 測試。模組 A 已於先前 commit 完成，不在本計畫。
- **與 spec 的偏離（明確記錄）**：spec §5.1 寫「SSE 串流」，本計畫改為**同步 JSON**——因既有前端 stub（`api.ts`）已用 `apiClient.post` 同步呼叫，且與 Portfolio 評估慣例一致，可免動承載 chat/assessment 串流的 `InsightService`，降低回歸風險。功能（點按生成 + 快取 + fallback）不變。
- **型別一致性**：後端 `ManagerInsightResponse(scope, ownerName, content, model, generatedAt)` ↔ 前端 `ManagerInsightResponse` 欄位一致；`OwnerStats`/`TeamSummary` 與 `Dtos` 欄位逐一對應。
- **快取鍵**：TEAM 用 `findFirstByScope`、OWNER 用 `findFirstByScopeAndOwnerName`，避免 null 查詢問題；upsert 沿用同列。
- **無 placeholder**：所有步驟含實際程式碼與指令。
