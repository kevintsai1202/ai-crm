# Post-SP9 優化修正計畫（SP10–SP15 程式）

> **For agentic workers:** 本檔是**多子專案主計畫**。實作時依 SP 順序開獨立 session；每個 SP 建議再用 `subagent-driven-development` 或 `executing-plans` 逐任務執行。步驟用 checkbox（`- [ ]`）追蹤。

**Goal:** 在不處理 API 金鑰／JWT 輪替的前提下，把 AI CRM 從「SP9 完成的可 demo MVP」推進到「體驗完整、生產防呆、RAG 更準、CRM 漏斗可行動、工程可維護」。

**Architecture:** 分六個可獨立交付的子專案（SP10–SP15）。前兩個偏配置／UX 快贏；中間補 AI 服務邊界與 chunking；後半補商機階段歷史與前端拆分／可觀測。每個 SP 結束必須：後端相關測試綠、前端 `tsc`/build 綠、關鍵 e2e 或 API 腳本可驗證。

**Tech Stack:** Java 21 / Spring Boot 4.1 / Spring AI 2.0 / Flyway / pgvector / JUnit5 + Testcontainers / React 19 + TS + Vite / Playwright。

**明確排除（本程式不做）：**
- 金鑰輪替、金鑰外洩處理、更換 `OPENAI_API_KEY` / `VOYAGE_API_KEY` / `APP_SECURITY_JWT_SECRET` 實值
- 重寫前端框架、一次上齊 PRD 六大旗艦模組
- 真 multi-agent tool-calling（僅在 SP13 附註產品命名；實作另開）

