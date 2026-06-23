# Dashboard 快取與逐一渲染 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 Caffeine in-memory cache（TTL 2 分鐘）消除 3 個並行 API 的全量 DB 掃描，並將前端 `Promise.all` 改為各 API 獨立渲染，讓儀表板 F5 後幾乎瞬間顯示。

**Architecture:** 後端 4 個 dashboard service 方法加 `@Cacheable`；`DemoDataService.generate()` 與 `SentimentIntentService.analyzeMissing()` 加 `@CacheEvict` 清對應 cache；`SentimentService.radar()` 同時修正 `aggregateCustomerRisk()` 被呼叫兩次的重複查詢。前端 `loadDashboardData()` 從 `Promise.all` 改為 4 個各自 `.then(setState)`，哪個 API 先回來就先渲染。

**Tech Stack:** Spring Boot 4.1 + Caffeine 3.x + `spring-boot-starter-cache` + React 19 + TypeScript

---

## 異動檔案地圖

**後端 — 新增：**
- `backend/src/main/java/com/aicrm/crm/config/CacheConfig.java`

**後端 — 修改：**
- `backend/pom.xml`（加 2 個 dependency）
- `backend/src/main/java/com/aicrm/AiCrmApplication.java`（加 `@EnableCaching`）
- `backend/src/main/java/com/aicrm/crm/service/SentimentService.java`（修雙重呼叫 + `@Cacheable`）
- `backend/src/main/java/com/aicrm/crm/service/DashboardService.java`（`@Cacheable` ×2）
- `backend/src/main/java/com/aicrm/crm/service/RfmService.java`（`@Cacheable`）
- `backend/src/main/java/com/aicrm/crm/service/DemoDataService.java`（`@CacheEvict` 全部）
- `backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java`（`@CacheEvict` sentiment）

**後端 — 測試（新增）：**
- `backend/src/test/java/com/aicrm/crm/service/SentimentServiceTest.java`

**前端 — 修改：**
- `frontend/src/features/dashboard/DashboardPage.tsx`

---

### Task 1: pom.xml 加 Caffeine dependency

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 在 `<dependencies>` 區塊加入兩個 dependency**

找到 `spring-boot-starter-test` 所在的 `<dependency>` 區塊，在其**前方**插入：

```xml
        <!-- Spring Cache 抽象層 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        <!-- Caffeine in-memory cache 實作（TTL 設定） -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
```

版本由 `spring-boot-starter-parent 4.1.0` BOM 管理，不需手動指定。

- [ ] **Step 2: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```

Expected: BUILD SUCCESS（無 dependency 解析錯誤）

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "build(backend): 新增 spring-boot-starter-cache + caffeine dependency"
```

---

### Task 2: 建立 CacheConfig + 啟用 @EnableCaching

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/config/CacheConfig.java`
- Modify: `backend/src/main/java/com/aicrm/AiCrmApplication.java`

- [ ] **Step 1: 建立 `CacheConfig.java`**

新建檔案 `backend/src/main/java/com/aicrm/crm/config/CacheConfig.java`，完整內容：

```java
package com.aicrm.crm.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine in-memory cache 設定，TTL 2 分鐘。
 * 四個 dashboard cache 各自獨立命名，供 @Cacheable / @CacheEvict 使用。
 */
@Configuration
public class CacheConfig {

    /** Dashboard cache 名稱常數，供 @Cacheable / @CacheEvict 引用。 */
    public static final String CACHE_DASHBOARD_SUMMARY   = "dashboard-summary";
    public static final String CACHE_DASHBOARD_REPORTS   = "dashboard-reports";
    public static final String CACHE_DASHBOARD_RFM       = "dashboard-rfm";
    public static final String CACHE_DASHBOARD_SENTIMENT = "dashboard-sentiment";

