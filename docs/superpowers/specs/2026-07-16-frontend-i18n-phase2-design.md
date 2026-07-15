# 設計：ai-crm 前端 i18n Phase 2 — format.ts 共用層 + dashboard + customers

- **日期**：2026-07-16
- **範圍**：`lib/format.ts` 共用格式化/標籤函式 i18n 化 + `dashboard` feature 遷移 + `customers` feature 遷移
- **語言**：英文（en，fallback 主語言）、繁體中文（zh-TW）— 沿用 Phase 1 決策
- **函式庫**：react-i18next（已在 Phase 1 建好基建，本次不新增套件）
- **前置**：[Phase 1 設計](2026-07-15-frontend-i18n-design.md) 與 [Phase 1 計畫](../plans/2026-07-15-frontend-i18n.md) 已完成並併入 main（基建 + LoginPage 試點 + AppShell 側邊欄切換）

## 1. 背景與目標

Phase 1 完成 i18n 基建與 LoginPage 試點頁，設計文件明確記錄「日後分批」項目：`lib/format.ts` 的列舉標籤函式與 `Intl` locale 寫死 `zh-TW`、其餘 59 個含中文檔案依 feature 逐頁遷移。

本次（Phase 2）目標：
1. 先處理 `lib/format.ts`——被 dashboard 與 customers 大量共用，不解決會導致兩個 feature 遷移後仍殘留一堆硬編碼中文標籤，體驗不一致。
2. 遷移 `dashboard` feature（`DashboardPage` + 6 個子元件 + `blockTypes.tsx`）。
3. 遷移 `customers` feature（`CustomersPage` + 14 個子元件），為目前含中文檔案數最多的 feature。

依序執行，每段完成後 `tsc --noEmit` + `pnpm build` + `pnpm test` 全綠才進下一段。其餘 feature（business-card、meeting-copilot、tasks、stakeholder-map、opportunity-intelligence、follow-up、team、admin、ai-assistant、my-workspace 等，多半是 codex/ai-crm-intelligent-sales-workflows 分支新增、完全未套用 i18n）留給下一批次，非本次範圍。

### 已確認決策

| 項目 | 決策 |
|------|------|
| 本次範圍 | format.ts 共用層 → dashboard → customers（依序，非平行） |
| format.ts 標籤函式 | 改回傳 i18n key（純函式，不 import i18n），呼叫端自行 `t(key)` |
| format.ts 格式化函式 | 改接受顯式 `locale`（與 `noDataLabel`）參數，不 import i18n |
| Namespace 粒度 | 新增 `dashboard`、`customers` 兩個 namespace；跨頁共用的列舉標籤放大既有 `common` namespace |
| 測試顆粒度 | 頁面級整合測試（`DashboardPage.test.tsx`、`CustomersPage.test.tsx`），非逐子元件開測試檔 |

## 2. `lib/format.ts` 重構

### 現況問題

- `formatMoney`/`formatCompactMoney`/`formatDateTime`/`formatDate` 內部寫死 `new Intl.NumberFormat("zh-TW", ...)` / `Intl.DateTimeFormat("zh-TW", ...)`，且 `formatDateTime`/`formatDate` 的無值 fallback 文字「尚無資料」是硬編碼中文。
- `riskLabel`/`intentLabel`/`stageLabel`/`leadSourceLabel`/`closeReasonLabel` 內部維護 `Record<string, string>` 中文標籤表，`closeReasonLabel` 的 null fallback「未填」也是硬編碼。

### 新簽章

```ts
export function formatMoney(value: number, locale: string): string
export function formatCompactMoney(value: number, locale: string): string
export function formatDateTime(value: string | null, locale: string, noDataLabel: string): string
export function formatDate(value: string | null, locale: string, noDataLabel: string): string

export function riskLabel(level: string): string
export function intentLabel(intent: string | null | undefined): string
export function stageLabel(stage: string): string
export function leadSourceLabel(source: string): string
export function closeReasonLabel(reason: string | null): string
```

- 格式化函式：`locale`/`noDataLabel` 一律由呼叫端傳入（呼叫端用 `useTranslation()` 取得 `i18n.language`、`t('common.noData')`），`format.ts` 完全不 import i18n，維持純函式與現有測試風格一致（僅需傳入字面 locale 字串即可測試，不需初始化 i18next 實例）。
- 標籤函式：回傳 **i18n key 字串**（帶 `common:` namespace 前綴，例如 `"common:enums.risk.LOW"`），未知 enum 值時回退原始值本身（與現行行為一致，不拋錯）；`intentLabel` 對 `null`/`undefined`/`OTHER` 仍回傳 `""`（呼叫端對空字串不需要、也不應該再包一層 `t("")`，維持 `{intentLabel(x) && t(intentLabel(x))}` 或元件內先判斷）；`closeReasonLabel(null)` 回傳 `"common:notFilled"` key。
- 呼叫端寫法變化範例：
  ```tsx
  // Before
  <span>{riskLabel(customer.riskLevel)}</span>
  <span>{formatMoney(amount)}</span>
  // After
  const { t, i18n } = useTranslation(["dashboard", "common"]);
  <span>{t(riskLabel(customer.riskLevel))}</span>
  <span>{formatMoney(amount, i18n.language)}</span>
  ```

### `common.json` 新增 key

