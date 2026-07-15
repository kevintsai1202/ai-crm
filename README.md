# AI CRM Intelligent Sales Assistant

[English](#english) | [繁體中文](#繁體中文)

## English

AI CRM is a full-stack teaching and demonstration project built from the Hahow course units and AI collaboration prompts. It uses a monorepo structure:

- `backend/`: Spring Boot **4.1**, Java 21, JPA, Flyway, Spring Security, Spring AI **2.0**, and pgvector.
- `frontend/`: Vite, React 19, and TypeScript.
- `docs/`: Specifications, API documentation, the roadmap (`docs/roadmap-progress.md`), and SP plans.
- `scripts/`: Verification scripts compatible with Windows and PowerShell 7+.

When `OPENAI_API_KEY` is configured, the AI features call a real LLM through OpenAI or an OpenAI-compatible gateway. The gateway `BASE_URL` must include `/v1`. If the key is missing or the request fails, the application falls back to a deterministic workflow. RAG uses Voyage embeddings with pgvector and falls back to deterministic embeddings when `VOYAGE_API_KEY` is unavailable.

### Quick start

```powershell
pwsh .\check-env.ps1
docker compose up -d postgres

$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Use the local profile when local debugging is required.
# Production deployments should use the prod profile, which disables demo cleanup and /api/dev.
mvn -pl backend spring-boot:run
# Alternative: mvn -pl backend spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

Required environment variable: `APP_SECURITY_JWT_SECRET` (at least 32 characters). Optional variables: `OPENAI_API_KEY`, `BASE_URL`, and `VOYAGE_API_KEY`. Store them in the project-root `.env` file and do not commit that file. An empty `BASE_URL` is normalized to the default OpenAI endpoint.

Docker exposes PostgreSQL on host port `15432` to avoid conflicts with other local PostgreSQL services on port `5432`. The backend API is available at `http://127.0.0.1:18080/api`, avoiding conflicts with Spring Boot applications on port `8080`.

Start the frontend after Node.js is available on `PATH`:

```powershell
Set-Location frontend
$env:Path = "D:\nodejs;$env:Path"
pnpm install
pnpm run dev
```

Port `5175` is recommended for a consistent local frontend URL:

```powershell
pnpm run dev -- --port 5175 --host 127.0.0.1
```

### Demo accounts

| Account | Password | Role |
| --- | --- | --- |
| `sales@aurora.local` | `password123` | SALES |
| `manager@aurora.local` | `password123` | MANAGER |
| `admin@aurora.local` | `password123` | ADMIN |

### Core endpoints

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
- `GET /api/agent/customers/{id}/trace` — a teaching-oriented decision-flow trace simulation, not a multi-step tool-calling agent.

See [`docs/api.md`](docs/api.md) for the API contract and [`docs/spec.md`](docs/spec.md) for the project specification.

### Dashboard and promotional assets

The dashboard includes a sales funnel, monthly revenue forecast, revenue by industry, customer risk breakdown, renewal forecast, owner leaderboard, and recent activities. Flyway migration `V4__add_promo_report_seed_data.sql` provides demo data across industries, owners, months, and opportunity stages.

Generate course screenshots, interaction screenshots, an operation video, and vertical Hahow promotional artwork with:

```powershell
$env:Path = "D:\nodejs;$env:Path"
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\capture-promo-screenshots.mjs
```

Generated assets are written to:

- Full-page and primary screenshots: `frontend/.promo-screenshots/`
- Individual chart screenshots: `frontend/.promo-screenshots/charts/`
- Chart hover, drill-down, and AI interaction screenshots: `frontend/.promo-screenshots/interactions/`
- Operation video: `frontend/.promo-screenshots/video/ai-crm-operation-flow.webm`
- Vertical Hahow promotional artwork: `frontend/.hahow-promo-vertical/`

### Verification

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -DskipTests

pwsh .\scripts\test-crm-api.ps1

$env:Path = "D:\nodejs;$env:Path"
Set-Location frontend
pnpm run build
Set-Location ..
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\verify-frontend.mjs
node .\scripts\capture-promo-screenshots.mjs
```

---

## 繁體中文

AI CRM 是依據 Hahow 教學站單元與 AI 協作提示詞建立的全端教學／示範專案，採用 monorepo 架構：

- `backend/`：Spring Boot **4.1**、Java 21、JPA、Flyway、Spring Security、Spring AI **2.0** 與 pgvector。
- `frontend/`：Vite、React 19 與 TypeScript。
- `docs/`：規格、API 文件、路線圖（`docs/roadmap-progress.md`）與 SP 計畫。
- `scripts/`：相容 Windows 與 PowerShell 7+ 的驗證腳本。

設定 `OPENAI_API_KEY` 後，AI 功能會呼叫 OpenAI 或 OpenAI 相容閘道的真實 LLM；閘道的 `BASE_URL` 必須包含 `/v1`。未設定金鑰或呼叫失敗時，系統會回退至 deterministic 流程。RAG 使用 Voyage embedding 與 pgvector；未設定 `VOYAGE_API_KEY` 時則使用 deterministic embedding。

### 快速啟動

```powershell
pwsh .\check-env.ps1
docker compose up -d postgres

$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 本機除錯時可使用 local profile。
# 正式部署應使用 prod profile，以關閉 demo 清除與 /api/dev。
mvn -pl backend spring-boot:run
# 替代指令：mvn -pl backend spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

必要環境變數為 `APP_SECURITY_JWT_SECRET`（至少 32 字元）；選用變數為 `OPENAI_API_KEY`、`BASE_URL` 與 `VOYAGE_API_KEY`。請將它們存放於專案根目錄的 `.env`，且不要提交該檔案。空白的 `BASE_URL` 會正規化為 OpenAI 預設端點。

Docker 將 PostgreSQL 對外映射至主機 port `15432`，避免與其他使用 `5432` 的本機 PostgreSQL 衝突。後端 API 位於 `http://127.0.0.1:18080/api`，避免與使用 `8080` 的其他 Spring Boot 專案衝突。

Node.js 加入 `PATH` 後啟動前端：

```powershell
Set-Location frontend
$env:Path = "D:\nodejs;$env:Path"
pnpm install
pnpm run dev
```

建議固定使用前端 port `5175`：

```powershell
pnpm run dev -- --port 5175 --host 127.0.0.1
```

### 示範帳號

| 帳號 | 密碼 | 角色 |
| --- | --- | --- |
| `sales@aurora.local` | `password123` | SALES |
| `manager@aurora.local` | `password123` | MANAGER |
| `admin@aurora.local` | `password123` | ADMIN |

### 核心端點

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
- `GET /api/agent/customers/{id}/trace`：教學用決策流程 Trace 模擬，不是多步驟 tool-calling Agent。

API 契約請參閱 [`docs/api.md`](docs/api.md)，專案規格請參閱 [`docs/spec.md`](docs/spec.md)。

### Dashboard 與宣傳素材

Dashboard 包含銷售漏斗、月度營收 Forecast、產業營收分布、客戶風險結構、續約到期預測、業務排行榜與近期活動。Flyway migration `V4__add_promo_report_seed_data.sql` 提供涵蓋多產業、多業務、多月份與各商機階段的展示資料。

使用下列指令產出課程截圖、互動過程截圖、操作影片與 Hahow 直式文宣圖：

```powershell
$env:Path = "D:\nodejs;$env:Path"
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\capture-promo-screenshots.mjs
```

產出位置：

- 桌面整頁與主要操作截圖：`frontend/.promo-screenshots/`
- 圖表拆分截圖：`frontend/.promo-screenshots/charts/`
- 圖表 hover、下鑽與 AI 互動過程截圖：`frontend/.promo-screenshots/interactions/`
- 操作影片：`frontend/.promo-screenshots/video/ai-crm-operation-flow.webm`
- Hahow 提案直式文宣圖稿：`frontend/.hahow-promo-vertical/`

### 驗證

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test -DskipTests

pwsh .\scripts\test-crm-api.ps1

$env:Path = "D:\nodejs;$env:Path"
Set-Location frontend
pnpm run build
Set-Location ..
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\verify-frontend.mjs
node .\scripts\capture-promo-screenshots.mjs
```