    /**
     * 建立 Caffeine CacheManager，統一 TTL 2 分鐘。
     * maximumSize=1：dashboard 聚合無分 key，只存一份結果。
     *
     * @return Spring CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(
                CACHE_DASHBOARD_SUMMARY,
                CACHE_DASHBOARD_REPORTS,
                CACHE_DASHBOARD_RFM,
                CACHE_DASHBOARD_SENTIMENT
        ));
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .maximumSize(1));
        return manager;
    }
}
```

- [ ] **Step 2: 在 `AiCrmApplication.java` 加 `@EnableCaching`**

找到 `@EnableScheduling` 那一行，在其**下方**加一行：

```java
@EnableCaching
```

並在 import 區加入：

```java
import org.springframework.cache.annotation.EnableCaching;
```

完整的 annotation 區段應為：

```java
@EnableJpaAuditing
@EnableScheduling
@EnableCaching
@SpringBootApplication
public class AiCrmApplication {
```

- [ ] **Step 3: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/config/CacheConfig.java \
        backend/src/main/java/com/aicrm/AiCrmApplication.java
git commit -m "feat(backend): CacheConfig Caffeine TTL=2min + @EnableCaching"
```

---

### Task 3: SentimentService — 修雙重查詢 + @Cacheable

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/SentimentService.java`
- Create: `backend/src/test/java/com/aicrm/crm/service/SentimentServiceTest.java`

- [ ] **Step 1: 先寫 failing 測試**

新建 `backend/src/test/java/com/aicrm/crm/service/SentimentServiceTest.java`：

```java
package com.aicrm.crm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicrm.crm.repository.InteractionInsightRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SentimentService 單元測試：驗證 radar() 不重複呼叫 aggregateCustomerRisk()。
 */
@ExtendWith(MockitoExtension.class)
class SentimentServiceTest {

    @Mock
    InteractionInsightRepository insights;

    @InjectMocks
    SentimentService service;

