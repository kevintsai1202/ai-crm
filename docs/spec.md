# AI CRM 智慧業務助理規格

## 規格來源

本規格依據 `D:\GitHub\hahow-ai-full-stack\teaching-site\course-data.js` 的 Unit 1 到 Unit 8 提示詞整理。

## 範圍

### V21 AI model capability governance

- Candidate models store explicit `VISION` and/or `AUDIO_TRANSCRIPTION` capabilities with `AUTO`, `MANUAL`, or `UNKNOWN` provenance.
- Provider metadata is authoritative only when it contains reliable input modalities; model names are never used for capability inference.
- The Admin UI displays `👁`/`👂` capability markers and the provenance badge. `UNKNOWN` and `MANUAL` entries can be governed with checkboxes; `AUTO` entries remain read-only.
- OCR selectors contain only `VISION` models and transcription selectors contain only `AUDIO_TRANSCRIPTION` models. Both frontend and backend reject stale or incompatible assignments.
- V21 migrates legacy model options to empty capabilities with `UNKNOWN`, preserving existing values where valid.

### V22 CRM task/activity

- `crm_tasks` is the single source of truth for OPEN, IN_PROGRESS, COMPLETED, and CANCELLED task state.
- Customer and workspace contexts can create a manual `PHONE_CALL` task; the workspace keeps rule recommendations visually separate from persisted tasks.
- Active tasks are ordered by `scheduledStart`, completed/cancelled tasks are hidden from the work queue, and overdue display state is derived from `scheduledEnd`.
- Postpone, complete, update, and explicit delete operations enforce owner scope and optimistic-lock `version`.
- Calendar export is generated on demand as UTF-8 RFC 5545 content with CRLF, stable UID, deterministic revision timestamp, and Asia/Taipei wall-clock times.

### V23 AI business card intake

- Card images are stored only in S3-compatible object storage (MinIO locally); PostgreSQL keeps just metadata and the recognized-field transcript.
- Recognition uses the governed V21 OCR (`VISION`) model assignment; a deterministic fake client is available behind `app.vision.fake.enabled` for E2E/local runs.
- The three-step wizard is upload → review/dedupe → confirm: low-confidence fields are flagged, and when duplicate customers are detected the user must explicitly choose CREATE or MERGE before proceeding.
- Confirm requires an `Idempotency-Key` and atomically creates/merges the customer then creates the contact, opportunity and a `PHONE_CALL` task; resubmitting the same key returns the original result.
- After a successful confirm the stored image is deleted post-commit and the media transitions to `DELETED`; deletion failures stay `DELETE_PENDING` for idempotent cleanup retry.

### V24 AI meeting copilot

- Meeting audio (MP3/M4A/WAV, ≤100 MB) is staged to object storage and transcribed with the governed `AUDIO_TRANSCRIPTION` model; a deterministic fake client sits behind `app.transcription.fake.enabled` for E2E/local runs.
- The structured draft is produced deterministically from the transcript and CRM context; every change carries a stable `changeId`, and low-confidence stakeholder suggestions are `selectedByDefault=false`.
- The review workspace is side-by-side: transcript/summary on the left, per-change checkboxes on the right; the confirm button shows the actual applied count.
- Confirm requires an `Idempotency-Key` and applies only the selected changes in one transaction (interaction/tasks/opportunity patch/stakeholder suggestions); it never mutates opportunity stage or probability outside the explicit patch.
- The audio is deleted post-commit while the transcript is retained on the session as the interaction record of truth.

### V25 AI follow-up email

- AI only drafts; a human approves before any send. Editing a draft creates a new immutable version (append-only chain) rather than overwriting.
- Sends go through `MailDeliveryClient` (Zeabur Sendmail in production, a fake behind `app.mail.fake.enabled` for E2E). The sender is the unified verified company address; `Reply-To` is the responsible sales rep's email; a rep without a valid email blocks the send.
- Approve-and-send requires an `Idempotency-Key`: the same key sends once and returns the same result; statuses are at least `QUEUED`/`SENT`/`FAILED`; only `FAILED` may be retried.
- Credentials are read solely from backend configuration and never returned to the client or written into audit/error text.

### V26 opportunity intelligence

- A pure deterministic calculator scores 0–100 with `sum(components) == total`; dimensions are stage dwell, expected close date, interaction heat, sentiment/intent signals, task status, and decision-chain completeness. Each component carries a Chinese reason and an evidence reference.
- The calculator never calls an LLM; the LLM only phrases the next-best-action and falls back to a deterministic message. The feature must not modify opportunity stage or probability.
- Snapshots are retained as history for a trend; the intelligence tab shows the score, component breakdown, evidence, trend, and next-best-action, and can create a task or open the follow-up composer.
- Decision-chain completeness degrades gracefully (contact-count proxy) until V27 stakeholder data exists.

### V27 stakeholder decision map

- `stakeholder_roles` and `stakeholder_relations` carry role/influence/stance/relation with a `source` (AI/MANUAL) and a `status` (SUGGESTED/CONFIRMED/REJECTED).
- AI suggestions and confirmed facts are always distinguishable: the map response keeps them in separate fields, confirmed items render solid, and pending suggestions render dashed with a pending badge.
- Suggest is deterministic and idempotent; confirm promotes a suggestion to a fact; reject keeps an audit row that is never shown as a fact or in the pending list; cross-customer relations are rejected.

1. 建立 Windows + PowerShell 7+ 可驗證的前後端分離 monorepo。
2. 後端提供 CRM REST API、JWT 認證、角色授權、全域錯誤處理與 AI 教學流程。
3. 前端提供登入、Dashboard、客戶列表、客戶詳情、互動時間線、商機看板、AI 助理與 Agent Trace。
4. RAG / Tool Calling / Embabel 先以 deterministic service 實作，避免本機驗收依賴外部 LLM key。
5. Dashboard 需提供 CRM 經典圖表報表：銷售漏斗、月度營收 Forecast、產業營收分布、客戶風險結構、續約到期預測、業務排行榜與近期活動。
6. 課程宣傳素材需可由腳本產出，包含 Dashboard 報表、圖表裁切、客戶 AI/Agent 詳情、新增客戶與新增互動畫面。

## 技術決策

- Java：Java 21。
- 後端：Spring Boot 3.5.x。
- 前端：React + TypeScript + Vite。
- 資料庫：預設使用實體 PostgreSQL (Docker pgvector/pgvector:pg16)；若有備用需求可透過 `h2` profile 切回 H2 本機資料庫。
- API 邊界：Controller 不直接回傳 Entity，所有輸入輸出使用 DTO。
- 安全：登入回傳 HMAC JWT，所有 CRM API 需攜帶 `Authorization: Bearer <token>`。

## 驗收重點

- `/api/health` 可回傳 `status=UP`。
- 未登入呼叫受保護 API 應回傳 401。
- SALES 不可刪除客戶，MANAGER/ADMIN 可審核或查看主管路徑。
- 客戶 seed data 需涵蓋活躍、高風險、續約延遲三種場景。
- 測試資料需足以支撐報表視覺化，至少包含多產業、多業務、多月份與各商機階段資料。
- AI chat 不可編造不存在客戶；不存在 ID 必須明確回覆查無資料。
- Agent Trace 需能呈現資料足夠、資料不足與高風險轉交主管三種路徑。
- `/api/dashboard/reports` 需回傳所有圖表報表所需資料，且前端需能渲染所有報表區塊。
