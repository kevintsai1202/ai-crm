# AI CRM 智慧業務助理

本專案依據 Hahow 教學站的單元與 AI 協作提示詞建立，是一套教學／示範用全端 AI CRM。專案採用 monorepo：

- `backend/`：Spring Boot **4.1**、Java 21、JPA、Flyway、Spring Security、Spring AI **2.0**、pgvector。
- `frontend/`：Vite、React 19、TypeScript。
- `docs/`：規格、API、路線圖（`docs/roadmap-progress.md`）與 SP 計畫。
- `scripts/`：Windows PowerShell 7+ 驗證腳本。

AI 行為：有 `OPENAI_API_KEY`（可走 OpenAI 相容閘道 `BASE_URL`，須含 `/v1`）時呼叫真實 LLM；無金鑰或失敗時 fallback 至 deterministic 流程。RAG 使用 Voyage embedding + pgvector；無 `VOYAGE_API_KEY` 時用 deterministic embedding。

## 快速啟動

```powershell
pwsh .\check-env.ps1
docker compose up -d postgres

$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 本機除錯日誌可加 local profile；正式部署用 prod（關閉 demo 清除與 /api/dev）
mvn -pl backend spring-boot:run
# 或：mvn -pl backend spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

必要環境變數（專案根目錄 `.env`，勿提交）：`APP_SECURITY_JWT_SECRET`（≥32 字元）、可選 `OPENAI_API_KEY` / `BASE_URL` / `VOYAGE_API_KEY`。`BASE_URL` 若留空字串會被正規化為 OpenAI 預設。

PostgreSQL 由 Docker 提供，host 端口固定使用 `15432`，避免和其他本機專案的 PostgreSQL `5432` 衝突。
後端預設使用 `http://127.0.0.1:18080/api`，避免和其他本機 Spring Boot 專案的 `8080` 衝突。

前端在 Node.js PATH 修正後啟動：

```powershell
cd frontend
$env:Path = "D:\nodejs;$env:Path"
pnpm install
pnpm run dev
```

建議使用固定前端 port `5175`：

```powershell
pnpm run dev -- --port 5175 --host 127.0.0.1
```

## 預設帳號

| 帳號 | 密碼 | 角色 |
| --- | --- | --- |
| `sales@aurora.local` | `password123` | SALES |
| `manager@aurora.local` | `password123` | MANAGER |
| `admin@aurora.local` | `password123` | ADMIN |

## 核心端點

- `GET /api/health`
- `POST /api/auth/login`
- `GET /api/customers`
- `POST /api/customers`
- `GET /api/customers/{id}`
- `PUT /api/customers/{id}/status`
- `POST /api/customers/{id}/interactions`
- `GET /api/dashboard/summary`
- `GET /api/dashboard/reports`
- `POST /api/ai/chat`
- `GET /api/agent/customers/{id}/trace`（**教學用決策流程 Trace 模擬**，非多步 tool-calling Agent）

## 圖表與宣傳截圖

Dashboard 目前包含銷售漏斗、月度營收 Forecast、產業營收分布、客戶風險結構、續約到期預測、業務排行榜與近期活動。測試資料由 Flyway `V4__add_promo_report_seed_data.sql` 補足，涵蓋多產業、多業務、多月份與各商機階段。

課程宣傳截圖、互動過程截圖、操作影片與 Hahow 直式文宣圖可用下列指令產出：

```powershell
$env:Path = "D:\nodejs;$env:Path"
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\capture-promo-screenshots.mjs
```

輸出位置：

- 桌面整頁與主要操作截圖：`frontend/.promo-screenshots/`
- 圖表拆分截圖：`frontend/.promo-screenshots/charts/`
- 圖表 hover / 下鑽與 AI 互動過程截圖：`frontend/.promo-screenshots/interactions/`
- 機上操作影片：`frontend/.promo-screenshots/video/ai-crm-operation-flow.webm`
- Hahow 提案直式文宣圖稿：`frontend/.hahow-promo-vertical/`

## 驗證命令

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -DskipTests

pwsh .\scripts\test-crm-api.ps1

$env:Path = "D:\nodejs;$env:Path"
cd frontend
pnpm run build
cd ..
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\verify-frontend.mjs
node .\scripts\capture-promo-screenshots.mjs
```
