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

## 驗證結果

- `mvn -pl backend test -DskipTests`：通過 Java 編譯。
- `pwsh .\scripts\test-crm-api.ps1`：通過後端 API 主線。
- `pnpm run build`（frontend）：通過 TypeScript 與 Vite build。
- `node .\scripts\verify-frontend.mjs`：通過瀏覽器登入、客戶資料、AI 回答與 Agent Trace 驗證。
- `node .\scripts\capture-promo-screenshots.mjs`：已產出桌面截圖、7 張圖表拆分截圖、4 張互動過程截圖、3 張 Hahow 直式文宣圖與 1 支 WebM 操作影片。
- `pnpm run verify`（teaching-site）：通過 build、資料驗證與渲染驗證。