**環境前置（所有後端任務）：**
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# 測試需 APP_SECURITY_JWT_SECRET（沿用本機 .env 即可，本計畫不改其值）
```

**Global Constraints:**
- 輸出與註解：繁體中文；函式級註解
- 單一任務原則：不順手重構無關檔案
- 測試：後端 TDD 優先；前端關鍵路徑 Playwright
- 演示環境預設可重置 demo；正式 profile 必須安全
- `BASE_URL` 在 Spring AI 2.0 必須含 `/v1`（勿套用 1.x 舊經驗）

---

## 總覽與依賴

```text
SP10 生產防呆 ─────────────────────────────┐
SP11 對話歷史 + UX ────────────────────────┤→ 可並行於 SP10 之後立即做
SP12 InsightService 拆分 + RAG chunking ──┤→ 建議在 SP11 後（共用 AI 路徑）
SP13 商機 StageHistory + 漏斗停留 ────────┤→ 獨立，可與 SP12 並行
SP14 前端 api/styles 拆分 + vitest ───────┤→ 建議 SP11 後（少衝突）
SP15 可觀測 + 限流 + 文件同步 ────────────┘→ 最後收斂
```

| SP | 名稱 | 預估 | 優先 | 依賴 |
|----|------|------|------|------|
| **SP10** | 生產防呆與設定硬化 | 0.5–1 天 | P0 | 無 |
| **SP11** | 客戶對話歷史 API + AI/詳情 UX | 2–4 天 | P0 | 無（可接 SP10） |
| **SP12** | InsightService 拆分 + 知識庫 chunking | 1–2 週 | P1 | 建議 SP11 後 |
| **SP13** | StageHistory + 漏斗停留／超時 | 1–2 週 | P1 | 無（可與 SP12 並行） |
| **SP14** | 前端模組化 + 純函式 vitest | 3–5 天 | P2 | 建議 SP11 後 |
| **SP15** | 限流／metrics／文件對齊 | 2–3 天 | P2 | SP10 後 |

**建議執行序：** SP10 → SP11 →（SP12 ∥ SP13）→ SP14 → SP15  
若人力有限：**只做 SP10 + SP11** 即可明顯改善體驗與安全邊界。

---

## 現況盤點（計畫基準，2026-07-10）

| 項目 | 現況 | 缺口 |
|------|------|------|
| 對話記憶 | `chat_messages` + `ChatMemoryService` 落庫／召回給 LLM | **無**「給前端顯示」的歷史 API；`useAiChat` 切客戶即 `resetChat()` 清空 |
| AI thinking | 已有 `AiThinkingIndicator.tsx` | 需確認所有串流入口（客戶助理／工作檯／評估）是否一致掛上；首 token 前狀態 |
| 客戶詳情 loading | 各 panel 各自 fetch | 缺統一 skeleton／分區 progressive |
| BASE_URL 空字串 | `${BASE_URL:預設}` | 設成 `BASE_URL=` 時 Spring 用空字串，不走預設 |
| Demo reset | `DEMO_RESET_ENABLED` 預設 **true** | 正式環境誤開風險；需 profile 硬化 |
| DEBUG log | `application.yml` 多個 DEBUG | prod 噪音／可能夾帶敏感請求 |
| RAG | 文件級 vector(1024) top-3 | **無 chunking**；長文檢索粗 |
| InsightService | ~1214 行 | 對話／評估／Portfolio／model-test 耦合 |
| 漏斗 | 快照階段計數 + leadSource 切片 | **無 StageHistory**，無法算精準停留天數 |
| 前端 | features 已拆；`api.ts` ~988、`styles.css` ~2340 | 維護成本高；無 vitest |
| Agent | deterministic 決策樹 | 產品命名／文件仍易誤導 |
| 文件 | README / consulting-review 部分過時 | 與 SP1–SP9 現況落差 |

---

## 檔案結構總表（跨 SP）

### 預期新建
| 路徑 | SP | 職責 |
|------|-----|------|
| `backend/.../config/AiBaseUrlEnvironmentPostProcessor.java`（或 `ApplicationEnvironmentPreparedListener`） | SP10 | 正規化空白 `BASE_URL` |
| `backend/.../config/LoggingProfile 或 application-prod.yml` | SP10 | prod 日誌等級 |
| `backend/.../api` 客戶聊天歷史端點（掛 `AiController` 或 `CustomerController`） | SP11 | `GET .../chat/messages` |
| `backend/.../service/ai/*` 拆出服務 | SP12 | Chat / Assessment / Portfolio / LlmClient |
| `backend/.../domain/KnowledgeChunk.java` + migration | SP12 | chunk 級向量 |
| `backend/.../domain/OpportunityStageHistory.java` + migration | SP13 | 階段轉換時間戳 |
| `frontend/src/api/*.ts` | SP14 | 依 domain 拆 API |
| `frontend/src/styles/*.css` | SP14 | 依頁面拆樣式 |
| `frontend/src/**/*.test.ts` + vitest config | SP14 | 純函式測試 |
| `docs/superpowers/specs/2026-07-10-sp1x-*.md` | 各 SP 開工前 | 必要時補短 spec |

### 預期修改（高頻）
| 路徑 | SP |
|------|-----|
| `backend/src/main/resources/application.yml` | SP10, SP15 |
| `backend/.../service/DemoDataService.java` | SP10 |
| `backend/.../security/SecurityConfig.java` | SP10（dev 端點）、SP11 |
| `backend/.../service/ChatMemoryService.java` + `ChatMessageRepository` | SP11 |
| `frontend/src/features/ai-assistant/useAiChat.ts` + Chat UI | SP11 |
| `frontend/src/features/customers/components/CustomerDetailPanel.tsx` | SP11 |
| `backend/.../service/InsightService.java` | SP12（拆出後瘦身） |
| `backend/.../service/KnowledgeIndexer.java` + vector repo | SP12 |
| `backend/.../domain/Opportunity.java` + opportunity services | SP13 |
| `backend/.../service/DashboardService.java` + 漏斗 API | SP13 |
| `frontend` 漏斗圖區塊 | SP13 |
| `README.md`、`docs/roadmap-progress.md`、`docs/zeabur-deployment.md` | SP10/SP15 |
| `frontend/src/api.ts`、`styles.css` | SP14 |

---

# SP10 — 生產防呆與設定硬化（P0）

**Goal:** 空白 `BASE_URL`、demo reset、prod 日誌、Dev 端點與文件防呆，避免示範站設定誤傷正式環境。

**Architecture:** 啟動期正規化環境；以 Spring profile `prod`（或 `!demo`）收斂危險預設；文件寫清 Zeabur 必填項。

### Task 10.1: BASE_URL 空白正規化

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/config/BaseUrlEnvironmentPostProcessor.java`  
  （實作 `EnvironmentPostProcessor`，在 `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor` 註冊）
