# API 契約

Base URL：`http://127.0.0.1:18080/api`

## V21 AI model capability governance

All endpoints below require the `ADMIN` role. Model capabilities are explicit and never inferred from model names.

- `GET /admin/settings/ai` returns model options with `capabilities` (`VISION`, `AUDIO_TRANSCRIPTION`) and `capabilitySource` (`AUTO`, `MANUAL`, `UNKNOWN`), plus OCR/transcription assignments.
- `POST /admin/settings/ai/providers/{id}/models/refresh` refreshes the provider catalog. Reliable input-modality metadata produces `AUTO`; missing metadata produces `UNKNOWN`.
- `PUT /admin/settings/ai/models/{model}/capabilities` accepts `{ "providerId": 1, "capabilities": ["VISION"] }` and records `MANUAL`.
- `PUT /admin/settings/ai/assignments` updates Chat/OCR/transcription model-provider pairs. OCR requires `VISION`; transcription requires `AUDIO_TRANSCRIPTION`; incompatible assignments return `400`.

```json
{
  "chatModel": "gpt-5-mini",
  "chatProviderId": 1,
  "ocrModel": "gpt-4o",
  "ocrProviderId": 1,
  "transcriptionModel": "whisper-1",
  "transcriptionProviderId": 1
}
```

## Health

`GET /health`

```json
{
  "status": "UP",
  "timestamp": "2026-06-13T00:00:00Z",
  "features": {
    "database": "JPA",
    "security": "JWT",
    "ai": "Deterministic Teaching Flow"
  }
}
```

## Auth

`POST /auth/login`

```json
{
  "username": "sales@aurora.local",
  "password": "password123"
}
```

## Customers

`GET /customers?page=0&size=10&keyword=&industry=&owner=&riskLevel=`

`POST /customers`

```json
{
  "name": "新客戶股份有限公司",
  "email": "contact@example.com",
  "phone": "0912345678",
  "taxId": "12345678",
  "industry": "SaaS",
  "ownerName": "業務代表",
  "contractStartDate": "2026-01-01",
  "contractEndDate": "2026-12-31",
  "renewalDueDate": "2026-11-30"
}
```

`PUT /customers/{id}/status`

```json
{
  "status": "ACTIVE"
}
```

`POST /customers/{id}/interactions`

```json
{
  "type": "EMAIL",
  "occurredAt": "2026-06-13T10:00:00",
  "content": "客戶要求安排產品續約簡報。"
}
```

## AI

`POST /ai/chat`

```json
{
  "customerId": 1,
  "message": "請分析這個客戶近期風險"
}
```

預設回傳 JSON `ChatResponse`；前端若要使用打字機串流效果，需送出 `Accept: text/event-stream`，同一路徑會回傳 SSE。

## Agent Trace

`GET /agent/customers/{id}/trace`

回傳 Agent steps、風險分數、建議路徑與審核結果。

## Dashboard Reports

`GET /dashboard/reports`

回傳 CRM 經典圖表報表資料：

- `pipelineByStage`：銷售漏斗，各階段商機筆數與金額。
- `monthlyForecast`：依預計成交月份彙總的營收 forecast。
- `industryBreakdown`：依產業彙總的客戶數與商機金額。
- `riskBreakdown`：LOW / MEDIUM / HIGH 客戶風險分布。
- `ownerLeaderboard`：業務排行榜，含客戶數、商機金額、高風險客戶數。
- `renewalForecast`：依續約月份彙總的續約客戶數與商機金額。
- `recentActivities`：近期關鍵互動紀錄。

## V22 CRM tasks and iCalendar

- `GET /api/tasks`: list formal tasks in the authenticated user's scope. SALES sees only assigned tasks; MANAGER/ADMIN use the existing management scope.
- `POST /api/tasks`: create a task with customer, optional opportunity/contact, assignee, local scheduled times, priority, type, and source.
- `GET/PUT /api/tasks/{id}`: read or update one visible task. Mutations require the current `version`.
- `POST /api/tasks/{id}/postpone`: move start/end and increment `postponeCount`; body includes `version`.
- `POST /api/tasks/{id}/complete`: mark the task completed; body includes `version`.
- `GET /api/tasks/{id}/calendar.ics`: download a stable UTF-8, CRLF, Asia/Taipei iCalendar event with UID `crm-task-{id}@ai-crm`.
- `DELETE /api/tasks/{id}?version={version}`: explicitly delete one visible task, primarily for deliberate cleanup; stale versions return `409` and owner scope is enforced.

The workspace may display rule-based recommendations beside formal tasks, but only `/api/tasks` represents persistent task status.

## V23 AI business card intake

- `POST /api/business-card-intakes` (multipart `file`): upload a business-card image, stage it to temporary media, and run the governed Vision OCR model. Returns the intake with `status` `REVIEW_PENDING`/`FAILED` and recognized fields plus duplicate-customer candidates. Requires an OCR model with `VISION` capability assigned (V21); otherwise `503`.
- `GET /api/business-card-intakes/{id}`: poll one intake visible to the caller (SALES sees only its own). Includes recognized fields, per-field confidence, duplicate candidates and any `errorSummary`.
- `POST /api/business-card-intakes/{id}/confirm` (header `Idempotency-Key`): human-confirmed creation. Atomically creates or merges the customer, then creates the contact, opportunity and a `PHONE_CALL` task in one transaction. Resending the same `Idempotency-Key` returns the original result; a different payload under the same key returns `409`.

