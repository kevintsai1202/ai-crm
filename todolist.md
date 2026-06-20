# 任務清單

- [x] 讀取教學站提示詞與教材。
- [x] 建立專案規格與 API 文件。
- [x] 建立 Spring Boot 後端。
- [x] 建立 React 前端。
- [x] 補齊 PowerShell 驗證腳本。
- [x] 檢查 teaching-site 資料一致性問題，目前 build 與 verify:data 通過，未發現需修改項。
- [x] 執行後端與前端可用性驗證。
- [x] 補足 CRM 報表展示資料，Flyway v4 已新增多產業、多業務、多月份與各商機階段資料。
- [x] 新增 CRM 經典圖表報表：銷售漏斗、月度營收 Forecast、產業營收分布、客戶風險結構、續約到期預測、業務排行榜與近期活動。
- [x] 產出課程宣傳截圖到 `frontend/.promo-screenshots/`。
- [x] 產出圖表拆分截圖到 `frontend/.promo-screenshots/charts/`。
- [x] 產出圖表 hover / 下鑽與 AI 互動過程截圖到 `frontend/.promo-screenshots/interactions/`。
- [x] 產出機上操作影片到 `frontend/.promo-screenshots/video/ai-crm-operation-flow.webm`。
- [x] 產出 Hahow 提案直式文宣圖稿到 `frontend/.hahow-promo-vertical/`。

## 待處理問題

- [x] **部署後情緒意圖卡片無資料**（2026-06-20 記錄，當日解決）
  - **解法**：新增 `bootstrap/InteractionInsightBackfillRunner.java`（`@Order(3)` ApplicationRunner），啟動時呼叫 `sentimentIntentService.analyzeMissing(false)` 補算所有缺 insight 的互動。冪等、deterministic、對種子/真人/示範資料皆適用，demo 環境每次部署啟動自動補資料。測試 `InteractionInsightBackfillRunnerTest` 通過（種子互動 → 補出 insight、分類正確、重跑不重複）。
  - **部署注意**：① 此 runner 無條件執行、不需任何環境變數開關。② 既有 Zeabur DB 的 V4 種子互動會在下次重新部署/重啟時自動補上。③ 跑後端測試需設 `APP_SECURITY_JWT_SECRET`（≥32 字元，JwtService fail-fast 要求）。
  - **現象**：Zeabur（<https://ai-crm.springai.world>）部署後，dashboard 5 張卡片無資料：意圖分布、情緒趨勢、高風險互動、流失雷達、優先關懷；但「近期關鍵互動」正常有資料。
  - **根因**：這 5 張卡片讀 `interaction_insights`（情緒/意圖分析結果）表，但 Flyway 種子 V2/V4 只 INSERT `interactions` 互動原文，沒有產生 insights；V8 只建表無種子資料。insights 只由 Java 的 `SentimentIntentService.analyzeMissing()` 在執行階段產生（新增互動走 API 時自動分析、或 `DemoDataService.generate()` 後補算），而純 SQL 種子繞過 Java 分析層 → insights 永遠為空。「近期關鍵互動」直接讀 `interactions` 故正常。
  - **影響**：只要靠 Flyway 種子塞資料，這 5 張卡片永遠空（與 DB 是否重建無關）；正式環境靠真人走 API 新增互動則會自動分析、不受影響。
  - **待決策**：正式環境要不要保留 demo 種子資料？
    - 情況 A（乾淨上線、不要 demo）：用 `spring.flyway.locations` 把 schema(`db/migration`) 與 demo 種子(`db/seed`) 分目錄，正式環境只載入 schema。
    - 情況 B（展示/教學站，要 demo）：補上 insights。推薦方案 A——加 `ApplicationRunner` 啟動時呼叫 `analyzeMissing(false)` 自動補算（冪等、重用既有分類器、符合 V11 owner 回填的既有模式）。
  - **臨時修復**：`POST /api/dev/analyze-insights`（DevController，補算所有缺 insight 的互動）可立即讓線上有資料；唯需先確認該端點在 Zeabur 的 SecurityConfig 權限（本次調查時未讀完）。
  - **關鍵檔案**：
    - `backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java`（`analyzeMissing` / deterministic 分類器）
    - `backend/src/main/java/com/aicrm/crm/service/DemoDataService.java`（生成後呼叫 `analyzeMissing`）
    - `backend/src/main/java/com/aicrm/crm/api/DevController.java`（`/api/dev/demo-data`、`/api/dev/analyze-insights`）
    - `backend/src/main/resources/db/migration/V4__add_promo_report_seed_data.sql`（demo 客戶/互動種子，無 insights）

## 驗證結果

- `mvn -pl backend test -DskipTests`：通過 Java 編譯。
- `pwsh .\scripts\test-crm-api.ps1`：通過後端 API 主線。
- `pnpm run build`（frontend）：通過 TypeScript 與 Vite build。
- `node .\scripts\verify-frontend.mjs`：通過瀏覽器登入、客戶資料、AI 回答與 Agent Trace 驗證。
- `node .\scripts\capture-promo-screenshots.mjs`：已產出桌面截圖、7 張圖表拆分截圖、4 張互動過程截圖、3 張 Hahow 直式文宣圖與 1 支 WebM 操作影片。
- `pnpm run verify`（teaching-site）：通過 build、資料驗證與渲染驗證。
