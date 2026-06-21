# 模組 A：客戶列表效能修復 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把客戶「風險等級」從每次查詢即時計算的衍生值，落地為 `customers.risk_level` DB 欄位，讓風險篩選改走資料庫分頁，消除「整表載入記憶體再分頁」的效能根因。

**Architecture:** 新增 `risk_level` / `risk_computed_at` 欄位；抽出純函式 `RiskLevelCalculator`（today 可注入、易測）；`RiskLevelMaintenanceService` 負責重算並寫回欄位；三處維護機制（寫入事件觸發 + 每日排程 + 啟動補算）確保欄位不過期；`CustomerService.search` 移除記憶體分頁路徑，風險篩選改為 SQL `where risk_level = ?`。

**Tech Stack:** Java 21、Spring Boot 4.1、Spring Data JPA + `JpaSpecificationExecutor`、Flyway、JUnit 5 + AssertJ + Testcontainers（pgvector Postgres）。

**Scope note:** 這是整體 spec（`docs/superpowers/specs/2026-06-21-manager-sales-analytics-design.md`）三個獨立子系統的第一份計畫，只涵蓋模組 A。模組 B（Manager 業務統計頁）、模組 C（兩個 AI 功能）各有獨立計畫，於本計畫完成後撰寫。

---

## 驗證環境（每個測試步驟前置）

PowerShell 設定 JDK 後用 Maven 跑測試（需 Docker 供 Testcontainers）：

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

單一測試類別：`mvn -pl backend test -Dtest=<ClassName>`

---

## File Structure

**新增：**
- `backend/src/main/java/com/aicrm/crm/service/RiskLevelCalculator.java` — 純函式：依最後互動時間、續約日、今天日期算出 HIGH/MEDIUM/LOW。
- `backend/src/main/java/com/aicrm/crm/service/RiskLevelMaintenanceService.java` — 重算單一客戶 / 全部客戶並寫回欄位。
- `backend/src/main/java/com/aicrm/crm/bootstrap/RiskLevelBackfillRunner.java` — 啟動時補算 `risk_level` 為 null 的客戶。
- `backend/src/main/java/com/aicrm/crm/bootstrap/RiskLevelScheduler.java` — 每日重算排程（property guard）。
- `backend/src/main/resources/db/migration/V13__add_customer_risk_level.sql` — 加欄位 + 索引。
- `backend/src/test/java/com/aicrm/crm/service/RiskLevelCalculatorTest.java`
- `backend/src/test/java/com/aicrm/crm/service/RiskLevelMaintenanceServiceTest.java`
- `backend/src/test/java/com/aicrm/crm/service/CustomerSearchRiskFilterTest.java`
- `backend/src/test/java/com/aicrm/crm/bootstrap/RiskLevelBackfillRunnerTest.java`

**修改：**
- `backend/src/main/java/com/aicrm/crm/domain/Customer.java` — 加欄位、getter、`applyRiskLevel`。
- `backend/src/main/java/com/aicrm/crm/service/CustomerMapper.java` — `toSummary` 改讀欄位；移除 private `calculateRiskLevel`。
- `backend/src/main/java/com/aicrm/crm/service/CustomerService.java` — `buildSpec` 加 riskLevel 條件；`search` 移除路徑 2；寫入後觸發重算。
- `backend/src/main/java/com/aicrm/crm/repository/CustomerRepository.java` — 加 `findIdsByRiskLevelIsNull`、`findAllIds`。
- `backend/src/main/java/com/aicrm/AiCrmApplication.java` — 加 `@EnableScheduling`。
- `backend/src/main/resources/application.yml` — 加 `app.risk.daily-recompute.enabled: true`。

---

## Task 1：RiskLevelCalculator（純函式 + 測試）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/RiskLevelCalculator.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/RiskLevelCalculatorTest.java`

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** RiskLevelCalculator 純函式測試：各風險分支與邊界。 */
class RiskLevelCalculatorTest {

    private final LocalDate today = LocalDate.of(2026, 6, 21);

    @Test
    void nullLastInteraction_returnsMedium() {
        assertThat(RiskLevelCalculator.calculate(null, null, today)).isEqualTo("MEDIUM");
    }

    @Test
    void over60DaysSinceInteraction_returnsHigh() {
        var last = LocalDateTime.of(2026, 4, 1, 0, 0); // 距 6/21 > 60 天
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("HIGH");
    }

