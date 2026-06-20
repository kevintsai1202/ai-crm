# SP2 設計規格：後端測試網（單元 + 整合 + 冒煙）

> 子專案：SP2（路線圖見 `docs/roadmap-progress.md`）
> 建立日期：2026-06-19
> 依據：`docs/consulting-review.md`（高風險 1：零測試）、`docs/crm-ai-consultant-analysis.md`（缺點 6：測試層需補強）
> 經驗參照：auto-skill `skill-spring-boot-testing.md`

---

## 1. 目標與成功標準

**目標**：為後端「錯了會出事」的核心邏輯建立三層測試網（單元 / 整合 / 冒煙），把現有正確行為釘住，作為後續 SP3–SP6 改動的安全網。

**成功標準**：
1. `mvn -pl backend test` 全綠。
2. 三層測試齊備：單元（Mockito）、整合（@SpringBootTest + H2 in-memory）、冒煙（context loads）。
3. 涵蓋：風險計算全分支、JWT 簽發/驗證、AI fallback、RBAC（401/403）、登入流程、Dashboard 聚合、Flyway/context 啟動。
4. JaCoCo 報告可產出（`target/site/jacoco/`），**不設 hard fail 門檻**。
5. 測試**不依賴外部 LLM**（強制 fallback）、不污染開發用 H2 檔案資料庫（用 in-memory）。

**非目標**：
- 不改動既有 production 程式邏輯（除非測試揭露真實 bug，另行回報）。
- 不導入 Testcontainers（除非 H2 無法跑 Flyway migration，見風險 §8）。
- 不設覆蓋率強制門檻。
- 不測前端（SP1 已用 Playwright 煙霧覆蓋）。

---

## 2. 測試分層與清單

### 2.1 單元測試（Mockito，不啟 Spring context）

**`InsightServiceRiskTest`** — `calculateOpportunityRisk(Customer)` 全分支：
- 無互動 → churn 含 +35（資料不足原因）。
- 最近互動 < 30 天 → churn 維持基底 10、無扣分原因。
- 互動 30–60 天 → churn +25。
- 互動 > 60 天 → churn +45（原因含天數）。
- 互動內容含「客訴／預算凍結／競品／未收到回覆」→ churn +30。
- 續約日已逾期 → renewal +55（原因含逾期天數）。
- 多條件疊加 → churn/renewal 上限封頂 100。
- 建構 InsightService 時以空 api-key + mock 依賴（CustomerService、KnowledgeDocumentRepository、ObjectProvider<ChatModel>）。

**`InsightServiceFallbackTest`** — AI 可切換策略：
- `aiEnabled=false`（空 api-key）時 `chat()` 回傳 deterministic 答案，內容含客戶名稱與風險分數，且**不呼叫 ChatModel**（mock ObjectProvider 回傳 null，驗證未互動）。
- `customerAssessment()` 同樣走 fallback。

**`JwtServiceTest`**：
- `issue()` → `parse()` round-trip 還原 username/displayName/role。
- 竄改 signature → `parse()` 拋 `IllegalArgumentException`。
- 過期 token（ttl ≤ 0）→ `parse()` 拋 `IllegalArgumentException`。
- 格式錯誤（非三段）→ 拋 `IllegalArgumentException`。
- 以 `new tools.jackson.databind.ObjectMapper()`（Jackson 3）建構。

### 2.2 整合測試（@SpringBootTest + MockMvc + H2 in-memory + test profile）

共同基礎：`@SpringBootTest(properties = "spring.ai.openai.api-key=")`（蓋空金鑰）、`@AutoConfigureMockMvc`、`@ActiveProfiles("test")`。

**`SecurityIntegrationTest`**（RBAC）：
- `GET /api/customers` 無 token → 401（ProblemDetail JSON）。
- `GET /api/customers` 帶竄改/無效 token → 401（驗證 filter catch 例外，非 500）。
- 登入取得 token → `GET /api/customers` 帶 token → 200。
- `DELETE /api/customers/1` 帶 SALES token → 403（驗證 DELETE 限 ADMIN 規則）。
- `GET /api/health`、`POST /api/auth/login` 免認證 → 可達。

