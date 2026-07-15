# AI CRM Intelligent Sales Assistant

AI CRM is a teaching and demonstration full-stack CRM for governed AI-assisted sales workflows. It combines a production-shaped Spring Boot backend, a React workspace, PostgreSQL migrations, deterministic AI fallbacks, and end-to-end verification that runs against real HTTP and database boundaries.

## Technology stack

- Backend: Spring Boot 4.1, Java 21, Spring Security, JPA/Hibernate, Flyway, Spring AI 2.0, and pgvector.
- Frontend: React 19, TypeScript, Vite, React Router, Vitest, and Playwright.
- Database: PostgreSQL 16 with pgvector, exposed on host port `15432` for local development.
- Tooling: Maven, pnpm, Docker Compose, and PowerShell 7+.

The repository is a monorepo:

- `backend/`: REST APIs, security, AI orchestration, persistence, and Flyway migrations.
- `frontend/`: CRM dashboard, customer workspace, administration, and AI-assisted workflows.
- `docs/`: specifications, API contracts, roadmap status, and implementation plans.
- `scripts/`: repeatable environment, API, frontend, screenshot, and phase-gate verification.

## Features

- JWT authentication with SALES, MANAGER, and ADMIN authorization boundaries.
- Customer, contact, interaction, opportunity, dashboard, forecast, RFM, and sentiment workflows.
- Governed AI chat, assessment, usage audit, feedback, PII masking, RAG, and deterministic fallback behavior.
- Explicit Vision and audio-transcription model capabilities with governed OCR/transcription assignments.
- Formal CRM tasks for phone calls, email, meetings, and general follow-up.
- Customer and workspace task entry, due-time ordering, overdue indicators, postponement, completion, and optimistic-lock recovery.
- Stable UTF-8 RFC 5545 `.ics` export with CRLF, an Asia/Taipei time zone, and deterministic task UID.
- AI business-card intake: upload a card image, review AI-recognized fields, resolve duplicate customers, and confirm to atomically create a customer, contact, opportunity, and phone-call task; the source image is deleted after confirmation.
- Rule-based workspace recommendations remain suggestions; persistent task status always comes from `/api/tasks`.

## Quick start

Requirements: Java 21, Maven, Node.js with pnpm, Docker Desktop, and PowerShell 7+.

```powershell
pwsh .\check-env.ps1
docker compose up -d postgres

$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;D:\nodejs;$env:Path"
$env:APP_SECURITY_JWT_SECRET = "replace-with-a-random-secret-at-least-32-characters"

mvn -pl backend spring-boot:run
```

In another PowerShell terminal:

```powershell
Set-Location .\frontend
pnpm install
pnpm run dev
```

The backend listens on `http://127.0.0.1:18080`; the default Vite development server listens on `http://127.0.0.1:5173`. To use the documentation/screenshot port:

```powershell
pnpm run dev -- --port 5175 --host 127.0.0.1
```

## Configuration

Create a project-root `.env` file for local secrets and never commit it.

| Variable | Required | Purpose |
| --- | --- | --- |
| `APP_SECURITY_JWT_SECRET` | Yes | Random JWT signing secret of at least 32 characters. |
| `OPENAI_API_KEY` | No | Enables a real OpenAI-compatible LLM; absence uses deterministic teaching fallbacks. |
| `BASE_URL` | No | OpenAI-compatible gateway URL, including `/v1`; blank values normalize to the OpenAI default. |
| `VOYAGE_API_KEY` | No | Enables Voyage embeddings; absence uses deterministic embeddings. |

The default datasource is `jdbc:postgresql://127.0.0.1:15432/aicrm`. Flyway applies migrations on startup and Hibernate validates the resulting schema. Use the `local` profile for additional development logging and the `prod` profile to disable demo cleanup/development endpoints.

```powershell
mvn -pl backend spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

## Default accounts

| Username | Password | Role |
| --- | --- | --- |
| `sales@aurora.local` | `password123` | SALES |
| `manager@aurora.local` | `password123` | MANAGER |
| `admin@aurora.local` | `password123` | ADMIN |

These accounts are for local teaching data only. Replace all default credentials before any shared deployment.

## Key endpoints

- `GET /api/health`
- `POST /api/auth/login`
- `GET/POST /api/customers`
- `GET /api/customers/{id}`
- `PUT /api/customers/{id}/status`
- `POST /api/customers/{id}/interactions`
- `GET /api/dashboard/summary`
- `GET /api/dashboard/reports`
- `POST /api/ai/chat`
- `GET /api/agent/customers/{id}/trace` — teaching decision-flow trace, not a multi-step tool-calling agent.
- `GET/POST /api/tasks`
- `GET/PUT /api/tasks/{id}`
- `POST /api/tasks/{id}/postpone`
- `POST /api/tasks/{id}/complete`
- `GET /api/tasks/{id}/calendar.ics`
- `DELETE /api/tasks/{id}?version={version}` — explicit owner-scoped cleanup with optimistic-lock protection.

See [`docs/api.md`](docs/api.md) for request/response contracts and authorization behavior.

## Testing and verification

Run backend regression tests with Java 21:

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:JDK_JAVA_OPTIONS = "-Djdk.net.URLClassPath.disableClassPathURLCheck=true"
mvn -pl backend test
```