- Test: `backend/src/test/java/com/aicrm/crm/config/BaseUrlEnvironmentPostProcessorTest.java`
- Modify: `application.yml` 註解補「空字串會被正規化」

**行為規格：**
| 輸入 `BASE_URL` | 結果 |
|-----------------|------|
| 未設定 | 維持 YAML 預設 `https://api.openai.com/v1` |
| `https://hnd1.aihub.zeabur.ai/v1` | 不變 |
| `""` 或只含空白 | 視為未設定 → 寫回預設 `https://api.openai.com/v1` |
| 有值但**結尾無** `/v1` | **本任務不自動補**（避免誤傷自架路徑）；僅文件警告。可選 follow-up：log WARN |

- [ ] **Step 1:** 寫單元測試覆蓋「空白 → 預設」「有值不變」
- [ ] **Step 2:** 實作 PostProcessor：讀 `BASE_URL` / `spring.ai.openai.base-url`，空白則 `MapPropertySource` 覆蓋
- [ ] **Step 3:** `mvn -pl backend -Dtest=BaseUrlEnvironmentPostProcessorTest test`
- [ ] **Step 4:** Commit：`fix(backend): normalize empty BASE_URL to OpenAI default (SP10)`

### Task 10.2: Demo reset 與 Dev 端點硬化

**Files:**
- Modify: `application.yml` — 拆清預設語意；新增 `application-prod.yml`（若尚無）
- Modify: `DemoDataService.java` — 啟動或呼叫時若 `prod` profile 且 reset=true → fail-fast 或強制拒絕
- Modify: `DevController.java` / `SecurityConfig` — `prod` 時可不註冊或一律 404（二選一，建議：**prod profile 不掃描 `DevController`** 或 `@Profile("!prod")`）
- Test: 既有 `DemoDataIntegrationTest` + 新增 profile 測試

**定案：**
- 本機／示範：維持 `DEMO_RESET_ENABLED` 可 true（現狀）
- `spring.profiles.active=prod`：`app.demo.reset-enabled` **強制 false**；`/api/dev/**` 不可用

- [ ] **Step 1:** `DevController` 加 `@Profile("!prod")`（或等價）
- [ ] **Step 2:** `DemoDataService` 在 reset 路徑 double-check profile
- [ ] **Step 3:** 測試：prod 模擬下 reset 拋錯或 404
- [ ] **Step 4:** `docs/zeabur-deployment.md` 加：`DEMO_RESET_ENABLED` 建議與「展示站可 true」說明
- [ ] **Step 5:** Commit：`fix(backend): harden demo reset and dev endpoints for prod (SP10)`

### Task 10.3: 日誌等級分 profile

**Files:**
- Modify: `application.yml` — 預設改回 INFO（或僅 `local` profile 開 DEBUG）
- Create/Modify: `application-local.yml` — 開發用 DEBUG（RestClient / Spring AI / Voyage）
- Create: `application-prod.yml` — root INFO，關閉敏感 DEBUG

- [ ] **Step 1:** 把現有 DEBUG 從主 yml 移到 `local`
- [ ] **Step 2:** README 本機啟動補：`--spring.profiles.active=local` 或文件說明
- [ ] **Step 3:** Commit：`chore(backend): move AI DEBUG logging to local profile (SP10)`

### Task 10.4: 文件最小對齊（SP10 範圍）

**Files:**
- Modify: `README.md` — Boot 版本、真 LLM + fallback、埠、測試指令
- Modify: `docs/roadmap-progress.md` — 登記 SP10–SP15 狀態列
- Modify: `docs/zeabur-deployment.md` — `BASE_URL` 含 `/v1`、空字串行為、`DEMO_RESET`

- [ ] **Step 1:** 改文件，不改業務邏輯
- [ ] **Step 2:** Commit：`docs: align README and deploy notes with post-SP9 reality (SP10)`

