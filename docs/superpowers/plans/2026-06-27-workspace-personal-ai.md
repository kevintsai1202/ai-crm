# 我的工作檯個人 AI 實作計畫 (SP9-B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 為「我的工作檯」加入個人 AI 助理：工作推薦（待辦 + AI 總結 + 建商機草稿）、個人問答（總覽 + 可深入單客戶）、AI 歷程，並強制資料隔離。

**Architecture:** 後端新增 `WorkspaceController`(`/api/workspace/**`) + `WorkspaceAiService`；待辦由 DB 規則計算當 grounding，AI 文字以 `ChatClient.stream()` 串流並有 deterministic fallback；重用 `AiGovernanceService`、`InsightService` 串流模板、`manager_insight` 快取表（新增 `SELF` scope）。前端在 `MyWorkspacePage` 加 AI 區塊，複用 `readSseStream`/`AiCallHistoryModal`/`AddOpportunityModal`。

**Tech Stack:** Java 21 / Spring Boot 4.1 / Spring AI 2.0 / Spring Data JPA / Testcontainers(pgvector) / JUnit5 / React 19 + TS + Vite。

**環境提醒：** 後端指令前置 `$env:JAVA_HOME="D:\java\jdk-21"; $env:Path="$env:JAVA_HOME\bin;$env:Path"`。Testcontainers 需 Docker。每個後端任務結束跑該任務測試類別；全部完成後跑 `mvn -pl backend test`（Zeabur CI 會驗）。

**子代理紀律：** 本專案曾出現 general-purpose 子代理擅自建立 CLAUDE.md。機械性實作由主控直接做或用 haiku/code-reviewer；勿讓子代理新增非計畫內檔案。

---

## 檔案結構

**後端（新建）**
- `backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java` — 待辦計算 + scope 解析 + 推薦/問答串流
- `backend/src/main/java/com/aicrm/crm/api/WorkspaceController.java` — `/api/workspace/**` 端點
- 測試：
  - `backend/src/test/java/com/aicrm/crm/service/WorkspaceAiServiceTest.java`
  - `backend/src/test/java/com/aicrm/crm/api/WorkspaceSecurityTest.java`

**後端（修改）**
- `backend/src/main/java/com/aicrm/crm/domain/AiCallType.java` — 加 `WORKSPACE_RECOMMENDATION`, `WORKSPACE_CHAT`
- `backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java` — 加依 subject + 多類型查詢
- `backend/src/main/java/com/aicrm/crm/repository/CustomerRepository.java` — 加 `findByOwnerName`
- `backend/src/main/java/com/aicrm/crm/api/Dtos.java` — 加 workspace 相關 record
- `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java:65` — `/api/workspace/**` 納入 `authenticated()`

**前端（修改）**
- `frontend/src/api.ts` — 加 workspace API 函式
- `frontend/src/features/my-workspace/MyWorkspacePage.tsx` — 加 AI 區塊
- 新建 `frontend/src/features/my-workspace/WorkspaceAiPanel.tsx` — AI 區塊元件
- `frontend/src/types.ts` — 加 workspace 型別
- 新建 `e2e/workspace-ai.spec.ts` 或 `scripts/verify-workspace-ai.ps1` — 驗證腳本

---

## Task 1: AiCallType 列舉與歷程查詢

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/domain/AiCallType.java`
- Modify: `backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java`

- [ ] **Step 1: 加列舉值**

在 `AiCallType` enum 既有值後加入：
```java
    WORKSPACE_RECOMMENDATION,   // 工作檯個人工作推薦
    WORKSPACE_CHAT              // 工作檯個人問答
```

- [ ] **Step 2: 加歷程查詢方法**

在 `AiCallLogRepository` 加入（依 subject 跨多個 callType 倒序）：
```java
    /** 依 subject 與多個呼叫類型查歷程（工作檯：subject=username）。 */
    List<AiCallLog> findByCallTypeInAndSubjectOrderByCreatedAtDesc(
            java.util.Collection<AiCallType> callTypes, String subject);
