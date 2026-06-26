# SP8 商機資料模型強化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 為 `Opportunity` 補齊 owner / leadSource / probability / closeReason+actualCloseDate 四類銷售欄位，並修正月營收 Forecast「未加權、未排除失單」的失真，讓既有報表與 AI 層立即取用。

**Architecture:** 單一 Flyway V18 加欄位+回填（子查詢版相容 H2/PG）；`Opportunity` 採 owner FK + `owner_name` 去正規化快取（仿 Customer）；`ManagerAnalyticsService` 改混合口徑（商機指標按商機 owner、客戶指標按客戶 owner）；Forecast 新增 `ForecastPoint(總額/加權)`；漏斗依 leadSource 切片。後端走 TDD，前端走 tsc/build + Playwright 煙霧。

**Tech Stack:** Java 21、Spring Boot 4.1、JPA、Flyway、JUnit5/Mockito/MockMvc/H2、React 19 + TypeScript + Vite。

**驗證環境（每次跑後端測試）：**
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test
```
前端：`export PATH="/d/nodejs:$PATH"; pnpm exec tsc --noEmit`（在 frontend/）。

> **與 spec 的精化**（已和現況對齊）：①結案觸發點是商機卡「階段下拉」選到 CLOSED_WON/CLOSED_LOST，非拖拽；②績效採混合口徑；③owner 加 `owner_name` 去正規化欄位避免 LAZY 載入；④Forecast 用新 record `ForecastPoint`。

---

## 梯次 A — 後端資料模型 + Migration

### Task 1: 新增 LeadSource 與 CloseReason enum

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/domain/LeadSource.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CloseReason.java`

- [ ] **Step 1: 建立 LeadSource**

```java
package com.aicrm.crm.domain;

/**
 * 商機來源，用於漏斗切片與通路分析。
 * INBOUND=主動上門/進線、OUTBOUND=業務開發、REFERRAL=推薦轉介。
 */
public enum LeadSource {
    INBOUND,
    OUTBOUND,
    REFERRAL
}
```

- [ ] **Step 2: 建立 CloseReason**

```java
package com.aicrm.crm.domain;

/**
 * 商機結案（輸/贏）原因。WON_* 用於 CLOSED_WON、LOST_* 用於 CLOSED_LOST。
 */
public enum CloseReason {
    WON_PRICE,
    WON_FEATURE,
    WON_RELATIONSHIP,
    WON_TIMING,
    LOST_PRICE,
    LOST_COMPETITOR,
    LOST_NO_BUDGET,
    LOST_NO_DECISION,
    LOST_NO_RESPONSE
}
```

- [ ] **Step 3: 編譯確認**

Run: `mvn -pl backend test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/LeadSource.java backend/src/main/java/com/aicrm/crm/domain/CloseReason.java
git commit -m "feat(backend): 新增 LeadSource 與 CloseReason enum (SP8)"
```

---

### Task 2: Opportunity entity 擴充六欄 + owner 關聯

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/domain/Opportunity.java`
- Test: `backend/src/test/java/com/aicrm/crm/domain/OpportunityTest.java`

- [ ] **Step 1: 寫失敗測試（驗 assignOwner 同步 ownerName、結案方法寫入原因、預設機率）**

```java
package com.aicrm.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Opportunity 新欄位行為單元測試（SP8）。 */
class OpportunityTest {

    private Opportunity newOpp(OpportunityStage stage) {
        return new Opportunity(null, "案子", stage, new BigDecimal("1000"),
                LocalDate.of(2026, 1, 1), OpportunityType.NEW_BUSINESS,
                LeadSource.OUTBOUND, 50);
    }

    @Test
    void assignOwner_syncsOwnerName() {
        var user = new AppUser("sales@a.local", "hash", "王小明", Role.SALES);
        var opp = newOpp(OpportunityStage.PROPOSAL);
        opp.assignOwner(user);
        assertThat(opp.getOwner()).isSameAs(user);
        assertThat(opp.getOwnerName()).isEqualTo("王小明");
    }

