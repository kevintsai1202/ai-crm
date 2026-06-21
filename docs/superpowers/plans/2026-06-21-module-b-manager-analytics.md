# 模組 B：Manager 業務統計頁 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 Manager 專屬頁面 `/team`，呈現各業務（owner）的績效統計（成交、活躍商機、互動情緒、續約四類指標）與團隊總覽，後端 `/api/manager/**` 以 RBAC 限制 MANAGER/ADMIN。

**Architecture:** 後端新增 `ManagerAnalyticsService`（純聚合，不打 LLM）+ `ManagerAnalyticsController`，DTO 以業務（ownerName，與既有 leaderboard / my-work 一致）為單位聚合；SecurityConfig 加一條 `hasAnyRole("MANAGER","ADMIN")`。前端仿 `AdminRoute` 新增 `ManagerRoute`，新頁 `ManagerAnalyticsPage` 顯示 KPI 列 + 可排序業務表。

**Tech Stack:** Java 21、Spring Boot 4.1、Spring Data JPA、JUnit 5 + Testcontainers；React 19 + TypeScript + Vite + react-router-dom v7。

**Scope note:** 整體 spec（`docs/superpowers/specs/2026-06-21-manager-sales-analytics-design.md`）三份計畫之第二份。依賴模組 A 已落地的 `risk_level` 欄位（高風險統計直接讀欄位）。模組 C（AI 功能）將在本頁再加兩個區塊，為獨立計畫。

**設計決策（業務識別）：** 以 `ownerName` 分組聚合，與既有 `ownerLeaderboard`（`DashboardService.java:100`）和「我的工作台」（以 `ownerName` 篩客戶）一致；`OwnerStats.ownerId` 取組內客戶的 `owner` 帳號 id（可能為 null，供前端/模組 C 參考），不作為分組鍵。

---

## 驗證環境

後端（PowerShell）：
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
```
- 後端測試：`mvn -pl backend test -Dtest=<ClassName>`

前端（PowerShell；Git Bash 需先 `export PATH="/d/nodejs:$PATH"`）：
- 型別檢查：`cd frontend; pnpm exec tsc --noEmit`（exit 0）

---

## File Structure

**後端新增：**
- `backend/src/main/java/com/aicrm/crm/service/ManagerAnalyticsService.java` — 各業務績效聚合。
- `backend/src/main/java/com/aicrm/crm/api/ManagerAnalyticsController.java` — `GET /api/manager/analytics`。
- `backend/src/test/java/com/aicrm/crm/service/ManagerAnalyticsServiceTest.java`
- `backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java`

**後端修改：**
- `backend/src/main/java/com/aicrm/crm/api/Dtos.java` — 加 `OwnerStats` / `TeamSummary` / `ManagerAnalyticsResponse`。
- `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java` — 加 `/api/manager/**` 規則。

**前端新增：**
- `frontend/src/app/ManagerRoute.tsx` — MANAGER/ADMIN 路由守衛。
- `frontend/src/features/manager/ManagerAnalyticsPage.tsx` — 統計頁。

**前端修改：**
- `frontend/src/types.ts` — 加 `OwnerStats` / `TeamSummary` / `ManagerAnalyticsResponse`。
- `frontend/src/api.ts` — 加 `fetchManagerAnalytics`。
- `frontend/src/App.tsx` — 加 `/team` 路由（ManagerRoute 包覆）。
- `frontend/src/app/AppShell.tsx` — 側邊欄對 MANAGER/ADMIN 顯示「📈 業務分析」。

---

## Task 1：後端 DTO（OwnerStats / TeamSummary / ManagerAnalyticsResponse）

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`

- [ ] **Step 1: 在 `OwnerReport` record（約 L221）之後新增三個 record**

```java
    /**
     * 單一業務的績效統計（模組 B）。
     *
     * @param ownerId 業務帳號 id（舊資料可能為 null）
     * @param ownerName 業務顯示名稱（分組鍵）
     * @param customerCount 負責客戶數
     * @param highRiskCount 高風險客戶數
     * @param pipelineAmount 進行中商機金額（非 CLOSED_WON/CLOSED_LOST）
     * @param activeOpportunityCount 進行中商機數
     * @param wonAmount 已成交金額（CLOSED_WON）
     * @param wonCount 已成交件數
     * @param winRate 成交率 = won /（won + lost），無已關閉商機時為 0
     * @param avgDaysSinceInteraction 客戶最後互動距今天數平均（無互動客戶不計；全無則 null）
     * @param avgSentimentScore 客戶情緒分數平均（無分析則 null）
     * @param renewalsThisMonth 本月續約到期客戶數
     * @param renewalsThisQuarter 本季續約到期客戶數
     */
    public record OwnerStats(
            Long ownerId,
            String ownerName,
            long customerCount,
            long highRiskCount,
            BigDecimal pipelineAmount,
            long activeOpportunityCount,
            BigDecimal wonAmount,
            long wonCount,
            double winRate,
            Double avgDaysSinceInteraction,
            Double avgSentimentScore,
            long renewalsThisMonth,
            long renewalsThisQuarter
    ) {}

    /**
     * 團隊總覽（模組 B），供統計頁頂部 KPI 列。
     *
     * @param totalCustomers 全部客戶數
     * @param totalWonAmount 全團隊成交金額
     * @param totalPipeline 全團隊進行中商機金額
     * @param totalHighRisk 全團隊高風險客戶數
     * @param avgWinRate 各業務成交率的平均
     * @param ownerCount 業務人數
     */
    public record TeamSummary(
            long totalCustomers,
            BigDecimal totalWonAmount,
            BigDecimal totalPipeline,
            long totalHighRisk,
            double avgWinRate,
            int ownerCount
    ) {}

    /** Manager 業務分析回應：團隊總覽 + 各業務統計（依成交金額降序）。 */
    public record ManagerAnalyticsResponse(TeamSummary team, List<OwnerStats> owners) {}