    /**
     * radar() 內 churnRadar() 與 priorityCare() 共用同一次 aggregateCustomerRisk()，
     * 不應對 DB 發出兩次相同查詢。
     */
    @Test
    void radar_aggregateCustomerRiskCalledOnce() {
        when(insights.countByIntent()).thenReturn(List.of());
        when(insights.sentimentTrendSince(any())).thenReturn(List.of());
        when(insights.findHighRiskInteractions(any())).thenReturn(List.of());
        when(insights.aggregateCustomerRisk()).thenReturn(List.of());

        service.radar();

        verify(insights, times(1)).aggregateCustomerRisk();
    }
}
```

- [ ] **Step 2: 執行測試，確認 FAIL**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -Dtest=SentimentServiceTest -q
```

Expected: FAIL — `aggregateCustomerRisk` 被呼叫 2 次，`Wanted 1 time but was 2 times`

- [ ] **Step 3: 修改 `SentimentService.java`**

**Step 3a：** 找到 `radar()` 方法，替換整個方法體：

```java
    /**
     * 產出情緒意圖雷達 5 區塊；churnRadar 先算一次，供 priorityCare 複用，避免重複查詢。
     *
     * @return 雷達聚合結果
     */
    public Dtos.SentimentRadarResponse radar() {
        var churn = churnRadar();
        return new Dtos.SentimentRadarResponse(
                intentDistribution(),
                sentimentTrend(),
                highRiskInteractions(),
                churn,
                priorityCareFrom(churn)
        );
    }
```

**Step 3b：** 找到 `priorityCare()` 方法（第 163 行附近），整個方法**改名並改簽名**為 `priorityCareFrom`，接收已算好的 churn list：

```java
    /**
     * 優先關懷 top 10：取傳入的 churnRadar 結果（最高分者），附中文關懷理由。
     * 不重複呼叫 DB；由 radar() 傳入已算好的結果。
     *
     * @param radar 流失雷達清單（已依分數降冪排序）
     * @return 優先關懷清單
     */
    private List<Dtos.PriorityCareItem> priorityCareFrom(List<Dtos.ChurnRadarItem> radar) {
        var result = new ArrayList<Dtos.PriorityCareItem>();
        for (var item : radar) {
            if (result.size() >= PRIORITY_CARE_LIMIT) {
                break;
            }
            result.add(new Dtos.PriorityCareItem(item.customerId(), item.name(), buildReason(item)));
        }
        return result;
    }
```

- [ ] **Step 4: 加 `@Cacheable`**

在 `radar()` 方法上方加 annotation 與 import：

在 import 區加入（若尚未存在）：
```java
import com.aicrm.crm.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
```

在 `radar()` 方法前加：
```java
    @Cacheable(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
```

最終 `radar()` 開頭應為：
```java
    @Cacheable(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
    public Dtos.SentimentRadarResponse radar() {
        var churn = churnRadar();
        ...
```

- [ ] **Step 5: 執行測試，確認 PASS**

```powershell
mvn -pl backend test -Dtest=SentimentServiceTest -q
```

Expected: BUILD SUCCESS, Tests run: 1, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/SentimentService.java \
        backend/src/test/java/com/aicrm/crm/service/SentimentServiceTest.java
git commit -m "fix(backend): SentimentService 修雙重 aggregateCustomerRisk + @Cacheable"
```

---

### Task 4: DashboardService + RfmService 加 @Cacheable

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/DashboardService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/RfmService.java`

- [ ] **Step 1: `DashboardService.java` — 加 import 與兩個 `@Cacheable`**

在 import 區加入（class 檔案頂部）：
```java
import com.aicrm.crm.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
```

在 `dashboardSummary()` 方法前加：
```java
    @Cacheable(CacheConfig.CACHE_DASHBOARD_SUMMARY)
```

在 `dashboardReports()` 方法前加：
```java
    @Cacheable(CacheConfig.CACHE_DASHBOARD_REPORTS)
```

`drilldown()` 方法**不加** cache（依參數 key 不同，結果各異，且非熱路徑）。

- [ ] **Step 2: `RfmService.java` — 加 import 與 `@Cacheable`**

在 import 區加入：
```java
import com.aicrm.crm.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
```

在 `computeRfm()` 方法前加：
```java
    @Cacheable(CacheConfig.CACHE_DASHBOARD_RFM)
```

- [ ] **Step 3: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/DashboardService.java \
        backend/src/main/java/com/aicrm/crm/service/RfmService.java
git commit -m "feat(backend): DashboardService + RfmService @Cacheable dashboard caches"
```

---

### Task 5: DemoDataService + SentimentIntentService 加 @CacheEvict

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/DemoDataService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java`

- [ ] **Step 1: `DemoDataService.java` — 加 import 與 `@CacheEvict`**

在 import 區加入：
```java
import com.aicrm.crm.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
```

在 `generate(int customers)` 方法前加：
```java
    @Caching(evict = {
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_SUMMARY),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_REPORTS),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_RFM),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
    })
```

（`@Caching` 是 Spring 組合多個 cache operation 的標準做法）

- [ ] **Step 2: `SentimentIntentService.java` — 加 import 與 `@CacheEvict`**

在 import 區加入：
```java
import com.aicrm.crm.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
```

在 `analyzeMissing(boolean useLlm)` 方法前加：
```java
    @CacheEvict(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
```

- [ ] **Step 3: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 執行全部後端測試，確認無 regression**

```powershell
mvn -pl backend test -q
```

Expected: BUILD SUCCESS（所有現有測試仍通過）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/DemoDataService.java \
        backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java
git commit -m "feat(backend): DemoDataService + SentimentIntentService @CacheEvict"
```

---

### Task 6: 前端 DashboardPage — Promise.all 改為逐一渲染

**Files:**
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

- [ ] **Step 1: 修改 `loadDashboardData()` 函式（第 68–79 行）**

找到：
```typescript
  /** 載入儀表板全部資料（摘要、報表、RFM、情緒雷達），供進頁與示範資料生成後重載共用。 */
  async function loadDashboardData() {
    const [summary, reportResult, rfmResult, sentimentResult] = await Promise.all([
      fetchDashboard(),
      fetchDashboardReports(),
      fetchRfm(),
      fetchSentimentRadar()
    ]);
    setDashboard(summary);
    setReports(reportResult);
    setRfm(rfmResult);
    setSentiment(sentimentResult);
  }
```

替換為：
```typescript
  /** 載入儀表板全部資料；各 API 獨立回來即 setState，不相互阻塞。 */
  function loadDashboardData() {
    fetchDashboard()
      .then(setDashboard)
      .catch((e) => console.error("摘要載入失敗:", e));
    fetchDashboardReports()
      .then(setReports)
      .catch((e) => console.error("報表載入失敗:", e));
    fetchRfm()
      .then(setRfm)
      .catch((e) => console.error("RFM 載入失敗:", e));
    fetchSentimentRadar()
      .then(setSentiment)
      .catch((e) => console.error("情緒雷達載入失敗:", e));
  }
```

- [ ] **Step 2: 修改 `handleGenerateDemo()` 中的呼叫方式（第 198–208 行附近）**

找到：
```typescript
  async function handleGenerateDemo() {
    setGeneratingDemo(true);
    try {
      await generateDemoData(200);
      await loadDashboardData();
    } catch (e) {
      console.error("產生示範資料失敗:", e);
    } finally {
      setGeneratingDemo(false);
    }
  }
```

替換為：
```typescript
  /** 產生示範資料（ADMIN）：生成 200 位客戶樣本後觸發儀表板各區塊逐一更新。 */
  async function handleGenerateDemo() {
    setGeneratingDemo(true);
    try {
      await generateDemoData(200);
      loadDashboardData();
    } catch (e) {
      console.error("產生示範資料失敗:", e);
    } finally {
      setGeneratingDemo(false);
    }
  }
```

（移除 `await loadDashboardData()`，因為 `loadDashboardData` 已改為 void 函式，各 API 回來後自動更新 UI）

- [ ] **Step 3: TypeScript 型別檢查**

```powershell
cd d:/GitHub/ai-crm/frontend
pnpm exec tsc --noEmit
```

Expected: exit 0（無型別錯誤）

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(frontend): loadDashboardData 改逐一渲染，消除 Promise.all 阻塞"
```

---

### Task 7: 後端本機冒煙測試

- [ ] **Step 1: 啟動後端**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
```

等待 `Started AiCrmApplication` 出現。

- [ ] **Step 2: 取得 token**

```powershell
$resp = Invoke-RestMethod -Method Post -Uri "http://localhost:18080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin@aurora.local","password":"password123"}'
$TOKEN = $resp.token
Write-Host "Token: $($TOKEN.Substring(0,20))..."
```

Expected: 取得 JWT token

- [ ] **Step 3: 呼叫 dashboard summary 兩次，量測時間差**

```powershell
$headers = @{Authorization = "Bearer $TOKEN"}

