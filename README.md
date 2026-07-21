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

## Built with GPT-5.6 and Codex

This project was built for the OpenAI Build Week Hackathon, and both **GPT-5.6** and **Codex** are core — not auxiliary — to how it works and how it was made.

### How GPT-5.6 is integrated

The backend talks to GPT-5.6 through Spring AI 2.0 (`spring-ai-starter-model-openai`) pointed at an OpenAI-compatible gateway. The active deployment model is `gpt-5.6-sol` (vision-enabled), configured through the Admin model-capability catalog. GPT-5.6 powers the following features:

- **Governed AI sales chat and customer insight** (`InsightService`): retrieval-grounded Q&A about a customer, with PII masking and per-call governance logging.
- **Workspace recommendations and opportunity drafting** (`WorkspaceAiService`): GPT-5.6 streams a prioritized daily work summary over SSE and proposes concrete new opportunities. Candidate customers, IDs, and risk facts are computed in Java/DB first and passed as grounding context; the model's output is then validated — `customerId` must fall inside the caller's authorized scope or the suggestion is dropped, the deal stage is normalized to a legal enum, and the customer name always comes from the database rather than the model. This is our anti-hallucination boundary.
- **Business-card vision OCR** (`OpenAiBusinessCardRecognitionClient`): the same GPT-5.6 vision model reads an uploaded card image and returns a strict JSON schema. The system prompt treats the image and its text as untrusted input and refuses to follow instructions embedded in it, defending against prompt injection.
- **Locale-aware responses**: reply-language control is enforced at the system-prompt level so AI output follows the user's UI locale.

Every GPT-5.6 call is recorded (type, model, token usage) for audit. When no API key is present, each feature degrades to a deterministic rule-based fallback so the teaching build stays demonstrable without an external key.

### How Codex was used

- **Where Codex accelerated the workflow**: Codex generated Flyway migrations and Testcontainers-backed integration-test scaffolding, batch-completed the frontend i18n string set across the app, produced the Playwright real-browser E2E specs (V21 model capability, V22 tasks, V23 business-card, V24 meeting copilot), and refactored cross-cutting details such as allowing the `Idempotency-Key` header through CORS for the cross-origin confirm endpoints.
- **Key product / engineering / design decisions I made**: I defined the grounding-first anti-hallucination boundary — risk facts, todos, and customer IDs are computed in Java/DB and passed to the model as grounding, and the model's output is re-validated against the caller's authorized scope before use. I lifted reply-language control to the system-prompt level so AI output follows the UI locale, and I kept the Chat, Vision, and Audio-transcription model capabilities separate so a media purpose never implicitly reuses the Chat model.
- **How GPT-5.6 and Codex contributed to the final result**: GPT-5.6 powers the product's runtime intelligence — grounded sales chat and customer insight, streaming workspace recommendations and opportunity drafting, and vision OCR for business-card intake. Codex was the primary engineering collaborator that built and verified that surface, from database migrations and services through to real-browser E2E coverage. Together they let a single developer ship a production-shaped, governed AI CRM with deterministic fallbacks and full audit logging.
- **Codex Session ID** (core-functionality thread, obtained via the `/feedback` command): `019f7509-b074-7f62-b557-94b3c5bd3f00`

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
| `AI_OCR_PROVIDER_NAME` / `AI_OCR_MODEL` | No | Deployment default for business-card OCR. The provider and model must exist in the Admin model catalog with `VISION`. |
| `AI_TRANSCRIPTION_PROVIDER_NAME` / `AI_TRANSCRIPTION_MODEL` | No | Deployment default for meeting transcription. The provider and model must exist in the Admin model catalog with `AUDIO_TRANSCRIPTION`. |

Admin assignments override these deployment defaults independently. A missing media-purpose assignment never reuses the Chat model implicitly.

### Configuring per-purpose AI models (Chat, Vision OCR, Audio transcription)

AI CRM treats the LLM not as a single default but as three independent roles, each resolved and governed separately:

| Role | Capability required | Used by |
| --- | --- | --- |
| Chat / reasoning | — (chat model) | Sales chat, customer insight, workspace recommendations, opportunity drafting |
| Vision OCR | `VISION` | Business-card intake (image → structured fields) |
| Audio transcription | `AUDIO_TRANSCRIPTION` | Meeting copilot (audio → transcript) |

**Step 1 — Register a model and declare its capabilities.** In the Admin settings page, add a provider and its models, then mark each model with the capabilities it actually supports (Vision and/or Audio). A model that is not flagged `VISION` can never be assigned as the OCR model, and a model not flagged `AUDIO_TRANSCRIPTION` can never be assigned as the transcription model — the backend re-validates this on every call and refuses a mismatched pair.

**Step 2 — Assign a model per purpose.** For the business-card OCR model and the audio transcription model, pick a provider + model pair from the capability-filtered options. These Admin assignments are stored in the database and take priority.

**Resolution order (per media purpose).** The backend resolves each media purpose independently:

1. **Database assignment** (set in the Admin UI) — used first when present.
2. **Deployment default** (`AI_OCR_*` / `AI_TRANSCRIPTION_*` environment variables) — used when no database assignment exists.
3. **Fail closed** — if neither a valid assignment nor a complete deployment default (provider, model, capability, and API key) is available, that feature reports "unavailable" rather than silently borrowing the Chat model.

This keeps the roles decoupled: you can point OCR and transcription at different providers or models from the Chat model, change one without affecting the others, and audit each independently. Every OCR and transcription call is recorded through the same AI governance log as chat.

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

The dashboard includes a sales funnel, monthly revenue forecast, revenue by industry, customer risk breakdown, renewal forecast, and owner leaderboard, seeded by the `V4__add_promo_report_seed_data.sql` migration. Generated screenshots and recordings are stored under `frontend/.promo-screenshots/` (with `charts/` and `interactions/` subfolders and an operation video at `frontend/.promo-screenshots/video/ai-crm-operation-flow.webm`); vertical proposal assets are stored under `frontend/.hahow-promo-vertical/`.

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
