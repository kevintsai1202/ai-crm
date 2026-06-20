# SP2 後端測試網 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development。Steps 用 checkbox（`- [ ]`）追蹤。

**Goal:** 為後端核心邏輯建立單元 + 整合 + 冒煙三層測試網，把「錯了會出事」的行為（風險計算、JWT、RBAC、AI fallback、context boot）釘住，作為 SP3+ 改動的安全網。

**Architecture:** 既有 production 程式碼不動；新增 `backend/src/test/**` 測試與 `application-test.yml`。單元測試用 Mockito（不啟 Spring）；整合測試用 `@SpringBootTest + MockMvc + H2 in-memory + @ActiveProfiles("test")` 並以 `@SpringBootTest(properties="spring.ai.openai.api-key=")` 蓋空金鑰強制 AI fallback、避免打真 LLM。這些是針對既有程式的特徵化/回歸測試，**預期一寫就綠**；若某測試揭露真實 bug，回報 DONE_WITH_CONCERNS 不要改 production 碼。

**Tech Stack:** JUnit 5、Mockito、Spring Boot Test、Spring Security Test、H2、JaCoCo、Jackson 3（tools.jackson）。

**環境限制：**
- 用 Maven，JDK 21。每個指令前設環境（Git Bash）：
  ```
  export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
  ```
  （PowerShell 版：`$env:JAVA_HOME="D:\java\jdk-21"; $env:Path="$env:JAVA_HOME\bin;$env:Path"`）
- 工作目錄：repo 根 `d:\GitHub\ai-crm`，以 `mvn -pl backend ...` 操作 backend 模組。
- spec：`docs/superpowers/specs/2026-06-19-sp2-backend-test-net-design.md`。
- 經驗：`skill-spring-boot-testing.md`（BCrypt 不可重生、@SpringBootTest 才抓得到 context 啟動失敗）。

**關鍵事實（已讀碼確認）：**
- `Customer(name,email,phone,taxId,industry,ownerName)`；`customer.addInteraction(Interaction)`；`customer.updateContractDates(start,end,renewalDue)`。
- `Interaction(InteractionType, LocalDateTime, content)`。`InteractionType`：PHONE/MEETING/EMAIL/SUPPORT_TICKET。
- `AppUser(username, passwordHash, displayName, role)`；`getId()` 無 setter（@GeneratedValue）。`Role`：SALES/MANAGER/ADMIN。
- `JwtService.issue()` 內 `Map.of("uid", user.getId())` → id 為 null 會 NPE，故 JwtServiceTest 需用 `ReflectionTestUtils.setField(user,"id",1L)` 設 id。
- `JwtService.parse()` 任何失敗都拋 `IllegalArgumentException`。
- SecurityConfig 已有 401 entry point；JwtAuthenticationFilter 已 catch `IllegalArgumentException`（測試確認此行為）。
- 無實際 DELETE handler，但 SecurityConfig 有 `DELETE /api/customers/** → hasRole("ADMIN")`，故 SALES 打 DELETE → 403。
- seed 帳號：`sales@aurora.local` / `manager@aurora.local` / `admin@aurora.local`，密碼皆 `password123`（**沿用，勿重生 BCrypt**）。
- 端點：`POST /api/auth/login`、`GET /api/customers`、`GET /api/dashboard/summary`、`GET /api/dashboard/reports`、`POST /api/ai/chat`。
- 登入/聊天 DTO 欄位需在實作時開 `backend/src/main/java/com/aicrm/crm/api/Dtos.java` 確認（LoginRequest/LoginResponse/ChatRequest/ChatResponse 欄位名）。

---

## 檔案結構總覽

```
backend/
  pom.xml                         # [修改] 加 jacoco-maven-plugin
  src/test/
    resources/application-test.yml         # [新] H2 in-memory + 空金鑰
    java/com/aicrm/crm/
      service/JwtServiceTest.java          # [新] 單元
      service/InsightServiceRiskTest.java  # [新] 單元
      service/InsightServiceFallbackTest.java # [新] 單元
      AiCrmApplicationContextTest.java     # [新] 冒煙
      api/SecurityIntegrationTest.java     # [新] 整合
      api/AuthIntegrationTest.java         # [新] 整合
      api/DashboardIntegrationTest.java    # [新] 整合
      api/AiFallbackIntegrationTest.java   # [新] 整合
```