    @Test
    void renewalOverdue_returnsHigh() {
        var last = LocalDateTime.of(2026, 6, 20, 0, 0); // 近期互動
        var overdueRenewal = LocalDate.of(2026, 6, 1);  // 已逾期
        assertThat(RiskLevelCalculator.calculate(last, overdueRenewal, today)).isEqualTo("HIGH");
    }

    @Test
    void between31And60Days_returnsMedium() {
        var last = LocalDateTime.of(2026, 5, 10, 0, 0); // 距 6/21 約 42 天
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("MEDIUM");
    }

    @Test
    void recentInteraction_returnsLow() {
        var last = LocalDateTime.of(2026, 6, 15, 0, 0); // 距 6/21 = 6 天
        assertThat(RiskLevelCalculator.calculate(last, null, today)).isEqualTo("LOW");
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=RiskLevelCalculatorTest`
Expected: 編譯失敗 `cannot find symbol RiskLevelCalculator`。

- [ ] **Step 3: 寫最小實作**

```java
package com.aicrm.crm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 風險等級計算純函式：自 CustomerMapper 抽出，today 改為參數注入以利測試與排程共用。
 * 規則維持原行為：無互動→MEDIUM；逾 60 天未互動或續約逾期→HIGH；逾 30 天→MEDIUM；其餘→LOW。
 */
public final class RiskLevelCalculator {

    private RiskLevelCalculator() {
    }

    /**
     * 計算風險等級。
     *
     * @param lastInteractionAt 最後互動時間（可為 null）
     * @param renewalDueDate 預計續約日（可為 null）
     * @param today 作為基準的今天日期
     * @return HIGH / MEDIUM / LOW
     */
    public static String calculate(LocalDateTime lastInteractionAt, LocalDate renewalDueDate, LocalDate today) {
        if (lastInteractionAt == null) {
            return "MEDIUM";
        }
        var days = ChronoUnit.DAYS.between(lastInteractionAt.toLocalDate(), today);
        if (days > 60 || (renewalDueDate != null && renewalDueDate.isBefore(today))) {
            return "HIGH";
        }
        if (days > 30) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=RiskLevelCalculatorTest`
Expected: PASS（5 個測試）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/RiskLevelCalculator.java backend/src/test/java/com/aicrm/crm/service/RiskLevelCalculatorTest.java
git commit -m "feat(risk): 抽出 RiskLevelCalculator 純函式(today 可注入)"
```

---

## Task 2：Customer entity 加 risk_level 欄位與行為

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/domain/Customer.java`

- [ ] **Step 1: 加欄位 import**

在 import 區加入（檔案已有 `jakarta.persistence.*` 多個 import，補 `Instant`）：

```java
import java.time.Instant;
```

- [ ] **Step 2: 在 `opportunities` 欄位宣告後（約 L88 之後）新增兩個欄位**

```java
    /** 已落地的風險等級（HIGH/MEDIUM/LOW）；由 RiskLevelMaintenanceService 維護，供清單 SQL 篩選。 */
    @Column(name = "risk_level", length = 10)
    private String riskLevel;

    /** 風險等級最後重算時間，供觀測欄位是否過期。 */
    @Column(name = "risk_computed_at")
    private Instant riskComputedAt;
```

- [ ] **Step 3: 在 `updateStatus` 方法後新增 `applyRiskLevel` 方法**

```java
    /**
     * 套用重算後的風險等級並記錄重算時間。
     *
     * @param level HIGH / MEDIUM / LOW
     */
    public void applyRiskLevel(String level) {
        this.riskLevel = level;
        this.riskComputedAt = Instant.now();
    }
```

- [ ] **Step 4: 在 getter 區（約 L185 `getOpportunities` 後）新增 getter**

```java
    public String getRiskLevel() { return riskLevel; }
    public Instant getRiskComputedAt() { return riskComputedAt; }
```

- [ ] **Step 5: 編譯確認（尚無 migration，先不跑需要 DB 的測試）**

Run: `mvn -pl backend test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/Customer.java
git commit -m "feat(risk): Customer 新增 risk_level / risk_computed_at 欄位與 applyRiskLevel"
```

---

## Task 3：V13 migration（加欄位與索引）

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__add_customer_risk_level.sql`

> 註：欄位設為 nullable，不在 SQL 內回填風險值（風險計算依賴「今天日期」與 Java 邏輯，SQL 難精確重現）；回填交由 Task 8 的啟動補算 runner。順帶補上 spec 第 6 節提到缺失的 status / renewal 索引。`ddl-auto: validate` 要求欄位與 entity 完全一致。

- [ ] **Step 1: 建立 migration**

```sql
-- 風險等級落地為 DB 欄位，讓清單風險篩選改走資料庫分頁（取代記憶體分頁）。
alter table customers add column risk_level varchar(10);
alter table customers add column risk_computed_at timestamp;

-- 風險篩選與既有缺失條件的索引。
create index if not exists idx_customers_risk_level on customers(risk_level);
create index if not exists idx_customers_status on customers(status);
create index if not exists idx_customers_renewal_due_date on customers(renewal_due_date);
```

- [ ] **Step 2: 啟動驗證 migration 套用成功（context 載入即跑 Flyway）**

Run: `mvn -pl backend test -Dtest=AiCrmApplicationContextTest`
Expected: PASS（context 啟動、Flyway 套用 V13 無 checksum/語法錯誤）。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V13__add_customer_risk_level.sql
git commit -m "feat(risk): V13 migration 新增 risk_level 欄位與 status/renewal/risk 索引"
```

---

## Task 4：CustomerMapper 改讀欄位

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/CustomerMapper.java`

> `toSummary` 仍需掃互動算 `lastInteractionAt`、掃商機算金額（summary 本就需要這些欄位）；改動只在於風險等級改讀已落地欄位，null 時即時計算當後備（相容尚未回填的資料）。移除原本的 private `calculateRiskLevel`（由 `RiskLevelCalculator` 取代）。

- [ ] **Step 1: 替換 `toSummary` 內風險等級的取得方式**

把 `toSummary` 中傳入 `CustomerSummaryResponse` 的第 9 個參數，由 `calculateRiskLevel(customer, lastInteractionAt)` 改為：

```java
                customer.getRiskLevel() != null
                        ? customer.getRiskLevel()
                        : RiskLevelCalculator.calculate(lastInteractionAt, customer.getRenewalDueDate(), LocalDate.now()),
```

完整的 `toSummary` 應為：

```java
    public Dtos.CustomerSummaryResponse toSummary(Customer customer) {
        var lastInteractionAt = customer.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        var amount = customer.getOpportunities().stream()
                .map(Opportunity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dtos.CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getTaxId(),
                customer.getIndustry(),
                customer.getOwnerName(),
                customer.getStatus(),
                // 風險等級改讀已落地欄位；尚未回填(null)時即時計算當後備
                customer.getRiskLevel() != null
                        ? customer.getRiskLevel()
                        : RiskLevelCalculator.calculate(lastInteractionAt, customer.getRenewalDueDate(), LocalDate.now()),
                customer.getRenewalDueDate(),
                lastInteractionAt,
                amount
        );
    }
```

- [ ] **Step 2: 刪除 private `calculateRiskLevel` 方法（約 L85-98）**

連同其上方的 JavaDoc 一併刪除。`ChronoUnit` import 若不再使用也一併移除。

- [ ] **Step 3: 編譯確認**

Run: `mvn -pl backend test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/CustomerMapper.java
git commit -m "refactor(risk): CustomerMapper.toSummary 改讀 risk_level 欄位,null 時後備計算"
```

---

## Task 5：RiskLevelMaintenanceService（重算服務 + 測試）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/RiskLevelMaintenanceService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/repository/CustomerRepository.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/RiskLevelMaintenanceServiceTest.java`

- [ ] **Step 1: 在 CustomerRepository 加查詢方法**

在 `findDistinctOwners()` 之後新增：

```java
    /**
     * 取得尚未回填風險等級的客戶 ID（供啟動補算）。
     *
     * @return risk_level 為 null 的客戶 ID 清單
     */
    @Query("select c.id from Customer c where c.riskLevel is null")
    List<Long> findIdsByRiskLevelIsNull();

    /**
     * 取得所有客戶 ID（供每日重算逐筆處理）。
     *
     * @return 全部客戶 ID
     */
    @Query("select c.id from Customer c")
    List<Long> findAllIds();
```

- [ ] **Step 2: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 風險等級重算服務整合測試：重算後欄位被正確寫入 DB。 */
class RiskLevelMaintenanceServiceTest extends PostgresTestBase {

    @Autowired RiskLevelMaintenanceService maintenance;
    @Autowired CustomerRepository customers;

    /** 久未互動的客戶重算後應為 HIGH 並寫回欄位。 */
    @Test
    void recompute_writesHighForStaleCustomer() {
        var customer = new Customer("重算測試客戶", "recompute@example.com", "0911222333", "88000001", "製造業", "王小明");
        customer.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(90), "很久沒聯絡了"));
        var id = customers.saveAndFlush(customer).getId();

        maintenance.recompute(id);

        var reloaded = customers.findById(id).orElseThrow();
        assertThat(reloaded.getRiskLevel()).isEqualTo("HIGH");
        assertThat(reloaded.getRiskComputedAt()).isNotNull();
    }
}
```

- [ ] **Step 3: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=RiskLevelMaintenanceServiceTest`
Expected: 編譯失敗 `cannot find symbol RiskLevelMaintenanceService`。

- [ ] **Step 4: 寫實作**

```java
package com.aicrm.crm.service;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.repository.CustomerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 風險等級維護服務：在交易內以 managed entity 重算並寫回 risk_level 欄位。
 * 以「客戶 ID」為參數而非 Customer 實例，確保在交易內存取 LAZY 互動集合不會 LazyInitialization。
 */
@Service
public class RiskLevelMaintenanceService {

    /** 客戶資料存取。 */
    private final CustomerRepository customers;

    public RiskLevelMaintenanceService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * 重算單一客戶的風險等級並寫回欄位（managed entity，交易結束自動 flush）。
     *
     * @param customerId 客戶 ID
     */
    @Transactional
    public void recompute(Long customerId) {
        var customer = customers.findById(customerId).orElse(null);
        if (customer == null) {
            return;
        }
        customer.applyRiskLevel(computeLevel(customer));
    }

    /**
     * 重算所有客戶的風險等級（每日排程用），單一交易內逐筆處理。
     *
     * @return 重算的客戶數
     */
    @Transactional
    public int recomputeAll() {
        var all = customers.findAll();
        all.forEach(c -> c.applyRiskLevel(computeLevel(c)));
        return all.size();
    }

    /**
     * 依客戶最後互動時間與續約日，以今天為基準計算風險等級。
     *
     * @param customer 客戶（managed，交易內可安全存取互動集合）
     * @return HIGH / MEDIUM / LOW
     */
    private String computeLevel(Customer customer) {
        LocalDateTime last = customer.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return RiskLevelCalculator.calculate(last, customer.getRenewalDueDate(), LocalDate.now());
    }
}
```

- [ ] **Step 5: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=RiskLevelMaintenanceServiceTest`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/RiskLevelMaintenanceService.java backend/src/main/java/com/aicrm/crm/repository/CustomerRepository.java backend/src/test/java/com/aicrm/crm/service/RiskLevelMaintenanceServiceTest.java
git commit -m "feat(risk): 新增 RiskLevelMaintenanceService 重算並寫回 risk_level"
```

---

## Task 6：CustomerService 寫入事件觸發重算

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/CustomerService.java`

> 新增互動、建立、編輯（含續約日變動）後重算該客戶風險等級。注入 `RiskLevelMaintenanceService`；其 `recompute` 為 `@Transactional`（REQUIRED 傳播），在 CustomerService 既有交易內呼叫，與當前 managed 實例為同一筆。

- [ ] **Step 1: 注入 RiskLevelMaintenanceService**

在建構子參數與欄位加入。欄位區（約 L55 `contacts` 後）新增：

```java
    /** 風險等級維護：寫入事件後重算 risk_level 欄位。 */
    private final RiskLevelMaintenanceService riskMaintenance;
```

建構子簽章末端加入參數並賦值：

```java
    public CustomerService(CustomerRepository customers,
                           SentimentIntentService sentimentIntentService,
                           InteractionInsightRepository interactionInsights,
                           InteractionRepository interactionRepository,
                           AppUserRepository users,
                           ContactRepository contacts,
                           RiskLevelMaintenanceService riskMaintenance) {
        this.customers = customers;
        this.sentimentIntentService = sentimentIntentService;
        this.interactionInsights = interactionInsights;
        this.interactionRepository = interactionRepository;
        this.users = users;
        this.contacts = contacts;
        this.riskMaintenance = riskMaintenance;
    }
```

- [ ] **Step 2: `create` 在回傳前重算**

把 `create` 結尾改為先存檔取得 id、重算、再回摘要：

```java
        customer.updateContractDates(request.contractStartDate(), request.contractEndDate(), request.renewalDueDate());
        var saved = customers.save(customer);
        riskMaintenance.recompute(saved.getId());
        return mapper.toSummary(saved);
```

- [ ] **Step 3: `update` 在回傳前重算**

把 `update` 結尾改為：

```java
        customer.updateContractDates(request.contractStartDate(), request.contractEndDate(), request.renewalDueDate());
        var saved = customers.save(customer);
        riskMaintenance.recompute(saved.getId());
        return mapper.toSummary(saved);
```

- [ ] **Step 4: `addInteraction` 在回傳前重算**

在 `addInteraction` 取得 `insight` 之後、`return` 之前插入：

```java
        // 新增互動會改變「最後互動時間」，重算該客戶風險等級
        riskMaintenance.recompute(id);
```

- [ ] **Step 5: 編譯確認**

Run: `mvn -pl backend test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/CustomerService.java
git commit -m "feat(risk): 建立/編輯/新增互動後觸發風險等級重算"
```

---

## Task 7：search 改走 SQL 風險篩選（移除記憶體分頁）

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/CustomerService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/CustomerSearchRiskFilterTest.java`

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 風險篩選改走 SQL 後的分頁正確性測試。 */
class CustomerSearchRiskFilterTest extends PostgresTestBase {

    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customers;
    @Autowired RiskLevelMaintenanceService maintenance;

    /** 建立一個久未互動（HIGH）的客戶，並回填其風險等級。 */
    @BeforeEach
    void seedHighRiskCustomer() {
        var c = new Customer("風險篩選客戶", "riskfilter@example.com", "0900111222", "77000001", "風險篩選產業", "王小明");
        c.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(120), "久未聯絡"));
        var id = customers.saveAndFlush(c).getId();
        maintenance.recompute(id);
    }