**SP10 完成定義：**
- [ ] 空白 `BASE_URL` 可啟動且 chat 不打錯 host
- [ ] prod profile 無法 clear-rebuild demo
- [ ] 預設 log 非 DEBUG 洪水
- [ ] roadmap 出現 SP10–SP15 列

---

# SP11 — 客戶對話歷史 + AI／詳情 UX（P0）

**Goal:** 切換／重開客戶後仍看得到對話；AI 等待狀態一致；客戶詳情載入有過渡。

**Architecture:** 後端在既有 `chat_messages` 上暴露唯讀歷史 API（OwnershipGuard 與客戶詳情一致）；前端 `useAiChat` 進客戶時 hydrate；串流沿用 SSE；loading 用既有 skeleton class。

### Task 11.1: 歷史查詢 Repository + Service

**Files:**
- Modify: `ChatMessageRepository.java`
- Modify: `ChatMemoryService.java` — 新增 `listRecentForUi(customerId, limit)`
- Modify: `Dtos.java` — `ChatMessageResponse(id, role, content, createdAt)`
- Test: `ChatMemoryIntegrationTest` 擴充或新測試類

**API 契約（定案）：**
```http
GET /api/ai/customers/{customerId}/messages?limit=50
Authorization: Bearer …
```
- 預設 `limit=50`，上限 `100`
- 回傳**舊→新**陣列（前端直接 render）
- 角色：`USER` / `ASSISTANT`（與 `ChatRole` 一致，前端 map 成 user/assistant）
- 權限：與讀取該客戶詳情相同（SALES 僅自己的客戶；MANAGER/ADMIN 可視政策沿用 `OwnershipGuard`）
- **不回傳 embedding**

- [ ] **Step 1:** 失敗測試：存 2 則訊息 → GET 回 2 則且順序正確
- [ ] **Step 2:** Repository：`findByCustomerIdOrderByCreatedAtAsc` 或 Pageable
- [ ] **Step 3:** Service 組 DTO
- [ ] **Step 4:** 測試綠 → Commit：`feat(backend): list chat messages for customer UI (SP11)`

### Task 11.2: Controller + Security

**Files:**
- Modify: `AiController.java`（建議掛這裡，與 AI 對話同域）
- Modify: `SecurityConfig` 若路徑已在 `/api/ai/**` 則可能無需改
- 確認 `OwnershipGuard` 在 controller 或 service 層呼叫

- [ ] **Step 1:** 整合測試：SALES 讀自己的客戶 200；讀別人 403/404（對齊專案慣例）
- [ ] **Step 2:** 未登入 401
- [ ] **Step 3:** Commit：`feat(backend): GET /api/ai/customers/{id}/messages (SP11)`

### Task 11.3: 前端 hydrate 對話

**Files:**
- Modify: `frontend/src/api.ts` — `fetchCustomerChatMessages(customerId, limit?)`
- Modify: `frontend/src/types.ts`
- Modify: `useAiChat.ts` — `loadHistory(customerId)`；切客戶時先 reset 再 load
- Modify: `ChatWindow.tsx` / launcher 掛載點
- 複用：`AiThinkingIndicator` — `pending && content===""` 時顯示

**行為：**
1. 開啟某客戶助理 → 呼叫歷史 API → 填入 messages（無 pending）
2. 送出新訊息 → 既有 SSE 追加（後端仍 save 到 `chat_messages`）
3. 歷史載入失敗 → toast／inline 錯誤，不擋新對話
4. 載入中 → 列表 skeleton 或 thinking 條

- [ ] **Step 1:** api + types
- [ ] **Step 2:** `useAiChat` 加 loadHistory
- [ ] **Step 3:** UI 綁定；thinking 在首 chunk 前顯示
- [ ] **Step 4:** Playwright：登入 → 客戶 → 發一則（可 mock/真後端）→ 重整或重進 → 歷史仍在（若 e2e 難依賴 LLM，可改 API 層腳本 `scripts/verify-chat-history.ps1`）
- [ ] **Step 5:** Commit：`feat(frontend): hydrate customer AI chat from server history (SP11)`