---

## Task 0：JaCoCo 外掛 + 測試 profile

**Files:**
- Modify: `backend/pom.xml`（`<build><plugins>` 內加 jacoco）
- Create: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: pom.xml 加 jacoco-maven-plugin**

在 `backend/pom.xml` 的 `<build><plugins>` 區塊內，`spring-boot-maven-plugin` 之後加入（**不加 check 門檻**）：
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.13</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: 建立 `backend/src/test/resources/application-test.yml`**

```yaml
# SP2 測試 profile：in-memory H2（PostgreSQL 相容）+ Flyway + 空金鑰（強制 AI fallback）
spring:
  datasource:
    url: jdbc:h2:mem:aicrm-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  ai:
    openai:
      api-key: ""
```

- [ ] **Step 3: 驗證 pom 可解析**

Run（repo 根，Git Bash）：
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -q -DskipTests compile
```
Expected: BUILD SUCCESS（pom 合法、jacoco 外掛可下載）。

---

## Task 1：JwtServiceTest（單元）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/service/JwtServiceTest.java`

- [ ] **Step 1: 寫測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * JwtService 單元測試：簽發/驗證 round-trip、竄改、過期、格式錯誤。
 */
class JwtServiceTest {

    /** 以固定密鑰與 Jackson 3 ObjectMapper 建立受測服務。 */
    private JwtService newService(long ttlSeconds) {
        return new JwtService("test-secret-please-change", ttlSeconds, new ObjectMapper());
    }

    /** 建立帶 id 的測試使用者（id 以反射設定，避免 issue() 的 Map.of NPE）。 */
    private AppUser newUser(Role role) {
        var user = new AppUser("sales@aurora.local", "hash", "業務小明", role);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    void issueThenParse_roundTrips() {
        var service = newService(3600);
        var token = service.issue(newUser(Role.SALES));
        var principal = service.parse(token);
        assertThat(principal.username()).isEqualTo("sales@aurora.local");
        assertThat(principal.displayName()).isEqualTo("業務小明");
        assertThat(principal.role()).isEqualTo(Role.SALES);
    }

    @Test
    void tamperedSignature_throws() {
        var service = newService(3600);
        var token = service.issue(newUser(Role.ADMIN));
        var tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredToken_throws() {
        var service = newService(-10); // 立即過期
        var token = service.issue(newUser(Role.MANAGER));
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedToken_throws() {
        var service = newService(3600);
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=JwtServiceTest test
```
Expected: Tests run: 4, Failures: 0。若 `tamperedSignature` 因末兩字剛好相同而未變動，改截不同片段確保 token 真的被改。

---

## Task 2：InsightServiceRiskTest（單元，分支覆蓋）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/service/InsightServiceRiskTest.java`

- [ ] **Step 1: 寫測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.repository.KnowledgeDocumentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * InsightService.calculateOpportunityRisk 分支測試（純邏輯，不啟 Spring）。
 */
class InsightServiceRiskTest {

    /** 以空金鑰建立受測服務（風險計算不需 AI）。 */
    @SuppressWarnings("unchecked")
    private InsightService newService() {
        var customerService = mock(CustomerService.class);
        var knowledge = mock(KnowledgeDocumentRepository.class);
        var provider = (ObjectProvider<ChatModel>) mock(ObjectProvider.class);
        return new InsightService(customerService, knowledge, provider, "");
    }

    /** 建立指定產業/業務的空客戶。 */
    private Customer customer() {
        return new Customer("測試客戶", "a@b.c", "0912345678", "12345678", "雲端服務", "業務A");
    }

    @Test
    void noInteraction_raisesChurnForInsufficientData() {
        var risk = newService().calculateOpportunityRisk(customer());
        // 基底 10 + 資料不足 35 = 45
        assertThat(risk.churnRisk()).isEqualTo(45);
        assertThat(risk.reasons()).anyMatch(r -> r.contains("互動資料不足"));
    }

    @Test
    void recentInteraction_keepsBaseChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(5), "正常會議"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(10);
    }

    @Test
    void interactionOver30Days_addsModerateChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(45), "久未聯繫"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(35); // 10 + 25
    }

    @Test
    void interactionOver60Days_addsHighChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(90), "長期失聯"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(55); // 10 + 45
        assertThat(risk.reasons()).anyMatch(r -> r.contains("天"));
    }

    @Test
    void riskyKeyword_addsChurn() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.SUPPORT_TICKET, LocalDateTime.now().minusDays(5), "客戶提出客訴"));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.churnRisk()).isEqualTo(40); // 10 + 30（近期互動不加天數分）
        assertThat(risk.reasons()).anyMatch(r -> r.contains("客訴"));
    }

    @Test
    void overdueRenewal_raisesRenewalRisk() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.MEETING, LocalDateTime.now().minusDays(5), "近期互動"));
        c.updateContractDates(null, null, LocalDate.now().minusDays(10));
        var risk = newService().calculateOpportunityRisk(c);
        assertThat(risk.renewalDelayRisk()).isEqualTo(65); // 10 + 55
        assertThat(risk.reasons()).anyMatch(r -> r.contains("逾期"));
    }

    @Test
    void multipleSignals_capAt100() {
        var c = customer();
        c.addInteraction(new Interaction(InteractionType.SUPPORT_TICKET, LocalDateTime.now().minusDays(120), "客訴加競品比較且預算凍結"));
        var risk = newService().calculateOpportunityRisk(c);
        // 10 + 45（>60天）+ 30（關鍵詞）= 85，未封頂；驗證不超過 100 且符合計算
        assertThat(risk.churnRisk()).isEqualTo(85);
        assertThat(risk.churnRisk()).isLessThanOrEqualTo(100);
    }
}
```

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=InsightServiceRiskTest test
```
Expected: Tests run: 7, Failures: 0。
> 若某斷言數字與實作不符，**先重讀 `InsightService.calculateOpportunityRisk` 確認預期值**（以實作為準調整斷言），不要改 production 碼。