```

- [ ] **Step 3: 編譯確認**

Run: `mvn -pl backend -q -o test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/AiCallType.java backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java
git commit -m "feat(backend): AiCallType 加 WORKSPACE_* 與 subject 歷程查詢 (SP9-B)"
```

---

## Task 2: Workspace DTO 與 CustomerRepository.findByOwnerName

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Modify: `backend/src/main/java/com/aicrm/crm/repository/CustomerRepository.java`

- [ ] **Step 1: 加 DTO**

在 `Dtos.java` 加入：
```java
    /** 工作檯待辦項目（純 DB 規則計算）。type: HIGH_RISK / RENEWAL_DUE / STALE_OPPORTUNITY。 */
    public record WorkspaceTodoItem(String type, Long customerId, String customerName,
                                    String reason, String severity) {}

    /** AI 建議商機草稿（使用者確認後才建立）。 */
    public record SuggestedOpportunityDraft(Long customerId, String customerName, String name,
                                            String suggestedStage, BigDecimal amount, String rationale) {}

    /** 工作檯問答請求（無 customerId 為總覽；有則深入單客戶）。scope: self / all。 */
    public record WorkspaceChatRequest(String scope, Long customerId,
                                       @jakarta.validation.constraints.NotBlank String message) {}

    /** 工作檯推薦的非串流回應（GET 讀快取用）。summary 可為 null（尚未產生）。 */
    public record WorkspaceRecommendationResponse(String summary, String model, String generatedAt,
                                                  List<WorkspaceTodoItem> todos,
                                                  List<SuggestedOpportunityDraft> drafts) {}
```

- [ ] **Step 2: 加 repository 查詢**

在 `CustomerRepository` 加入：
```java
    /** 載入某業務（依 owner_name 去正規化欄位）負責的所有客戶。 */
    List<Customer> findByOwnerName(String ownerName);
```

- [ ] **Step 3: 編譯確認**

Run: `mvn -pl backend -q -o test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/Dtos.java backend/src/main/java/com/aicrm/crm/repository/CustomerRepository.java
git commit -m "feat(backend): workspace DTO 與 findByOwnerName (SP9-B)"
```

---

## Task 3: WorkspaceAiService.computeTodos（待辦規則 + scope 解析）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/WorkspaceAiServiceTest.java`

- [ ] **Step 1: 寫失敗測試**

`WorkspaceAiServiceTest.java`：
```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.support.PostgresTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** WorkspaceAiService 待辦計算與 scope 隔離測試。 */
@Transactional
class WorkspaceAiServiceTest extends PostgresTestBase {

    @Autowired WorkspaceAiService workspaceAiService;
    @Autowired CustomerRepository customerRepository;

    private final AuthPrincipal sales = new AuthPrincipal("amy@aurora.local", "艾美", Role.SALES);

    @BeforeEach
    void setup() {
        // 艾美的高風險客戶
        var high = new Customer("艾美高風險客", "h@x.com", "0900000001", "11110001", "金融", "艾美");
        high.applyRiskLevel("HIGH");
        high.updateContractDates(LocalDate.now().minusYears(1), LocalDate.now().plusMonths(6), LocalDate.now().plusDays(5));
        // 艾美的逾期商機客戶
        var stale = new Customer("艾美逾期客", "s@x.com", "0900000002", "11110002", "製造", "艾美");
        var opp = new Opportunity(stale, "逾期案", OpportunityStage.PROPOSAL, BigDecimal.valueOf(500000),
                LocalDate.now().minusDays(10), OpportunityType.NEW, com.aicrm.crm.domain.LeadSource.OUTBOUND, 50);
        stale.getOpportunities().add(opp);
        // 別人的高風險客戶（不應出現在艾美的待辦）
        var other = new Customer("他人高風險客", "o@x.com", "0900000003", "11110003", "零售", "別的業務");
        other.applyRiskLevel("HIGH");
        customerRepository.saveAll(List.of(high, stale, other));
        customerRepository.flush();
    }

    @Test
    void computeTodos_salesScope_onlyOwnCustomers() {
        var todos = workspaceAiService.computeTodos(sales, "all"); // SALES 帶 all 仍被強制 self
        // 只含艾美的客戶，不含「他人高風險客」
        assertThat(todos).extracting(Dtos -> Dtos.customerName())
                .doesNotContain("他人高風險客");
        assertThat(todos).anyMatch(t -> "HIGH_RISK".equals(t.type()) && "艾美高風險客".equals(t.customerName()));
        assertThat(todos).anyMatch(t -> "RENEWAL_DUE".equals(t.type()) && "艾美高風險客".equals(t.customerName()));
        assertThat(todos).anyMatch(t -> "STALE_OPPORTUNITY".equals(t.type()) && "艾美逾期客".equals(t.customerName()));
    }
}
```
> 註：若 `Customer` 無 `setRiskLevel`，改用既有設定風險的方法（檢視 `Customer.java:176` 的 `riskLevel` setter 名稱，可能是 `updateRiskLevel` / `assignRiskLevel`）；測試請對齊實際方法名。