**`AuthIntegrationTest`**：
- `POST /api/auth/login` 用 seed 帳號 `sales@aurora.local / password123` → 200 + token + user（role SALES）。
- 錯誤密碼 → 401 或錯誤回應（依 AuthService 實作斷言）。
- **不重新生成 BCrypt hash**：沿用 seed（運行中已驗證可用）。

**`DashboardIntegrationTest`**：
- `GET /api/dashboard/summary` 帶 token → 200 + customerCount/activeOpportunityCount/opportunityAmount/highRiskCount。
- `GET /api/dashboard/reports` 帶 token → 200 + 各報表陣列。

**`AiFallbackIntegrationTest`**：
- `POST /api/ai/chat`（customerId + message）帶 token → 200，回傳 deterministic answer + risk + citations（同時證實金鑰已被蓋空、未打真 LLM）。

### 2.3 冒煙測試

**`AiCrmApplicationContextTest`** — `@SpringBootTest(properties = "spring.ai.openai.api-key=") @ActiveProfiles("test")` 僅驗證 context 能 boot（觸發 Flyway migration、JPA metamodel、derived query 解析），對應經驗教訓「只有完整 boot 才抓得到啟動期失敗」。

---

## 3. 測試環境設定

**新增 `backend/src/test/resources/application-test.yml`**：
```yaml
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

- in-memory H2 PostgreSQL 相容模式，Flyway 跑 V1–V4 建表 + seed。
- `DB_CLOSE_DELAY=-1` 確保測試期間連線不被關閉。
- api-key 空（雙保險：profile + 各整合測試的 `@SpringBootTest(properties=...)`）。
- **金鑰中和的權威來源**是 `@SpringBootTest(properties = "spring.ai.openai.api-key=")`（inlined property source 優先級最高，必定蓋過 application.yml 的 `../.env` 匯入）。

---

## 4. 套件異動（pom.xml）

- 既有：`spring-boot-starter-test`、`spring-security-test`、`h2`（已具備，無需新增測試框架）。
- 新增：`jacoco-maven-plugin`（`prepare-agent` + `report`，綁 `test` / `verify` 階段，**不加 `check` 門檻**）。

---

## 5. 測試資料策略

- 整合測試依賴 Flyway seed（V2/V4）提供的客戶、商機、互動、知識文件、使用者。
- 單元測試自建 fixture（用 domain entity 的可用建構子/setter 組裝 Customer + Interaction + Opportunity）。
- 不新增測試專用 seed SQL（沿用既有 migration），保持與 production schema 一致。

---

## 6. 目錄結構

```
backend/src/test/
  java/com/aicrm/crm/
    service/
      InsightServiceRiskTest.java
      InsightServiceFallbackTest.java
      JwtServiceTest.java
    api/
      SecurityIntegrationTest.java
      AuthIntegrationTest.java
      DashboardIntegrationTest.java
      AiFallbackIntegrationTest.java
    AiCrmApplicationContextTest.java
  resources/
    application-test.yml
```

---

## 7. 驗證

- `mvn -pl backend test`（JAVA_HOME=D:\java\jdk-21）全綠。
- `target/site/jacoco/index.html` 可開，確認 InsightService / JwtService 覆蓋。
- 整合測試 log 無「真實 OpenAI 呼叫」痕跡（fallback 生效）。

---

## 8. 風險與緩解

| 風險 | 緩解 |
|------|------|
| Flyway migration 含 Postgres-only 語法，H2 跑不起來 | 冒煙測試會第一時間抓到；若失敗則改用 Testcontainers PostgreSQL（@ServiceConnection），並更新本 spec |
| `.env` 真實金鑰滲入整合測試 → 打真 LLM | 用 `@SpringBootTest(properties="spring.ai.openai.api-key=")` 最高優先級蓋空；AiFallbackIntegrationTest 斷言 deterministic 輸出反向驗證 |
| domain entity 無 setter 難建 fixture | 用既有建構子/JPA 欄位；必要時測試內用反射輔助（僅測試碼） |
| Jackson 3（tools.jackson）型別誤用 | JwtService 測試用 `tools.jackson.databind.ObjectMapper`，與 production 一致 |
| BCrypt hash 被誤重生 | 明確禁止，沿用 seed；登入測試直接用 password123 |

---

## 9. 完成後

- 更新 `docs/roadmap-progress.md`：SP2 → ✅，目前在 SP3。
- 以 writing-plans 展開實作計畫。