    @Test
    void closeWith_setsReasonAndDate() {
        var opp = newOpp(OpportunityStage.NEGOTIATION);
        opp.closeWith(OpportunityStage.CLOSED_WON, CloseReason.WON_PRICE, "價格有競爭力",
                LocalDate.of(2026, 3, 1));
        assertThat(opp.getStage()).isEqualTo(OpportunityStage.CLOSED_WON);
        assertThat(opp.getCloseReason()).isEqualTo(CloseReason.WON_PRICE);
        assertThat(opp.getCloseReasonNote()).isEqualTo("價格有競爭力");
        assertThat(opp.getActualCloseDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void constructor_keepsProbability() {
        assertThat(newOpp(OpportunityStage.PROPOSAL).getProbability()).isEqualTo(50);
    }
}
```

> 註：`AppUser` 建構子簽章若與 `new AppUser(username, hash, displayName, role)` 不符，執行時先 `Read` AppUser.java 對齊參數順序再寫測試。

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=OpportunityTest`
Expected: 編譯失敗（assignOwner/closeWith/getProbability 不存在）

- [ ] **Step 3: 修改 Opportunity entity**

在 import 區加入：
```java
import jakarta.persistence.Index;
```
在 `private OpportunityType type;` 之後加入新欄位：
```java
    /** 負責業務帳號（正規關聯）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 負責業務顯示名稱（去正規化快取，與 owner.displayName 同步）。 */
    @Column(name = "owner_name")
    private String ownerName;

    /** 商機來源。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "lead_source", nullable = false)
    private LeadSource leadSource = LeadSource.OUTBOUND;

    /** 成交機率（0–100），加權預測用。 */
    @Column
    private Integer probability;

    /** 結案（輸/贏）原因；僅 CLOSED_* 時填。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private CloseReason closeReason;

    /** 結案補充說明。 */
    @Column(name = "close_reason_note")
    private String closeReasonNote;

    /** 實際成交/結案日。 */
    @Column(name = "actual_close_date")
    private LocalDate actualCloseDate;
```
擴充建構子（保留舊 6 參數建構子供既有呼叫，新增帶 leadSource/probability 的建構子）。把現有 6 參數建構子改為委派：
```java
    public Opportunity(Customer customer, String name, OpportunityStage stage, BigDecimal amount,
                       LocalDate expectedCloseDate, OpportunityType type) {
        this(customer, name, stage, amount, expectedCloseDate, type, LeadSource.OUTBOUND, null);
    }

    /** 完整建構子（SP8）：含來源與成交機率。 */
    public Opportunity(Customer customer, String name, OpportunityStage stage, BigDecimal amount,
                       LocalDate expectedCloseDate, OpportunityType type,
                       LeadSource leadSource, Integer probability) {
        this.customer = customer;
        this.name = name;
        this.stage = stage;
        this.amount = amount;
        this.expectedCloseDate = expectedCloseDate;
        this.type = type;
        this.leadSource = leadSource;
        this.probability = probability;
    }
```
加入 getter 與行為方法：
```java
    public AppUser getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public LeadSource getLeadSource() { return leadSource; }
    public Integer getProbability() { return probability; }
    public CloseReason getCloseReason() { return closeReason; }
    public String getCloseReasonNote() { return closeReasonNote; }
    public LocalDate getActualCloseDate() { return actualCloseDate; }

    /** 指派負責業務並同步去正規化的 ownerName。 */
    public void assignOwner(AppUser owner) {
        this.owner = owner;
        this.ownerName = owner == null ? null : owner.getDisplayName();
    }

    /** 設定來源與機率（新增/編輯共用）。 */
    public void applySalesFields(LeadSource leadSource, Integer probability) {
        this.leadSource = leadSource;
        this.probability = probability;
    }

    /** 結案：設定階段 + 輸贏原因 + 實際成交日。 */
    public void closeWith(OpportunityStage stage, CloseReason closeReason, String note, LocalDate actualCloseDate) {
        this.stage = stage;
        this.closeReason = closeReason;
        this.closeReasonNote = note;
        this.actualCloseDate = actualCloseDate;
    }
```
同時在 `updateDetails(...)` 末尾不動；`updateStage(...)` 保留原樣（非結案用）。
確認檔案頂部已 import `AppUser`（同 package，無需 import）。

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=OpportunityTest`
Expected: PASS（3 tests）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/Opportunity.java backend/src/test/java/com/aicrm/crm/domain/OpportunityTest.java
git commit -m "feat(backend): Opportunity 加 owner/leadSource/probability/closeReason 六欄 (SP8)"
```

---

### Task 3: V18 Migration（加欄位 + 回填）

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__enrich_opportunity_sales_model.sql`
- Test: 沿用既有 `AiCrmApplicationContextTest`（context boot 觸發 Flyway + JPA validate）

- [ ] **Step 1: 撰寫 migration**

```sql
-- V18__enrich_opportunity_sales_model.sql
-- SP8：商機資料模型強化（owner / leadSource / probability / closeReason + actualCloseDate）

-- ① 新增欄位（lead_source 以 DEFAULT 完成回填）
ALTER TABLE opportunities ADD COLUMN owner_id          BIGINT;
ALTER TABLE opportunities ADD COLUMN owner_name        VARCHAR(255);
ALTER TABLE opportunities ADD COLUMN lead_source       VARCHAR(16) NOT NULL DEFAULT 'OUTBOUND';
ALTER TABLE opportunities ADD COLUMN probability       INT;
ALTER TABLE opportunities ADD COLUMN close_reason      VARCHAR(32);
ALTER TABLE opportunities ADD COLUMN close_reason_note TEXT;
ALTER TABLE opportunities ADD COLUMN actual_close_date DATE;

ALTER TABLE opportunities
  ADD CONSTRAINT fk_opportunities_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
CREATE INDEX idx_opportunities_owner       ON opportunities(owner_id);
CREATE INDEX idx_opportunities_lead_source ON opportunities(lead_source);

-- ② 回填 owner_id / owner_name ← 客戶現有負責業務（績效口徑改商機 owner 的前提；保證切換零差異）
UPDATE opportunities o
SET owner_id = (SELECT c.owner_id FROM customers c WHERE c.id = o.customer_id)
WHERE o.owner_id IS NULL;

UPDATE opportunities o
SET owner_name = (SELECT c.owner_name FROM customers c WHERE c.id = o.customer_id)
WHERE o.owner_name IS NULL;

-- ③ 回填 probability ← 依階段預設機率（與後端對照表一致）
UPDATE opportunities SET probability = CASE stage
    WHEN 'QUALIFICATION' THEN 20
    WHEN 'PROPOSAL'      THEN 50
    WHEN 'NEGOTIATION'   THEN 75
    WHEN 'CLOSED_WON'    THEN 100
    WHEN 'CLOSED_LOST'   THEN 0
END
WHERE probability IS NULL;

-- ④ 回填 actual_close_date ← 已結案者用預計成交日（缺則今天）
UPDATE opportunities
SET actual_close_date = COALESCE(expected_close_date, CURRENT_DATE)
WHERE stage IN ('CLOSED_WON', 'CLOSED_LOST') AND actual_close_date IS NULL;

-- ⑤ close_reason 刻意不回填：歷史案子無真實輸贏原因，留 NULL（報表歸「未填」）。
```

- [ ] **Step 2: 跑冒煙測試確認 migration 套用 + entity 對齊（ddl-auto=validate）**

Run: `mvn -pl backend test -Dtest=AiCrmApplicationContextTest`
Expected: PASS（context boot；若 H2 對 `ALTER TABLE ... ADD COLUMN` 多欄語法報錯，拆成多行單欄 ADD，已採此寫法）

- [ ] **Step 3: 跑全後端測試確認無回歸**

Run: `mvn -pl backend test`
Expected: 既有測試全綠（owner_name 回填等價，績效測試預期值不變）

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V18__enrich_opportunity_sales_model.sql
git commit -m "feat(backend): V18 商機銷售欄位 migration + 回填 (SP8)"
```

---

## 梯次 B — API 與 DTO

### Task 4: 擴充商機 DTO 與 Controller

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/OpportunityController.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/OpportunityApiTest.java`

- [ ] **Step 1: 寫整合測試（建立帶新欄位 → 回傳正確；結案帶 closeReason）**

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.support.TestAuth; // 若無此工具，改用既有整合測試的登入取 token 方式
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.ai.openai.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpportunityApiTest {

    @Autowired MockMvc mvc;

    @Test
    void create_withSalesFields_returnsThem() throws Exception {
        String token = TestAuth.salesToken(mvc); // 對齊既有整合測試的取 token 寫法
        mvc.perform(post("/api/opportunities")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                    {"customerId":100,"name":"測試案","stage":"PROPOSAL","amount":500000,
                     "expectedCloseDate":"2026-09-01","type":"NEW_BUSINESS",
                     "ownerId":null,"leadSource":"INBOUND","probability":60}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leadSource").value("INBOUND"))
                .andExpect(jsonPath("$.probability").value(60));
    }
}
```

> 執行時先 `Read` 一個既有整合測試（如 `DashboardIntegrationTest`）對齊取 token / seed 客戶 id（V2 客戶從 100 起）寫法，替換 `TestAuth.salesToken` 與 customerId。

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=OpportunityApiTest`
Expected: FAIL（DTO 無 leadSource 欄位、反序列化失敗或回傳缺欄）

- [ ] **Step 3: 擴充 DTO records**

`OpportunityResponse` 改為：
```java
    public record OpportunityResponse(
            Long id,
            String name,
            OpportunityStage stage,
            BigDecimal amount,
            LocalDate expectedCloseDate,
            OpportunityType type,
            Long ownerId,
            String ownerName,
            LeadSource leadSource,
            Integer probability,
            CloseReason closeReason,
            String closeReasonNote,
            LocalDate actualCloseDate
    ) {}
```
`CreateOpportunityRequest` 加三欄（owner 可空，預設帶客戶 owner；probability 可空，預設帶階段值）：
```java
    public record CreateOpportunityRequest(
            @NotNull Long customerId,
            @NotBlank String name,
            @NotNull OpportunityStage stage,
            @NotNull @PositiveOrZero BigDecimal amount,
            LocalDate expectedCloseDate,
            @NotNull OpportunityType type,
            Long ownerId,
            LeadSource leadSource,
            Integer probability
    ) {}
```
`UpdateOpportunityRequest` 加 ownerId/leadSource/probability（同上三欄附加在尾）。
`UpdateStageRequest` 加結案欄位：
```java
    public record UpdateStageRequest(
            @NotNull OpportunityStage stage,
            CloseReason closeReason,
            String closeReasonNote,
            LocalDate actualCloseDate
    ) {}
```
在 Dtos 頂部 import：
```java
import com.aicrm.crm.domain.CloseReason;
import com.aicrm.crm.domain.LeadSource;
```

- [ ] **Step 4: 改 OpportunityController（三處組裝 + owner/probability 預設邏輯）**

新增注入 `AppUserRepository users`（建構子加參數）與一個 `OpportunityStage→預設機率` 私有方法：
```java
    /** 階段預設成交機率（與 V18 回填一致）。 */
    private int defaultProbability(OpportunityStage stage) {
        return switch (stage) {
            case QUALIFICATION -> 20;
            case PROPOSAL -> 50;
            case NEGOTIATION -> 75;
            case CLOSED_WON -> 100;
            case CLOSED_LOST -> 0;
        };
    }

    /** 統一組裝 OpportunityResponse（13 欄）。 */
    private Dtos.OpportunityResponse toResponse(Opportunity o) {
        return new Dtos.OpportunityResponse(o.getId(), o.getName(), o.getStage(), o.getAmount(),
                o.getExpectedCloseDate(), o.getType(),
                o.getOwner() == null ? null : o.getOwner().getId(), o.getOwnerName(),
                o.getLeadSource(), o.getProbability(), o.getCloseReason(),
                o.getCloseReasonNote(), o.getActualCloseDate());
    }
```
`create`：建構商機後套用銷售欄位 + 指派 owner（未給 ownerId 用客戶 owner、未給 probability 用階段預設）：
```java
        var leadSource = request.leadSource() == null ? com.aicrm.crm.domain.LeadSource.OUTBOUND : request.leadSource();
        var probability = request.probability() == null ? defaultProbability(request.stage()) : request.probability();
        var opportunity = new Opportunity(customer, request.name(), request.stage(), request.amount(),
                request.expectedCloseDate(), request.type(), leadSource, probability);
        var owner = request.ownerId() != null
                ? users.findById(request.ownerId()).orElse(null)
                : customer.getOwner();
        opportunity.assignOwner(owner);
        opportunityRepository.save(opportunity);
        return toResponse(opportunity);
```
`update`：在 `updateDetails(...)` 後加 `opportunity.applySalesFields(...)` 與（若 ownerId 非空）`assignOwner`，回 `toResponse`。
`updateStage`：依目標階段是否結案分流：
```java
        if (request.stage() == OpportunityStage.CLOSED_WON || request.stage() == OpportunityStage.CLOSED_LOST) {
            var closeDate = request.actualCloseDate() == null ? java.time.LocalDate.now() : request.actualCloseDate();
            opportunity.closeWith(request.stage(), request.closeReason(), request.closeReasonNote(), closeDate);
        } else {
            opportunity.updateStage(request.stage());
        }
        opportunityRepository.save(opportunity);
        return toResponse(opportunity);
```
import `com.aicrm.crm.domain.OpportunityStage` 與 `com.aicrm.crm.repository.AppUserRepository`。

- [ ] **Step 5: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=OpportunityApiTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/Dtos.java backend/src/main/java/com/aicrm/crm/api/OpportunityController.java backend/src/test/java/com/aicrm/crm/api/OpportunityApiTest.java
git commit -m "feat(backend): 商機 API 收/回 owner/leadSource/probability/closeReason (SP8)"
```

---

## 梯次 C — 報表 / AI 連動

### Task 5: Forecast 加權（ForecastPoint）

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/DashboardService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/DashboardForecastTest.java`

- [ ] **Step 1: 寫單元測試（同月含一筆 NEGOTIATION 機率75 + 一筆 CLOSED_LOST，驗總額含 LOST、加權排除 LOST 且 ×機率）**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Forecast 加權邏輯單元測試（SP8）。 */
class DashboardForecastTest {

    @Test
    void monthlyForecast_totalIncludesLost_weightedExcludesLostAndScalesByProbability() {
        var customer = new Customer(); // 若無預設建構子，依既有測試 fixture 方式建立
        var won = new Opportunity(customer, "win", OpportunityStage.NEGOTIATION, new BigDecimal("1000"),
                LocalDate.of(2026, 5, 10), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 75);
        var lost = new Opportunity(customer, "lost", OpportunityStage.CLOSED_LOST, new BigDecimal("400"),
                LocalDate.of(2026, 5, 20), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 0);
        // 將 won/lost 掛到 customer.getOpportunities()（依 Customer 既有 addOpportunity 或反射 fixture）

        var repo = Mockito.mock(CustomerRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(customer));
        var service = new DashboardService(repo);

        var point = service.dashboardReports().monthlyForecast().stream()
                .filter(p -> p.label().equals("2026-05")).findFirst().orElseThrow();
        assertThat(point.totalAmount()).isEqualByComparingTo("1400");   // 含 LOST
        assertThat(point.weightedAmount()).isEqualByComparingTo("750"); // 1000×0.75，排除 LOST
    }
}
```

> 執行時先確認 `Customer` 如何掛入 opportunities（看既有測試或 Customer entity 的 addOpportunity）。

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=DashboardForecastTest`
Expected: FAIL（monthlyForecast 元素無 totalAmount/weightedAmount）

- [ ] **Step 3: 新增 ForecastPoint record + 改 DashboardReports + 改聚合**

Dtos 新增：
```java
    /** 月營收預測點：總額 pipeline 與機率加權預測（SP8）。 */
    public record ForecastPoint(String label, BigDecimal totalAmount, BigDecimal weightedAmount, long count) {}
```
`DashboardReports` 的 `monthlyForecast` 型別由 `List<MoneyChartPoint>` 改 `List<ForecastPoint>`。
DashboardService 的 monthlyForecast 聚合改為：
```java
        var monthlyForecast = opportunities.stream()
                .filter(opp -> opp.getExpectedCloseDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        opp -> opp.getExpectedCloseDate().withDayOfMonth(1),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var total = entry.getValue().stream()
                            .map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    var weighted = entry.getValue().stream()
                            .filter(o -> o.getStage() != OpportunityStage.CLOSED_LOST)
                            .map(o -> o.getAmount().multiply(BigDecimal.valueOf(
                                    o.getProbability() == null ? 0 : o.getProbability()))
                                    .divide(BigDecimal.valueOf(100)))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new Dtos.ForecastPoint(entry.getKey().toString().substring(0, 7),
                            total, weighted, entry.getValue().size());
                })
                .toList();
```

- [ ] **Step 4: 跑測試確認通過 + 全後端回歸**

Run: `mvn -pl backend test -Dtest=DashboardForecastTest` 然後 `mvn -pl backend test`
Expected: PASS（注意 DashboardIntegrationTest 若斷言 monthlyForecast 結構需同步調整）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/Dtos.java backend/src/main/java/com/aicrm/crm/service/DashboardService.java backend/src/test/java/com/aicrm/crm/service/DashboardForecastTest.java
git commit -m "feat(backend): Forecast 加權預測（排除失單、×機率）(SP8)"
```

---

### Task 6: 漏斗依 leadSource 過濾

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/DashboardService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/DashboardController.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/DashboardFunnelFilterTest.java`

- [ ] **Step 1: 寫單元測試（傳 leadSource=INBOUND 只計 INBOUND 商機）**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 漏斗依來源過濾單元測試（SP8）。 */
class DashboardFunnelFilterTest {

    @Test
    void pipelineByStage_filtersByLeadSource() {
        var customer = new Customer();
        var inbound = new Opportunity(customer, "in", OpportunityStage.PROPOSAL, new BigDecimal("100"),
                LocalDate.of(2026, 5, 1), OpportunityType.NEW_BUSINESS, LeadSource.INBOUND, 50);
        var outbound = new Opportunity(customer, "out", OpportunityStage.PROPOSAL, new BigDecimal("200"),
                LocalDate.of(2026, 5, 1), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 50);
        // 掛入 customer.getOpportunities()

        var repo = Mockito.mock(CustomerRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(customer));
        var service = new DashboardService(repo);

        var proposal = service.dashboardReports("INBOUND").pipelineByStage().stream()
                .filter(s -> s.stage().equals("PROPOSAL")).findFirst().orElseThrow();
        assertThat(proposal.count()).isEqualTo(1);
        assertThat(proposal.amount()).isEqualByComparingTo("100");
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=DashboardFunnelFilterTest`
Expected: FAIL（dashboardReports 無 String 多載）

- [ ] **Step 3: 加 leadSource 過濾多載**

DashboardService：把現有 `dashboardReports()` 改為委派 `dashboardReports(null)`；新增：
```java
    @Transactional(readOnly = true)
    public Dtos.DashboardReports dashboardReports(String leadSource) {
        var all = allCustomersWithDetail();
        var summaries = all.stream().map(mapper::toSummary).toList();
        var opportunities = all.stream().flatMap(customer -> customer.getOpportunities().stream())
                .filter(o -> leadSource == null || o.getLeadSource().name().equals(leadSource))
                .toList();
        // ...原本聚合內容不變（pipelineByStage / monthlyForecast 等沿用 opportunities）
    }
```
> 注意：`@Cacheable` 移到無參版或改用 key=leadSource；最簡：無參版保留 `@Cacheable`，多載版不快取（過濾為輕量計算）。industryBreakdown/ownerLeaderboard/renewalForecast 仍用 `all`（不受來源過濾，維持原行為）。

DashboardController 的 `/dashboard/reports` 加選填 query 參數：
```java
    @GetMapping("/reports")
    public Dtos.DashboardReports reports(@RequestParam(required = false) String leadSource) {
        return dashboardService.dashboardReports(leadSource);
    }
```
> 先 `Read` DashboardController 對齊既有方法簽章與注入名。

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=DashboardFunnelFilterTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/DashboardService.java backend/src/main/java/com/aicrm/crm/api/DashboardController.java backend/src/test/java/com/aicrm/crm/service/DashboardFunnelFilterTest.java
git commit -m "feat(backend): 漏斗支援依 leadSource 過濾 (SP8)"
```

---

### Task 7: ManagerAnalyticsService 混合口徑

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/ManagerAnalyticsService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/ManagerAnalyticsMixedOwnerTest.java`

- [ ] **Step 1: 寫測試（一筆商機改派他人 → 商機指標歸商機 owner、客戶指標歸客戶 owner）**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 績效混合口徑：商機指標按商機 owner、客戶指標按客戶 owner（SP8）。 */
class ManagerAnalyticsMixedOwnerTest {