### Task 11.4: 客戶詳情載入過渡

**Files:**
- Modify: `CustomerDetailPanel.tsx`（及 `CustomersPage` 載入狀態）
- Modify: `styles.css`（若缺 skeleton 樣式則補最小）

**行為：**
- `detail === null && loading` → skeleton（標題列 + 時間線灰條 + 看板灰塊）
- 錯誤態與空態分離

- [ ] **Step 1:** 接 loading flag（既有則串；無則加）
- [ ] **Step 2:** skeleton UI
- [ ] **Step 3:** 手動／e2e 點客戶不「空白久候」
- [ ] **Step 4:** Commit：`feat(frontend): customer detail loading skeleton (SP11)`

**SP11 完成定義：**
- [ ] 同一客戶重新開啟可看到先前 user/assistant 訊息
- [ ] 權限隔離測試綠
- [ ] AI 送出後至首 token 有明確等待 UI
- [ ] 詳情載入有 skeleton

---

# SP12 — InsightService 拆分 + 知識庫 Chunking（P1）

**Goal:** 降低 AI 核心服務認知負荷；RAG 改 chunk 級檢索，提升引用精準度。

**Architecture:**  
1) **拆分（行為不變）：** 從 `InsightService` 抽出  
   - `LlmInvocationSupport`（建 ChatClient、options、fallback、governance 記錄）  
   - `CustomerChatAiService`（streamChat / chat）  
   - `AssessmentAiService`（customer / portfolio assessment）  
   - 原 `InsightService` 可保留為 façade 或刪除後改注入點  
2) **Chunking：** 索引時切段 → 存 `knowledge_chunks(embedding vector(1024))`；檢索 top-k chunks；引用 DTO 帶 title + 片段。

### Task 12.1: 行為鎖定測試（拆前）

- [ ] 確認既有 `InsightService*`、`AiFallback*`、`RagRelevance*`、`ChatMemory*` 全綠，作為回歸基線
- [ ] Commit（若補測試）：`test(backend): lock InsightService behavior before split (SP12)`

### Task 12.2: 抽出 LlmInvocationSupport + 遷移 streamChat

**Files:** 新建 `service/ai/` 套件；修改 `AiController` / `WorkspaceAiService` 注入點

- [ ] 搬移時**不改** prompt 字串與 SSE 事件型別
- [ ] 每搬一塊跑相關測試
- [ ] Commit：`refactor(backend): extract LlmInvocationSupport and chat AI service (SP12)`

### Task 12.3: 遷移 assessment / portfolio

- [ ] Portfolio 暫不改演算法（全掃仍可接受）；僅搬家
- [ ] Commit：`refactor(backend): extract assessment AI services (SP12)`

### Task 12.4: Chunk 資料模型 + migration

**Files:**
- Create: `V19__knowledge_chunks.sql`（版本號以當下最新+1 為準，實作時先 `ls db/migration`）
- 表建議：
  - `knowledge_chunks(id, document_id, chunk_index, content, embedding vector(1024), created_at)`
  - hnsw cosine index on embedding
- `KnowledgeIndexer`：文件 → chunks（目標 ~500–800 中文字元，overlap ~80）
- `KnowledgeVectorRepository`：改搜 chunk；回傳時 join 文件 title

- [ ] **Step 1:** 單元測試：切段邊界（短文 1 chunk、長文多 chunk、overlap）
- [ ] **Step 2:** migration + entity/repo
- [ ] **Step 3:** reindex 路徑（啟動補索引 + ADMIN reindex）寫 chunk
- [ ] **Step 4:** `loadCitations` 改 chunk top-3（或 top-5 再合併）
- [ ] **Step 5:** 整合測試向量檢索仍綠
- [ ] **Step 6:** Commit：`feat(backend): knowledge chunk embeddings for RAG (SP12)`

### Task 12.5: Portfolio 效能小優化（可選、同一 SP 尾）

- 若時間夠：Portfolio 改「先 SQL 聚合指標，只抽 top N 高風險客戶進 prompt」，避免全表 entity graph
- 不夠則開 follow-up，不阻塞 chunking

