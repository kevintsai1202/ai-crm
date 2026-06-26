# SP8 設計規格：商機資料模型強化（owner / leadSource / probability / closeReason）

> 子專案：SP8（追加於原路線圖 SP1–SP7 之後；路線圖見 `docs/roadmap-progress.md`）
> 建立日期：2026-06-26
> 緣起：使用者檢視「銷售漏斗」報表時發現形狀不合理（上窄下寬、已成交比議價多），追查後確認根因是
>   ①漏斗用「當下階段快照」計數、②CLOSED_WON 為吸收態混入流動階段、③缺少來源維度無法區分主動上門 vs 業務開發。
>   進一步盤點發現系統「AI 分析層豐富、銷售流程資料層單薄」，本 SP 補齊商機的核心銷售欄位。
> 經驗參照：auto-skill `backend-dev.md`──「字串欄位事後正規化為 FK（含啟動回填 + 去正規化同步）」、
>   「衍生資料要與來源一起初始化」、「Flyway migration 不可變」。

---

## 1. 目標與成功標準

**目標**：為 `Opportunity` 補齊四個核心銷售欄位（負責業務、來源、成交機率、輸贏原因），讓既有的報表與 AI 分析層能立即取用，並順手修正月營收 Forecast「未加權、未排除失單」的失真問題。

**成功標準**：
1. `mvn -pl backend test` 全綠（含為新行為新增的測試；既有 SP2 績效測試預期值維持不變，見 §5.2 等價性說明）。
2. `pnpm exec tsc --noEmit` 與 `pnpm build` 綠燈。
3. V18 migration 在生產與示範環境部署時自動加欄位並回填，既有商機無 null 破圖。
4. 漏斗可依來源（全部 / INBOUND / OUTBOUND）切片，兌現使用者最初訴求。
5. 月營收 Forecast 同時呈現「總額 pipeline」與「機率加權預測」兩條，加權線排除 CLOSED_LOST。

**非目標（守 YAGNI，明確不做）**：
- ❌ Stage History（階段歷史/停留時間）──留待後續 SP，本 SP 不追蹤階段轉換時間軸。
- ❌ Next-Best-Action、失單原因分布圖、餵 AI 做失單模式分析──資料先存著，分析另開 SP。
- ❌ 報價單 / 產品目錄 / Email 整合 / 工作流引擎等企業級重型功能。

---

## 2. 欄位設計（`opportunities` 表）

| 欄位 | 型別 | Nullable | 預設/回填 | 用途 |
|------|------|----------|-----------|------|
| `owner_id` | BIGINT FK→app_users | Y | 回填＝所屬客戶 owner_id | 商機負責業務（績效歸屬來源） |
| `lead_source` | VARCHAR(16) | N | DEFAULT `'OUTBOUND'` | 商機來源 |
| `probability` | INT (0–100) | Y | 依階段回填（見 §3） | 成交機率，加權預測用 |
| `close_reason` | VARCHAR(32) | Y | 刻意留 NULL | 輸贏原因（僅結案時填） |
| `close_reason_note` | TEXT | Y | NULL | 結案補充說明 |
| `actual_close_date` | DATE | Y | 已結案者回填 expected_close_date | 實際成交/結案日 |

**新增 enum：**

- `LeadSource`：`INBOUND`（主動上門/進線）、`OUTBOUND`（業務開發）、`REFERRAL`（推薦轉介）。
- `CloseReason`（依拖入 WON / LOST 顯示對應子集）：
  - 贏單：`WON_PRICE`、`WON_FEATURE`、`WON_RELATIONSHIP`、`WON_TIMING`
  - 失單：`LOST_PRICE`、`LOST_COMPETITOR`、`LOST_NO_BUDGET`、`LOST_NO_DECISION`、`LOST_NO_RESPONSE`

> `owner` 採 backend-dev 經驗的「FK + 去正規化快取」模式：entity 同時持有 `@ManyToOne owner` 與顯示用 `ownerName`，
> 透過 `assignOwner(u){ owner=u; ownerName=u.getDisplayName(); }` 保持一致，讓任何以 ownerName 字串彙總的既有查詢免改。

---

## 3. 階段 → 預設成交機率對照表

供「回填」與「新增商機時自動帶值」共用單一真實來源（後端常數，可手動覆寫）：

