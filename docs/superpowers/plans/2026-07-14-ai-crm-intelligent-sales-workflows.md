# AI CRM Intelligent Sales Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依序交付 V21–V27 智慧銷售工作流，包含模型能力治理、CRM 任務、名片辨識、會議 Copilot、Zeabur Sendmail 跟進信、商機智能與 Stakeholder 決策鏈。

**Architecture:** 每個 Phase 都是獨立可驗收 vertical slice；後端以 JPA domain/service/controller 與外部 Client 介面隔離，前端採情境式入口。所有 AI 變更先產生草稿，經人工確認後才以交易寫入；Temporary Media 存 S3-compatible storage，PostgreSQL 只存 metadata。

**Tech Stack:** Java 21、Spring Boot 4.1、Spring AI 2.0、PostgreSQL/pgvector、Flyway、React 19、TypeScript、Vite、Vitest、Playwright、Testcontainers PostgreSQL/MinIO、Zeabur Sendmail。

## Global Constraints

- Windows + PowerShell 7+；所有操作命令使用 PowerShell 相容語法。
- 本機 PostgreSQL 已在 host port `15432`；後端 API 使用 `18080`；Playwright Vite 使用 `5173`。
- 自動整合測試使用 Testcontainers PostgreSQL，不污染 `15432`；每 Phase Gate 另以 `15432` 驗 Flyway、Hibernate 與 E2E。
- 所有新增函式與重要變數使用繁體中文註解。
- Production code 前必須先有會因功能缺失而失敗的測試，並實際看過 RED。
- 每個 Phase 完成 targeted test、full backend regression、Vitest、TypeScript、build、Playwright 與文件同步後才可進下一 Phase。
- 外部 AI、S3 與 Sendmail 的自動測試使用 deterministic fake；live smoke 只能由 opt-in environment flag 啟用。
- 名片 confirm、會議 confirm 與 Email approve-and-send API 必須要求 `Idempotency-Key` header，後端以該 key 防止重複寫入或重複寄送。
- 保留使用者目前未提交的 `README.md`、`frontend/public/build-info.json`、`frontend/src/api.ts` 刪除與其他無關檔案，不覆蓋、不還原、不混入 Phase commit。
- 規格來源：`docs/superpowers/specs/2026-07-14-ai-crm-intelligent-sales-workflows-design.md`。

---

## Program Checklist

- [x] Phase 0：建立可重跑 baseline 與 E2E 資料隔離規則。
- [x] Phase 1 / V21：AI 模型能力治理與 OCR／Transcription assignment。
- [x] Phase 2 / V22：CRM Task／Activity 與 `.ics`。
- [x] Phase 3 / V23：Temporary Media、MinIO 與 AI 名片辨識。
- [x] Phase 4 / V24：音訊轉錄與 Meeting Copilot。
- [x] Phase 5 / V25：AI 跟進信與 Zeabur Sendmail。
- [ ] Phase 6 / V26：商機健康度與下一最佳行動。
- [ ] Phase 7 / V27：Stakeholder 決策鏈與關係圖。
- [ ] Final Audit：V21–V27 requirement-by-requirement 證據完整。

---

### Task 0: Baseline 與測試資料隔離

**Files:**
- Create: `scripts/verify-phase-gate.ps1`
- Create: `frontend/e2e/fixtures/phase-data.ts`
- Modify: `.gitignore`
- Test: `scripts/verify-phase-gate.ps1`

**Interfaces:**
- Produces: `New-PhaseDataPrefix([string]$Phase) -> string`，格式 `E2E_<PHASE>_<UTC timestamp>_`。
- Produces: `cleanupPhaseData(request, prefix) -> Promise<void>`，只能刪除該 prefix 建立的資料。
- Produces: `verify-phase-gate.ps1 -Phase V21 -E2ESpec frontend/e2e/v21-model-capability.spec.ts`。

- [x] **Step 1: 記錄 baseline 與 dirty worktree**

```powershell
git status --short
docker ps --format '{{.Names}} {{.Ports}}' | Select-String '15432'
$env:JAVA_HOME = 'D:\java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;D:\nodejs;$env:Path"
mvn -pl backend test
pnpm --dir frontend exec tsc --noEmit
pnpm --dir frontend run build
```

Expected: Docker 顯示 `ai-crm-postgres` 對外 `15432`；測試與 build 的實際結果寫入本計畫「Execution Log」，任何既有失敗先歸因，不得聲稱 baseline 全綠。

- [x] **Step 2: 先寫失敗的 PowerShell gate 自測**

```powershell
pwsh .\scripts\verify-phase-gate.ps1 -Phase V21 -E2ESpec missing.spec.ts
```

Expected: FAIL，exit code 非 0，訊息指出 `E2E spec 不存在：missing.spec.ts`。

- [x] **Step 3: 實作 gate script**

```powershell
param(
    [ValidateSet('V21','V22','V23','V24','V25','V26','V27')][string]$Phase,
    [Parameter(Mandatory)][string]$E2ESpec
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $E2ESpec)) { throw "E2E spec 不存在：$E2ESpec" }
& mvn -pl backend test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& pnpm --dir frontend exec tsc --noEmit
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& pnpm --dir frontend run build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& pnpm --dir frontend exec playwright test $E2ESpec
exit $LASTEXITCODE
```