**SP12 完成定義：**
- [ ] `InsightService` 主檔 < ~400 行或已刪改 façade
- [ ] 既有 AI 測試全綠
- [ ] reindex 後 chunk 表有資料；引用 content 為片段非全文（長文情境）

---

# SP13 — 商機 StageHistory + 漏斗停留／超時（P1）

**Goal:** 漏斗從「快照形狀」升級為「可行動：各階段平均停留 + 超時示警」。

**Architecture:** 每次商機階段變更寫 `opportunity_stage_history`；漏斗 API 附加 `avgDaysInStage`、`overdueCount`；前端漏斗卡顯示。歷史商機無紀錄者：自 migration 生效後才精準（文件註明；可選回填「當前階段 entered_at = updated_at/now」近似）。

### Task 13.1: Spec 定案（本 SP 開工第一天）

寫短 spec：`docs/superpowers/specs/2026-07-10-sp13-opportunity-stage-history-design.md`

**必拍板：**
- 超時門檻：每階段預設天數（例：資格 14／提案 14／議價 21，可寫死常數或 system_setting）
- 回填策略：僅當前階段插一筆 `from=null, to=current, at=now` vs 不回填
- 失單／成交是否仍算停留

### Task 13.2: Migration + Domain

```sql
-- 示意；實作時用下一個 Flyway 版號
CREATE TABLE opportunity_stage_history (
  id BIGSERIAL PRIMARY KEY,
  opportunity_id BIGINT NOT NULL REFERENCES opportunities(id),
  from_stage VARCHAR(32),
  to_stage VARCHAR(32) NOT NULL,
  changed_at TIMESTAMPTZ NOT NULL,
  changed_by_user_id BIGINT
);
CREATE INDEX idx_osh_opp ON opportunity_stage_history(opportunity_id);
```

- [ ] 變更階段 API（Kanban 拖曳、edit）必寫 history
- [ ] 測試：兩次變更 → 兩筆 history
- [ ] Commit：`feat(backend): opportunity stage history table and writers (SP13)`

### Task 13.3: 漏斗聚合 API 擴充

- Modify: `DashboardService` 漏斗 DTO 加欄位（**向後相容**：舊前端可忽略新欄）
- 計算：目前在該階段的商機，`now - last_entered_at` 的平均／中位；超過門檻計 overdue
- 前端 `ReportsSection` 漏斗：tooltip 或副標顯示「平均停留 X 天 · Y 筆超時」

- [ ] 測試固定時鐘或注入 `Clock`
- [ ] Commit：`feat: funnel dwell time and overdue counts (SP13)`

**SP13 完成定義：**
- [ ] 新商機走完兩次階段變更有 history
- [ ] 儀表板漏斗可見停留／超時
- [ ] 文件註明歷史資料限制

---

# SP14 — 前端模組化 + vitest（P2）

**Goal:** 降低 `api.ts` / `styles.css` 單檔成本；為 layout／format 等純邏輯加單元測試。

### Task 14.1: 拆 `api.ts`

建議目錄：
```text
frontend/src/api/
  client.ts      # axios instance, TOKEN_KEY, interceptors
  auth.ts
  customers.ts
  dashboard.ts
  ai.ts
  workspace.ts
  admin.ts
  index.ts       # re-export 保持 `from "../api"` 相容
```

- [ ] 搬移後 `tsc -b && vite build` 綠
- [ ] 全專案 import 不破（index re-export）
- [ ] Commit：`refactor(frontend): split api.ts by domain (SP14)`

### Task 14.2: 拆 `styles.css`

- 按 shell / dashboard / customers / ai / workspace 拆檔，`main.tsx` 或 `styles.css` 入口 import
- 視覺回歸：跑既有 Playwright sp1-smoke、sp7-layout

### Task 14.3: vitest

- 依賴：`vitest` + 設定
- 優先測：`features/dashboard/layout.ts`（overlap / findFreeSlot）、`lib/format.ts`
- package.json script：`"test": "vitest run"`