| 階段 | 預設機率 |
|------|----------|
| QUALIFICATION | 20 |
| PROPOSAL | 50 |
| NEGOTIATION | 75 |
| CLOSED_WON | 100 |
| CLOSED_LOST | 0 |

---

## 4. 後端改動

- **Entity**：`Opportunity` 加 6 欄 + `assignOwner()` + 建構子/更新方法帶新欄位；新增 `LeadSource`、`CloseReason` enum。
- **DTO（`Dtos`）**：`OpportunityResponse` 加 ownerId/ownerName/leadSource/probability/closeReason/closeReasonNote/actualCloseDate；
  `CreateOpportunityRequest`、`UpdateOpportunityRequest` 收 ownerId（預設帶客戶 owner）、leadSource、probability（預設帶階段值）；
  `UpdateStageRequest` 加 closeReason/closeReasonNote/actualCloseDate（拖入 CLOSED_* 時必填原因，actualCloseDate 預設今天）。
- **Controller/Service**：`OpportunityController` + `CustomerService`（或商機相關 service）解析新欄位；ownerId 用 `users.findById` 解析後 `assignOwner`；probability 未給時以階段對照表帶入。

---

## 5. 報表與 AI 連動

### 5.1 月營收 Forecast 加權（`DashboardService` 約 L73–84）
- 現況失真：`filter(expectedCloseDate != null)` 後金額全加總，**未排除 CLOSED_LOST、未加權**。
- 改為輸出兩個數列：
  - `totalAmount`：維持現有口徑（總額 pipeline）。
  - `weightedAmount`：排除 CLOSED_LOST，金額 × probability/100 加總（CLOSED_WON 視為 100% = 已實現）。
- DTO（MoneyChartPoint 或新 record）加 weightedAmount；前端 Forecast 圖疊加第二條線。

### 5.2 業務績效口徑改用商機 owner（`ManagerAnalyticsService`）
- 由「客戶 owner」改為「商機 owner」group by。
- **等價性（關鍵，降低風險）**：回填後每筆商機 owner = 客戶 owner，故切換當下績效數值與舊口徑**完全相同**；
  SP2 既有績效測試**預期值不需改**，僅需確認實作改讀 `opportunity.owner` 後仍通過。差異只在未來商機改派時才出現。
- ⚠️ 待實作時 trace `ManagerAnalyticsService` 現行聚合方式（是否以 `owner_name` 字串彙總），決定改動點。

### 5.3 漏斗依來源切片（前端 `ReportsSection` PipelineFunnel）
- 漏斗區塊加「全部 / INBOUND / OUTBOUND」切換；後端 `pipelineByStage` 需支援依 leadSource 過濾（新增查詢參數或回傳含來源維度的明細供前端過濾）。

---

## 6. 回填策略與 V18 Migration（生產 + 示範環境共用）

> 依 backend-dev 經驗：migration 一旦套用不可變 → 一律新增 V18，不動舊檔。
> 回填採「子查詢版 UPDATE」以同時相容 Postgres 與測試 H2（避免 `UPDATE...FROM` 方言差異）。
> 寫在同一支 migration → Flyway 部署到本機 / Zeabur 生產 / Zeabur 示範時自動冪等套用，無須手動連線跑 SQL。