- [ ] **Step 2: 執行確認失敗**

Run: `mvn -pl backend test -Dtest=WorkspaceAiServiceTest -o`
Expected: 編譯失敗（WorkspaceAiService 不存在）

- [ ] **Step 3: 實作 WorkspaceAiService（先只 computeTodos + scope）**

```java
package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作檯個人 AI 服務：計算個人待辦、解析資料 scope、產生推薦與問答（串流方法於後續任務補上）。
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceAiService {

    /** 即將續約的判定天數。 */
    private static final int RENEWAL_DUE_DAYS = 14;

    private final CustomerRepository customerRepository;

    public WorkspaceAiService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * 載入呼叫者 scope 內的客戶。SALES 一律強制自己（忽略 requestedScope）；
     * MANAGER/ADMIN：requestedScope="all" 回全部，否則回自己。
     */
    List<Customer> loadScopedCustomers(AuthPrincipal principal, String requestedScope) {
        boolean all = principal.role() != Role.SALES && "all".equalsIgnoreCase(requestedScope);
        return all ? customerRepository.findAll()
                   : customerRepository.findByOwnerName(principal.displayName());
    }

    /**
     * 以純 DB 規則計算個人待辦：高風險、即將續約（14 天內）、逾期未結商機。
     */
    public List<Dtos.WorkspaceTodoItem> computeTodos(AuthPrincipal principal, String scope) {
        var customers = loadScopedCustomers(principal, scope);
        var today = LocalDate.now();
        var todos = new ArrayList<Dtos.WorkspaceTodoItem>();
        for (var c : customers) {
            if ("HIGH".equalsIgnoreCase(c.getRiskLevel())) {
                todos.add(new Dtos.WorkspaceTodoItem("HIGH_RISK", c.getId(), c.getName(),
                        "客戶風險等級為高，建議優先聯繫關懷", "HIGH"));
            }
            var due = c.getRenewalDueDate();
            if (due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(RENEWAL_DUE_DAYS))) {
                todos.add(new Dtos.WorkspaceTodoItem("RENEWAL_DUE", c.getId(), c.getName(),
                        "續約日 " + due + " 即將到期，建議啟動續約", "MEDIUM"));
            }
            for (var o : c.getOpportunities()) {
                boolean open = o.getStage() != OpportunityStage.CLOSED_WON
                        && o.getStage() != OpportunityStage.CLOSED_LOST;
                if (open && o.getExpectedCloseDate() != null && o.getExpectedCloseDate().isBefore(today)) {
                    todos.add(new Dtos.WorkspaceTodoItem("STALE_OPPORTUNITY", c.getId(), c.getName(),
                            "商機「" + o.getName() + "」預計成交日已過（" + o.getExpectedCloseDate() + "），需推進或結案", "MEDIUM"));
                }
            }
        }
        return todos;
    }
}
```

- [ ] **Step 4: 執行確認通過**

Run: `mvn -pl backend test -Dtest=WorkspaceAiServiceTest -o`
Expected: PASS（若 risk setter 名稱不符，先修測試對齊實際方法名再跑）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java backend/src/test/java/com/aicrm/crm/service/WorkspaceAiServiceTest.java
git commit -m "feat(backend): WorkspaceAiService 待辦計算 + scope 隔離 (SP9-B)"
```

---

## Task 4: streamRecommendation（AI 總結 + 商機草稿 + fallback + 治理紀錄）

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java`
- Test: 延用 `WorkspaceAiServiceTest.java`（加非串流 fallback 行為測試）