    /** search 帶 riskLevel=HIGH 時，回傳項目應全為 HIGH。 */
    @Test
    void search_byHighRisk_returnsOnlyHigh() {
        var result = customerService.search(0, 20, null, "風險篩選產業", null, null, "HIGH", null, null);
        assertThat(result.items()).isNotEmpty();
        assertThat(result.items()).allMatch(s -> "HIGH".equals(s.riskLevel()));
        assertThat(result.totalElements()).isEqualTo(result.items().size());
    }
}
```

> 註：`PageResponse` 的存取子假設為 `items()` / `totalElements()`（與 `CustomerController` 回傳型別一致）。若實際 record 元件名不同，依 `Dtos.PageResponse` 定義調整斷言。

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=CustomerSearchRiskFilterTest`
Expected: FAIL — 此時 `search` 仍走記憶體路徑（功能上可能碰巧通過），但下一步重構後語意才正確。若已 PASS，仍續行重構以移除效能根因。

- [ ] **Step 3: `buildSpec` 加入 riskLevel 條件**

在 `buildSpec` 簽章加入 `String riskLevel` 參數（放在 `status` 之後）：

```java
    private Specification<Customer> buildSpec(String keyword, String industry, String owner,
                                              CustomerStatus status, String riskLevel,
                                              LocalDate renewalFrom, LocalDate renewalTo) {
```