```jsonc
{
  "noData": "No data yet / 尚無資料",
  "notFilled": "Not filled / 未填",
  "enums": {
    "risk": { "LOW": "...", "MEDIUM": "...", "HIGH": "..." },
    "intent": { "ASK_PRICING": "...", "COMPARE_COMPETITOR": "...", "CHURN_SIGNAL": "...", "RENEWAL_INTEREST": "...", "UPSELL_SIGNAL": "...", "COMPLAINT": "..." },
    "stage": { "QUALIFICATION": "...", "PROPOSAL": "...", "NEGOTIATION": "...", "CLOSED_WON": "...", "CLOSED_LOST": "..." },
    "leadSource": { "INBOUND": "...", "OUTBOUND": "...", "REFERRAL": "..." },
    "closeReason": { "WON_PRICE": "...", "WON_FEATURE": "...", "WON_RELATIONSHIP": "...", "WON_TIMING": "...", "LOST_PRICE": "...", "LOST_COMPETITOR": "...", "LOST_NO_BUDGET": "...", "LOST_NO_DECISION": "...", "LOST_NO_RESPONSE": "..." }
  }
}
```

（上方 `"..."` 為結構示意——實際內容即目前 `format.ts` 標籤表裡已有的中文字串對應英文翻譯，非待決事項。`intent.OTHER` 不建 key，維持空字串短路行為。）

### `format.test.ts` 改寫

現有測試斷言寫死中文輸出，改為傳入字面 `locale`/`noDataLabel`/驗證回傳 key 字串，不需要初始化 i18next：

```ts
expect(formatMoney(1000, "zh-TW")).toBe(/* Intl 實際輸出 */);
expect(formatMoney(1000, "en")).toBe(/* Intl 實際輸出 */);
expect(riskLabel("LOW")).toBe("common:enums.risk.LOW");
expect(closeReasonLabel(null)).toBe("common:notFilled");
```

## 3. Namespace / 翻譯資源結構

```text
frontend/src/i18n/locales/
  en/common.json        # 既有 + enums/noData/notFilled
  en/dashboard.json      # 新增
  en/customers.json      # 新增
  zh-TW/common.json
  zh-TW/dashboard.json
  zh-TW/customers.json
```

- `i18n/index.ts` 的 `resources` 加入 `dashboard`、`customers` 兩個 namespace 的 en/zh-TW 資源；`defaultNS` 仍為 `common`。
- 頁面內用 `useTranslation(["dashboard", "common"])`（或 `["customers", "common"]`）取得 `t`；跨 namespace 讀 key 用 i18next 標準的 `t("common:enums.risk.LOW")` 前綴語法。
- `blockTypes.tsx` 的 `DashboardBlock.title` 型別不變（仍是 `string`），但各 section 檔（`DashboardCards.tsx`、`ReportsSection.tsx`、`RfmSection.tsx`、`SentimentRadarSection.tsx`、`AiUsageCard.tsx`）內組出 `DashboardBlock` 物件時，`title` 欄位改由呼叫處 `t("dashboard.blocks.xxx")` 產生，而非在 `blockTypes.tsx` 內硬編碼。
- `LayoutDrawer.tsx`（抽屜 UI 本身文字，如「隱藏卡片」等按鈕文案）比照一般頁面遷移。

## 4. 遷移順序與逐檔清單

**Step A — `lib/format.ts`**：改函式簽章 + `common.json` 補 enum key + 改寫 `format.test.ts`。

**Step B — `dashboard`**（依賴 Step A）：
- `blockTypes.tsx`（`LoadingCard` 的「載入中...」文字）
- `layout.ts`（掃過去多為內部邏輯註解，若有使用者可見字串一併處理）
- `DashboardPage.tsx`、`DashboardCards.tsx`、`ReportsSection.tsx`（7 個內嵌圖表元件）、`RfmSection.tsx`、`SentimentRadarSection.tsx`、`AiUsageCard.tsx`、`LayoutDrawer.tsx`
- 新增 `DashboardPage.test.tsx`

**Step C — `customers`**（依賴 Step A）：
- `CustomersPage.tsx` + `components/` 下 14 個檔案：`CustomerList`、`Pagination`、`Timeline`、`OpportunityBoard`、`CustomerDetailPanel`、`ContactsPanel`、`ContactModal`、`UpcomingPanel`、`AddCustomerModal`、`EditCustomerModal`、`AddInteractionModal`、`EditInteractionModal`、`AddOpportunityModal`、`EditOpportunityModal`、`CloseOpportunityModal`、`AiHistoryModal`
- 新增 `CustomersPage.test.tsx`

## 5. 測試策略

- `format.test.ts`：改寫為傳入顯式 locale/key 斷言（見上）。
- `DashboardPage.test.tsx`：比照 `LoginPage.test.tsx` 模式——預設 en 渲染後斷言 3-5 個代表性英文字串（頁面標題、至少一張卡片標題、一個 KPI 標籤），切換 `zh-TW` 後斷言對應中文字串。不逐子元件開獨立測試檔（YAGNI，頁面整合測試已能涵蓋子元件是否正確接上 `t()`）。
- `CustomersPage.test.tsx`：同上模式，涵蓋客戶列表標題、至少一個 Modal 開啟後的欄位標籤、一個 enum 標籤（如風險等級）。
- 既有 Playwright e2e（`v22-tasks.spec.ts` 等）若斷言硬編碼中文字串，需檢查是否受影響（dashboard/customers 目前的 e2e 覆蓋需在遷移後跑一次確認）。
- 每個 Step 完成後執行：`pnpm test && pnpm exec tsc --noEmit && pnpm build`，全綠才 commit 進下一步。

## 6. 非目標（Out of Scope）

- 不遷移 business-card、meeting-copilot、tasks、stakeholder-map、opportunity-intelligence、follow-up、team、admin、ai-assistant、my-workspace 等其餘 feature（留待下一批次）。
- 不新增 en / zh-TW 以外的語言。
- 不改動後端訊息（API `detail` 等）的 i18n。
- 不逐子元件開獨立單元測試檔（採頁面級整合測試）。