> 串流本身難單元測試（reactive + 真 LLM）。本任務把「組 prompt grounding」與「deterministic fallback 文字」抽成可測純方法，串流方法薄包裝。測試針對純方法。

- [ ] **Step 1: 寫失敗測試（fallback 文字 + grounding 含待辦）**

在 `WorkspaceAiServiceTest` 加：
```java
    @Test
    void recommendationFallback_containsTodoCustomers() {
        var todos = workspaceAiService.computeTodos(sales, "self");
        String fallback = workspaceAiService.deterministicRecommendation(sales, todos);
        assertThat(fallback).contains("艾美高風險客");      // 接地：列出待辦客戶
        assertThat(fallback).contains("待辦");
    }
```

- [ ] **Step 2: 執行確認失敗**

Run: `mvn -pl backend test -Dtest=WorkspaceAiServiceTest#recommendationFallback_containsTodoCustomers -o`
Expected: 編譯失敗（deterministicRecommendation 不存在）

- [ ] **Step 3: 實作 grounding + fallback + 串流**

注入 `@Value("${spring.ai.openai.api-key:}")`、`ObjectProvider<org.springframework.ai.chat.client.ChatClient.Builder>`（或既有 `ChatModel` provider，對齊 `InsightService` 的注入方式）、`AiGovernanceService`、`@Value("${spring.ai.openai.chat.model:}")`。新增方法：

```java
    /** 把待辦組成 grounding context 文字（餵 LLM，明示不可竄改數字）。 */
    String buildRecommendationPrompt(AuthPrincipal principal, List<Dtos.WorkspaceTodoItem> todos) {
        var sb = new StringBuilder();
        sb.append("你是 CRM 業務助理。以下為業務「").append(principal.displayName())
          .append("」目前由系統計算出的待辦（數字與對象已由資料庫確認，請勿更改）：\n");
        if (todos.isEmpty()) {
            sb.append("（目前無待辦）\n");
        } else {
            for (var t : todos) {
                sb.append("- [").append(t.type()).append("] ").append(t.customerName())
                  .append("：").append(t.reason()).append("\n");
            }
        }
        sb.append("\n請用繁體中文，依急迫性排序，給出今天的工作優先建議（150 字內），不要編造未列出的客戶或數字。");
        return sb.toString();
    }

    /** deterministic fallback：無 AI 或失敗時的工作建議文字。 */
    public String deterministicRecommendation(AuthPrincipal principal, List<Dtos.WorkspaceTodoItem> todos) {
        if (todos.isEmpty()) {
            return "根據 CRM 資料庫，您目前沒有待辦事項，維持既有跟進節奏即可。";
        }
        var sb = new StringBuilder("根據 CRM 資料庫，您有 ").append(todos.size()).append(" 項待辦需處理：\n");
        for (var t : todos) {
            sb.append("• ").append(t.customerName()).append("：").append(t.reason()).append("\n");
        }
        sb.append("建議優先處理標記為高的項目。");
        return sb.toString();
    }
```

串流方法 `streamRecommendation(AuthPrincipal principal, String scope)` 回傳 `SseEmitter`，流程對齊 `InsightService.streamChat`：
1. `var todos = computeTodos(principal, scope);`
2. 先送 `{type:"todos", items: todos}`（用既有 SSE 送出方式，JSON）。
3. `aiEnabled`（金鑰非空）時用 `ChatClient.stream()` 跑 `buildRecommendationPrompt`，逐塊送 `{type:"content", delta}`；累積全文。
4. 商機草稿：可由 LLM 結構化產生；本期先以 deterministic 規則由「STALE_OPPORTUNITY 以外、且該客戶近期無開放商機」產生簡單草稿（避免複雜結構化解析失敗）。送 `{type:"drafts", items: drafts}`。
5. 失敗或無金鑰：送 `deterministicRecommendation` 全文。
6. 結尾 `AiGovernanceService.record(WORKSPACE_RECOMMENDATION, null, principal.username(), model, ...tokens, aiEnabled, false, fullText)`，送 `{type:"callId"}` 與 `[DONE]`。