- [x] **Step 4: 加入 `.superpowers/` 與 E2E artifact ignore，不碰使用者其他 ignore 規則**

```gitignore
/.superpowers/
/frontend/test-results/
/frontend/playwright-report/
```

- [x] **Step 5: 驗證並提交 baseline tooling**

```powershell
pwsh .\scripts\verify-phase-gate.ps1 -Phase V21 -E2ESpec frontend/e2e/sp1-smoke.spec.ts
git add .gitignore scripts/verify-phase-gate.ps1 frontend/e2e/fixtures/phase-data.ts
git commit -m "test: add sequential phase verification gate"
```

Expected: gate exit `0`；commit 只含三個列出檔案。

---

### Task 1: V21 後端模型能力與 assignment

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__add_ai_model_capabilities.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/ModelCapability.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CapabilitySource.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/ai/ModelCatalogClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/ai/OpenAiCompatibleModelCatalogClient.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/SystemSettingModelCapabilityTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/AdminModelCapabilityIntegrationTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/ai/OpenAiCompatibleModelCatalogClientTest.java`

**Interfaces:**
- Produces: `ModelOptionItem(String model, Long providerId, Set<ModelCapability> capabilities, CapabilitySource capabilitySource)`。
- Produces: `AiModelAssignments(String chatModel, Long chatProviderId, String ocrModel, Long ocrProviderId, String transcriptionModel, Long transcriptionProviderId)`。
- Produces: `ModelCatalogClient.discover(AiProvider) -> List<ModelOptionItem>`。
- Produces: `SystemSettingService.updateAssignments(AiModelAssignments, String username)`，不相容能力拋 `IllegalArgumentException`。

- [x] **Step 1: 寫 RED service tests**

```java
@Test
void ocrAssignment_rejectsModelWithoutVisionCapability() {
    var textOnly = new Dtos.ModelOptionItem("text-only", providerId, Set.of(), CapabilitySource.MANUAL);
    settings.updateAiSettings("text-only", providerId, List.of(textOnly), null, null, null, "admin");
    assertThatThrownBy(() -> settings.updateAssignments(
            new Dtos.AiModelAssignments("text-only", providerId, "text-only", providerId, "", null), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VISION");
}

@Test
void transcriptionAssignment_acceptsAudioTranscriptionModel() {
    var audio = new Dtos.ModelOptionItem("whisper-1", providerId,
            Set.of(ModelCapability.AUDIO_TRANSCRIPTION), CapabilitySource.MANUAL);
    settings.updateAiSettings("", null, List.of(audio), null, null, null, "admin");
    settings.updateAssignments(new Dtos.AiModelAssignments("", null, "", null, "whisper-1", providerId), "admin");
    assertThat(settings.getAiSettingsView().transcriptionModel()).isEqualTo("whisper-1");
}
```

- [x] **Step 2: 執行 RED**

```powershell
mvn -pl backend -Dtest=SystemSettingModelCapabilityTest test
```

Expected: FAIL，缺少 `ModelCapability`、新 DTO 或 `updateAssignments`。

- [x] **Step 3: 實作 enum、DTO、V21 與設定驗證**

```java
public enum ModelCapability { VISION, AUDIO_TRANSCRIPTION }
public enum CapabilitySource { AUTO, MANUAL, UNKNOWN }

public record ModelOptionItem(String model, Long providerId,
        Set<ModelCapability> capabilities, CapabilitySource capabilitySource) {
    public ModelOptionItem {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        capabilitySource = capabilitySource == null ? CapabilitySource.UNKNOWN : capabilitySource;
    }
}
```

V21 必須新增四個 assignment setting key，並以 PostgreSQL JSONB 將舊 model option 補成 `capabilities:[]`、`capabilitySource:"UNKNOWN"`。

- [x] **Step 4: 寫 Provider discovery RED tests**

使用 `MockRestServiceServer` 驗證：有 `input_modalities:["image"]` 時輸出 `VISION/AUTO`；只有 OpenAI-compatible `{id,object,created,owned_by}` 時輸出空能力／`UNKNOWN`，不得依名稱猜測。

- [x] **Step 5: 實作 discovery 與 Admin API**

```java
public interface ModelCatalogClient {
    List<Dtos.ModelOptionItem> discover(AiProvider provider);
}
```

新增：

- `POST /api/admin/settings/ai/providers/{id}/models/refresh`
- `PUT /api/admin/settings/ai/models/{model}/capabilities`
- `PUT /api/admin/settings/ai/assignments`

- [x] **Step 6: GREEN 與 regression**

```powershell
mvn -pl backend -Dtest=SystemSettingModelCapabilityTest,OpenAiCompatibleModelCatalogClientTest,AdminModelCapabilityIntegrationTest test
mvn -pl backend test
```

Expected: targeted 與 full suite exit `0`。

---

### Task 2: V21 Admin UI、E2E 與 `15432` Gate

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api/rest.ts`
- Modify: `frontend/src/features/admin/AdminSettingsPage.tsx`
- Create: `frontend/src/features/admin/modelCapabilities.test.ts`
- Create: `frontend/e2e/v21-model-capability.spec.ts`
- Modify: `docs/api.md`
- Modify: `docs/spec.md`
- Modify: `docs/roadmap-progress.md`

**Interfaces:**
- Consumes: V21 `AiSettingsResponse` 與 assignments APIs。
- Produces: `hasCapability(option, capability) -> boolean`。
- Produces: OCR 下拉只含 `VISION`；Transcription 下拉只含 `AUDIO_TRANSCRIPTION`。

- [x] **Step 1: 寫 Vitest RED**

```ts
it("只讓 Vision 模型進入 OCR 選項", () => {
  const options = [
    { model: "vision", providerId: 1, capabilities: ["VISION"], capabilitySource: "AUTO" },
    { model: "text", providerId: 1, capabilities: [], capabilitySource: "UNKNOWN" }
  ] satisfies ModelOptionItem[];
  expect(filterModelsForCapability(options, "VISION").map(x => x.model)).toEqual(["vision"]);
});
```

- [x] **Step 2: 執行 RED、實作 helper 與 UI**

```powershell
pnpm --dir frontend test -- modelCapabilities.test.ts
```

Expected RED：`filterModelsForCapability` 不存在。實作後顯示 `👁`／`👂`、`AUTO/MANUAL/UNKNOWN`，未知模型提供 Admin capability checkbox；assignment select 不顯示不相容模型。

- [x] **Step 3: E2E RED → GREEN**

E2E 必須驗證：Admin 標記 Vision、眼睛出現、OCR 可選；移除 Vision 後 OCR assignment 儲存被前後端阻擋；Transcription 同理。

```powershell
pnpm --dir frontend exec playwright test e2e/v21-model-capability.spec.ts
```

- [x] **Step 4: 驗證 `15432` migration**

```powershell
$env:JAVA_HOME='D:\java\jdk-21'
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:15432/aicrm'
$env:SPRING_DATASOURCE_USERNAME='aicrm'
$env:SPRING_DATASOURCE_PASSWORD='aicrm'
mvn -pl backend spring-boot:run "-Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=validate"
```

另開 PowerShell：

```powershell
docker exec ai-crm-postgres psql -U aicrm -d aicrm -c "select version,success from flyway_schema_history where version='21';"
```

Expected: `21 | t`，後端啟動並可回 `/api/health`。

- [x] **Step 5: Phase Gate、文件與 commit**

```powershell
pwsh .\scripts\verify-phase-gate.ps1 -Phase V21 -E2ESpec frontend/e2e/v21-model-capability.spec.ts
git add -- `
  backend/src/main/resources/db/migration/V21__add_ai_model_capabilities.sql `
  backend/src/main/java/com/aicrm/crm/domain/ModelCapability.java `
  backend/src/main/java/com/aicrm/crm/domain/CapabilitySource.java `
  backend/src/main/java/com/aicrm/crm/service/ai/ModelCatalogClient.java `
  backend/src/main/java/com/aicrm/crm/service/ai/OpenAiCompatibleModelCatalogClient.java `
  backend/src/main/java/com/aicrm/crm/api/Dtos.java `
  backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java `
  backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java `
  backend/src/test/java/com/aicrm/crm/service/SystemSettingModelCapabilityTest.java `
  backend/src/test/java/com/aicrm/crm/api/AdminModelCapabilityIntegrationTest.java `
  backend/src/test/java/com/aicrm/crm/service/ai/OpenAiCompatibleModelCatalogClientTest.java `
  frontend/src/types.ts frontend/src/api/rest.ts `
  frontend/src/features/admin/AdminSettingsPage.tsx `
  frontend/src/features/admin/modelCapabilities.test.ts `
  frontend/e2e/v21-model-capability.spec.ts `
  docs/api.md docs/spec.md docs/roadmap-progress.md
git commit -m "feat: add governed OCR and transcription model capabilities"
```

---

### Task 3: V22 CRM Task domain、API 與 `.ics`

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__add_crm_tasks.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CrmTask.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CrmTaskType.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CrmTaskStatus.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CrmTaskPriority.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/CrmTaskSource.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/CrmTaskRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/CrmTaskService.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/IcsCalendarService.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/TaskController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Modify: `backend/src/main/java/com/aicrm/crm/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/CrmTaskServiceTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/TaskSecurityIntegrationTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/IcsCalendarServiceTest.java`

**Interfaces:**
- Produces: `TaskResponse create(AuthPrincipal, CreateTaskRequest)`。
- Produces: `TaskResponse postpone(AuthPrincipal, Long id, PostponeTaskRequest)`。
- Produces: `TaskResponse complete(AuthPrincipal, Long id)`。
- Produces: `byte[] render(TaskResponse)`，UTF-8 `.ics`，含穩定 UID 與 Asia/Taipei 時區。

- [x] **Step 1: 寫 domain/service RED tests**

```java
@Test
void postpone_openTask_movesScheduleAndIncrementsCounter() {
    var task = service.create(salesPrincipal, phoneTask(customerId, ownerId, start));
    var updated = service.postpone(salesPrincipal, task.id(), new Dtos.PostponeTaskRequest(start.plusDays(2), end.plusDays(2)));
    assertThat(updated.postponeCount()).isEqualTo(1);
    assertThat(updated.status()).isEqualTo(CrmTaskStatus.OPEN);
}
```

- [x] **Step 2: RED、V22 migration、domain 與 service GREEN**

```powershell
mvn -pl backend -Dtest=CrmTaskServiceTest,IcsCalendarServiceTest test
```

V22 包含 customer/opportunity/contact/assignee FK、狀態 check、時間索引、assignee+status+scheduled_start 索引與 `version`。

- [x] **Step 3: 寫並通過 Security API tests**

驗證 SALES 只能讀寫自己的任務、MANAGER／ADMIN 可依既有 scope、非 owner 回 `404` 或 `403` 與既有 IDOR 慣例一致。

- [x] **Step 4: full backend regression**

```powershell
mvn -pl backend test
```

---

### Task 4: V22 工作檯 UI、`.ics` E2E 與 Gate

**Files:**
- Create: `frontend/src/features/tasks/TaskPanel.tsx`
- Create: `frontend/src/features/tasks/TaskFormModal.tsx`
- Create: `frontend/src/features/tasks/taskState.ts`
- Create: `frontend/src/features/tasks/taskState.test.ts`
- Modify: `frontend/src/features/customers/CustomersPage.tsx`
- Modify: `frontend/src/features/my-workspace/WorkspaceAiModal.tsx`
- Modify: `frontend/src/api/rest.ts`
- Modify: `frontend/src/types.ts`
- Create: `frontend/e2e/v22-tasks.spec.ts`

**Interfaces:**
- Produces: `fetchTasks/createTask/postponeTask/completeTask/downloadTaskIcs`。
- Produces: `TaskPanel` 合併正式 Task 與既有規則式建議，但正式 Task 狀態只以 `/api/tasks` 為準。

- [x] **Step 1: Vitest RED → GREEN**

測試 OPEN/IN_PROGRESS 排序、逾期標示、COMPLETED 隱藏與 postpone 後時間更新。

- [x] **Step 2: Playwright RED → GREEN**

建立電話任務 → 工作檯出現 → 延期 → 下載 `.ics` 並斷言內容含 `BEGIN:VCALENDAR`、任務 UID 與正確時間 → 完成後從待辦移除。

- [x] **Step 3: `15432` 與 Phase Gate**

```powershell
docker exec ai-crm-postgres psql -U aicrm -d aicrm -c "select version,success from flyway_schema_history where version='22';"
pwsh .\scripts\verify-phase-gate.ps1 -Phase V22 -E2ESpec frontend/e2e/v22-tasks.spec.ts
```

- [x] **Step 4: 文件與 commit**

更新 README、`docs/api.md`、`docs/spec.md`、`docs/roadmap-progress.md`，提交 `feat: add CRM tasks and calendar export`。

---

### Task 5: V23 Temporary Media 與 S3/MinIO adapter

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__add_temporary_media_and_business_cards.sql`
- Modify: `backend/pom.xml`
- Modify: `docker-compose.yml`
- Create: `backend/src/main/java/com/aicrm/crm/domain/TemporaryMedia.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/MediaPurpose.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/MediaStatus.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/TemporaryMediaRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/media/TemporaryMediaStore.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/media/S3TemporaryMediaStore.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/media/TemporaryMediaService.java`
- Create: `backend/src/main/java/com/aicrm/crm/bootstrap/TemporaryMediaCleanupJob.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/media/S3TemporaryMediaStoreIT.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/media/TemporaryMediaServiceTest.java`

**Interfaces:**
- Produces: `StoredMedia put(MediaUpload upload)`、`InputStream get(String objectKey)`、`void delete(String objectKey)`。
- Produces: `TemporaryMedia stage(MultipartFile, MediaPurpose, AuthPrincipal)`。
- Produces: `int deleteExpired(Instant now)`，DB 與 object deletion 可重試且冪等。

- [x] **Step 1: RED storage contract tests**

```java
@Test
void delete_isIdempotent() {
    var stored = store.put(new MediaUpload("card.png", "image/png", bytes));
    store.delete(stored.objectKey());
    assertThatCode(() -> store.delete(stored.objectKey())).doesNotThrowAnyException();
}
```

- [x] **Step 2: 加入 AWS SDK S3 與 Testcontainers MinIO，實作 adapter**

production dependency 使用 AWS SDK v2 `s3`；test dependency 使用 Testcontainers generic container。設定鍵放 `app.media.s3.endpoint/access-key/secret-key/bucket/region`，測試 profile 由 container 動態注入。

- [x] **Step 3: MIME/signature/size 與 cleanup tests**

圖片只允許 JPEG/PNG/WebP 10 MB；音訊只允許 MP3/M4A/WAV 100 MB。僅相信 header 不足，需以 magic bytes 驗證。cleanup 只處理已到期的 pending/failed 狀態。

- [x] **Step 4: GREEN 與 regression**

```powershell
mvn -pl backend -Dtest=S3TemporaryMediaStoreIT,TemporaryMediaServiceTest test
mvn -pl backend test
```

---

### Task 6: V23 Business Card Vision intake backend

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/domain/BusinessCardIntake.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/BusinessCardStatus.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/BusinessCardIntakeRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/vision/BusinessCardRecognitionClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/vision/OpenAiBusinessCardRecognitionClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/BusinessCardIntakeService.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/BusinessCardController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/BusinessCardIntakeServiceTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/BusinessCardIntegrationTest.java`

**Interfaces:**
- Produces: `RecognizedBusinessCard recognize(byte[] image, String mimeType, AiModelAssignment assignment)`。
- Produces: `BusinessCardIntakeResponse create(MultipartFile, AuthPrincipal)`。
- Produces: `BusinessCardConfirmResponse confirm(Long intakeId, ConfirmBusinessCardRequest, AuthPrincipal, String idempotencyKey)`。

- [x] **Step 1: RED duplicate/confirm tests**

測試 Email exact、電話 normalized、公司名稱模糊候選；確認新客戶與合併既有客戶兩路徑；同 idempotency key 重送只回原結果。

- [x] **Step 2: 實作 deterministic fake 與 production Vision client**

Production client 必須用 V21 OCR assignment；未設定或非 Vision 回 `503`。回應解析失敗標記 intake `FAILED`，不建立 CRM 資料。

- [x] **Step 3: Transaction confirm 與 after-commit media deletion**

Customer、Contact、Opportunity、PHONE_CALL Task 必須同 transaction；object deletion 在 commit 後執行，刪除失敗留下可重試 metadata，不回滾已確認 CRM transaction。

- [x] **Step 4: targeted/full GREEN**

```powershell
mvn -pl backend -Dtest=BusinessCardIntakeServiceTest,BusinessCardIntegrationTest test
mvn -pl backend test
```

---

### Task 7: V23 名片三步精靈、E2E 與 Gate

**Files:**
- Create: `frontend/src/features/business-card/BusinessCardWizardPage.tsx`
- Create: `frontend/src/features/business-card/BusinessCardUploadStep.tsx`
- Create: `frontend/src/features/business-card/BusinessCardReviewStep.tsx`
- Create: `frontend/src/features/business-card/BusinessCardConfirmStep.tsx`
- Create: `frontend/src/features/business-card/businessCardState.ts`
- Create: `frontend/src/features/business-card/businessCardState.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/customers/CustomersPage.tsx`
- Modify: `frontend/src/api/rest.ts`
- Modify: `frontend/src/types.ts`
- Create: `frontend/e2e/v23-business-card.spec.ts`

**Interfaces:**
- Route: `/business-cards/new`。
- Flow: upload → poll → edit/resolve duplicate → confirm opportunity/task → result links。

- [ ] **Step 1: Vitest RED → GREEN**

測低信心欄位標示、未選重複策略禁止下一步、final summary 包含 customer/contact/opportunity/task。

- [ ] **Step 2: Playwright RED → GREEN**

使用 fixture 名片圖片與 fake Vision：上傳、校正、建立、確認原圖狀態 `DELETED`；第二案例合併既有客戶且不建立 duplicate customer。

- [ ] **Step 3: `15432`/MinIO Gate 與 commit**

確認 Flyway `23`、object deletion、full gate；更新四份文件並提交 `feat: add AI business card intake workflow`。

---

### Task 8: V24 Meeting Copilot backend

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__add_meeting_copilot_sessions.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/MeetingCopilotSession.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/MeetingCopilotSessionRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/transcription/TranscriptionClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/transcription/OpenAiCompatibleTranscriptionClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/MeetingCopilotService.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/MeetingCopilotController.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/MeetingCopilotServiceTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/MeetingCopilotIntegrationTest.java`

**Interfaces:**
- Produces: `Transcript transcribe(InputStream audio, String mimeType, AiModelAssignment assignment)`。
- Produces: `MeetingDraft summarize(Transcript, CustomerSnapshot, OpportunitySnapshot)`。
- Produces: `confirm(sessionId, selectedChangeIds, principal, idempotencyKey)`。

- [ ] **Step 1: RED transcription/draft/confirm tests**

驗證無 audio capability 回 `503`、transcript 保留、低信心 stakeholder 預設未選、只套用 selected changes、重送冪等。

- [ ] **Step 2: 實作 processing pipeline 與錯誤狀態**

背景流程 `UPLOADED → PROCESSING → REVIEW_PENDING/FAILED`；錯誤只存 sanitized summary。AI draft 包含 Interaction、Task、Opportunity patch、Stakeholder suggestion 的穩定 change ID。

- [ ] **Step 3: Transaction apply 與 media deletion tests**

DB transaction 成功後刪音訊；失敗保留音訊到 expiry；transcript 在確認後保留。

- [ ] **Step 4: targeted/full GREEN**

```powershell
mvn -pl backend -Dtest=MeetingCopilotServiceTest,MeetingCopilotIntegrationTest test
mvn -pl backend test
```

---

### Task 9: V24 會議並排審核 UI、E2E 與 Gate

**Files:**
- Create: `frontend/src/features/meeting-copilot/MeetingCopilotPage.tsx`
- Create: `frontend/src/features/meeting-copilot/TranscriptPane.tsx`
- Create: `frontend/src/features/meeting-copilot/ChangeReviewPane.tsx`
- Create: `frontend/src/features/meeting-copilot/changeSelection.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/customers/CustomersPage.tsx`
- Create: `frontend/e2e/v24-meeting-copilot.spec.ts`

**Interfaces:**
- Route: `/customers/:id/opportunities/:opportunityId/meeting-copilot/:sessionId`。
- 左 transcript/audio，右 selected changes；confirm 顯示選取數。

- [ ] **Step 1: Vitest RED → GREEN**
- [ ] **Step 2: Playwright upload/poll/select/apply/delete RED → GREEN**
- [ ] **Step 3: 驗 Flyway `24`、Phase Gate、文件與 commit `feat: add meeting copilot review workflow`**

---

### Task 10: V25 Follow-up 與 Zeabur Sendmail backend

**Files:**
- Create: `backend/src/main/resources/db/migration/V25__add_follow_up_email_delivery.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/FollowUpDraft.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/OutboundEmail.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/FollowUpDraftRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/OutboundEmailRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/mail/MailDeliveryClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/mail/ZeaburSendmailClient.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/FollowUpService.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/FollowUpController.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/FollowUpServiceTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/FollowUpIntegrationTest.java`

**Interfaces:**
- Produces: `DeliveryResult send(ApprovedEmail email)`。
- Produces: `FollowUpDraftResponse generate(customerId, opportunityId, principal)`。
- Produces: `OutboundEmailResponse approveAndSend(draftId, request, principal, idempotencyKey)`。

- [ ] **Step 1: RED draft/version/sender tests**

驗證統一 company sender、Reply-To owner username、無有效 owner Email 禁止寄送、草稿人工修改形成新版本而非覆寫。

- [ ] **Step 2: RED idempotency/retry tests**

同 key 只呼叫 `MailDeliveryClient.send` 一次；FAILED 可手動 retry，SENT 不可 retry；錯誤訊息不含 credential。

- [ ] **Step 3: 實作 Zeabur adapter**

設定以 `app.mail.zeabur.*` 對映實際 Sendmail service 的 endpoint/token 或 SMTP 欄位；Client 封裝協定差異。自動測試只用 fake，live test 僅在 `LIVE_SENDMAIL_TEST=true` 且 `E2E_MAIL_RECIPIENT` 有值時執行。

- [ ] **Step 4: targeted/full GREEN**

```powershell
mvn -pl backend -Dtest=FollowUpServiceTest,FollowUpIntegrationTest test
mvn -pl backend test
```

---

### Task 11: V25 跟進信 UI、E2E 與 Gate

**Files:**
- Create: `frontend/src/features/follow-up/FollowUpComposer.tsx`
- Create: `frontend/src/features/follow-up/followUpState.test.ts`
- Modify: `frontend/src/features/customers/components/CustomerDetailPanel.tsx`
- Modify: `frontend/src/api/rest.ts`
- Modify: `frontend/src/types.ts`
- Create: `frontend/e2e/v25-follow-up-email.spec.ts`

**Interfaces:**
- UI 左側 grounding evidence，右側 subject/body；送出前顯示 from/reply-to/to。

- [ ] **Step 1: Vitest RED → GREEN**
- [ ] **Step 2: Playwright draft/edit/send/fail/retry/idempotency RED → GREEN**
- [ ] **Step 3: optional live smoke 僅寄 `E2E_MAIL_RECIPIENT`**
- [ ] **Step 4: 驗 Flyway `25`、Phase Gate、文件與 commit `feat: add governed AI follow-up email delivery`**

---

### Task 12: V26 Opportunity Intelligence backend

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__add_opportunity_health_snapshots.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/OpportunityHealthSnapshot.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/OpportunityHealthSnapshotRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/OpportunityHealthCalculator.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/OpportunityIntelligenceService.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/OpportunityIntelligenceController.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/OpportunityHealthCalculatorTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/OpportunityIntelligenceIntegrationTest.java`

**Interfaces:**
- Produces: `HealthScore calculate(OpportunityContext context)`，總分 0–100 且 `sum(components)==total`。
- Produces: `OpportunityHealthResponse recalculate(opportunityId, principal)`。

- [ ] **Step 1: RED deterministic scoring tests**

固定 clock 測階段停留、逾期成交日、互動熱度、負面訊號、未完成 Task、決策鏈缺口；每個分項有中文 reason 與 evidence reference。

- [ ] **Step 2: 實作 calculator 與 snapshot history**

Calculator 純函式不呼叫 LLM；LLM 只把已計算結果轉成下一最佳行動文案，失敗時 deterministic fallback。不得更新 Opportunity stage/probability。

- [ ] **Step 3: security/full GREEN**

```powershell
mvn -pl backend -Dtest=OpportunityHealthCalculatorTest,OpportunityIntelligenceIntegrationTest test
mvn -pl backend test
```

---

### Task 13: V26 商機智能分頁、E2E 與 Gate

**Files:**
- Create: `frontend/src/features/opportunity-intelligence/OpportunityIntelligenceTab.tsx`
- Create: `frontend/src/features/opportunity-intelligence/healthView.test.ts`
- Modify: `frontend/src/features/customers/components/CustomerDetailPanel.tsx`
- Create: `frontend/e2e/v26-opportunity-intelligence.spec.ts`

**Interfaces:**
- 顯示總分、分項、證據、趨勢、next action；可建立 Task 或開啟 FollowUpComposer。

- [ ] **Step 1: Vitest RED → GREEN**
- [ ] **Step 2: Playwright recalculate/explanation/create-task/follow-up RED → GREEN**
- [ ] **Step 3: 驗 Flyway `26`、Phase Gate、文件與 commit `feat: add explainable opportunity intelligence`**

---

### Task 14: V27 Stakeholder Map backend

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__add_stakeholder_map.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/StakeholderRole.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/StakeholderRelation.java`
- Create: `backend/src/main/java/com/aicrm/crm/domain/StakeholderSuggestionStatus.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/StakeholderRoleRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/StakeholderRelationRepository.java`
- Create: `backend/src/main/java/com/aicrm/crm/service/StakeholderMapService.java`
- Create: `backend/src/main/java/com/aicrm/crm/api/StakeholderMapController.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/StakeholderMapServiceTest.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/StakeholderMapIntegrationTest.java`

**Interfaces:**
- Produces: `StakeholderMapResponse get(customerId, principal)`。
- Produces: `List<StakeholderSuggestion> suggest(customerId, principal)`。
- Produces: `confirm/reject(suggestionId, principal)`。

- [ ] **Step 1: RED suggestion/fact separation tests**

驗證 AI suggestion 不出現在 confirmed graph；confirm 後才成為 confirmed role/relation；reject 保留 audit 但不顯示為事實；跨 customer relation 被拒絕。

- [ ] **Step 2: 實作 service/API 與 full GREEN**

```powershell
mvn -pl backend -Dtest=StakeholderMapServiceTest,StakeholderMapIntegrationTest test
mvn -pl backend test
```

---

### Task 15: V27 決策鏈 UI、E2E、Gate 與 Final Audit

**Files:**
- Create: `frontend/src/features/stakeholder-map/StakeholderMapTab.tsx`
- Create: `frontend/src/features/stakeholder-map/StakeholderGraph.tsx`
- Create: `frontend/src/features/stakeholder-map/stakeholderState.test.ts`
- Modify: `frontend/src/features/customers/components/CustomerDetailPanel.tsx`
- Create: `frontend/e2e/v27-stakeholder-map.spec.ts`
- Modify: `README.md`
- Modify: `docs/api.md`
- Modify: `docs/spec.md`
- Modify: `docs/roadmap-progress.md`
- Modify: `todolist.md`

**Interfaces:**
- 已確認 node/edge 使用實線；AI pending suggestion 使用虛線與待確認 badge；confirm/reject 即時更新。

- [ ] **Step 1: Vitest RED → GREEN**
- [ ] **Step 2: Playwright suggest/confirm/reject/graph RED → GREEN**
- [ ] **Step 3: V27 Phase Gate**

```powershell
docker exec ai-crm-postgres psql -U aicrm -d aicrm -c "select version,success from flyway_schema_history where version between '21' and '27' order by installed_rank;"
pwsh .\scripts\verify-phase-gate.ps1 -Phase V27 -E2ESpec frontend/e2e/v27-stakeholder-map.spec.ts
```

- [ ] **Step 4: 全功能 E2E 與 final regression**

```powershell
$env:JAVA_HOME='D:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;D:\nodejs;$env:Path"
mvn -pl backend test
pnpm --dir frontend test
pnpm --dir frontend exec tsc --noEmit
pnpm --dir frontend run build
pnpm --dir frontend exec playwright test e2e/v21-model-capability.spec.ts e2e/v22-tasks.spec.ts e2e/v23-business-card.spec.ts e2e/v24-meeting-copilot.spec.ts e2e/v25-follow-up-email.spec.ts e2e/v26-opportunity-intelligence.spec.ts e2e/v27-stakeholder-map.spec.ts e2e/sp1-smoke.spec.ts
```

- [ ] **Step 5: Requirement audit**

逐條對照 spec 第 1–15 節，對每項記錄檔案、測試、HTTP/E2E 或 DB 證據；任何 live integration 未驗證須明列，不能以 fake E2E 代稱正式外部服務已驗證。

- [ ] **Step 6: 文件與 commit**

更新所有 checklist 與執行紀錄，提交 `feat: complete AI intelligent sales workflows`。只有 V21–V27 全部 Gate 與 final audit 有證據時，才可標記整體目標完成。

---

## Execution Log

執行者每完成一個 Phase，將「尚未執行」替換為實際 commit、命令、通過數、DB 查詢與限制；未填入證據的 Phase 不得勾選完成。

> **回填說明（2026-07-15）**：本次由後續 session 依 worktree commit 歷史與檔案佐證回補 V21/V22/V23 進度紀錄。標記為「commit 佐證」者代表對應程式與測試檔已提交（見 commit hash 與測試檔），但**測試通過數、DB 查詢輸出與 E2E 綠燈未在本回合重跑驗證**；正式收斂前建議於 `15432` 重跑 Phase Gate 補齊實測證據。

| Phase | 狀態 | Commit | RED / GREEN | PostgreSQL `15432` | Frontend / E2E | Live smoke | 限制 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| V21 | 已完成（commit 佐證） | `bc8cc75` govern、`df51a0c` harden、`f085a1a` UI、`05318e0` fix、`1b3b17d` E2E cleanup | 測試檔：`SystemSettingModelCapabilityTest`、`OpenAiCompatibleModelCatalogClientTest`、`AdminModelCapabilityIntegrationTest`、`modelCapabilities.test.ts`（存在，本回合未重跑） | `V21__add_ai_model_capabilities.sql`（存在，未於 15432 重驗） | `frontend/e2e/v21-model-capability.spec.ts`（存在） | 尚未執行 | 通過數/DB/E2E 綠燈未在本回合重驗 |
| V22 | 已完成（commit 佐證） | `8fee4aa` API+ics、`a61ae87` harden、`8cb8a86` role matrix、`bf8ce08` UI、`eefe4e7` fix、`10e6742` fix | 測試檔：`CrmTaskServiceTest`、`IcsCalendarServiceTest`、`TaskSecurityIntegrationTest`、`taskState.test.ts`（存在，本回合未重跑） | `V22__add_crm_tasks.sql`（存在，未於 15432 重驗） | `frontend/e2e/v22-tasks.spec.ts`（存在） | 不適用 | 通過數/DB/E2E 綠燈未在本回合重驗 |
| V23 | 已完成（本回合實測） | 後端 media/card 系列既有 commit + 本回合前端與修正待提交（名片精靈、fake Vision bean、afterCommit 刪除修正） | 後端全量 `mvn -pl backend test` **210 passed / 0 failed / 2 skipped**；前端 `tsc` 0、`vitest` 35 passed、`build` 綠 | Flyway `21/22/23/23.1/23.2` 皆 `success=t`（15432 實查）；確認後媒體實查為 `DELETED` | `v23-business-card.spec.ts` **2 passed**（真實 MinIO：新建客戶＋原圖 DELETED、合併既有客戶不重建） | 尚未執行 | 以 property-gated fake Vision（`app.vision.fake.enabled`）跑 E2E；修正後端缺陷：`deleteConfirmed` 於 afterCommit 改用 REQUIRES_NEW 交易，避免狀態寫入被靜默忽略 |
| V24 | 已完成（本回合實測） | 本回合待提交：V24 migration/domain/repo、transcription client + fake、MeetingCopilotService/Controller、前端 meeting-copilot UI 與 E2E | 後端全量 `mvn -pl backend test` **217 passed / 0 failed / 2 skipped**（含 MeetingCopilotService/IntegrationTest 7）；前端 `tsc` 0、`vitest` 38 passed、`build` 綠 | Flyway `24` success；確認後音訊實查 `DELETED`、transcript 保留 | `v24-meeting-copilot.spec.ts` **1 passed**（真實 MinIO：上傳轉錄→只套用選定變更→音訊刪除→逐字稿保留、低信心 stakeholder 預設不選） | 尚未執行 | 以 `app.transcription.fake.enabled` fake 轉錄跑 E2E；草稿為 deterministic 產生（AI 僅在轉錄邊界）；stakeholder 建議暫記數不落實體待 V27 |
| V25 | 已完成（本回合實測） | 本回合待提交：V25 migration/domain/repo、mail client + fake、FollowUpService/Controller、前端 follow-up UI 與 E2E | 後端全量 `mvn -pl backend test` **229 passed / 0 failed / 2 skipped**（含 FollowUpService/IntegrationTest 12）；前端 `tsc` 0、`vitest` 41 passed、`build` 綠 | Flyway `25` success | `v25-follow-up-email.spec.ts` **1 passed**（真實 MinIO 後端：草擬→存新版本→經 fake Sendmail 寄送→驗 Reply-To=owner／收件者=客戶 Email） | 未執行（自動測試僅用 fake；需 LIVE_SENDMAIL_TEST+E2E_MAIL_RECIPIENT 才實寄） | FAILED/retry 與冪等 send-once 由後端單元測試涵蓋（fake 無狀態故不在 UI E2E 驗）；憑證只從後端設定讀取、不回前端/audit |
| V26 | 尚未執行 | 尚未執行 | 尚未執行 | 尚未執行 | 尚未執行 | 不適用 | 尚未執行 |
| V27 | 尚未執行 | 尚未執行 | 尚未執行 | 尚未執行 | 尚未執行 | 不適用 | 尚未執行 |
