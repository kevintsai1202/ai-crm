# Backend 開發規範

本目錄為 AI CRM 智慧業務助理後端，對應教學站 Unit 1 到 Unit 8 的 Spring Boot 實作。

## 技術棧

- Java 21
- Spring Boot 4.1.x（採 Jackson 3 `tools.jackson`，注入 ObjectMapper 時須用 Jackson 3 型別）
- Spring AI 2.0（`spring-ai-starter-model-openai`，透過 `spring-ai-bom` 管理版本）
- Spring Web / Validation / Data JPA / Security
- Flyway migration（V1–V17，位於 `src/main/resources/db/migration/`）
- 預設連線實體 PostgreSQL（Docker `pgvector/pgvector:pg16`，port 15432）；`h2` profile 可切回 H2 檔案資料庫

## 指令

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 啟動後端（需先 docker compose up -d 啟動 Postgres）
mvn -pl backend spring-boot:run

# 跑全測試（Testcontainers 需 Docker；無需外部 API key）
mvn -pl backend test

# 跑指定測試類別
mvn -pl backend test -Dtest=OpportunityTest,OpportunityApiTest

# API 煙霧測試
pwsh .\scripts\test-crm-api.ps1
```

## 測試架構

所有整合測試繼承 `src/test/java/com/aicrm/crm/support/PostgresTestBase.java`：

- 以 Testcontainers static singleton `pgvector/pgvector:pg16` 取代 H2，確保與生產環境一致
- `application-test.yml`（`-Dspring.profiles.active=test`）：啟用 Flyway、空金鑰強制 AI fallback、固定測試用 JWT 密鑰
- 不可在多個測試類別同 JVM 執行時呼叫 `container.stop()`（靠 Ryuk 回收）

## 開發規範

- Controller 僅處理 HTTP request/response 邊界，業務邏輯放 Service。
- 所有 DTO（request/response record）集中定義在 `api/Dtos.java`，不直接暴露 Entity。
- 函式級別註解與重要變數註解使用繁體中文。
- 錯誤回應使用 `ProblemDetail`（`GlobalExceptionHandler`）：`EntityNotFoundException → 404`、`MethodArgumentNotValidException → 400`。
- AI 採可切換策略：設定環境變數 `OPENAI_API_KEY` 時 `InsightService` 呼叫真實 Spring AI + OpenAI；
  未設定或呼叫失敗時 fallback 回 deterministic 流程，確保本機驗收不依賴外部 API key。
- 風險評分（churn/renewal）與 RAG 引用一律由 Java/DB 計算後作為 grounding context 餵給 LLM，防止幻覺捏造數字。
- RAG / Agent Flow（`AgentFlowService`）仍為 deterministic 教學實作。
- `/api/health` 的 `ai` 欄位會依 `OPENAI_API_KEY` 是否設定動態回報模式。
- `Customer.assignOwner(AppUser)` 同時更新 `owner`（FK）與 `owner_name`（去正規化快取），勿單獨更新其中一個。