---

## Task 3：InsightServiceFallbackTest（單元）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/service/InsightServiceFallbackTest.java`

- [ ] **Step 1: 寫測試**

先讀 `Dtos.ChatRequest` 確認建構子（customerId, message 欄位順序）。

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.repository.KnowledgeDocumentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * InsightService AI 可切換策略測試：空金鑰時走 deterministic fallback，不呼叫 ChatModel。
 */
class InsightServiceFallbackTest {

    @SuppressWarnings("unchecked")
    @Test
    void chat_withoutApiKey_usesDeterministicFallback() {
        var customerService = mock(CustomerService.class);
        var knowledge = mock(KnowledgeDocumentRepository.class);
        var provider = (ObjectProvider<ChatModel>) mock(ObjectProvider.class);

        var customer = new Customer("艾克玫", "a@b.c", "0912345678", "12345678", "雲端服務", "業務A");
        when(customerService.findDetail(1L)).thenReturn(customer);
        when(knowledge.findTop3ByOrderBySimilarityHintDesc()).thenReturn(List.of());

        var service = new InsightService(customerService, knowledge, provider, ""); // 空金鑰 → aiEnabled=false
        var response = service.chat(new Dtos.ChatRequest(1L, "請評估這位客戶"));

        assertThat(response.answer()).contains("艾克玫");          // deterministic 內容含客戶名
        assertThat(response.answer()).contains("業務A");
        assertThat(response.risk()).isNotNull();
        verifyNoInteractions(provider);                           // 空金鑰時根本不取 ChatModel
    }
}
```

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=InsightServiceFallbackTest test
```
Expected: Tests run: 1, Failures: 0。
> 若 `Dtos.ChatRequest` / `ChatResponse` 欄位名不同（如 `answer()`），依實際 record accessor 調整。

---

## Task 4：AiCrmApplicationContextTest（冒煙，驗證 H2 + Flyway + context）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/AiCrmApplicationContextTest.java`