> 跨執行緒紀律（沿用 backend-dev.md「SSE 真串流」條目）：subscribe callback 前先把 todos/fallback 算成純字串；callback 內只用 String/record。

- [ ] **Step 4: 執行確認通過**

Run: `mvn -pl backend test -Dtest=WorkspaceAiServiceTest -o`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java backend/src/test/java/com/aicrm/crm/service/WorkspaceAiServiceTest.java
git commit -m "feat(backend): 工作檯推薦串流 + grounding + fallback (SP9-B)"
```

---

## Task 5: streamChat（總覽問答 + 單客戶深入 + 可見性驗證）

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java`
- Test: `WorkspaceAiServiceTest.java`

- [ ] **Step 1: 寫失敗測試（越權單客戶被擋）**

```java
    @Test
    void chatDrilldown_foreignCustomer_isRejected() {
        var foreign = customerRepository.findByOwnerName("別的業務").get(0);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> workspaceAiService.assertCustomerVisible(sales, "self", foreign.getId()));
    }
```

- [ ] **Step 2: 執行確認失敗**

Run: `mvn -pl backend test -Dtest=WorkspaceAiServiceTest#chatDrilldown_foreignCustomer_isRejected -o`
Expected: 編譯失敗（assertCustomerVisible 不存在）

- [ ] **Step 3: 實作可見性驗證 + 問答串流**

```java
    /** 驗證 customerId 在呼叫者 scope 內，否則拋 EntityNotFoundException（避免洩漏存在性）。 */
    public void assertCustomerVisible(AuthPrincipal principal, String scope, Long customerId) {
        boolean visible = loadScopedCustomers(principal, scope).stream()
                .anyMatch(c -> c.getId().equals(customerId));
        if (!visible) {
            throw new jakarta.persistence.EntityNotFoundException("查無此客戶資料：" + customerId);
        }
    }
```

`streamChat(AuthPrincipal principal, Dtos.WorkspaceChatRequest req)`：
- `req.customerId()` 非 null：`assertCustomerVisible(principal, req.scope(), req.customerId())`，通過後委派既有 `InsightService.streamChat(new Dtos.ChatRequest(req.customerId(), req.message()))`（單客戶，既有 grounding/PII/治理）。
- `req.customerId()` 為 null：以 `loadScopedCustomers` 的客戶摘要（名稱/風險/續約/開放商機數）組 grounding，`ChatClient.stream()` 回答；fallback 為 deterministic 摘要式回答；結尾 `AiGovernanceService.record(WORKSPACE_CHAT, null, principal.username(), ...)`。

> 需注入 `InsightService`（委派單客戶問答）。注意避免循環依賴：`WorkspaceAiService` → `InsightService` 單向即可。

- [ ] **Step 4: 執行確認通過**

Run: `mvn -pl backend test -Dtest=WorkspaceAiServiceTest -o`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java backend/src/test/java/com/aicrm/crm/service/WorkspaceAiServiceTest.java
git commit -m "feat(backend): 工作檯問答串流 + 單客戶可見性驗證 (SP9-B)"
```

---

## Task 6: WorkspaceController 端點 + 安全 + 歷程

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/api/WorkspaceController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java:65`
- Modify: `WorkspaceAiService.java`（加 `getRecommendation` GET 讀快取 + `history`）
- Test: `backend/src/test/java/com/aicrm/crm/api/WorkspaceSecurityTest.java`