```

- [ ] **Step 2: 編譯確認**

Run: `mvn -pl backend test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/Dtos.java
git commit -m "feat(manager): 新增 OwnerStats/TeamSummary/ManagerAnalyticsResponse DTO"
```

---

## Task 2：ManagerAnalyticsService（聚合 + 測試）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/ManagerAnalyticsService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/ManagerAnalyticsServiceTest.java`

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Manager 業務分析聚合測試：以種子資料驗證回應結構與基本一致性。 */
class ManagerAnalyticsServiceTest extends PostgresTestBase {

    @Autowired ManagerAnalyticsService analyticsService;

    /** 聚合應回傳非空業務清單，且團隊客戶數等於各業務客戶數總和。 */
    @Test
    void analytics_returnsOwnersAndConsistentTeamTotals() {
        var result = analyticsService.analytics();

        assertThat(result.owners()).isNotEmpty();
        assertThat(result.team().ownerCount()).isEqualTo(result.owners().size());
        long sumCustomers = result.owners().stream().mapToLong(o -> o.customerCount()).sum();
        assertThat(result.team().totalCustomers()).isEqualTo(sumCustomers);
        // 成交率介於 0 與 1
        assertThat(result.owners()).allMatch(o -> o.winRate() >= 0.0 && o.winRate() <= 1.0);
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=ManagerAnalyticsServiceTest`
Expected: 編譯失敗 `cannot find symbol ManagerAnalyticsService`。

- [ ] **Step 3: 寫實作**

```java
package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionInsight;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager 業務分析聚合服務：以 ownerName 為單位彙總各業務績效（成交、活躍商機、情緒、續約），
 * 純 Java/DB 計算，不呼叫 LLM。供 /api/manager/analytics 與模組 C 的 AI 分析作為資料來源。
 */
@Service
@Transactional(readOnly = true)
public class ManagerAnalyticsService {

    /** 客戶資料存取（含 LAZY 商機 / 互動，靠 default_batch_fetch_size 批次載入）。 */
    private final CustomerRepository customers;

    /** 互動情緒意圖分析結果存取，供情緒平均聚合。 */
    private final InteractionInsightRepository insights;

    public ManagerAnalyticsService(CustomerRepository customers, InteractionInsightRepository insights) {
        this.customers = customers;
        this.insights = insights;
    }

    /**
     * 聚合所有業務的績效統計與團隊總覽。
     *
     * @return Manager 業務分析回應（業務清單依成交金額降序）
     */
    public Dtos.ManagerAnalyticsResponse analytics() {
        var all = customers.findAll();
        var today = LocalDate.now();

        // 每個客戶的情緒分數平均（先依 customerId 聚合，避免逐客戶查 DB）
        Map<Long, Double> avgScoreByCustomer = insights.findAll().stream()
                .collect(Collectors.groupingBy(InteractionInsight::getCustomerId,
                        Collectors.averagingInt(InteractionInsight::getSentimentScore)));

        // 以 ownerName 分組（與既有 leaderboard / 我的工作台一致）
        var byOwner = all.stream().collect(Collectors.groupingBy(Customer::getOwnerName));

        var owners = byOwner.entrySet().stream()
                .map(entry -> buildOwnerStats(entry.getKey(), entry.getValue(), today, avgScoreByCustomer))
                .sorted((a, b) -> b.wonAmount().compareTo(a.wonAmount()))
                .toList();

        return new Dtos.ManagerAnalyticsResponse(buildTeamSummary(all.size(), owners), owners);
    }

    /**
     * 聚合單一業務的績效。
     *
     * @param ownerName 業務顯示名稱
     * @param ownerCustomers 該業務負責的客戶
     * @param today 基準日
     * @param avgScoreByCustomer 每客戶情緒平均
     * @return 該業務的統計
     */
    private Dtos.OwnerStats buildOwnerStats(String ownerName, List<Customer> ownerCustomers, LocalDate today,
                                            Map<Long, Double> avgScoreByCustomer) {
        // ownerId 取組內第一個有正規關聯的客戶（供前端/模組 C 參考）
        Long ownerId = ownerCustomers.stream()
                .map(c -> c.getOwner() == null ? null : c.getOwner().getId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        var opps = ownerCustomers.stream().flatMap(c -> c.getOpportunities().stream()).toList();
        var pipelineAmount = opps.stream()
                .filter(o -> o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST)
                .map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeCount = opps.stream()
                .filter(o -> o.getStage() != OpportunityStage.CLOSED_WON && o.getStage() != OpportunityStage.CLOSED_LOST)
                .count();
        var wonOpps = opps.stream().filter(o -> o.getStage() == OpportunityStage.CLOSED_WON).toList();
        var wonAmount = wonOpps.stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long wonCount = wonOpps.size();
        long lostCount = opps.stream().filter(o -> o.getStage() == OpportunityStage.CLOSED_LOST).count();
        // 成交率 = won /（won + lost）；無已關閉商機時為 0
        double winRate = (wonCount + lostCount) == 0 ? 0.0 : (double) wonCount / (wonCount + lostCount);

        long highRisk = ownerCustomers.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();

        // 互動活躍度：有互動客戶的「最後互動距今天數」平均
        var daysList = ownerCustomers.stream()
                .map(c -> lastInteractionDays(c, today))
                .filter(Objects::nonNull)
                .toList();
        Double avgDays = daysList.isEmpty() ? null
                : daysList.stream().mapToLong(Long::longValue).average().orElse(0);

        // 客戶情緒平均：取有分數的客戶平均
        var scoreList = ownerCustomers.stream()
                .map(c -> avgScoreByCustomer.get(c.getId()))
                .filter(Objects::nonNull)
                .toList();
        Double avgSentiment = scoreList.isEmpty() ? null
                : scoreList.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        long renewalsThisMonth = countRenewalsInRange(ownerCustomers,
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        int q = (today.getMonthValue() - 1) / 3;
        var qStart = LocalDate.of(today.getYear(), q * 3 + 1, 1);
        var qEnd = qStart.plusMonths(3).minusDays(1);
        long renewalsThisQuarter = countRenewalsInRange(ownerCustomers, qStart, qEnd);

        return new Dtos.OwnerStats(ownerId, ownerName, ownerCustomers.size(), highRisk,
                pipelineAmount, activeCount, wonAmount, wonCount, winRate,
                avgDays, avgSentiment, renewalsThisMonth, renewalsThisQuarter);
    }

    /**
     * 客戶最後互動距今天數（無互動回 null）。
     */
    private Long lastInteractionDays(Customer c, LocalDate today) {
        LocalDateTime last = c.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return last == null ? null : ChronoUnit.DAYS.between(last.toLocalDate(), today);
    }

    /**
     * 計算續約日落在 [from, to]（含端點）的客戶數。
     */
    private long countRenewalsInRange(List<Customer> ownerCustomers, LocalDate from, LocalDate to) {
        return ownerCustomers.stream()
                .map(Customer::getRenewalDueDate)
                .filter(Objects::nonNull)
                .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                .count();
    }

    /**
     * 由各業務統計彙總團隊總覽。
     */
    private Dtos.TeamSummary buildTeamSummary(int totalCustomers, List<Dtos.OwnerStats> owners) {
        var totalWon = owners.stream().map(Dtos.OwnerStats::wonAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalPipeline = owners.stream().map(Dtos.OwnerStats::pipelineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalHighRisk = owners.stream().mapToLong(Dtos.OwnerStats::highRiskCount).sum();
        double avgWinRate = owners.isEmpty() ? 0.0
                : owners.stream().mapToDouble(Dtos.OwnerStats::winRate).average().orElse(0);
        return new Dtos.TeamSummary(totalCustomers, totalWon, totalPipeline, totalHighRisk, avgWinRate, owners.size());
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=ManagerAnalyticsServiceTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/ManagerAnalyticsService.java backend/src/test/java/com/aicrm/crm/service/ManagerAnalyticsServiceTest.java
git commit -m "feat(manager): ManagerAnalyticsService 聚合各業務績效"
```

---

## Task 3：Controller + 權限（RBAC + 測試）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/api/ManagerAnalyticsController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java`

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Manager 分析端點權限測試：MANAGER 可進、SALES 被擋。 */
class ManagerAnalyticsSecurityTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 以指定帳號登入取得 token。 */
    private String login(String username) throws Exception {
        var body = "{\"username\":\"" + username + "\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void manager_canAccessAnalytics() throws Exception {
        var token = login("manager@aurora.local");
        mockMvc().perform(get("/api/manager/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void sales_isForbidden() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/manager/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -pl backend test -Dtest=ManagerAnalyticsSecurityTest`
Expected: FAIL（端點不存在 → 404 而非預期狀態）。

- [ ] **Step 3: 建立 Controller**

```java
package com.aicrm.crm.api;

import com.aicrm.crm.service.ManagerAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager 業務分析 API：提供各業務績效統計與團隊總覽。
 * 端點存取由 SecurityConfig 以 hasAnyRole("MANAGER","ADMIN") 限制。
 */
@RestController
@RequestMapping("/api/manager")
public class ManagerAnalyticsController {

    /** 業務分析聚合服務。 */
    private final ManagerAnalyticsService analyticsService;

    public ManagerAnalyticsController(ManagerAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 回傳各業務績效統計與團隊總覽。
     *
     * @return Manager 業務分析回應
     */
    @GetMapping("/analytics")
    public Dtos.ManagerAnalyticsResponse analytics() {
        return analyticsService.analytics();
    }
}
```

- [ ] **Step 4: SecurityConfig 加授權規則**

在 `.requestMatchers(HttpMethod.GET, "/api/ai/usage").hasAnyRole("MANAGER", "ADMIN")`（L58）之後新增一行：

```java
                        .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
```

- [ ] **Step 5: 跑測試確認通過**

Run: `mvn -pl backend test -Dtest=ManagerAnalyticsSecurityTest`
Expected: PASS（manager 200、sales 403）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/ManagerAnalyticsController.java backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java
git commit -m "feat(manager): /api/manager/analytics 端點 + MANAGER/ADMIN RBAC"
```

---

## Task 4：前端型別與 API

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`

- [ ] **Step 1: types.ts 在 `OwnerReport`（約 L132）之後新增介面**

```typescript
/** 單一業務的績效統計（模組 B），對應後端 Dtos.OwnerStats。 */
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
  avgDaysSinceInteraction: number | null;
  avgSentimentScore: number | null;
  renewalsThisMonth: number;
  renewalsThisQuarter: number;
}

/** 團隊總覽（模組 B），對應後端 Dtos.TeamSummary。 */
export interface TeamSummary {
  totalCustomers: number;
  totalWonAmount: number;
  totalPipeline: number;
  totalHighRisk: number;
  avgWinRate: number;
  ownerCount: number;
}

/** Manager 業務分析回應，對應後端 Dtos.ManagerAnalyticsResponse。 */
export interface ManagerAnalyticsResponse {
  team: TeamSummary;
  owners: OwnerStats[];
}
```

- [ ] **Step 2: api.ts 加入型別 import 與函式**

在 `import type { ... } from "./types";` 區塊加入 `ManagerAnalyticsResponse,`（與其他型別並列）。

在 `fetchDashboardReports`（約 L115）之後新增：

```typescript
/**
 * 讀取 Manager 業務分析（各業務績效 + 團隊總覽）。
 *
 * @returns 業務分析回應
 */
export async function fetchManagerAnalytics() {
  const { data } = await apiClient.get<ManagerAnalyticsResponse>("/manager/analytics");
  return data;
}
```

- [ ] **Step 3: 型別檢查**

Run: `cd frontend; pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types.ts frontend/src/api.ts
git commit -m "feat(manager): 前端 ManagerAnalytics 型別與 API"
```

---

## Task 5：路由守衛、路由表、側邊欄

**Files:**
- Create: `frontend/src/app/ManagerRoute.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/app/AppShell.tsx`

- [ ] **Step 1: 建立 ManagerRoute（仿 AdminRoute）**

```tsx
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * 業務主管路由守衛：MANAGER 與 ADMIN 可進入，其餘導回儀表板。
 * 函式級註解：用於 /team 路由，前端先擋一層；後端 /api/manager/** 亦以 RBAC 限制（雙重防護）。
 */
export function ManagerRoute() {
  const { user } = useAuth();
  return user?.role === "MANAGER" || user?.role === "ADMIN" ? <Outlet /> : <Navigate to="/dashboard" replace />;
}
```

- [ ] **Step 2: App.tsx 加 import 與路由**

加 import：

```tsx
import { ManagerRoute } from "./app/ManagerRoute";
import { ManagerAnalyticsPage } from "./features/manager/ManagerAnalyticsPage";
```

在 `<Route path="/my-work" ... />` 之後、AdminRoute 區塊之前加入：

```tsx
          <Route element={<ManagerRoute />}>
            <Route path="/team" element={<ManagerAnalyticsPage />} />
          </Route>
```

- [ ] **Step 3: AppShell.tsx 側邊欄加選單**

在 `/my-work` 的 NavLink（L41）之後、`/customers` 之前加入（僅 MANAGER/ADMIN 顯示）：

```tsx
          {user?.role === "MANAGER" || user?.role === "ADMIN" ? (
            <NavLink to="/team" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>📈 業務分析</NavLink>
          ) : null}
```

- [ ] **Step 4: 型別檢查（此時 ManagerAnalyticsPage 尚未建立，預期失敗）**

Run: `cd frontend; pnpm exec tsc --noEmit`
Expected: FAIL — 找不到 `./features/manager/ManagerAnalyticsPage`。下一個 Task 建立後即通過。

- [ ] **Step 5: 暫不 commit，待 Task 6 頁面建立後一起驗證**

---

## Task 6：ManagerAnalyticsPage（KPI 列 + 可排序業務表）

**Files:**
- Create: `frontend/src/features/manager/ManagerAnalyticsPage.tsx`

- [ ] **Step 1: 建立頁面元件**

```tsx
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchManagerAnalytics } from "../../api";
import type { ManagerAnalyticsResponse, OwnerStats } from "../../types";
import { formatMoney } from "../../lib/format";

/** 可排序欄位鍵。 */
type SortKey = "wonAmount" | "pipelineAmount" | "winRate" | "customerCount" | "highRiskCount" | "renewalsThisQuarter";

/**
 * Manager 業務分析頁：頂部團隊 KPI 列 + 各業務績效可排序表。
 * 函式級註解：唯讀檢視，資料來自 /api/manager/analytics；點業務列導向客戶工作台並以該業務名稱預篩。
 */
export function ManagerAnalyticsPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<ManagerAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  // 預設依成交金額降序
  const [sortKey, setSortKey] = useState<SortKey>("wonAmount");

  // 進頁載入分析資料
  useEffect(() => {
    setLoading(true);
    setError(false);
    fetchManagerAnalytics()
      .then(setData)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  // 依選定欄位對業務排序（皆為數值，降序）
  const sortedOwners = useMemo(() => {
    if (!data) return [];
    return [...data.owners].sort((a, b) => (b[sortKey] as number) - (a[sortKey] as number));
  }, [data, sortKey]);

  /** 成交率顯示為百分比。 */
  const pct = (v: number) => `${Math.round(v * 100)}%`;
  /** 情緒分數顯示（null 顯示 —）。 */
  const score = (v: number | null) => (v === null ? "—" : Math.round(v).toString());

  /** 可點擊排序的表頭欄位。 */
  function SortableTh({ label, k }: { label: string; k: SortKey }) {
    return (
      <th onClick={() => setSortKey(k)} style={{ cursor: "pointer" }}>
        {label}{sortKey === k ? " ▼" : ""}
      </th>
    );
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>業務分析</h2>
        </div>
      </section>

      {loading ? (
        <section className="panel"><p>載入中…</p></section>
      ) : error ? (
        <section className="panel empty-state-box"><p>無法載入業務分析資料。</p></section>
      ) : data ? (
        <>
          {/* 團隊 KPI 列 */}
          <div className="kpi-row">
            <div className="kpi-card"><span className="kpi-label">業務人數</span><span className="kpi-value">{data.team.ownerCount}</span></div>
            <div className="kpi-card"><span className="kpi-label">團隊成交額</span><span className="kpi-value kpi-value-accent">{formatMoney(data.team.totalWonAmount)}</span></div>
            <div className="kpi-card"><span className="kpi-label">團隊 Pipeline</span><span className="kpi-value">{formatMoney(data.team.totalPipeline)}</span></div>
            <div className="kpi-card"><span className="kpi-label">平均成交率</span><span className="kpi-value">{pct(data.team.avgWinRate)}</span></div>
            <div className="kpi-card"><span className="kpi-label">高風險客戶</span><span className="kpi-value">{data.team.totalHighRisk}</span></div>
          </div>

          {/* 業務績效表（點欄位頭排序、點列預篩客戶） */}
          <section className="panel">
            <table className="data-table">
              <thead>
                <tr>
                  <th>業務</th>
                  <SortableTh label="客戶數" k="customerCount" />
                  <SortableTh label="成交額" k="wonAmount" />
                  <SortableTh label="成交率" k="winRate" />
                  <SortableTh label="Pipeline" k="pipelineAmount" />
                  <SortableTh label="高風險" k="highRiskCount" />
                  <th>平均情緒</th>
                  <SortableTh label="本季續約" k="renewalsThisQuarter" />
                </tr>
              </thead>
              <tbody>
                {sortedOwners.map((o: OwnerStats) => (
                  <tr key={o.ownerName} style={{ cursor: "pointer" }}
                      onClick={() => navigate(`/customers?owner=${encodeURIComponent(o.ownerName)}`)}>
                    <td>{o.ownerName}</td>
                    <td>{o.customerCount}</td>
                    <td>{formatMoney(o.wonAmount)}（{o.wonCount}）</td>
                    <td>{pct(o.winRate)}</td>
                    <td>{formatMoney(o.pipelineAmount)}（{o.activeOpportunityCount}）</td>
                    <td>{o.highRiskCount}</td>
                    <td>{score(o.avgSentimentScore)}</td>
                    <td>{o.renewalsThisQuarter}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      ) : null}
    </>
  );
}
```

> 註：`data-table`、`panel`、`kpi-row`、`kpi-card` 等 className 沿用既有樣式系統（`MyWorkspacePage` 使用 `kpi-row`/`kpi-card`；`AdminUsersPage` 使用表格樣式）。若 `data-table` class 不存在，沿用 `AdminUsersPage` 實際使用的表格 className（實作時打開該檔確認）。`formatMoney` 來自 `lib/format`，可接受 number。

- [ ] **Step 2: 型別檢查（含 Task 5 的路由接線）**

Run: `cd frontend; pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 3: Commit（Task 5 + 6 一起）**

```bash
git add frontend/src/app/ManagerRoute.tsx frontend/src/App.tsx frontend/src/app/AppShell.tsx frontend/src/features/manager/ManagerAnalyticsPage.tsx
git commit -m "feat(manager): 業務分析頁 + ManagerRoute 守衛 + 側邊欄入口"
```

---

## Task 7：整合驗證

**Files:** 無（驗證步驟）

- [ ] **Step 1: 後端全測試**

Run: `mvn -pl backend test`
Expected: 全綠（含模組 A 既有測試 + 本計畫 2 個新測試類別）。

- [ ] **Step 2: 前端型別與建置**

Run: `cd frontend; pnpm exec tsc --noEmit; pnpm build`
Expected: tsc exit 0、build 成功。

- [ ] **Step 3: 手動煙霧驗證（需後端在 18080、前端 dev）**

以 `manager@aurora.local / password123` 登入 → 側邊欄出現「📈 業務分析」→ 進入 `/team` 看到 KPI 列與業務表、可點欄位排序；以 `sales@aurora.local` 登入 → 不顯示該選單、手動進 `/team` 被導回 `/dashboard`。

- [ ] **Step 4: 最終 commit（若有修正）**

```bash
git add -A
git commit -m "test(manager): 模組 B 整合驗證通過"
```

---

## Self-Review 紀錄

對照 spec 第 4 節（模組 B）逐項：
- ✅ `GET /api/manager/analytics` 回 `OwnerStats`（Task 1/2/3）
- ✅ 四類指標：成交（wonAmount/wonCount/winRate）、活躍商機（pipelineAmount/activeOpportunityCount）、互動情緒（avgDaysSinceInteraction/avgSentimentScore）、續約（renewalsThisMonth/ThisQuarter）+ 既有 customerCount/highRiskCount（Task 2）
- ✅ 以業務聚合（ownerName，決策已註記；ownerId 帶出供參考）（Task 2）
- ✅ 團隊 KPI 列 + 可排序表（Task 6）
- ✅ ManagerRoute 守衛 + 後端 RBAC（Task 3/5）
- ✅ 點業務列帶到客戶工作台預篩（Task 6）

型別一致性：後端 `OwnerStats`（13 欄）↔ 前端 `OwnerStats` interface 欄位逐一對齊；`winRate` 後端 double（0~1）、前端 `pct()` 轉百分比；`avgSentimentScore` 可為 null 兩端一致。`fetchManagerAnalytics` → `/manager/analytics` 對應 Controller `@GetMapping("/analytics")` + `@RequestMapping("/api/manager")`。

待實作注意：Task 6 的表格 className 需於實作時對照 `AdminUsersPage` 既有樣式確認；`/customers?owner=` 預篩需 `CustomersPage` 支援讀取該 query（若不支援則僅導向，不影響核心功能，可後續加強）。