在 `status` 條件之後、續約區間之前加入：

```java
            if (StringUtils.hasText(riskLevel)) {
                // 風險等級已落地為 DB 欄位，直接以等值條件篩選（取代記憶體過濾）
                predicate = cb.and(predicate, cb.equal(root.get("riskLevel"), riskLevel.toUpperCase()));
            }
```

- [ ] **Step 4: 改寫 `search` 移除路徑 2，統一 DB 分頁**

把 `search` 方法體（L88-110）整段改為：

```java
        int pageIdx = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 50);
        var spec = buildSpec(keyword, industry, owner, status, riskLevel, renewalFrom, renewalTo);

        // 所有條件（含風險等級）皆為 DB 欄位 → 統一走資料庫分頁；
        // toSummary 的 LAZY 關聯由 default_batch_fetch_size 對「當頁」批次載入，不再整表載入。
        var pageable = PageRequest.of(pageIdx, pageSize, Sort.by("id").ascending());
        var result = customers.findAll(spec, pageable);
        var items = result.getContent().stream().map(mapper::toSummary).toList();
        return new Dtos.PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
```

- [ ] **Step 5: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=CustomerSearchRiskFilterTest`
Expected: PASS。

- [ ] **Step 6: 跑既有客戶/儀表板相關測試確認無回歸**

Run: `mvn -pl backend test -Dtest=DashboardIntegrationTest`
Expected: PASS（`toSummary` 變更未破壞既有聚合）。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/CustomerService.java backend/src/test/java/com/aicrm/crm/service/CustomerSearchRiskFilterTest.java
git commit -m "perf(customers): 風險篩選改走 SQL 分頁,移除整表載入記憶體分頁路徑"
```

