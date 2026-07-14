# Task 3 Implementation Report

## Scope

- 完成 V22 `crm_tasks` domain、repository、service、REST API 與即時 `.ics` 匯出。
- SALES 僅可列出／操作自己的任務；跨 assignee 單筆存取沿用既有 IDOR 慣例回 403。
- MANAGER／ADMIN 沿用既有全團隊 scope，不新增另一套團隊模型。

## TDD Evidence

- Service／ICS RED：`CrmTaskService`、enums、DTO、`IcsCalendarService` 尚不存在，test compile 共 28 個 missing-symbol errors。
- Service／ICS GREEN：4 tests，0 failures，0 errors；後補 optimistic version test 後 `CrmTaskServiceTest` 為 4 tests。
- Security API RED：3 tests 全部因 Controller 尚不存在而 404（預期 201／403／200）。
- Security API GREEN：3 tests，0 failures，0 errors，驗證 SALES 自建、跨 owner 403／MANAGER 200、ICS headers/body。
- 首次 full regression：144 tests，0 failures，2 errors；精準發現 non-cascade FK 與示範 reset 相容性。
- 修正 targeted：10 tests，0 failures，0 errors，`BUILD SUCCESS`。
- 最終 full backend：144 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。

## Migration

- `V22__add_crm_tasks.sql` 含 customer／opportunity／contact／assignee FK，皆刻意不使用 `ON DELETE CASCADE`。
- 含 type/status/priority/source/schedule/postpone/completed checks、`version`、時間與 assignee 複合索引。
- Testcontainers PostgreSQL 16 log：validated/applied 22 migrations，schema current version `22`。
- 示範資料 reset 僅在既有 `reset-enabled=true` 安全開關下主動先刪任務；正式 FK 仍保護歷史任務。

## Security and API

- API：`GET/POST /api/tasks`、`GET/PUT /api/tasks/{id}`、complete、postpone、calendar.ics。
- Bean Validation、排程順序、狀態轉換、關聯同客戶、client version 與 JPA `@Version` 均由 service／DTO 控制。
- `.ics`：UTF-8、CRLF、RFC text escaping、stable UID、`TZID=Asia/Taipei`、安全 Content-Disposition。

## Files

- 新增 V22 migration、CrmTask entity/enums/repository/service/controller/ICS service。
- 修改 `Dtos`、`SecurityConfig`、`GlobalExceptionHandler`、`DemoDataService`。
- 新增 3 個 Task 3 test classes。
- 未納入既有 dirty `frontend/public/build-info.json` 與 `frontend/test-results/.last-run.json`。

## Commit

- `feat: add CRM task activity APIs and calendar export`（本報告所屬 commit）。

## Self-review / Concerns

- `git diff --check` 無 whitespace errors（僅既有 Windows LF/CRLF warning）。
- 未使用 cascade 刪歷史任務；reset 相容修正限制在明確啟用的示範資料流程。
- Windows worktree 執行 Surefire 需 `JDK_JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true`；此為本機 manifest classpath workaround，非產品修改。
- 本任務不含前端 UI，將由後續 Task 4 實作。