- [ ] **Step 1: SecurityConfig 納入 /api/workspace/**

在第 65 行的 `requestMatchers(...).authenticated()` 清單加入 `"/api/workspace/**"`：
```java
.requestMatchers("/api/customers/**", "/api/opportunities/**", "/api/dashboard/**", "/api/ai/**", "/api/agent/**", "/api/me/**", "/api/workspace/**").authenticated()
```

- [ ] **Step 2: 寫失敗測試（未登入 401、SALES 歷程只見自己）**

`WorkspaceSecurityTest.java`（仿 `ManagerAnalyticsSecurityTest` 既有風格，用 MockMvc + 測試 JWT）：
```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** 工作檯端點安全測試：未登入應 401。 */
class WorkspaceSecurityTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;

    @Test
    void recommendation_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/workspace/recommendation"))
                .andExpect(status().isUnauthorized());
    }
}
```
> 若既有測試取得 JWT 的輔助方式不同（檢視 `ManagerAnalyticsSecurityTest`），請對齊；至少保留「未登入 401」這條。

- [ ] **Step 3: 執行確認失敗**

Run: `mvn -pl backend test -Dtest=WorkspaceSecurityTest -o`
Expected: 編譯失敗或 404（Controller 尚未建立）

- [ ] **Step 4: 實作 Controller**

```java
package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.service.WorkspaceAiService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 我的工作檯個人 AI 端點（任何登入角色；scope 由後端強制）。 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceAiService workspaceAiService;

    public WorkspaceController(WorkspaceAiService workspaceAiService) {
        this.workspaceAiService = workspaceAiService;
    }

    /** 產生工作推薦（SSE：todos → AI 總結 → drafts）。 */
    @PostMapping("/recommendation")
    public SseEmitter streamRecommendation(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(defaultValue = "self") String scope) {
        return workspaceAiService.streamRecommendation(principal, scope);
    }

    /** 讀上次快取的 AI 總結 + 即時重算待辦。 */
    @GetMapping("/recommendation")
    public Dtos.WorkspaceRecommendationResponse getRecommendation(@AuthenticationPrincipal AuthPrincipal principal,
                                                                  @RequestParam(defaultValue = "self") String scope) {
        return workspaceAiService.getRecommendation(principal, scope);
    }

    /** 個人問答（SSE）。 */
    @PostMapping("/chat")
    public SseEmitter streamChat(@AuthenticationPrincipal AuthPrincipal principal,
                                 @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid Dtos.WorkspaceChatRequest req) {
        return workspaceAiService.streamChat(principal, req);
    }

    /** 本人工作檯 AI 歷程。 */
    @GetMapping("/history")
    public List<Dtos.AiCallHistoryItem> history(@AuthenticationPrincipal AuthPrincipal principal) {
        return workspaceAiService.history(principal);
    }
}
```

`WorkspaceAiService` 補：
- `getRecommendation(principal, scope)`：讀 `manager_insight`（scope=SELF, ownerName=username）取 summary/model/generatedAt（無則 null），加 `computeTodos`，回 `WorkspaceRecommendationResponse`（drafts 為空，只有重新產生才有）。
- `history(principal)`：`aiCallLogRepository.findByCallTypeInAndSubjectOrderByCreatedAtDesc(List.of(WORKSPACE_RECOMMENDATION, WORKSPACE_CHAT), principal.username())` 轉 `Dtos.AiCallHistoryItem`（對齊 `AiGovernanceService` 既有轉換）。
- recommendation 串流結尾把 summary upsert 進 `manager_insight`（scope=SELF）——對齊 `ManagerInsightService` 既有 upsert 寫法。

- [ ] **Step 5: 執行確認通過**

Run: `mvn -pl backend test -Dtest=WorkspaceSecurityTest -o`
Expected: PASS

- [ ] **Step 6: 全後端測試 + Commit**

Run: `mvn -pl backend test -o`
Expected: BUILD SUCCESS（含先前 99 + 新增）
```bash
git add backend/src/main/java/com/aicrm/crm/api/WorkspaceController.java backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java backend/src/main/java/com/aicrm/crm/service/WorkspaceAiService.java backend/src/test/java/com/aicrm/crm/api/WorkspaceSecurityTest.java
git commit -m "feat(backend): WorkspaceController 端點 + 安全 + 歷程/快取 (SP9-B)"
```

---

## Task 7: 前端 API 函式與型別

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`

- [ ] **Step 1: 加型別**

