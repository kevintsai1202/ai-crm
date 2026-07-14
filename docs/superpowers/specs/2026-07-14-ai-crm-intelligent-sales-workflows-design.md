# AI CRM 智慧銷售工作流擴充設計

**日期：** 2026-07-14
**狀態：** 已完成互動設計確認，待書面規格核准
**適用專案：** `D:\GitHub\ai-crm`

## 1. 目標

在既有 AI CRM 的 Customer、Contact、Opportunity、Interaction、Workspace AI、RAG、情緒意圖分析與 AI 治理能力上，依相依關係新增七個可獨立驗收的階段：

1. AI 模型能力治理。
2. CRM Task／Activity 與 `.ics` 匯出。
3. AI 名片辨識、重複比對、客戶／聯絡人／商機／電話任務建立。
4. AI 電話／會議 Copilot 音訊轉錄與人工確認。
5. AI 跟進信與 Zeabur Sendmail 寄送。
6. 可解釋的商機健康度與下一最佳行動。
7. 客戶 Stakeholder 決策鏈與關係圖。

所有階段遵循「AI 產生草稿 → 人工確認 → 交易寫入」；AI 不得直接改寫 CRM 正式資料。每階段完成後必須通過 TDD、PostgreSQL `15432` 驗證與 Playwright E2E，才可開始下一階段。

## 2. 非目標

- 第一版不做 Google Calendar 或 Microsoft Calendar OAuth 與雙向同步。
- 第一版不做瀏覽器即時錄音、即時字幕或即時話術提示。
- 第一版不允許 AI 自動寄信；每封信都需人工預覽與確認。
- 第一版不讓 AI 自動修改商機階段、成交機率或 Stakeholder 關係。
- 第一版不永久保存名片原圖或會議音訊。
- 不把媒體 binary 存入 PostgreSQL。
- 不在本計畫內重構與功能無關的既有模組。

## 3. 開發順序與完成邊界

採用基礎能力優先的相依順序：

1. V21 模型能力治理。
2. V22 Task／Activity。
3. V23 Temporary Media 與 Business Card Intake。
4. V24 Meeting Copilot。
5. V25 Follow-up Mail。
6. V26 Opportunity Intelligence。
7. V27 Stakeholder Map。

每一階段是獨立可審查、可回歸、可 E2E 的交付單位。上一階段的完成證據必須包含 migration、後端測試、前端測試／建置、Playwright E2E 與文件同步。

## 4. 共用架構

### 4.1 模組邊界

- `ai-model-capability`：模型能力、能力來源與 Chat／OCR／Transcription 用途設定。
- `task-activity`：正式 CRM 任務、提醒、延期、完成與 `.ics`。
- `temporary-media`：S3-compatible object lifecycle 與 metadata。
- `business-card-intake`：Vision 辨識、重複搜尋與確認建檔。
- `meeting-copilot`：音訊轉錄、會議草稿與選擇性套用。
- `follow-up`：AI 草稿、人工版本、Zeabur Sendmail 寄送與重試。
- `opportunity-intelligence`：健康度 snapshot、解釋與下一最佳行動。
- `stakeholder-map`：角色、關係、AI 建議與人工確認。

Controller 僅處理 HTTP 邊界；業務規則放在 Service；外部服務透過 Client 介面隔離。新增函式與重要變數遵循專案規範使用繁體中文註解。

### 4.2 AI 寫入規則

所有會改變 CRM 正式資料的 AI 功能必須：

1. 保存模型、Provider、輸入來源與草稿版本。
2. 呈現可編輯草稿與引用依據。
3. 由具權限的使用者確認。
4. 以單一 DB transaction 寫入選定項目。
5. 保存確認者與時間。
6. 外部服務或 transaction 失敗時不得留下部分正式資料。

## 5. V21：AI 模型能力治理

### 5.1 模型資料

既有 `ai.chat.model_options` 從 `{model, providerId}` 擴充為：

```json
{
  "model": "gpt-4o",
  "providerId": 1,
  "capabilities": ["VISION"],
  "capabilitySource": "MANUAL"
}
```

能力值：

- `VISION`
- `AUDIO_TRANSCRIPTION`

能力來源：