```sql
-- V18__enrich_opportunity_sales_model.sql
-- SP8：商機資料模型強化（owner / leadSource / probability / closeReason + actualCloseDate）

-- ① 新增欄位（lead_source 以 DEFAULT 完成回填）
ALTER TABLE opportunities ADD COLUMN owner_id          BIGINT;
ALTER TABLE opportunities ADD COLUMN lead_source       VARCHAR(16) NOT NULL DEFAULT 'OUTBOUND';
ALTER TABLE opportunities ADD COLUMN probability       INT;
ALTER TABLE opportunities ADD COLUMN close_reason      VARCHAR(32);
ALTER TABLE opportunities ADD COLUMN close_reason_note TEXT;
ALTER TABLE opportunities ADD COLUMN actual_close_date DATE;

ALTER TABLE opportunities
  ADD CONSTRAINT fk_opportunities_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
CREATE INDEX idx_opportunities_owner       ON opportunities(owner_id);
CREATE INDEX idx_opportunities_lead_source ON opportunities(lead_source);

-- ② 回填 owner_id ← 客戶現有負責業務（績效口徑改用商機 owner 的前提；保證切換零差異）
UPDATE opportunities o
SET owner_id = (SELECT c.owner_id FROM customers c WHERE c.id = o.customer_id)
WHERE o.owner_id IS NULL;

-- ③ 回填 probability ← 依階段預設機率（與後端對照表一致）
UPDATE opportunities SET probability = CASE stage
    WHEN 'QUALIFICATION' THEN 20
    WHEN 'PROPOSAL'      THEN 50
    WHEN 'NEGOTIATION'   THEN 75
    WHEN 'CLOSED_WON'    THEN 100
    WHEN 'CLOSED_LOST'   THEN 0
END
WHERE probability IS NULL;

-- ④ 回填 actual_close_date ← 已結案者用預計成交日（缺則今天）
UPDATE opportunities
SET actual_close_date = COALESCE(expected_close_date, CURRENT_DATE)
WHERE stage IN ('CLOSED_WON', 'CLOSED_LOST') AND actual_close_date IS NULL;

-- ⑤ close_reason 刻意不回填：歷史案子無真實輸贏原因，留 NULL（報表歸「未填」），
--    不假造原因汙染日後失單分析。新案子才在結案 modal 強制填寫。
```

**「所有異動的表」說明**：本 SP 僅 `opportunities` 一張表結構變更；業務績效是查詢（不落表），②回填 owner_id 後即正確。

---

## 7. 示範環境（DemoDataService）

依 backend-dev 經驗「衍生資料要與來源一起初始化」：
- `DemoDataService.generate()` 產出的商機需帶齊：owner（指派業務）、隨機 leadSource（INBOUND/OUTBOUND/REFERRAL）、依階段 probability、結案案子帶 closeReason + actualCloseDate。
- 既有 dev 資料靠 V18 自動回填；未來 `POST /api/dev/generate-demo-data` 重新生成的資料才完整。

---

## 8. 前端改動

- `AddOpportunityModal` / `EditOpportunityModal`：負責業務下拉（沿用 `/customers/options` owners）、來源下拉、機率欄位（預設帶階段值）。
- 拖入 CLOSED_WON / CLOSED_LOST 時彈「結案原因」modal（closeReason 依輸贏顯示子集 + 備註 + 實際成交日，預設今天）。
- `OpportunityBoard` 卡片顯示 owner。
- 漏斗加 全部 / INBOUND / OUTBOUND 切換。
- Forecast 圖疊加加權線。
- `types.ts` / `lib/format.ts`：對應型別與 leadSource/closeReason 中文標籤。

---

## 9. 測試計畫

- **單元**：`DashboardService` 加權 Forecast（排除 LOST、× probability 正確）；probability 預設帶值。
- **整合**：建立商機帶新欄位 → 200 + 回傳正確；拖入 CLOSED_* 必填 closeReason；漏斗依 leadSource 過濾。
- **回歸**：SP2 績效測試（預期值不變，驗等價重構）；V18 migration 在 H2 與 Testcontainers 皆能套用。
- 硬規則：後端改動後**必跑** `mvn -pl backend test`（否則 Zeabur CI 部署失敗）。

---

## 10. 風險與緩解

| 風險 | 緩解 |
|------|------|
| V18 回填 SQL 在 H2 跑不起來（方言差異） | 用子查詢版 UPDATE（非 UPDATE...FROM）；冒煙/整合測試第一時間驗證 |
| `lead_source NOT NULL DEFAULT` 在既有列的相容性 | 加欄位即帶 DEFAULT，既有列自動填 'OUTBOUND'，安全 |
| 績效口徑切換造成數字突變 | §5.2 等價性：回填後一一對應，切換當下零差異 |
| ManagerAnalyticsService 實際聚合方式未知 | 列為實作第一步 trace 點，再決定最小改動 |
| 拖拉結案未填原因造成資料缺漏 | 前端結案 modal 設必填；後端 UpdateStageRequest 對 CLOSED_* 驗證 closeReason 非空 |
| 既有 migration 被誤改觸發 checksum mismatch | 嚴守「只新增 V18」原則 |

---

## 11. 完成後

- 更新 `docs/roadmap-progress.md`：新增 SP8 一列並標狀態。
- 以 writing-plans（superpowers）展開實作計畫，依「後端（V18 + entity + 測試綠燈）→ 前端」分段 review。