    @Test
    void wonAmount_attributedToOpportunityOwner_notCustomerOwner() {
        var alice = new AppUser("alice@a.local", "h", "Alice", Role.SALES);
        var bob = new AppUser("bob@a.local", "h", "Bob", Role.SALES);
        var customer = new Customer();
        customer.assignOwner(alice); // 客戶屬 Alice
        var won = new Opportunity(customer, "deal", OpportunityStage.CLOSED_WON, new BigDecimal("900"),
                LocalDate.of(2026, 4, 1), OpportunityType.NEW_BUSINESS, LeadSource.OUTBOUND, 100);
        won.assignOwner(bob); // 商機改派 Bob
        // 掛 won 到 customer.getOpportunities()

        var repo = Mockito.mock(CustomerRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(customer));
        var insights = Mockito.mock(InteractionInsightRepository.class);
        Mockito.when(insights.findAll()).thenReturn(List.of());
        var service = new ManagerAnalyticsService(repo, insights);

        var owners = service.analytics().owners();
        var bobStats = owners.stream().filter(o -> o.ownerName().equals("Bob")).findFirst().orElseThrow();
        var aliceStats = owners.stream().filter(o -> o.ownerName().equals("Alice")).findFirst().orElseThrow();
        assertThat(bobStats.wonAmount()).isEqualByComparingTo("900"); // 商機歸 Bob
        assertThat(aliceStats.customerCount()).isEqualTo(1);          // 客戶歸 Alice
        assertThat(bobStats.customerCount()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=ManagerAnalyticsMixedOwnerTest`
Expected: FAIL（現況商機按客戶 owner 計，wonAmount 會歸 Alice）

- [ ] **Step 3: 重構 analytics() 為混合口徑**

把 `analytics()` 改為：客戶按客戶 owner 分組、商機按商機 ownerName 分組，owner 名單取兩者 union；`buildOwnerStats` 改簽章接 `(ownerName, ownerCustomers, ownerOpps, ...)`：
```java
    public Dtos.ManagerAnalyticsResponse analytics() {
        var all = customers.findAll();
        var today = LocalDate.now();
        Map<Long, Double> avgScoreByCustomer = insights.findAll().stream()
                .collect(Collectors.groupingBy(InteractionInsight::getCustomerId,
                        Collectors.averagingInt(InteractionInsight::getSentimentScore)));

        // 客戶層級指標：按客戶 owner 分組
        var customersByOwner = all.stream().collect(Collectors.groupingBy(Customer::getOwnerName));
        // 商機層級指標：按商機 owner 分組（owner_name 去正規化快取，回填後 = 客戶 owner）
        var oppsByOwner = all.stream().flatMap(c -> c.getOpportunities().stream())
                .filter(o -> o.getOwnerName() != null)
                .collect(Collectors.groupingBy(Opportunity::getOwnerName));

        var ownerNames = new java.util.TreeSet<String>();
        ownerNames.addAll(customersByOwner.keySet());
        ownerNames.addAll(oppsByOwner.keySet());

        var owners = ownerNames.stream()
                .map(name -> buildOwnerStats(name,
                        customersByOwner.getOrDefault(name, List.of()),
                        oppsByOwner.getOrDefault(name, List.of()),
                        today, avgScoreByCustomer))
                .sorted((a, b) -> b.wonAmount().compareTo(a.wonAmount()))
                .toList();

        return new Dtos.ManagerAnalyticsResponse(buildTeamSummary(all.size(), owners), owners);
    }
```
`buildOwnerStats` 簽章改為 `(String ownerName, List<Customer> ownerCustomers, List<Opportunity> opps, LocalDate today, Map<Long,Double> avgScoreByCustomer)`，並把方法內 `var opps = ownerCustomers.stream().flatMap(...).toList();` 這行**刪除**（改用傳入的 `opps`）；其餘商機指標（pipelineAmount/activeCount/wonOpps/wonAmount/wonCount/lostCount/winRate）沿用傳入 `opps`；客戶指標（highRisk/avgDays/avgSentiment/renewals/customerCount）沿用 `ownerCustomers`。ownerId 取得改：
```java
        Long ownerId = ownerCustomers.stream().map(c -> c.getOwner() == null ? null : c.getOwner().getId())
                .filter(Objects::nonNull).findFirst()
                .orElseGet(() -> opps.stream().map(o -> o.getOwner() == null ? null : o.getOwner().getId())
                        .filter(Objects::nonNull).findFirst().orElse(null));
```

- [ ] **Step 4: 跑測試 + 全後端回歸（確認 SP2 既有績效測試仍綠 = 等價性成立）**

Run: `mvn -pl backend test`
Expected: 新測試 PASS + 既有 ManagerAnalytics/Dashboard 測試全綠

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/ManagerAnalyticsService.java backend/src/test/java/com/aicrm/crm/service/ManagerAnalyticsMixedOwnerTest.java
git commit -m "feat(backend): 業務績效改混合口徑（商機指標按商機 owner）(SP8)"
```

---

## 梯次 D — 示範資料

### Task 8: DemoDataService 商機帶新欄位

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/DemoDataService.java`

- [ ] **Step 1: 定位商機生成處**

Run: `grep -n "new Opportunity\|Opportunity(" backend/src/main/java/com/aicrm/crm/service/DemoDataService.java`
找到建立商機的迴圈/工廠方法。

- [ ] **Step 2: 改用新建構子 + 指派 owner + 結案欄位**

把 `new Opportunity(customer, name, stage, amount, date, type)` 改為帶 leadSource/probability 的建構子，並在 save 前 `assignOwner` 與（結案時）`closeWith`。範例（依實際變數名調整）：
```java
        var leadSources = com.aicrm.crm.domain.LeadSource.values(); // INBOUND/OUTBOUND/REFERRAL
        var ls = leadSources[idx % leadSources.length];             // 用既有迴圈索引輪替（避免 Math.random）
        int prob = switch (stage) {
            case QUALIFICATION -> 20; case PROPOSAL -> 50; case NEGOTIATION -> 75;
            case CLOSED_WON -> 100; case CLOSED_LOST -> 0;
        };
        var opp = new Opportunity(customer, name, stage, amount, expectedClose,
                type, ls, prob);
        opp.assignOwner(customer.getOwner()); // 與客戶 owner 一致（與回填同口徑）
        if (stage == OpportunityStage.CLOSED_WON) {
            opp.closeWith(stage, com.aicrm.crm.domain.CloseReason.WON_PRICE, "示範：價格優勢", expectedClose);
        } else if (stage == OpportunityStage.CLOSED_LOST) {
            opp.closeWith(stage, com.aicrm.crm.domain.CloseReason.LOST_COMPETITOR, "示範：輸給競品", expectedClose);
        }
```

- [ ] **Step 3: 編譯 + 全後端測試**

Run: `mvn -pl backend test`
Expected: BUILD SUCCESS + 全綠

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/DemoDataService.java
git commit -m "feat(backend): 示範資料商機帶 owner/leadSource/probability/closeReason (SP8)"
```

---

## 梯次 E — 前端

> 前端無單元測試慣例；每個 task 末以 `pnpm exec tsc --noEmit` 驗證，梯次結尾跑 Playwright 煙霧。

### Task 9: 型別與標籤

**Files:**
- Modify: `frontend/src/types.ts`（OpportunityResponse L89、StageReport、DashboardReports L166）
- Modify: `frontend/src/lib/format.ts`（新增 leadSource/closeReason 中文標籤）

- [ ] **Step 1: 改型別**

`OpportunityResponse` interface 加：`ownerId: number | null; ownerName: string | null; leadSource: "INBOUND"|"OUTBOUND"|"REFERRAL"; probability: number | null; closeReason: string | null; closeReasonNote: string | null; actualCloseDate: string | null;`。
`DashboardReports.monthlyForecast` 型別改為 `ForecastPoint[]`，新增 interface：
```typescript
export interface ForecastPoint { label: string; totalAmount: number; weightedAmount: number; count: number; }
```

- [ ] **Step 2: 加標籤函式（format.ts）**

```typescript
/** 商機來源中文標籤。 */
export function leadSourceLabel(s: string): string {
  return ({ INBOUND: "主動上門", OUTBOUND: "業務開發", REFERRAL: "推薦轉介" } as Record<string, string>)[s] ?? s;
}

/** 結案原因中文標籤。 */
export function closeReasonLabel(s: string | null): string {
  if (!s) return "未填";
  return ({
    WON_PRICE: "贏-價格", WON_FEATURE: "贏-功能", WON_RELATIONSHIP: "贏-關係", WON_TIMING: "贏-時機",
    LOST_PRICE: "輸-價格", LOST_COMPETITOR: "輸-競品", LOST_NO_BUDGET: "輸-無預算",
    LOST_NO_DECISION: "輸-未決策", LOST_NO_RESPONSE: "輸-無回應"
  } as Record<string, string>)[s] ?? s;
}
```

- [ ] **Step 3: 驗證 + Commit**

Run: `pnpm exec tsc --noEmit`（會因下游元件未更新而有錯，僅確認本檔語法；下游 task 修完再整體綠）
```bash
git add frontend/src/types.ts frontend/src/lib/format.ts
git commit -m "feat(frontend): 商機新欄位型別與 leadSource/closeReason 標籤 (SP8)"
```

---

### Task 10: 新增/編輯商機 Modal 加欄位

**Files:**
- Modify: `frontend/src/features/customers/components/AddOpportunityModal.tsx`
- Modify: `frontend/src/features/customers/components/EditOpportunityModal.tsx`
- Modify: 呼叫端（`CustomersPage` 或 detail panel）的 onSubmit 型別與 API 呼叫

- [ ] **Step 1: AddOpportunityModal 加 來源 / 機率 欄位**

onSubmit 型別擴充 `leadSource: string; probability: number | null`；表單加：
```tsx
        <label>來源
          <select name="leadSource" required defaultValue="OUTBOUND">
            <option value="OUTBOUND">業務開發</option>
            <option value="INBOUND">主動上門</option>
            <option value="REFERRAL">推薦轉介</option>
          </select>
        </label>
        <label>成交機率(%) <input name="probability" type="number" min="0" max="100" placeholder="留空則依階段預設" /></label>
```
handle() 內加：`const prob = String(fd.get("probability") || ""); leadSource: String(fd.get("leadSource")), probability: prob ? Number(prob) : null`。
> owner（負責業務）下拉：沿用 `/customers/options` 的 owners；若 modal 無法取得清單，第一梯可預設帶客戶 owner（後端 ownerId 傳 null 即用客戶 owner），owner 下拉留待後續。本 task 不強制加 owner 下拉。

- [ ] **Step 2: EditOpportunityModal 比照加欄位**（含 defaultValue 帶現值）並更新 API 呼叫帶 leadSource/probability。

- [ ] **Step 3: 更新 api.ts 的 createOpportunity/updateOpportunity 參數**（帶新欄位）。先 `Read` api.ts 對齊既有函式簽章。

- [ ] **Step 4: 驗證 + Commit**

Run: `pnpm exec tsc --noEmit`
```bash
git add frontend/src/features/customers/components/AddOpportunityModal.tsx frontend/src/features/customers/components/EditOpportunityModal.tsx frontend/src/api.ts frontend/src/features/customers/CustomersPage.tsx
git commit -m "feat(frontend): 商機表單加來源/機率欄位 (SP8)"
```

---

### Task 11: 商機卡結案 modal + 顯示 owner

**Files:**
- Modify: `frontend/src/features/customers/components/OpportunityBoard.tsx`
- Create: `frontend/src/features/customers/components/CloseOpportunityModal.tsx`

- [ ] **Step 1: 建立 CloseOpportunityModal**（依 WON/LOST 顯示對應 closeReason 子集 + 備註 + 實際成交日，預設今天）

```tsx
import { FormEvent } from "react";

/** 結案原因 Modal：階段選到 CLOSED_WON/CLOSED_LOST 時收集輸贏原因。 */
export function CloseOpportunityModal({ stage, onSubmit, onClose }: {
  stage: "CLOSED_WON" | "CLOSED_LOST";
  onSubmit: (data: { closeReason: string; closeReasonNote: string; actualCloseDate: string }) => void;
  onClose: () => void;
}) {
  const won = stage === "CLOSED_WON";
  const options = won
    ? [["WON_PRICE", "價格"], ["WON_FEATURE", "功能"], ["WON_RELATIONSHIP", "關係"], ["WON_TIMING", "時機"]]
    : [["LOST_PRICE", "價格太高"], ["LOST_COMPETITOR", "輸給競品"], ["LOST_NO_BUDGET", "無預算"], ["LOST_NO_DECISION", "未決策"], ["LOST_NO_RESPONSE", "無回應"]];
  const today = new Date().toISOString().slice(0, 10);
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      closeReason: String(fd.get("closeReason")),
      closeReasonNote: String(fd.get("closeReasonNote") || ""),
      actualCloseDate: String(fd.get("actualCloseDate") || today)
    });
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{won ? "成交結案" : "失單結案"}</h3>
        <label>原因
          <select name="closeReason" required>
            {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </label>
        <label>備註 <input name="closeReasonNote" type="text" placeholder="選填" /></label>
        <label>實際成交日 <input name="actualCloseDate" type="date" defaultValue={today} /></label>
        <div className="modal-actions">
          <button type="submit">確認結案</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: OpportunityBoard：下拉選到 CLOSED_* 時開 modal，否則直接送出**

`changeStage` 改為：目標是 CLOSED_WON/CLOSED_LOST 時，不直接送出，而是設定 `pendingClose` 狀態開 modal；modal 確認後帶 closeReason 呼叫 `updateOpportunityStage`（API 需支援帶結案欄位）。卡片標題列加顯示 `{opportunity.ownerName}`。
> 需把 OpportunityBoard 改為帶 `useState` 的元件管理 `pendingClose`；`updateOpportunityStage` 簽章擴充選填結案參數（改 api.ts）。

- [ ] **Step 3: 驗證 + Commit**

Run: `pnpm exec tsc --noEmit`
```bash
git add frontend/src/features/customers/components/OpportunityBoard.tsx frontend/src/features/customers/components/CloseOpportunityModal.tsx frontend/src/api.ts
git commit -m "feat(frontend): 商機結案原因 modal + 卡片顯示負責業務 (SP8)"
```

---

### Task 12: 漏斗來源切換 + Forecast 加權線

**Files:**
- Modify: `frontend/src/features/dashboard/components/ReportsSection.tsx`
- Modify: 報表載入端（`DashboardPage` 或 api.ts 的 fetchReports）

- [ ] **Step 1: PipelineFunnel 加 全部/INBOUND/OUTBOUND 切換**

在 PipelineFunnel 上方加三顆切換鈕，狀態 `source: ""|"INBOUND"|"OUTBOUND"`；切換時重新呼叫 `/dashboard/reports?leadSource=<source>` 並更新 funnelData。
> 若 reports 由上層一次載入，最小做法：DashboardPage 持 source 狀態，傳 leadSource 給 fetchReports；或 PipelineFunnel 自行 fetch 子集。依既有資料流（reports 由 DashboardPage 載入）選上層持狀態。

- [ ] **Step 2: MonthlyForecastChart 疊加加權線**

forecast 資料改讀 `totalAmount` 與 `weightedAmount` 兩數列，圖上畫兩條（總額實線、加權虛線），圖例標注。

- [ ] **Step 3: 驗證 + Commit**

Run: `pnpm exec tsc --noEmit`
```bash
git add frontend/src/features/dashboard/components/ReportsSection.tsx frontend/src/features/dashboard/DashboardPage.tsx frontend/src/api.ts
git commit -m "feat(frontend): 漏斗來源切換 + Forecast 加權線 (SP8)"
```

---

### Task 13: 整體驗證與煙霧回歸

- [ ] **Step 1: 前端型別與建置全綠**

Run: `pnpm exec tsc --noEmit` 然後 `pnpm build`
Expected: exit 0

- [ ] **Step 2: 後端全測試**

Run: `mvn -pl backend test`
Expected: 全綠

- [ ] **Step 3: Playwright 煙霧（需後端在 18080）**

Run: `pnpm exec playwright test e2e/sp1-smoke.spec.ts`
Expected: passed（若商機表單欄位改動影響既有腳本，同步更新斷言）

- [ ] **Step 4: 更新 roadmap-progress.md**

把 SP8 狀態改為 ✅，補一條完成變更紀錄（涵蓋實際落地內容與測試數）。

- [ ] **Step 5: Commit**

```bash
git add docs/roadmap-progress.md
git commit -m "docs: SP8 完成，更新路線圖進度"
```

---

## Self-Review 對照

- **Spec §2 欄位**：Task 2（entity）+ Task 3（migration）涵蓋全部六欄；額外加 `owner_name` 去正規化欄（精化記於開頭）。
- **Spec §3 階段機率**：Task 3 回填 + Task 4 `defaultProbability` + Task 8 示範資料，三處用同一對照表。
- **Spec §5.1 Forecast 加權**：Task 5（含排除 LOST、×機率、總額並存）。
- **Spec §5.2 績效口徑**：Task 7（混合口徑，等價性由 SP2 測試回歸把關）。
- **Spec §5.3 漏斗切片**：Task 6（後端過濾）+ Task 12（前端切換）。
- **Spec §6 回填 SQL**：Task 3。
- **Spec §7 示範環境**：Task 8。
- **Spec §8 前端**：Task 9–12。
- **Spec §9 測試**：各後端 task 內含 TDD；Task 13 煙霧。
- **型別一致**：`ForecastPoint`、`OpportunityResponse(13 欄)`、`assignOwner`/`applySalesFields`/`closeWith`、`dashboardReports(String)` 在前後端 task 間一致使用。
