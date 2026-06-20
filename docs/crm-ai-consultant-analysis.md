# AI CRM 顧問分析報告

**日期：** 2026-06-19  
**分析視角：** 資深 CRM 顧問、AI 顧問  
**專案定位：** 教學版 AI CRM 工作台，適合展示 AI CRM 的完整體驗與後續擴充方向；目前尚非完整商用 CRM。

## 一、整體結論

本專案目前已具備完整的 CRM 教學展示主線：登入、Dashboard、客戶列表、客戶詳情、互動時間線、商機看板、AI 助理、Agent Trace、Portfolio 整體評估與圖表下鑽。從顧問角度看，這是一個相當適合作為「AI CRM prototype / 教學案例 / 內部概念驗證」的基礎。

若要往正式商用 CRM 發展，下一階段不應只增加更多 AI 對話功能，而應先補齊 CRM 基礎營運能力、權限治理、資料品質、審核流程與 AI 可追溯性。AI 的價值應從「回答問題」擴大到「降低業務輸入成本、主動預警、輔助跟進、主管決策與人機協作」。

## 二、目前優點

### 1. CRM 展示主線完整

目前已具備一套可操作的 CRM 工作台，包含：

- JWT 登入與角色概念。
- 客戶列表與客戶詳情。
- 互動時間線。
- 商機 Kanban 看板。
- Dashboard 摘要與圖表報表。
- 圖表下鑽明細。
- AI 助理對話。
- 單一客戶 360 度評估。
- 全公司 Portfolio 整體評估。
- Agent Trace 決策流程展示。

這讓專案不只是 CRUD demo，而是具備一條完整的銷售管理體驗。

### 2. Dashboard 具備銷售主管視角

目前 Dashboard 已包含銷售漏斗、月度營收 Forecast、產業營收分布、客戶風險結構、續約到期預測、業務排行榜與近期活動。這些報表能支撐銷售主管進行初步檢視，例如：

- 哪些階段卡住最多商機。
- 哪些月份預估營收集中。
- 哪些產業貢獻較高。
- 哪些客戶具有流失或續約風險。
- 哪些業務負責較多高風險客戶。

這是 CRM 從資料記錄工具走向管理決策工具的重要基礎。

### 3. AI 整合邊界設計務實

後端已採用 Spring AI + OpenAI 的可切換設計：有 API key 時走真實 LLM，沒有 API key 或呼叫失敗時 fallback 到 deterministic 教學流程。這對教學與本機驗收非常重要，因為它避免系統完全依賴外部 LLM。

目前 AI 回答也有幾個正確方向：

- 風險分數由系統計算，避免 LLM 自行編造。
- 回答有 grounding context。
- RAG 引用有資料來源概念。
- OpenAI 呼叫失敗時不中斷服務。
- Portfolio 報告與單一客戶報告分開處理。

### 4. Agent Trace 已有決策流程雛形

雖然目前 Agent Flow 是 deterministic 模擬，但已經把業務流程拆成可觀察步驟，例如：

- 擷取客戶快照。
- 摘要互動歷史。
- 評估風險。
- 高風險轉交主管。
- 檢索知識。
- 產生建議草稿。
- 安全檢查。

這對後續升級成真 Agent 很有幫助，因為已有可視化 trace 與業務路徑概念。

### 5. 工程結構適合教學與擴充

專案採 monorepo，包含 backend、frontend、docs、scripts。後端有 Spring Boot、JPA、Flyway、Security、DTO 邊界；前端有 React、TypeScript、Vite；驗證腳本也已涵蓋 API 與前端主流程。

這種結構適合持續往課程、prototype、MVP 或內部 PoC 擴充。

## 三、目前缺點與風險

### 1. 文件與實作版本存在落差

文件仍有部分內容描述 Spring Boot 3.5，但後端 pom 已升級到 Spring Boot 4.1.0。這會造成後續交接、部署與除錯時的認知落差。

建議將 README、spec、api 與實際 pom / application.yml 同步，尤其是：

- Spring Boot 版本。
- Spring AI 版本。
- 預設 port。
- PostgreSQL / H2 profile 使用方式。
- OpenAI / base-url / model 設定方式。

### 2. CRM 領域深度仍偏 MVP

目前資料模型已涵蓋 Customer、Contact、Interaction、Opportunity，但正式 CRM 常見能力尚未完整具備：

- Lead lifecycle。
- Account hierarchy。
- 聯絡人角色與決策權重。
- 任務與提醒。
- 行事曆活動。
- 商機階段歷史。
- 報價與合約。
- 銷售目標與 quota。
- 客戶重複資料合併。
- 匯入匯出。
- 欄位自訂。
- 銷售團隊與區域管理。

