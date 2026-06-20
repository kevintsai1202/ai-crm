# AI CRM 智慧業務助理規格

## 規格來源

本規格依據 `D:\GitHub\hahow-ai-full-stack\teaching-site\course-data.js` 的 Unit 1 到 Unit 8 提示詞整理。

## 範圍

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