Run frontend unit tests, type checking, and a production build:

```powershell
pnpm --dir frontend test
pnpm --dir frontend exec tsc --noEmit
pnpm --dir frontend run build
```

With the backend listening on port `18080`, run the V22 real-browser workflow or its complete phase gate:

```powershell
pnpm --dir frontend exec playwright test frontend/e2e/v22-tasks.spec.ts
pwsh .\scripts\verify-phase-gate.ps1 -Phase V22 -E2ESpec frontend/e2e/v22-tasks.spec.ts
```

The V22 test creates uniquely prefixed data, creates and postpones a phone task through the UI, reloads to verify persistence, reads the browser-downloaded `.ics` bytes, completes the task, verifies formal API state, and safely removes only its own task/customer aggregate.

For V23 the backend also needs MinIO (`docker compose up -d minio`) and a deterministic Vision fake. Start the backend with `--app.vision.fake.enabled=true` and the MinIO credentials in `MEDIA_S3_ACCESS_KEY`/`MEDIA_S3_SECRET_KEY`, then:

```powershell
pnpm --dir frontend exec playwright test frontend/e2e/v23-business-card.spec.ts
pwsh .\scripts\verify-phase-gate.ps1 -Phase V23 -E2ESpec frontend/e2e/v23-business-card.spec.ts
```

The V23 test provisions a governed Vision OCR assignment through the real Admin API, uploads a fixture card, reviews and confirms a brand-new customer (asserting the source image reaches `DELETED`), and in a second case merges into a pre-created duplicate customer without creating a new one.

Additional verification and promotional assets:

```powershell
pwsh .\scripts\test-crm-api.ps1
node .\scripts\verify-frontend.mjs
$env:FRONTEND_URL = "http://127.0.0.1:5175/"
node .\scripts\capture-promo-screenshots.mjs
```

Generated screenshots and recordings are stored under `frontend/.promo-screenshots/`; vertical proposal assets are stored under `frontend/.hahow-promo-vertical/`.

## Documentation

- [`docs/spec.md`](docs/spec.md): current functional and technical specification.
- [`docs/api.md`](docs/api.md): API contract.
- [`docs/roadmap-progress.md`](docs/roadmap-progress.md): delivery status and evidence.
- [`docs/superpowers/specs/`](docs/superpowers/specs/): approved design specifications.
- [`docs/superpowers/plans/`](docs/superpowers/plans/): implementation plans and phase checklists.

## Contribution guidelines

1. Read the relevant specification and local `CLAUDE.md` before editing.
2. Keep each change scoped to one task and preserve unrelated working-tree changes.
3. Add Traditional Chinese function-level comments for new functions and comments for important objects.
4. Use TDD: capture a failure caused by the missing behavior, implement the minimum fix, and rerun focused plus regression verification.
5. Update the API/specification/roadmap documentation when behavior changes.
6. Do not mark a phase complete until migration, backend, frontend, real E2E, and PostgreSQL `15432` evidence are green.

## Troubleshooting

- Backend refuses to start: confirm Java 21, `APP_SECURITY_JWT_SECRET` length, Docker PostgreSQL health, and that port `18080` is free.
- Database connection fails: confirm `docker compose up -d postgres` and host port `15432`.
- Windows worktree Maven tests report a manifest classpath root error: set `JDK_JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true` before running Maven.
- Frontend cannot reach APIs: confirm backend health at `/api/health` and the Vite `/api` proxy target `127.0.0.1:18080`.
- AI calls use fallback unexpectedly: verify `OPENAI_API_KEY`, the gateway `BASE_URL` including `/v1`, and provider/model settings in the ADMIN UI.
- Task mutation returns `409`: another update won the optimistic lock; reload the task list and retry using the latest version.

---

## 中文摘要

AI CRM 是一套教學／示範用的全端智慧業務助理，後端採 Spring Boot 4.1、Java 21、PostgreSQL、Flyway 與 pgvector，前端採 React 19、TypeScript、Vite、Vitest 與 Playwright。系統包含客戶、聯絡人、互動、商機、儀表板、RFM、情緒意圖、RAG、PII 遮罩、AI 治理與模型能力設定。

V22 新增正式 CRM 任務：可從客戶工作台或「我的工作檯」安排電話追蹤、延期、完成與下載 `.ics`。正式狀態只以 `/api/tasks` 為準；既有 AI／規則式工作建議仍是建議，不會冒充已持久化任務。任務更新使用 `version` 樂觀鎖，發生 `409` 時前端會提示並重新載入。

### 中文快速啟動

```powershell
pwsh .\check-env.ps1
docker compose up -d postgres
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;D:\nodejs;$env:Path"
$env:APP_SECURITY_JWT_SECRET = "請替換為至少32字元的隨機字串"
mvn -pl backend spring-boot:run
```

另開終端機執行：

```powershell
Set-Location .\frontend
pnpm install
pnpm run dev
```

本機 PostgreSQL 固定使用 `15432`，後端使用 `18080`，Vite 預設使用 `5173`。完整 V22 驗收使用：

```powershell
pwsh .\scripts\verify-phase-gate.ps1 -Phase V22 -E2ESpec frontend/e2e/v22-tasks.spec.ts
```

詳細規格、API 與進度請見 `docs/spec.md`、`docs/api.md`、`docs/roadmap-progress.md`。