如果要從教學版走向實務版，這些基礎能力比新增更多聊天功能更優先。

### 3. 權限設計仍偏展示

目前已有 SALES、MANAGER、ADMIN 角色概念，但企業級 CRM 通常需要更細的資料隔離：

- 業務只能看自己負責的客戶。
- 主管可看團隊客戶。
- 管理員可看全部資料。
- AI 報告需遵循同樣權限。
- MANAGER_REVIEW 的敏感決策內容不應任意暴露給一般 SALES。
- 刪除、審核、商機改階段應留下 audit log。

目前前端已顯示部分角色按鈕，但完整的業務動作與後端權限尚需補齊。

### 4. AI 治理能力不足

目前 AI 已能產生分析，但若要企業導入，還需要補上治理能力：

- Prompt 版本管理。
- 模型版本與參數紀錄。
- Token 用量與成本紀錄。
- 每次 AI 回答的引用來源與資料版本。
- 使用者採納 / 拒絕 AI 建議的紀錄。
- 敏感資料遮罩。
- AI 輸出安全檢查。
- 人工審核流程。
- AI 錯誤與 fallback 監控。

企業客戶通常不只問「AI 能不能回答」，更會問「回答根據什麼、誰看過、誰採納、出錯怎麼追」。

### 5. RAG 目前仍是教學模擬

目前 RAG 類似用 similarity hint 模擬 top 3 文件，不是真正 embedding search。若要支援正式 AI CRM，建議使用 PostgreSQL + pgvector 建立真正的向量檢索流程。

正式 RAG 應包含：

- 文件匯入。
- chunking。
- embedding。
- 向量索引。
- 權限過濾。
- 相似度排序。
- 引用段落回傳。
- 文件版本追蹤。

### 6. 測試層仍需補強

目前已有 PowerShell API 驗證與前端驗證腳本，但缺少標準化的單元測試與整合測試目錄。後續應補：

- Controller 權限測試。
- Service 風險計算測試。
- Dashboard 聚合測試。
- AI fallback 測試。
- RAG 引用測試。
- 商機階段更新測試。
- 前端主要元件測試。

這對長期迭代非常重要。

### 7. 資料量放大後會有查詢效能風險

目前多數報表可以用 Java stream 聚合處理，對 seed data 沒問題。但正式 CRM 資料量增加後，應改為：

- DB 層聚合查詢。
- 報表專用 projection。
- 分頁與索引。
- Materialized view 或快取。
- 背景排程更新 dashboard summary。

否則客戶、互動、商機資料增加後，Dashboard 與 AI Portfolio 分析可能會變慢。

### 8. 前端單檔負擔過重

目前 App.tsx 承載登入、Dashboard、報表、客戶詳情、聊天視窗、Kanban、Modal、Trace 等功能。短期可接受，但長期維護會變困難。

建議拆分為：

- `features/auth`
- `features/dashboard`
- `features/customers`
- `features/opportunities`
- `features/ai-assistant`
- `features/agent-trace`
- `components/common`

## 四、建議擴充方向

### 第一階段：補齊 CRM 基礎營運能力

優先目標是讓它從教學展示變成可日常操作的 CRM。

建議功能：

- 客戶完整 CRUD。
- 聯絡人 CRUD。
- 商機新增、編輯、刪除。
- 商機階段歷史。
- 任務與提醒。
- 活動排程。
- 客戶匯入 / 匯出。
- 客戶重複資料偵測。
- Audit log。
- 主管審核流程。

這一階段的重點不是 AI，而是讓 CRM 本體穩固。

### 第二階段：建立企業級權限與資料治理

建議補上：

- Owner-based access control。
- Team-based access control。
- Manager hierarchy。
- 欄位級權限。
- AI 報告權限隔離。
- 操作紀錄。
- 資料異動歷史。
- 刪除保護與軟刪除。

CRM 的信任基礎來自資料安全與權限正確，這會直接影響 AI 能否被放心使用。

### 第三階段：把 RAG 做成正式知識庫

建議將目前教學式引用升級成正式 RAG：

- 建立 `knowledge_documents` 版本欄位。
- 新增 `knowledge_chunks`。
- 使用 pgvector 儲存 embedding。
- 支援文件匯入與重建索引。
- AI 回答回傳引用 chunk。
- 引用結果遵循角色權限。

適合放入的知識來源：

- 產品手冊。
- 合約條款。
- 報價規則。
- 服務等級協議。
- 常見客訴處理 SOP。
- 續約話術。

