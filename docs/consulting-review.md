# AI CRM 顧問診斷與擴充路線圖

> 視角：資深 CRM 顧問 × AI 顧問
> 建立日期：2026-06-19
> 範圍：backend（Spring Boot 4 / Spring AI 2.0）、frontend（React 19 / Vite 7）、docs

---

## 0. 一句話總結

> 這是一套「**地基扎實、裝潢用了真材料、但水電（測試 / RAG / Agent）還沒接好**」的 AI CRM。
> 不要急著蓋 PRD 的 6 大旗艦模組（二樓），先用兩週把 **pgvector、測試、PII** 三件地基補好，
> 接著做 **RFM 分群 + 情緒雷達**，就能從「教學專案」變成「可賣的 AI CRM MVP」。

## 2026-07 現況附錄（SP1–SP15）

原報告中「零測試／假 RAG／無 PII／App.tsx 巨石」**已過時**。至 2026-07：

- 後端大量單元＋整合測試（Testcontainers pgvector）、PII 遮罩、AI 治理與用量
- 真 pgvector RAG + **chunk 級** embedding（V19）、對話記憶與前端歷史 hydrate
- 前端 Router + features 拆分、儀表板可編排、工作檯個人 AI
- 商機 StageHistory + 漏斗停留／超時（V20）
- 生產防呆（BASE_URL 正規化、prod 關 demo reset、限流）
- Agent Trace 仍為**教學用決策流程模擬**，勿對外宣稱 multi-agent

---

## 1. 定位判斷

教學起家、但骨架已具商用雛形的全端 AI CRM。

- 技術選型現代：Spring Boot 4 / Java 21 / Spring AI 2.0 / React 19 / Vite 7。
- CRM 領域模型比多數教學專案完整。
- 真實狀態：**可 demo、可教學、可當 MVP 起點，但不可直接上線服務客戶**。

### 重要事實校正
- README 稱 AI 為「deterministic 假實作」，但後端實際已接好 **Spring AI 2.0 + OpenAI ChatClient**：
  有 `OPENAI_API_KEY` 走真 LLM、沒有才 fallback 到 deterministic。→ 比文件宣稱的更接近商用。
- 但 **RAG 仍是假的**：`KnowledgeDocument.similarityHint` 為人工標註相似度，無 embedding、無向量檢索。

---

## 2. 優點（值得保留的資產）

### CRM 顧問視角
| 面向 | 評價 |
|---|---|
| 領域模型完整 | Customer / Contact / Interaction / Opportunity / KnowledgeDocument 五大實體齊備，含合約起迄、續約到期日（多數教學 CRM 缺的「續約生命週期」概念） |
| 銷售流程閉環 | 商機 5 階段漏斗 + Kanban 拖拽改階段 + 互動時間線，業務日常動線完整 |
| 管理層視角到位 | Dashboard 7 張圖（漏斗／月度 Forecast／產業分布／風險結構／續約預測／業務排行／近期活動）全部支援下鑽 drilldown |
| 風險評分有業務邏輯 | churn / renewal 風險用「互動間隔 + 關鍵詞（客訴/預算凍結/競品）+ 續約逾期」計算，可解釋的規則引擎 |
| RBAC 分層 | SALES / MANAGER / ADMIN 三角色，刪除限 ADMIN、審核走 MANAGER，符合真實銷售組織 |

### AI 顧問視角
- **Grounding 做對了**：風險與引用由 Java 算好再餵給 LLM 當 context，LLM 只負責組織語言、不負責算數字 → 架構上避免幻覺。
- **真 SSE 串流**：前端 `fetch ReadableStream` 逐字打字機。
- **fallback 機制**：無 key 時 deterministic 回覆，demo / CI 不依賴外部服務。
- **擴充邊界清楚**：`docs/ai-crm-requirements.md` 已規劃 6 大 AI 模組 PRD。

---

## 3. 缺點與風險（上線前必須補的洞）

### 🔴 高風險（擋住生產化）
1. **零測試**：後端 `src/test` 空、前端無 vitest。風險評分、JWT、權限等「錯了會出事」的邏輯無測試網。
2. **RAG 是假的**：`similarityHint` 人工標註，無 embedding／向量檢索。知識文件變多後「引用」會失準 — AI 功能最大技術債。
3. **Agent 名實不符**：`AgentFlowService` 是寫死的 if-else 決策樹模擬 Embabel GOAP，無真正 planning / tool-calling。對外不應宣稱「Agent」。

### 🟡 中風險（影響可維護性）
4. **前端單檔災難**：`App.tsx` 1338 行內聯 15 個元件、15 個 `useState`、無路由、無狀態管理庫。
5. **多輪對話無伺服器端記憶**：對話歷史只在前端 state，重整即失憶，無法跨 session 分析。
6. **無 AI 成本 / 用量治理**：無 token 計量、限流、稽核 — 商用時是帳單與資安雙重風險。
7. **PII 未脫敏**：客戶 Email / 電話 / 統編直接進 prompt 送 OpenAI，PRD 規劃要做 PII 遮罩但尚未實作。

### 🟢 低風險（體質問題）
8. 缺 RFM / 客戶分群、無活動自動化（workflow rules）、無資料變更歷史（audit 欄位有了但無 history table）。

---

## 4. 擴充方向（依「投報比 × 急迫性」排序）

> 建議先補地基，**不要急著做 PRD 的 6 大炫炮模組**。

### 梯次一：地基補強（1–2 週，最優先）
- [ ] **假 RAG → 真 pgvector**：加 `spring-ai-starter-vector-store-pgvector` + embedding model，知識文件改存向量。AI 可信度的根本。
- [ ] **拆 App.tsx + 加測試**：前端拆元件 / 上 React Router；後端對 `InsightService.calculateOpportunityRisk` 與 Security 補單元測試。
- [ ] **PII 遮罩 + token 用量記錄**：送 LLM 前過 Regex 遮罩，每次呼叫記 token 數入庫。

### 梯次二：CRM 核心增值（高商業價值、技術風險低）
- [ ] **客戶分群 / RFM 評分**：用現有 Interaction + Opportunity 資料即可算，立刻給業務「該追誰」。
- [ ] **真正的對話記憶**：chat 歷史落庫，串成「客戶溝通脈絡」，AI 評估更準。
- [ ] **自動跟進提醒**：續約到期日 + 風險分數 → 排程任務推播（PRD 模組 5 輕量版）。

### 梯次三：AI 差異化（PRD 旗艦功能，挑 1–2 個做深）
推薦排序：
1. 🥇 **Sentiment & Intent Radar（模組 2）** — 資料現成、技術直接、流失預警痛點明確。**投報比最高**。
2. 🥈 **Hyper-personalized Follow-up（模組 3）** — 開發信生成，業務最有感，需接外部新聞 API。
3. 🥉 **AI Meeting Copilot（模組 1）** — Whisper 轉錄自動更新欄位，效果炫但工程量大。
- ⚠️ **AI Negotiation Sandbox（模組 4）/ Power Mapping（模組 6）** 建議最後做 — demo 效果好但使用頻率低。

---

## 5. 後續可執行項

1. 將「梯次一」展開成檔案層級的實作計畫（含修改清單）。
2. 針對某一 PRD 模組做技術 spike 設計。
3. 先動手把假 RAG 換成真 pgvector（CP 值最高的單一改動）。