- `AUTO`：Provider API 回傳可靠的 input modality metadata。
- `MANUAL`：Admin 人工設定。
- `UNKNOWN`：只能取得模型名稱，無法可靠判斷能力。

舊 model option migration 為空能力與 `UNKNOWN`，不得依模型名稱猜測。

### 5.2 用途設定

新增 system setting：

- `ai.ocr.model`
- `ai.ocr.provider_id`
- `ai.transcription.model`
- `ai.transcription.provider_id`

OCR 模型只能選 `VISION`；Transcription 模型只能選 `AUDIO_TRANSCRIPTION`。前端用眼睛 `👁` 與耳朵 `👂` 標示能力並過濾選項，後端必須再次驗證，不能依賴前端。

### 5.3 API

- `POST /api/admin/settings/ai/providers/{id}/models/refresh`
- `PUT /api/admin/settings/ai/models/{model}/capabilities`
- `PUT /api/admin/settings/ai/assignments`

Provider 無 capability metadata 時只匯入模型名稱並標成 `UNKNOWN`，由 Admin 人工設定。

## 6. V22：CRM Task／Activity

新增 `crm_tasks`，欄位包含：

- customer、opportunity、contact 關聯。
- `PHONE_CALL`、`EMAIL`、`MEETING`、`GENERAL` 類型。
- `OPEN`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED` 狀態。
- 優先級、標題、說明、負責人。
- 預定開始／結束、完成時間、延期次數。
- `MANUAL`、`BUSINESS_CARD`、`MEETING_AI`、`FOLLOW_UP_AI` 建立來源。
- `version` optimistic lock、建立／修改者與時間。

API：

- `GET/POST /api/tasks`
- `GET/PUT /api/tasks/{id}`
- `POST /api/tasks/{id}/complete`
- `POST /api/tasks/{id}/postpone`
- `GET /api/tasks/{id}/calendar.ics`

CRM Task 是單一真實來源，整合現有「我的工作檯」。`.ics` 由任務即時產生，不保存檔案。SALES 只能操作自己負責的任務；MANAGER／ADMIN 沿用既有團隊可見性規則。

## 7. V23：Temporary Media 與 AI 名片辨識

### 7.1 媒體儲存

正式環境使用 S3-compatible storage；測試使用 Testcontainers MinIO。PostgreSQL `temporary_media` 只存：

- object key、原始檔名、MIME、大小、SHA-256。
- `BUSINESS_CARD`／`MEETING_AUDIO` 用途。
- `UPLOADED`、`PROCESSING`、`REVIEW_PENDING`、`CONFIRMED`、`FAILED`、`DELETED` 狀態。
- 建立者、到期、刪除與錯誤摘要。

確認寫入成功後立即刪除 object；待確認／失敗資料到期後由排程清除。

### 7.2 名片流程

`business_card_intakes` 保存媒體、OCR 模型／Provider、結構化結果、欄位信心與警告、重複候選、狀態、確認者及最終建立的 CRM ID。

流程：

1. `POST /api/business-card-intakes` multipart 上傳 JPEG、PNG 或 WebP，最大 10 MB。
2. 後端驗證 MIME、檔案 signature、大小與權限。
3. 存入 object storage，背景呼叫 Vision 模型。
4. 前端輪詢 `GET /api/business-card-intakes/{id}`。
5. 使用者校正欄位並處理重複候選。
6. `POST /api/business-card-intakes/{id}/confirm` 交易建立／合併 Customer、Contact、Opportunity 與 PHONE_CALL Task。
7. DB commit 後刪除名片原圖。

確認 API 必須冪等；重複呼叫不得建立第二份資料。

### 7.3 UI

入口位於「我的工作檯」。採全頁三步精靈：

1. 上傳與辨識。
2. 校正欄位與重複比對。
3. 檢查並建立商機與電話任務。

最後一步列出所有即將寫入的正式資料；低信心欄位明確標示，重複候選不得預設自動合併。

## 8. V24：AI 電話／會議 Copilot

第一版接受 MP3、M4A、WAV，最大 100 MB、120 分鐘。`meeting_copilot_sessions` 保存媒體、客戶／商機、轉錄模型／Provider、transcript、AI 摘要、結構化草稿、狀態與確認 audit。

流程：

1. `POST /api/meeting-copilot/sessions` 上傳音訊。
2. 背景轉錄並生成結構化草稿。
3. `GET /api/meeting-copilot/sessions/{id}` 查詢進度。
4. `POST /api/meeting-copilot/sessions/{id}/confirm` 選擇套用摘要、Interaction、Task、Opportunity 更新與 Stakeholder 建議。
5. DB commit 後刪除原始音訊；確認後保留 transcript 作為正式互動依據。

UI 採左右並排審核工作區：左側播放／逐字稿，右側逐項勾選 CRM 變更。低信心 Stakeholder 推測預設不勾選，確認按鈕顯示實際套用項目數。

## 9. V25：AI 跟進信與 Zeabur Sendmail

### 9.1 草稿與寄送

`follow_up_drafts` 保存模型、Provider、grounding、草稿版本、人工修改內容與核准者。`outbound_emails` 保存寄件者、Reply-To、收件者、主旨、內容快照、狀態、Zeabur message ID、重試次數、錯誤與時間。

API：

- `POST /api/customers/{id}/follow-ups/drafts`
- `PUT /api/follow-ups/drafts/{id}`
- `POST /api/follow-ups/drafts/{id}/approve-and-send`
- `POST /api/outbound-emails/{id}/retry`

AI 只產生草稿；人工確認後才透過 `MailDeliveryClient` 呼叫 Zeabur Sendmail。寄件者使用統一已驗證公司信箱，`Reply-To` 使用客戶／商機負責業務 Email；負責業務沒有有效 Email 時禁止寄送。

寄送 API 必須要求 `Idempotency-Key`；同一 key 只能回傳同一寄送結果，不得重寄。狀態至少包含 `QUEUED`、`SENT`、`FAILED`。憑證只從後端環境設定讀取，不回傳前端或寫入 audit。

### 9.2 UI

入口位於客戶／商機情境。畫面並排顯示 AI 引用依據與可編輯草稿；寄送按鈕明確顯示透過 Zeabur Sendmail 寄送，並在按下前顯示寄件者、Reply-To、收件者與主旨。

## 10. V26：商機健康度與下一最佳行動

`opportunity_health_snapshots` 保存總分、分項分數、可解釋原因、下一最佳行動、模型／規則版本與計算時間。歷史 snapshot 保留以呈現趨勢。

API：

- `GET /api/opportunities/{id}/health`
- `POST /api/opportunities/{id}/health/recalculate`

分數至少考慮階段停留、預計成交日、互動熱度、情緒／意圖訊號、Task 狀態與決策鏈完整度。每個扣分／加分都要可解釋；AI 不得自動修改 Opportunity stage 或 probability。

UI 在客戶／商機頁的「商機智能」分頁顯示健康度、分數構成、依據與下一最佳行動，並可由建議建立 Task 或產生跟進信。

## 11. V27：Stakeholder 決策鏈

`stakeholder_roles` 保存 Contact 的決策角色、影響力、立場、信心、來源與確認狀態。`stakeholder_relations` 保存兩位 Contact 的關係類型、來源、AI 建議與人工確認狀態。

API：

- `GET /api/customers/{id}/stakeholder-map`
- `POST /api/customers/{id}/stakeholder-map/suggest`
- `POST /api/stakeholder-suggestions/{id}/confirm`
- `POST /api/stakeholder-suggestions/{id}/reject`

AI 建議與人工確認資料必須可區分。UI 位於客戶／商機頁「決策鏈」分頁；AI 建議使用待確認樣式，不得與已確認關係使用相同視覺狀態。

## 12. 統一錯誤、安全與冪等

沿用既有 `ProblemDetail`：

- `400`：格式、模型能力或狀態轉換錯誤。
- `401/403`：未登入或越權。
- `404`：資源不存在或不可見。
- `409`：重複確認、optimistic-lock 或狀態衝突。
- `413`：媒體超過限制。
- `422`：AI 回應無法解析或必要欄位不足。
- `502`：Vision、Transcription、Object Storage 或 Sendmail 失敗。
- `503`：未設定相容模型或寄信服務。

名片確認、會議套用與寄信必須支援冪等。權限測試必須覆蓋 SALES 水平越權、MANAGER／ADMIN 範圍與媒體 object 存取。錯誤訊息與 audit 不得包含 API key、完整憑證或不必要的個資。

## 13. 每階段測試與完成 Gate

每階段必須完成以下 checklist：

### 13.1 TDD

- [ ] 先寫後端失敗測試並確認因功能缺失而 RED。
- [ ] 寫最小實作並確認 targeted tests GREEN。
- [ ] 完成 refactor 後重跑 targeted 與完整 backend regression tests。
- [ ] 前端先寫 Vitest RED，再完成 GREEN。
- [ ] 新增函式與重要變數具繁體中文註解。

### 13.2 PostgreSQL `15432`

自動化整合測試使用隔離 Testcontainers PostgreSQL；階段驗收另連目前 host port `15432` 的 `ai-crm-postgres`：

- [ ] Flyway migration 套用成功。
- [ ] `flyway_schema_history` 有正確版本。
- [ ] Hibernate `ddl-auto=validate` 通過。
- [ ] table、constraint、index 與舊資料相容。
- [ ] E2E 使用唯一資料前綴，完成後只清除該批資料，不清空現有 DB。

### 13.3 外部服務

自動測試使用 deterministic stub／fake：

- Vision／Transcription：固定結構測試 client。
- Object Storage：Testcontainers MinIO。
- Sendmail：fake `MailDeliveryClient`。
- Capability discovery：MockWebServer 模擬有／無 metadata。

另提供 opt-in live smoke flags：

- `LIVE_VISION_TEST=true`
- `LIVE_TRANSCRIPTION_TEST=true`
- `LIVE_OBJECT_STORAGE_TEST=true`
- `LIVE_SENDMAIL_TEST=true`

Sendmail live test 只能寄至 `E2E_MAIL_RECIPIENT`。

### 13.4 Frontend 與 E2E

- [ ] `pnpm exec tsc --noEmit`
- [ ] `pnpm run build`
- [ ] 新階段 Playwright spec。
- [ ] 既有核心 smoke E2E。
- [ ] API 權限／IDOR 測試。
- [ ] 媒體 MIME、大小、signature 測試。
- [ ] confirm／send 冪等與併發測試。
- [ ] 媒體成功刪除與逾期清除測試。

階段 E2E：

- V21：能力標示與不相容模型禁止選取。
- V22：建立、延期、完成電話任務與下載 `.ics`。
- V23：名片上傳、校正、重複比對、建檔與原圖刪除。
- V24：音訊上傳、轉錄、選擇套用與音訊刪除。
- V25：草稿修改、人工確認、寄送成功／失敗／冪等。
- V26：健康度、解釋、下一最佳行動與建立 Task。
- V27：建議、確認／拒絕與決策鏈圖譜。

### 13.5 文件與完成證據

- [ ] 更新 implementation plan checklist。
- [ ] 更新 `docs/api.md`、`docs/spec.md`、`docs/roadmap-progress.md`。
- [ ] 更新 README 設定與啟動方式。
- [ ] 保存測試命令、通過數與 E2E 證據。
- [ ] 完成 requirement-by-requirement audit。
- [ ] 所有 Gate 全綠後才標記該階段完成並開始下一階段。

## 14. UI 資訊架構定案

- 採情境式入口，不新增獨立 AI 中心或固定 AI 側欄。
- 名片入口位於「我的工作檯」。
- 會議 Copilot、跟進信、健康度與決策鏈位於客戶／商機情境。
- 客戶／商機頁採「總覽、互動、商機智能、決策鏈、任務」分頁，避免單頁無限延伸。
- 已核准的 Visual Companion mockup 保存在 `.superpowers/brainstorm/`，僅作設計參考，不屬正式應用資產。

## 15. 最終驗收

完整計畫只有在 V21–V27 全部通過各自 Gate，且既有 backend regression、frontend build、核心 Playwright smoke 與 PostgreSQL `15432` 驗證仍全綠時，才可宣告完成。任何外部服務尚未完成 live smoke、任何階段缺少 E2E、或任何 checklist 未勾選，均視為未完成。