The uploaded image lives only in S3-compatible storage; after a successful confirm the object is deleted and the media metadata transitions to `DELETED` in a post-commit transaction, while the transcript of recognized fields remains on the intake for audit.

For local/E2E verification without a real Vision provider, start the backend with `--app.vision.fake.enabled=true` to activate a deterministic fake recognition client.

## V24 AI meeting copilot

- `POST /api/meeting-copilot/sessions` (multipart `file` + `customerId`, optional `opportunityId`): upload MP3/M4A/WAV meeting audio, stage it to temporary media, transcribe with the governed transcription model (V21), and generate a structured draft. Returns the session with `status` `REVIEW_PENDING`/`FAILED`, the transcript, an AI summary, and a list of `changes` (each with a stable `changeId`, `type` in `INTERACTION`/`TASK`/`OPPORTUNITY_PATCH`/`STAKEHOLDER_SUGGESTION`, and `selectedByDefault`). Requires an `AUDIO_TRANSCRIPTION` model assignment; otherwise `503`.
- `GET /api/meeting-copilot/sessions/{id}`: poll one session visible to the caller (SALES sees only its own).
- `POST /api/meeting-copilot/sessions/{id}/confirm` (header `Idempotency-Key`, body `{ selectedChangeIds }`): atomically apply only the selected changes — create the interaction (keeping the transcript as the record of truth), the selected tasks, opportunity patch, and stakeholder suggestions. Resending the same key returns the original result; a different payload under the same key returns `409`.

Low-confidence stakeholder suggestions default to unselected. After a successful confirm the audio object is deleted post-commit while the transcript is retained on the session for audit. Start the backend with `--app.transcription.fake.enabled=true` for a deterministic fake transcription client in local/E2E runs.

## V25 AI follow-up email

- `POST /api/customers/{id}/follow-ups/drafts` (body `{ opportunityId? }`): generate a grounded follow-up draft (version 1).
- `PUT /api/follow-ups/drafts/{id}` (body `{ subject, body }`): a human edit creates a NEW draft version (versionNumber + 1, parentId chain) rather than overwriting.
- `POST /api/follow-ups/drafts/{id}/approve-and-send` (header `Idempotency-Key`): approve and send via `MailDeliveryClient`. The sender is the unified company address (`app.mail.from`); `Reply-To` is the owning sales rep's email (opportunity owner first, else customer owner); sending is blocked (`400`) when the owner has no valid email. Returns an `OutboundEmail` with `status` `SENT`/`FAILED`. Resending the same key sends once and returns the original result; a different draft under the same key returns `409`.
- `POST /api/outbound-emails/{id}/retry`: retry a `FAILED` email; `SENT` cannot be retried (`409`).

Credentials are read only from backend configuration and never returned to the client or written to audit/error text. Automated tests use a deterministic fake behind `app.mail.fake.enabled`; a real send is opt-in via `LIVE_SENDMAIL_TEST=true` with `E2E_MAIL_RECIPIENT`.

## V26 opportunity intelligence

- `GET /api/opportunities/{id}/health`: return the latest explainable health snapshot — `totalScore` (0–100), `components` (each with `score`/`maxScore`/`reason`/`evidence`), `nextBestAction`, `ruleVersion`, `model`, and a `trend` of prior snapshots. Computes and stores the first snapshot on demand if none exists. Owner-scoped.
- `POST /api/opportunities/{id}/health/recalculate`: recompute and store a new snapshot (history retained for the trend). Owner-scoped.

The score is produced by a pure, deterministic calculator (`sum(components) == total`); the LLM only phrases the next-best-action and falls back to a deterministic message. This feature never modifies opportunity stage or probability. Scoring dimensions: stage dwell, expected close date, interaction heat, sentiment/intent signals, task status, and decision-chain completeness (degrades gracefully until V27 stakeholder data exists).

## V27 stakeholder decision map

- `GET /api/customers/{id}/stakeholder-map`: return the map with confirmed facts and pending AI suggestions in separate fields — `confirmedRoles`, `confirmedRelations`, and `suggestions` (SUGGESTED only). Rejected items appear in neither. Owner-scoped.
- `POST /api/customers/{id}/stakeholder-map/suggest`: deterministically derive role/relation suggestions from the customer's contacts (idempotent — re-suggesting does not duplicate). Owner-scoped.
- `POST /api/customers/{id}/stakeholder-map/relations`: manually create a MANUAL/CONFIRMED relation between two contacts of the same customer; a cross-customer pair is rejected (`400`).
- `POST /api/stakeholder-suggestions/{id}/confirm` and `.../reject`: the suggestion id is a token (`role-{id}` / `relation-{id}`). Confirm promotes a suggestion to a confirmed fact; reject keeps an audit row (REJECTED) that is never shown as a fact or in the pending list.

AI suggestions and human-confirmed facts are always distinguishable via `status` and `source`; the UI renders confirmed items solid and pending suggestions dashed with a pending badge.
