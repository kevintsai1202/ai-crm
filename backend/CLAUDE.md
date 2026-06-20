# Backend 開發規範

本目錄為 AI CRM 智慧業務助理後端，對應教學站 Unit 1 到 Unit 8 的 Spring Boot 實作。

## 技術棧

- Java 21
- Spring Boot 4.1.x（採 Jackson 3 `tools.jackson`，注入 ObjectMapper 時須用 Jackson 3 型別）
- Spring AI 2.0（`spring-ai-starter-model-openai`，透過 `spring-ai-bom` 管理版本）
- Spring Web / Validation / Data JPA / Security
- Flyway migration
- 預設連線實體 PostgreSQL (Docker pgvector/pgvector:pg16)，`h2` profile 可切回 H2 檔案資料庫

## 開發規範

- Controller 僅處理 HTTP request/response 邊界，業務邏輯放 Service。
- API 輸入輸出使用 DTO，不直接暴露 Entity。
- 函式級別註解與重要變數註解使用繁體中文。
- 錯誤回應使用 `ProblemDetail`。
- AI 採可切換策略：設定環境變數 `OPENAI_API_KEY` 時 `InsightService` 呼叫真實 Spring AI + OpenAI；
  未設定或呼叫失敗時 fallback 回 deterministic 流程，確保本機驗收不依賴外部 API key。
- 風險評分（churn/renewal）與 RAG 引用一律由 Java/DB 計算後作為 grounding context 餵給 LLM，防止幻覺捏造數字。
- RAG / Agent Flow（`AgentFlowService`）仍為 deterministic 教學實作。
- `/api/health` 的 `ai` 欄位會依 `OPENAI_API_KEY` 是否設定動態回報模式。

## 驗證命令

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -DskipTests
mvn -pl backend spring-boot:run
pwsh .\scripts\test-crm-api.ps1
```