`types.ts`：
```typescript
export interface WorkspaceTodoItem { type: string; customerId: number; customerName: string; reason: string; severity: string; }
export interface SuggestedOpportunityDraft { customerId: number; customerName: string; name: string; suggestedStage: string; amount: number | null; rationale: string; }
export interface WorkspaceRecommendation { summary: string | null; model: string | null; generatedAt: string | null; todos: WorkspaceTodoItem[]; drafts: SuggestedOpportunityDraft[]; }
```

- [ ] **Step 2: 加 API 函式（複用 readSseStream）**

`api.ts`（對齊既有 `streamOwnerInsight`/`askAssistantStream` 寫法）：
```typescript
export async function fetchWorkspaceRecommendation(scope: string): Promise<WorkspaceRecommendation> {
  return apiGet(`/api/workspace/recommendation?scope=${encodeURIComponent(scope)}`);
}
export async function streamWorkspaceRecommendation(scope: string, onChunk, onDone, onError) {
  const res = await fetch(`/api/workspace/recommendation?scope=${encodeURIComponent(scope)}`, {
    method: "POST", headers: authHeaders(),
  });
  return readSseStream(res, onChunk, onDone /* , onError 對齊既有簽章 */);
}
export async function streamWorkspaceChat(scope, customerId, message, onChunk, onDone, onError) {
  const res = await fetch(`/api/workspace/chat`, {
    method: "POST", headers: { ...authHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ scope, customerId, message }),
  });
  return readSseStream(res, onChunk, onDone);
}
export async function fetchWorkspaceHistory() { return apiGet(`/api/workspace/history`); }
```
> 對齊 `api.ts` 既有 helper 名稱（`apiGet` / `authHeaders` / `readSseStream` 的實際簽章）；SSE chunk 需處理 `type: "todos" | "drafts" | "content" | "callId"`。

- [ ] **Step 3: 編譯確認**

Run: `pnpm --dir frontend run build`（或 `tsc --noEmit`，對齊專案指令）
Expected: 無型別錯誤

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types.ts frontend/src/api.ts
git commit -m "feat(frontend): workspace AI API 函式與型別 (SP9-B)"
```

---

## Task 8: WorkspaceAiPanel 元件（待辦 + 推薦串流）

**Files:**
- Create: `frontend/src/features/my-workspace/WorkspaceAiPanel.tsx`
- Modify: `frontend/src/features/my-workspace/MyWorkspacePage.tsx`

- [ ] **Step 1: 建立 WorkspaceAiPanel**

元件職責（單一）：呈現「我的 AI 助理」區塊。狀態：`todos`、`summary`（串流逐字）、`drafts`、`pending`、`scope`。
- 「產生我的工作建議」按鈕 → `streamWorkspaceRecommendation`，onChunk 依 `type` 更新 todos/summary(append delta)/drafts。
- 待辦清單：每筆可點 → `navigate('/customers/' + customerId)`；severity 上色。
- AI 總結：Markdown 渲染 + `pending` 時顯示 `AiThinkingIndicator`。
- MANAGER/ADMIN（`user.role !== 'SALES'`）顯示「自己 / 全部」scope 切換；SALES 不顯示。

```tsx
// 關鍵 onChunk 骨架
function onChunk(chunk) {
  if (chunk.type === "todos") setTodos(chunk.items);
  else if (chunk.type === "drafts") setDrafts(chunk.items);
  else if (chunk.type === "content") setSummary(prev => prev + (chunk.delta ?? ""));
}
```

- [ ] **Step 2: 掛進 MyWorkspacePage**

在 KPI 卡（`kpi-row`）之後、客戶列表之前插入 `<WorkspaceAiPanel user={user} />`。

- [ ] **Step 3: 啟動前後端手動驗證（逐字 + 待辦可點）**

啟動後端（先確認 18080 無殘留）與前端，登入 SALES，產生建議：確認待辦即時出現、AI 逐字、點待辦跳客戶詳情。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/my-workspace/WorkspaceAiPanel.tsx frontend/src/features/my-workspace/MyWorkspacePage.tsx
git commit -m "feat(frontend): 工作檯 AI 區塊 - 待辦 + 推薦逐字串流 (SP9-B)"
```