**SP14 完成定義：**
- [ ] api 單檔 < 200 行／檔
- [ ] vitest 至少 5 個有意義 assert
- [ ] e2e 主線綠

---

# SP15 — 限流、可觀測、文件收斂（P2）

**Goal:** AI 端點基本保護；關鍵指標可觀測；文件與程式一致。

### Task 15.1: AI／登入限流

- 方案 A（輕量）：Bucket4j 或自寫 Caffeine 計數 filter（每 user 每分鐘 N 次 AI、每 IP 登入 M 次）
- 方案 B：API Gateway／Zeabur 層（若選 B，本 repo 只文件化）

**定案建議：** 方案 A 只保護 `POST /api/auth/login`、`POST /api/ai/**`、`POST /api/workspace/**` 串流入口。

### Task 15.2: Metrics

- Actuator + Micrometer（若 Boot 4 依賴允許）：`ai.call.count`、`ai.call.duration`、`ai.fallback.count`、`rag.hit.count`
- 無 Prometheus 環境時至少 log structured 一行 summary

### Task 15.3: 文件大掃除

- `docs/consulting-review.md` 頂部加「2026-07 現況附錄：SP1–SP9 已完成」
- README 驗證指令與真實埠一致
- Agent 文案：UI／README 改為「決策流程 Trace（教學模擬）」除非未來真接 tool-calling
- `roadmap-progress.md` 全部 SP10–15 狀態更新

**SP15 完成定義：**
- [ ] 暴力打 login/AI 會 429 或等價保護
- [ ] 文件不再宣稱「零測試／假 RAG／無 PII」

---

## 跨 SP 測試指令（驗收共用）

```powershell
# 後端
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend test

# 前端
cd frontend
$env:Path = "D:\nodejs;$env:Path"
pnpm exec tsc -b --pretty false
pnpm run build
# 需 dev server 時
pnpm exec playwright test e2e/sp1-smoke.spec.ts
```

---

## 風險與緩解

| 風險 | 緩解 |
|------|------|
| SP12 拆分引起 SSE 行為回歸 | 拆前鎖測試；一次只搬一個 call type |
| Chunk migration 大表 reindex 久 | 啟動補索引非同步或 ADMIN 手動 reindex；文件說明 |
| StageHistory 舊資料不準 | UI 標「自啟用日起統計」；不假造完整歷史 |
| api.ts 拆分衝突 | SP14 排在 SP11 後；用 re-export 保相容 |
| 限流誤傷 demo 課室多人同 IP | 限流閾值可配置；本機 profile 放寬 |

---

## 建議的第一個實作切片（若只開一個 session）

1. **SP10 全做完**（防呆，半日）  
2. **SP11 Task 11.1–11.3**（對話歷史，體感最大）  
3. SP11.4 skeleton  
4. 再決定 SP12 vs SP13（技術債 vs 業務價值）

---

## 進度追蹤（實作時勾選）

- [ ] SP10 完成並更新 `docs/roadmap-progress.md`
- [ ] SP11 完成
- [ ] SP12 完成
- [ ] SP13 完成
- [ ] SP14 完成
- [ ] SP15 完成

---

## Self-Review（計畫自檢）

| 檢查 | 結果 |
|------|------|
| 先前分析 Wave A–D 是否覆蓋 | 是（金鑰輪替已排除） |
| backlog 對話歷史／thinking／skeleton／StageHistory／BASE_URL／chunking | 皆有 SP |
| Agent 真 tool-calling | 刻意不做，僅文件改名 |
| 占位符 TBD | 無；版號 V19 註明以實作為準 |
| 可獨立交付 | 每個 SP 有完成定義 |

---

## 執行交接

計畫已存於：

`docs/superpowers/plans/2026-07-10-post-sp9-optimization-program.md`

**執行方式建議：**

1. **Subagent-Driven（建議）** — 每個 Task 一個子代理，Task 間 code review  
2. **Inline Execution** — 本 session 從 SP10 Task 10.1 連續做，設 checkpoint  

若要開始實作，請指定：  
- `先做 SP10+SP11`（建議預設）  
- 或 `只做 SP10` / `只做 SP11` / `從 SP13 漏斗開始`