$sw1 = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-RestMethod -Uri "http://localhost:18080/api/dashboard/summary" -Headers $headers | Out-Null
$sw1.Stop(); Write-Host "第一次（DB 查詢）: $($sw1.ElapsedMilliseconds) ms"

$sw2 = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-RestMethod -Uri "http://localhost:18080/api/dashboard/summary" -Headers $headers | Out-Null
$sw2.Stop(); Write-Host "第二次（cache hit）: $($sw2.ElapsedMilliseconds) ms"
```

Expected: 第二次應明顯快於第一次（快取命中，通常 < 5 ms）

- [ ] **Step 4: 驗證 sentiment 端點**

```powershell
$sw1 = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-RestMethod -Uri "http://localhost:18080/api/dashboard/sentiment" -Headers $headers | Out-Null
$sw1.Stop(); Write-Host "第一次: $($sw1.ElapsedMilliseconds) ms"

$sw2 = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-RestMethod -Uri "http://localhost:18080/api/dashboard/sentiment" -Headers $headers | Out-Null
$sw2.Stop(); Write-Host "第二次（cache hit）: $($sw2.ElapsedMilliseconds) ms"
```

Expected: 第二次 < 5 ms

- [ ] **Step 5: 確認無 regression**

```powershell
mvn -pl backend test -q
```

Expected: BUILD SUCCESS

---

## Self-Review

**Spec coverage 對照：**
- ✓ Caffeine TTL 2 分鐘（Task 2 CacheConfig）
- ✓ `dashboard-summary` / `dashboard-reports` / `dashboard-rfm` / `dashboard-sentiment` 四個 cache（Task 2, 3, 4）
- ✓ `DemoDataService.generate()` evict 全部（Task 5）
- ✓ `SentimentIntentService.analyzeMissing()` evict `dashboard-sentiment`（Task 5）
- ✓ `SentimentService.radar()` 修正雙重 `aggregateCustomerRisk()` 呼叫（Task 3）
- ✓ 前端 `Promise.all` → 逐一 `.then(setState)`（Task 6）
- ✓ `handleGenerateDemo` 移除 `await loadDashboardData()`（Task 6）

**Placeholder scan：** 無 TBD / TODO / "類似上方" 等模糊描述。

**Type consistency：**
- `CacheConfig.CACHE_DASHBOARD_*` 常數在 Task 2 定義，Task 3–5 引用，名稱一致。
- `priorityCareFrom(List<Dtos.ChurnRadarItem>)` 在 Task 3 定義並使用，簽名一致。
- `loadDashboardData()` 從 `async function` 改為 `function`（void），Task 6 Step 2 移除 `await` 呼叫，一致。