---

## Task 8：啟動補算 Runner（回填既有資料 + 測試）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/bootstrap/RiskLevelBackfillRunner.java`
- Test: `backend/src/test/java/com/aicrm/crm/bootstrap/RiskLevelBackfillRunnerTest.java`

> 仿既有 `InteractionInsightBackfillRunner`（`@Component` + `@Order` + `ApplicationRunner`），啟動時補算 `risk_level` 為 null 的客戶；冪等（補過的不再變 null，重跑不重算）。

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 風險等級啟動補算 runner 測試：補上 risk_level 為 null 的客戶。 */
class RiskLevelBackfillRunnerTest extends PostgresTestBase {

    @Autowired RiskLevelBackfillRunner runner;
    @Autowired CustomerRepository customers;

    /** 以 repository 直接灌入未回填客戶，runner 應補上 risk_level。 */
    @Test
    void run_backfillsNullRiskLevel() {
        var c = new Customer("補算風險客戶", "riskbackfill@example.com", "0922333444", "66000001", "補算產業", "王小明");
        c.addInteraction(new Interaction(InteractionType.EMAIL, LocalDateTime.now().minusDays(100), "久未聯絡"));
        var id = customers.saveAndFlush(c).getId();
        // 前置：新建客戶 risk_level 為 null（未經 service 寫入路徑）
        assertThat(customers.findById(id).orElseThrow().getRiskLevel()).isNull();

        runner.run(null);

        assertThat(customers.findById(id).orElseThrow().getRiskLevel()).isEqualTo("HIGH");
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=RiskLevelBackfillRunnerTest`
Expected: 編譯失敗 `cannot find symbol RiskLevelBackfillRunner`。

- [ ] **Step 3: 寫實作**

```java
package com.aicrm.crm.bootstrap;

import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.RiskLevelMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 啟動補算風險等級：為 risk_level 為 null 的客戶（例如 Flyway 種子或舊資料）逐筆重算。
 * @Order(4)：排在情緒意圖補算（@Order(3)）之後。冪等：補過的不再為 null，重跑不重算。
 */
@Component
@Order(4)
public class RiskLevelBackfillRunner implements ApplicationRunner {

    /** 記錄補算筆數。 */
    private static final Logger log = LoggerFactory.getLogger(RiskLevelBackfillRunner.class);

    /** 客戶資料存取。 */
    private final CustomerRepository customers;

    /** 風險等級重算服務。 */
    private final RiskLevelMaintenanceService maintenance;

    public RiskLevelBackfillRunner(CustomerRepository customers, RiskLevelMaintenanceService maintenance) {
        this.customers = customers;
        this.maintenance = maintenance;
    }

    /**
     * 啟動時補算缺漏的風險等級。
     *
     * @param args 應用參數（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        var ids = customers.findIdsByRiskLevelIsNull();
        ids.forEach(maintenance::recompute);
        if (!ids.isEmpty()) {
            log.info("啟動補算風險等級完成：{} 筆", ids.size());
        }
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=RiskLevelBackfillRunnerTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/bootstrap/RiskLevelBackfillRunner.java backend/src/test/java/com/aicrm/crm/bootstrap/RiskLevelBackfillRunnerTest.java
git commit -m "feat(risk): 啟動補算 runner 回填 risk_level 為 null 的客戶"
```

---

## Task 9：每日重算排程（property guard）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/bootstrap/RiskLevelScheduler.java`
- Modify: `backend/src/main/java/com/aicrm/AiCrmApplication.java`
- Modify: `backend/src/main/resources/application.yml`

> 風險等級依賴「今天日期」，即使無新事件也會隨時間變化，故需每日重算。做成 opt-in 設定（預設啟用、可關），避免「隱性啟用」反模式。

- [ ] **Step 1: 在 AiCrmApplication 加 `@EnableScheduling`**

於主類別 `@SpringBootApplication` 下方加註解，並補 import：

```java
import org.springframework.scheduling.annotation.EnableScheduling;
```

```java
@SpringBootApplication
@EnableScheduling
public class AiCrmApplication {
```

- [ ] **Step 2: application.yml 在 `app:` 區塊新增設定**

於 `app:` 下（與 `security` / `voyage` 同層）新增：

```yaml
  # 風險等級每日重算排程：預設啟用；風險等級依賴當天日期，不每日更新會失準。
  # 以環境變數 APP_RISK_DAILY_RECOMPUTE_ENABLED=false 可關閉。
  risk:
    daily-recompute:
      enabled: ${APP_RISK_DAILY_RECOMPUTE_ENABLED:true}
```

- [ ] **Step 3: 建立排程元件**

```java
package com.aicrm.crm.bootstrap;

import com.aicrm.crm.service.RiskLevelMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日風險等級重算排程。預設啟用（matchIfMissing=true），可由
 * app.risk.daily-recompute.enabled=false 關閉。每日凌晨 03:00 重算全表。
 */
@Component
@ConditionalOnProperty(name = "app.risk.daily-recompute.enabled", havingValue = "true", matchIfMissing = true)
public class RiskLevelScheduler {

    /** 記錄每日重算結果。 */
    private static final Logger log = LoggerFactory.getLogger(RiskLevelScheduler.class);

    /** 風險等級重算服務。 */
    private final RiskLevelMaintenanceService maintenance;

    public RiskLevelScheduler(RiskLevelMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    /**
     * 每日凌晨 03:00 重算所有客戶風險等級。
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void recomputeDaily() {
        int n = maintenance.recomputeAll();
        log.info("每日風險等級重算完成：{} 筆", n);
    }
}
```

- [ ] **Step 4: 編譯與 context 啟動驗證**

Run: `mvn -pl backend test -Dtest=AiCrmApplicationContextTest`
Expected: PASS（`@EnableScheduling` 與排程 bean 不阻礙 context 啟動）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/bootstrap/RiskLevelScheduler.java backend/src/main/java/com/aicrm/AiCrmApplication.java backend/src/main/resources/application.yml
git commit -m "feat(risk): 每日重算排程(預設啟用可關) + 啟用 Scheduling"
```

---

## Task 10：全模組回歸驗證

**Files:** 無（驗證步驟）

- [ ] **Step 1: 跑 backend 全測試套件**

Run: `mvn -pl backend test`
Expected: 全綠，含本計畫新增的 4 個測試類別與既有測試。

- [ ] **Step 2: 若有失敗**

逐一檢視失敗測試，回到對應 Task 修正。常見點：`ddl-auto: validate` 對欄位型別不符（檢查 V13 與 entity 一致）；`PageResponse` record 元件名與 Task 7 斷言不符（依實際定義調整）。

- [ ] **Step 3: 最終 commit（若有修正）**

```bash
git add -A
git commit -m "test(risk): 模組 A 全套件回歸通過"
```

---

## Self-Review 紀錄

對照 spec 第 3 節（模組 A）逐項：
- ✅ 風險落地欄位 `risk_level` + `risk_computed_at`（Task 2、3）
- ✅ `search` 移除路徑 2、風險改 SQL 篩選（Task 7）
- ✅ `toSummary` 改讀欄位（Task 4）
- ✅ 維護機制三路：事件觸發（Task 6）、每日排程（Task 9）、啟動補算（Task 8）
- ✅ opt-in 設定 `app.risk.daily-recompute.enabled` 預設 true（Task 9）
- ✅ status / renewal 索引補上（Task 3）
- ✅ 測試：計算純函式、重算服務、風險篩選分頁、啟動補算（Task 1/5/7/8）

型別一致性：`RiskLevelCalculator.calculate(LocalDateTime, LocalDate, LocalDate)`、`RiskLevelMaintenanceService.recompute(Long)` / `recomputeAll()`、`Customer.applyRiskLevel(String)` / `getRiskLevel()`、`CustomerRepository.findIdsByRiskLevelIsNull()` 在各 Task 間一致引用。