### 第四階段：AI Meeting Copilot

這是最有業務價值的 AI 擴充方向之一。

功能內容：

- 上傳 MP3 / WAV。
- Whisper 或其他 STT 轉逐字稿。
- LLM 抽取摘要、痛點、預算、決策者、下一步行動。
- 自動寫入互動時間線。
- 偵測商機欄位變更。
- 由業務確認後更新 CRM。

價值：

- 降低業務輸入成本。
- 提升 CRM 資料完整度。
- 減少會後漏記。
- 讓 AI 分析有更好的資料來源。

### 第五階段：Sentiment & Intent Radar

針對互動紀錄、客服工單、email 內容做情緒與意圖分析。

建議分類：

- `ASK_PRICING`：詢價。
- `COMPARE_COMPETITOR`：競品比較。
- `CHURN_SIGNAL`：流失信號。
- `RENEWAL_INTEREST`：續約意願。
- `UPSELL_SIGNAL`：加購可能。
- `COMPLAINT`：客訴。

Dashboard 可新增：

- 情緒趨勢。
- 高風險互動警示。
- 意圖分類分布。
- 客戶流失雷達。
- 本週需優先關懷清單。

### 第六階段：超個人化跟進信 Copilot

在客戶詳情頁新增「一鍵生成跟進信」。

輸入來源：

- 客戶基本資料。
- 最近互動紀錄。
- 商機階段。
- 風險分數。
- 產品知識庫。
- 外部公司新聞。

輸出：

- Email 草稿。
- 語氣選擇。
- 跟進理由。
- 引用來源。
- 人工修改與送出紀錄。

此功能比全自動 agent 更容易落地，因為它保留人類業務最後審核。

### 第七階段：AI Negotiation Sandbox

提供業務訓練用途，讓 AI 扮演特定客戶進行談判演練。

功能內容：

- AI 模擬客戶決策者。
- 讀取歷史互動、價格敏感度、競品偏好。
- 即時顯示好感度、成交機率、價格接受度。
- 演練後產生覆盤報告。

這對業務訓練、銷售主管 coaching 與新手 onboarding 很有價值。

### 第八階段：Autonomous Follow-up Agent

這是進階功能，建議在權限、RAG、審核、寄信紀錄都成熟後再做。

功能內容：

- 將低意向 lead 託管給 AI。
- AI 依排程生成跟進信。
- 安全審核後寄出。
- 根據客戶回覆判斷下一步。
- 高意向或負面訊號轉交人類業務。
- 保留完整代理日誌。

此功能風險較高，必須先有 guardrails、審核、停止條件與人工接管。

### 第九階段：AI Power Mapping

透過聯絡人、會議、email CC、互動語意建立組織決策鏈圖譜。

建議識別角色：

- Decision Maker。
- Gatekeeper。
- Champion。
- Blocker。
- Influencer。

UI 可使用 force-directed graph 呈現關係強度、好感度與決策影響力。這能讓 CRM 從客戶資料庫變成 account strategy 工具。

## 五、建議優先順序

### 短期，1 到 2 週

- 同步 README / spec / api 與實作版本。
- 補正式刪除客戶與審核建議 API，或移除尚未落地的 UI 按鈕。
- 拆分 App.tsx 的高負擔元件。
- 補 backend service / controller 測試。
- 將 Dashboard 聚合邏輯整理成可測試單元。

### 中期，3 到 6 週

- 補 CRM 基礎營運能力：聯絡人、商機、任務、活動、審核、audit log。
- 建立 owner/team 權限模型。
- 導入真正 pgvector RAG。
- 建立 AI 回答紀錄與引用追蹤。
- 做一鍵跟進信 Copilot。

### 長期，2 到 3 個月以上

- AI Meeting Copilot。
- Sentiment & Intent Radar。
- Negotiation Sandbox。
- Autonomous Follow-up Agent。
- AI Power Mapping。
- 成本監控、AI 評測、prompt registry、企業級部署與多租戶隔離。

## 六、顧問建議

本專案的正確演進方向不是把 AI 聊天做得更花俏，而是讓 AI 深入 CRM 的核心工作流：

- 幫業務少打字。
- 幫主管早發現風險。
- 幫團隊沉澱銷售知識。
- 幫新人訓練話術。
- 幫管理者追蹤 AI 建議是否有效。

建議下一步先把 CRM 基礎盤與權限治理補穩，再逐步導入 RAG、跟進信、會議助理與風險雷達。這樣能讓專案從「漂亮的 AI CRM demo」進化成「有實務導入價值的 AI Sales Operating System」。