- [ ] **Step 1: 寫測試**

```java
package com.aicrm.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 冒煙測試：完整啟動 Spring context（觸發 Flyway migration、JPA metamodel、derived query 解析）。
 * 以空金鑰啟動避免任何真實 LLM 設定影響；只要 context 能 boot 即通過。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@ActiveProfiles("test")
class AiCrmApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: 執行（關鍵驗證點）**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=AiCrmApplicationContextTest test
```
Expected: Tests run: 1, Failures: 0；log 可見 Flyway 在 H2 跑 V1–V4。
> **若此測試失敗於 Flyway（Postgres-only 語法在 H2 跑不起來）**：回報 BLOCKED 並附錯誤；控制者將決定改用 Testcontainers PostgreSQL 並更新 spec。**先別硬改 migration**。

---

## Task 5：SecurityIntegrationTest（整合，RBAC）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/api/SecurityIntegrationTest.java`

- [ ] **Step 1: 寫測試**

先讀 `Dtos.LoginRequest`/`LoginResponse` 確認欄位（推測 LoginRequest{username,password}、LoginResponse{token,user}）。下方用 JSON 字串直送登入、從回應 JSON 取 token。

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;

/**
 * RBAC / 認證整合測試：未登入 401、壞 token 401、登入後 200、SALES DELETE 403。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 用 seed 帳號登入取得 JWT。 */
    private String login(String username) throws Exception {
        var body = "{\"username\":\"" + username + "\",\"password\":\"password123\"}";
        var json = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        return node.get("token").asText();
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc().perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401NotServerError() throws Exception {
        mockMvc().perform(get("/api/customers").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void delete_asSales_returns403() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(delete("/api/customers/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
```
> 註：用 `MockMvcBuilders.webAppContextSetup + springSecurityFilterChain` 確保套用 security filter。若專案有更簡潔慣例（`@AutoConfigureMockMvc`），可改用 `@AutoConfigureMockMvc` + `@Autowired MockMvc`，但需確認 security filter 已套用（`@AutoConfigureMockMvc(addFilters = true)` 預設啟用）。優先用 `@AutoConfigureMockMvc` 較簡潔。
> JSON 解析這裡用 Jackson 2（`com.fasterxml.jackson`，spring-boot-starter-test 內建）；不要與 production 的 Jackson 3 混淆，測試解析回應用哪個皆可。

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=SecurityIntegrationTest test
```
Expected: Tests run: 4, Failures: 0。
> 若 `login()` 取 token 的 JSON path 不對（欄位非 `token`），讀 `Dtos.LoginResponse` 修正。

---

## Task 6：AuthIntegrationTest（整合，登入流程）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/api/AuthIntegrationTest.java`

- [ ] **Step 1: 寫測試**

用 `@AutoConfigureMockMvc` 簡潔版。先讀 `Dtos.LoginResponse` 確認 user 結構與欄位（role 路徑）。

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 登入整合測試：seed 帳號正確登入回 token + user；錯誤密碼被拒。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void login_withSeedCredentials_returnsTokenAndUser() throws Exception {
        var body = "{\"username\":\"sales@aurora.local\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("SALES"));
    }

    @Test
    void login_withWrongPassword_isRejected() throws Exception {
        var body = "{\"username\":\"sales@aurora.local\",\"password\":\"wrong-password\"}";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is4xxClientError());
    }
}
```
> 若 user 物件的 role 路徑不是 `$.user.role`（讀 `Dtos.LoginResponse` 與其 user 子物件確認），對應修正 jsonPath。

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=AuthIntegrationTest test
```
Expected: Tests run: 2, Failures: 0。

---

## Task 7：DashboardIntegrationTest（整合，聚合）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/api/DashboardIntegrationTest.java`

- [ ] **Step 1: 寫測試**

先讀 `Dtos.DashboardSummary` 確認欄位名（customerCount / activeOpportunityCount / opportunityAmount / highRiskCount）。

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dashboard 整合測試：summary 與 reports 帶 token 回 200 與預期欄位。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    @Autowired MockMvc mockMvc;
    private String token;

    @BeforeEach
    void loginFirst() throws Exception {
        var body = "{\"username\":\"manager@aurora.local\",\"password\":\"password123\"}";
        var json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void summary_returnsCounts() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCount").exists())
                .andExpect(jsonPath("$.highRiskCount").exists());
    }

    @Test
    void reports_returnsArrays() throws Exception {
        mockMvc.perform(get("/api/dashboard/reports").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineByStage").isArray());
    }
}
```
> `$.pipelineByStage` 等欄位名若與 `Dtos.DashboardReports` 不符，讀該 DTO 修正。

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=DashboardIntegrationTest test
```
Expected: Tests run: 2, Failures: 0。

---

## Task 8：AiFallbackIntegrationTest（整合，確認不打真 LLM）

**Files:**
- Create: `backend/src/test/java/com/aicrm/crm/api/AiFallbackIntegrationTest.java`

- [ ] **Step 1: 寫測試**

先讀 `Dtos.ChatRequest`/`ChatResponse` 確認欄位（customerId/message；answer/risk/citations）。需要一個存在的 customerId（seed 第一筆，通常 id=1；若不確定，先 GET /api/customers 取第一筆 id）。

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AI fallback 整合測試：空金鑰下 /api/ai/chat 回 200 deterministic 答案（證實未打真 LLM）。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFallbackIntegrationTest {

    @Autowired MockMvc mockMvc;
    private String token;

    @BeforeEach
    void loginFirst() throws Exception {
        var body = "{\"username\":\"sales@aurora.local\",\"password\":\"password123\"}";
        var json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("token").asText();
    }

    @Test
    void chat_withoutApiKey_returnsDeterministicAnswer() throws Exception {
        var body = "{\"customerId\":1,\"message\":\"請評估\"}";
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.risk").exists());
    }
}
```
> 注意：`POST /api/ai/chat` 可能預設回 SSE 串流（`AiController`）。先讀 `AiController` 確認 `/api/ai/chat` 的非串流 vs 串流行為與回應型別：
> - 若該端點僅 SSE，改測非串流端點或加 `Accept: application/json`；依 AiController 實際 mapping 調整 body/assertions。
> - 目標是驗證「空金鑰 → 200 + deterministic（answer 非空）」，端點細節以 AiController 為準。

- [ ] **Step 2: 執行**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend -Dtest=AiFallbackIntegrationTest test
```
Expected: Tests run: 1, Failures: 0。

---

## Task 9：全量測試 + JaCoCo + 更新進度

**Files:**
- Modify: `docs/roadmap-progress.md`

- [ ] **Step 1: 全量測試**

Run:
```
export JAVA_HOME="/d/java/jdk-21"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl backend test
```
Expected: BUILD SUCCESS，所有測試綠（單元 12+、整合 9+、冒煙 1）。

- [ ] **Step 2: 確認 JaCoCo 報告**

確認 `backend/target/site/jacoco/index.html` 存在，InsightService 與 JwtService 有覆蓋數據。

- [ ] **Step 3: 更新 `docs/roadmap-progress.md`**

SP2 列狀態改 `✅ 完成`、plan 欄填本計畫路徑、「目前在」改 SP3，變更紀錄追加完成摘要。

---

## 自我審查（spec 覆蓋對照）

- spec §2.1 單元（風險全分支 / JWT / fallback）→ Task 1/2/3 ✅
- spec §2.2 整合（RBAC / 登入 / Dashboard / AI fallback）→ Task 5/6/7/8 ✅
- spec §2.3 冒煙 → Task 4 ✅
- spec §3 測試 profile（H2 + 空金鑰）→ Task 0 ✅
- spec §4 JaCoCo → Task 0 + Task 9 ✅
- spec §8 風險（Flyway/H2、金鑰滲入、fixture、Jackson3、BCrypt）→ 各 Task 內註記處理 ✅
- 金鑰中和一致用 `@SpringBootTest(properties="spring.ai.openai.api-key=")` + profile 空值（雙保險）✅
- 無 production 碼改動；測試揭露 bug 一律回報不自行改 ✅
- 無 git commit 步驟（非 git repo）✅
```