---

## Task 9: AI 建議商機草稿 → 預填 AddOpportunityModal

**Files:**
- Modify: `frontend/src/features/my-workspace/WorkspaceAiPanel.tsx`
- 檢視複用：`frontend/src/.../AddOpportunityModal`（確認可接受預填 props）

- [ ] **Step 1: 草稿卡 + 建立流程**

drafts 每筆顯示卡片（客戶 / 名稱 / 建議階段 / 理由）+「建立」按鈕。點擊開 `AddOpportunityModal`，以草稿預填 `customerId/name/stage/amount`；使用者確認後走既有 `POST /api/opportunities`（既有 modal 的 onSubmit）。

> 若 `AddOpportunityModal` 目前不接受外部預填值，加一個 `initialValues?` prop（最小改動，預設沿用原行為）。

- [ ] **Step 2: 手動驗證**

產生建議 → 點草稿「建立」→ Modal 預填正確 → 送出成功 → 商機出現在該客戶。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/my-workspace/WorkspaceAiPanel.tsx frontend/src/...AddOpportunityModal*
git commit -m "feat(frontend): AI 商機草稿一鍵預填建立 (SP9-B)"
```

---

## Task 10: 個人問答 + scope 切換 + AI 歷程

**Files:**
- Modify: `frontend/src/features/my-workspace/WorkspaceAiPanel.tsx`
- 複用：`AiCallHistoryModal`、`useAiChat` 模式

- [ ] **Step 1: 問答區**

對話框（複用 `ChatMessage` 模型與逐字串流）：上方「範圍」下拉（全部我的客戶 / 選某客戶深入）；送出走 `streamWorkspaceChat(scope, customerId|null, message, …)`。

- [ ] **Step 2: AI 歷程**

「AI 歷程」按鈕 → `AiCallHistoryModal`，資料來源 `fetchWorkspaceHistory()`。

- [ ] **Step 3: 手動驗證**

總覽問答有回應、可切某客戶深入問、越權客戶（非自己）無法選到、歷程列出剛才的呼叫。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/my-workspace/WorkspaceAiPanel.tsx
git commit -m "feat(frontend): 工作檯個人問答 + scope 切換 + AI 歷程 (SP9-B)"
```

---

## Task 11: 端對端驗證腳本 + 隔離回歸

**Files:**
- Create: `e2e/workspace-ai.spec.ts`（或 `scripts/verify-workspace-ai.ps1`，依專案既有 e2e 慣例）

- [ ] **Step 1: 寫可重跑驗證腳本**

依 CLAUDE.md 規約（瀏覽器自動化先寫成可重跑腳本、加中文註解）。流程：
1. 啟動前後端（或假設已啟動）。
2. 以 SALES 登入 → 進 `/my-work` → 產生工作建議 → 斷言待辦只含自己客戶、AI 總結非空、可點待辦。
3. 產生草稿 → 建立商機 → 斷言成功。
4. 總覽問答 → 斷言有回應。
5. （回歸）以該 SALES 嘗試問非自己客戶 → 斷言被擋。

- [ ] **Step 2: 執行腳本確認綠**

Run: 腳本對應指令（`pnpm e2e` 或 `pwsh scripts/verify-workspace-ai.ps1`）
Expected: 全步驟通過

- [ ] **Step 3: 後端全測試最終確認**

Run: `mvn -pl backend test -o`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add e2e/workspace-ai.spec.ts
git commit -m "test(e2e): 工作檯 AI 端對端 + 隔離回歸腳本 (SP9-B)"
```

---

## 完成準則（Definition of Done）

- 後端 `mvn -pl backend test` 全綠（含 workspace 隔離 / 待辦 / fallback / 401）。
- SALES 帶 `scope=all` 仍只見自己客戶；深入非自己客戶被擋（403/404）。
- 前端 `/my-work`：待辦可點、AI 逐字、建議商機可預填建立、個人問答（總覽+單客戶）、AI 歷程可查、MANAGER/ADMIN 有 scope 切換。
- 文件：更新 `docs/roadmap-progress.md` 標記 SP9-B 完成。
