# Frontend i18n Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 將 `lib/format.ts` 共用格式化/標籤函式改為純函式（回傳 i18n key / 接受顯式 locale 參數），並依此遷移 `dashboard`、`customers` 兩個 feature 至 react-i18next。

**Architecture:** `format.ts` 維持零 i18n 依賴的純函式；區塊產生器（`kpiBlocks`/`reportBlocks`/`rfmBlock`/`sentimentBlocks`/`usageBlock`）改為額外接受 `t`（與需要格式化金額/日期時額外接受 `locale`）參數，由 `DashboardPage`/`CustomersPage` 以 `useTranslation()` 取得後逐一傳入；純元件（`LayoutDrawer` 等）直接用 `useTranslation()`。新增 `dashboard`、`customers` 兩個 namespace，`common` namespace 擴充列舉標籤與分頁通用字串。

**Tech Stack:** React 19 + TypeScript + Vite；react-i18next（已於 Phase 1 建好基建）；Vitest + @testing-library/react。

## Global Constraints

- 套件管理用 **pnpm**；Git Bash 環境每個指令前先 `export PATH="/d/nodejs:$PATH"`。
- 每個 task 完成前後皆須 `pnpm exec tsc --noEmit` exit 0。
- 所有新函式需**中文函式級註解**（`/** ... */`），重要變數加中文行內註解（依 `frontend/CLAUDE.md`）。
- 支援語言固定 `["en", "zh-TW"]`；fallback/預設主語言為 en（Phase 1 已定案，不變動）。
- 不遷移 business-card、meeting-copilot、tasks、stakeholder-map、opportunity-intelligence、follow-up、team、admin、ai-assistant、my-workspace 等其餘 feature（下一批次）。
- 不逐子元件開獨立單元測試檔——`dashboard`/`customers` 各僅一個頁面級整合測試檔（`DashboardPage.test.tsx`、`CustomersPage.test.tsx`）。
- 依序執行：Step A（format.ts）→ Step B（dashboard，Task 2-11）→ Step C（customers，Task 12+）；每個 Step 完成都跑 `pnpm test && pnpm exec tsc --noEmit && pnpm build` 全綠才 commit 進下一步。
- `format.ts` 的標籤函式回傳 key 字串（帶 `common:` 前綴），呼叫端一律用 `t(riskLabel(x))` 讀取；格式化函式（`formatMoney` 等）改接受顯式 `locale` 參數，呼叫端傳入 `i18n.language`。
- 純函式產生器（`kpiBlocks` 等，非 React 元件）不可呼叫 `useTranslation()`；一律由呼叫端（`DashboardPage`/`CustomersPage`）以參數傳入 `t`（與需要時的 `locale`）。

---

## File Structure

- Modify: `frontend/src/lib/format.ts` — 純函式改造（locale 參數化 + 標籤回傳 key + 新增 `rfmSegmentLabel`）
- Modify: `frontend/src/lib/format.test.ts` — 改用顯式 locale/key 斷言
- Modify: `frontend/src/i18n/locales/en/common.json` — 新增 `noData`/`notFilled`/`enums.*`/`pagination.*`
- Modify: `frontend/src/i18n/locales/zh-TW/common.json` — 同上中文對照
- Create: `frontend/src/i18n/locales/en/dashboard.json` — dashboard namespace 英文資源
- Create: `frontend/src/i18n/locales/zh-TW/dashboard.json` — dashboard namespace 中文資源
- Modify: `frontend/src/i18n/index.ts` — 註冊 `dashboard` namespace
- Modify: `frontend/src/components/common/PaginatedList.tsx` — 分頁文字 i18n 化
- Modify: `frontend/src/features/dashboard/blockTypes.tsx` — `LoadingCard` 載入文字
- Modify: `frontend/src/features/dashboard/components/DashboardCards.tsx` — `kpiBlocks` 簽章加 `t`/`locale`
- Modify: `frontend/src/features/dashboard/components/RfmSection.tsx` — `rfmBlock` 簽章加 `t`/`locale`
- Modify: `frontend/src/features/dashboard/components/SentimentRadarSection.tsx` — `sentimentBlocks` 簽章加 `t`
- Modify: `frontend/src/features/dashboard/components/AiUsageCard.tsx` — `usageBlock` 簽章加 `t`/`locale`
- Modify: `frontend/src/features/dashboard/components/LayoutDrawer.tsx` — 直接用 `useTranslation()`
- Modify: `frontend/src/features/dashboard/components/ReportsSection.tsx` — `reportBlocks` 簽章加 `t`/`locale`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx` — 全頁 i18n 化 + 逐一傳入 `t`/`locale`
- Create: `frontend/src/features/dashboard/DashboardPage.test.tsx` — 頁面級整合測試
- Create: `frontend/src/i18n/locales/en/customers.json` — customers namespace 英文資源
- Create: `frontend/src/i18n/locales/zh-TW/customers.json` — customers namespace 中文資源
- Modify: `frontend/src/i18n/index.ts`（再次）— 註冊 `customers` namespace
- Modify: `frontend/src/features/customers/components/CustomerList.tsx`
- Modify: `frontend/src/features/customers/components/Pagination.tsx`
- Modify: `frontend/src/features/customers/components/Timeline.tsx`
- Modify: `frontend/src/features/customers/components/OpportunityBoard.tsx`
- Modify: `frontend/src/features/customers/components/CloseOpportunityModal.tsx`
- Modify: `frontend/src/features/customers/components/CustomerDetailPanel.tsx`
- Modify: `frontend/src/features/customers/components/ContactsPanel.tsx`
- Modify: `frontend/src/features/customers/components/ContactModal.tsx`
- Modify: `frontend/src/features/customers/components/UpcomingPanel.tsx`
- Modify: `frontend/src/features/customers/components/AddCustomerModal.tsx`
- Modify: `frontend/src/features/customers/components/EditCustomerModal.tsx`
- Modify: `frontend/src/features/customers/components/AddInteractionModal.tsx`
- Modify: `frontend/src/features/customers/components/EditInteractionModal.tsx`
- Modify: `frontend/src/features/customers/components/AddOpportunityModal.tsx`
- Modify: `frontend/src/features/customers/components/EditOpportunityModal.tsx`
- Modify: `frontend/src/features/customers/components/AiHistoryModal.tsx`
- Modify: `frontend/src/features/customers/CustomersPage.tsx`
- Create: `frontend/src/features/customers/CustomersPage.test.tsx` — 頁面級整合測試

---

## Step A: `lib/format.ts` 共用層

### Task 1: format.ts 純函式改造 + common.json 擴充

**Files:**
- Modify: `frontend/src/lib/format.ts`
- Modify: `frontend/src/lib/format.test.ts`
- Modify: `frontend/src/i18n/locales/en/common.json`
- Modify: `frontend/src/i18n/locales/zh-TW/common.json`

**Interfaces:**
- Consumes: 無（本任務為最底層）
- Produces:
  - `formatMoney(value: number, locale: string): string`
  - `formatCompactMoney(value: number, locale: string): string`
  - `formatDateTime(value: string | null, locale: string, noDataLabel: string): string`
  - `formatDate(value: string | null, locale: string, noDataLabel: string): string`
  - `riskLabel(level: string): string`（回傳如 `"common:enums.risk.LOW"`）
  - `intentLabel(intent: string | null | undefined): string`（`null`/`undefined`/`"OTHER"` 回傳 `""`）
  - `stageLabel(stage: string): string`
  - `leadSourceLabel(source: string): string`
  - `closeReasonLabel(reason: string | null): string`（`null` 回傳 `"common:notFilled"`）
  - `rfmSegmentLabel(segment: string): string`（新函式，對應後端 `RfmService.decideSegment` 回傳的固定中文字面值）

- [ ] **Step 1: 寫失敗測試（改寫 format.test.ts）**

完整覆寫 `frontend/src/lib/format.test.ts`：

```ts
import { describe, expect, it } from "vitest";
import {
  closeReasonLabel, formatCompactMoney, formatDate, formatDateTime, formatMoney,
  intentLabel, leadSourceLabel, riskLabel, rfmSegmentLabel, stageLabel
} from "./format";

describe("format helpers", () => {
  it("riskLabel maps levels to i18n keys", () => {
    expect(riskLabel("HIGH")).toBe("common:enums.risk.HIGH");
    expect(riskLabel("LOW")).toBe("common:enums.risk.LOW");
    // 未知值原樣回傳，不拋錯
    expect(riskLabel("UNKNOWN")).toBe("UNKNOWN");
  });

  it("stageLabel maps stages to i18n keys", () => {
    expect(stageLabel("PROPOSAL")).toBe("common:enums.stage.PROPOSAL");
    expect(stageLabel("CLOSED_WON")).toBe("common:enums.stage.CLOSED_WON");
  });

  it("intentLabel hides OTHER and empty values", () => {
    expect(intentLabel("ASK_PRICING")).toBe("common:enums.intent.ASK_PRICING");
    expect(intentLabel("OTHER")).toBe("");
    expect(intentLabel(null)).toBe("");
    expect(intentLabel(undefined)).toBe("");
  });

  it("leadSourceLabel maps sources to i18n keys", () => {
    expect(leadSourceLabel("INBOUND")).toBe("common:enums.leadSource.INBOUND");
  });

  it("closeReasonLabel maps reasons and null to i18n keys", () => {
    expect(closeReasonLabel("WON_PRICE")).toBe("common:enums.closeReason.WON_PRICE");
    expect(closeReasonLabel(null)).toBe("common:notFilled");
  });

  it("rfmSegmentLabel maps backend literal segment text to i18n keys", () => {
    expect(rfmSegmentLabel("冠軍客戶")).toBe("common:enums.rfmSegment.champion");
    expect(rfmSegmentLabel("瀕危流失")).toBe("common:enums.rfmSegment.atRisk");
    // 未知值原樣回傳
    expect(rfmSegmentLabel("未知分群")).toBe("未知分群");
  });

  it("formatMoney formats by explicit locale", () => {
    expect(formatMoney(12345, "zh-TW")).toBe(
      new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(12345)
    );
    expect(formatMoney(12345, "en")).toBe(
      new Intl.NumberFormat("en", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(12345)
    );
  });

  it("formatCompactMoney formats by explicit locale", () => {
    expect(formatCompactMoney(1234567, "en")).toBe(
      new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(1234567)
    );
  });

  it("formatDateTime uses noDataLabel when value is null", () => {
    expect(formatDateTime(null, "en", "No data yet")).toBe("No data yet");
    expect(formatDateTime("2026-01-01T10:00:00", "en", "No data yet")).toMatch(/2026/);
  });

  it("formatDate uses noDataLabel when value is null", () => {
    expect(formatDate(null, "zh-TW", "尚無資料")).toBe("尚無資料");
  });
});
```

- [ ] **Step 2: 執行確認失敗**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm test -- format`
Expected: FAIL（現有 `format.ts` 簽章與回傳值皆不符新測試）

- [ ] **Step 3: 改寫 format.ts**

完整覆寫 `frontend/src/lib/format.ts`：

```ts
/**
 * 將金額格式化為貨幣樣式；locale 由呼叫端傳入（不在此耦合 i18n）。
 */
export function formatMoney(value: number, locale: string) {
  return new Intl.NumberFormat(locale, { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(value);
}

/**
 * 將金額縮短為報表圖表適合閱讀的單位；locale 由呼叫端傳入。
 */
export function formatCompactMoney(value: number, locale: string) {
  return new Intl.NumberFormat(locale, { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

/**
 * 將日期時間轉為本地可讀格式；無值時回傳呼叫端提供的 noDataLabel（已翻譯文字）。
 */
export function formatDateTime(value: string | null, locale: string, noDataLabel: string) {
  if (!value) return noDataLabel;
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

/**
 * 將日期轉為只到「日」的精簡格式（不含時間），供 KPI 卡等空間有限處使用，避免折行。
 */
export function formatDate(value: string | null, locale: string, noDataLabel: string) {
  if (!value) return noDataLabel;
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(new Date(value));
}

/** 風險等級 → i18n key 對照表（common namespace）。 */
const RISK_KEYS: Record<string, string> = { LOW: "common:enums.risk.LOW", MEDIUM: "common:enums.risk.MEDIUM", HIGH: "common:enums.risk.HIGH" };

/**
 * 將風險等級轉成 i18n key；呼叫端需自行 `t(riskLabel(level))`。未知值原樣回傳。
 */
export function riskLabel(level: string) {
  return RISK_KEYS[level] || level;
}

/** 互動意圖（SP6 intent enum）→ i18n key 對照表。 */
const INTENT_KEYS: Record<string, string> = {
  ASK_PRICING: "common:enums.intent.ASK_PRICING",
  COMPARE_COMPETITOR: "common:enums.intent.COMPARE_COMPETITOR",
  CHURN_SIGNAL: "common:enums.intent.CHURN_SIGNAL",
  RENEWAL_INTEREST: "common:enums.intent.RENEWAL_INTEREST",
  UPSELL_SIGNAL: "common:enums.intent.UPSELL_SIGNAL",
  COMPLAINT: "common:enums.intent.COMPLAINT"
};

/**
 * 將互動意圖轉成 i18n key；OTHER 或 null/undefined 回傳空字串（不顯示，維持原行為）。
 */
export function intentLabel(intent: string | null | undefined) {
  if (!intent || intent === "OTHER") return "";
  return INTENT_KEYS[intent] ?? "";
}

/** 商機階段 → i18n key 對照表。 */
const STAGE_KEYS: Record<string, string> = {
  QUALIFICATION: "common:enums.stage.QUALIFICATION",
  PROPOSAL: "common:enums.stage.PROPOSAL",
  NEGOTIATION: "common:enums.stage.NEGOTIATION",
  CLOSED_WON: "common:enums.stage.CLOSED_WON",
  CLOSED_LOST: "common:enums.stage.CLOSED_LOST"
};

/**
 * 將商機階段轉成 i18n key；呼叫端自行 `t(stageLabel(stage))`。未知值原樣回傳。
 */
export function stageLabel(stage: string) {
  return STAGE_KEYS[stage] || stage;
}

/** 商機來源（leadSource）→ i18n key 對照表。 */
const LEAD_SOURCE_KEYS: Record<string, string> = {
  INBOUND: "common:enums.leadSource.INBOUND",
  OUTBOUND: "common:enums.leadSource.OUTBOUND",
  REFERRAL: "common:enums.leadSource.REFERRAL"
};

/**
 * 將商機來源轉成 i18n key。未知值原樣回傳。
 */
export function leadSourceLabel(source: string) {
  return LEAD_SOURCE_KEYS[source] || source;
}

/** 結案原因（closeReason enum 名）→ i18n key 對照表。 */
const CLOSE_REASON_KEYS: Record<string, string> = {
  WON_PRICE: "common:enums.closeReason.WON_PRICE", WON_FEATURE: "common:enums.closeReason.WON_FEATURE",
  WON_RELATIONSHIP: "common:enums.closeReason.WON_RELATIONSHIP", WON_TIMING: "common:enums.closeReason.WON_TIMING",
  LOST_PRICE: "common:enums.closeReason.LOST_PRICE", LOST_COMPETITOR: "common:enums.closeReason.LOST_COMPETITOR",
  LOST_NO_BUDGET: "common:enums.closeReason.LOST_NO_BUDGET", LOST_NO_DECISION: "common:enums.closeReason.LOST_NO_DECISION",
  LOST_NO_RESPONSE: "common:enums.closeReason.LOST_NO_RESPONSE"
};

/**
 * 將結案原因轉成 i18n key；null 回傳「未填」對應 key。未知值原樣回傳。
 */
export function closeReasonLabel(reason: string | null) {
  if (!reason) return "common:notFilled";
  return CLOSE_REASON_KEYS[reason] || reason;
}

/**
 * RFM 分群 → i18n key 對照表。後端 RfmService.decideSegment 回傳固定中文字面值
 * （非英文 enum code），故以字面值本身作為對照表的 key。
 */
const RFM_SEGMENT_KEYS: Record<string, string> = {
  "冠軍客戶": "common:enums.rfmSegment.champion",
  "忠誠客戶": "common:enums.rfmSegment.loyal",
  "具潛力": "common:enums.rfmSegment.potential",
  "需關注": "common:enums.rfmSegment.attention",
  "瀕危流失": "common:enums.rfmSegment.atRisk"
};

/**
 * 將後端 RFM 分群字面值轉成 i18n key；呼叫端自行 `t(rfmSegmentLabel(segment))`。未知值原樣回傳。
 */
export function rfmSegmentLabel(segment: string) {
  return RFM_SEGMENT_KEYS[segment] || segment;
}
```

- [ ] **Step 4: common.json 新增 key（en）**

修改 `frontend/src/i18n/locales/en/common.json`，在 `"lang"` 與 `"login"` 之間（或之後）插入：

```jsonc
{
  "lang": { "label": "Language", "en": "English", "zh-TW": "繁體中文" },
  "noData": "No data yet",
  "notFilled": "Not filled",
  "enums": {
    "risk": { "LOW": "Low risk", "MEDIUM": "Medium risk", "HIGH": "High risk" },
    "intent": {
      "ASK_PRICING": "Pricing inquiry", "COMPARE_COMPETITOR": "Competitor comparison",
      "CHURN_SIGNAL": "Churn signal", "RENEWAL_INTEREST": "Renewal interest",
      "UPSELL_SIGNAL": "Upsell signal", "COMPLAINT": "Complaint"
    },
    "stage": {
      "QUALIFICATION": "Qualification", "PROPOSAL": "Proposal", "NEGOTIATION": "Negotiation",
      "CLOSED_WON": "Closed won", "CLOSED_LOST": "Closed lost"
    },
    "leadSource": { "INBOUND": "Inbound", "OUTBOUND": "Outbound", "REFERRAL": "Referral" },
    "closeReason": {
      "WON_PRICE": "Won - price", "WON_FEATURE": "Won - feature", "WON_RELATIONSHIP": "Won - relationship", "WON_TIMING": "Won - timing",
      "LOST_PRICE": "Lost - price", "LOST_COMPETITOR": "Lost - competitor", "LOST_NO_BUDGET": "Lost - no budget",
      "LOST_NO_DECISION": "Lost - no decision", "LOST_NO_RESPONSE": "Lost - no response"
    },
    "rfmSegment": {
      "champion": "Champion", "loyal": "Loyal", "potential": "Potential", "attention": "Needs attention", "atRisk": "At risk"
    }
  },
  "login": {
    "badge": "Unit 4 + Unit 5",
    "title": "Sign in to AI CRM Workbench",
    "intro": "Use the seed accounts to enter the full workbench and verify JWT, role permissions, Dashboard, customer data, AI assistant, and Agent Trace.",
    "username": "Username",
    "password": "Password",
    "submit": "Sign in",
    "accounts": "Available accounts: sales@aurora.local / manager@aurora.local / admin@aurora.local, password is password123.",
    "surveyLink": "📋 Fill out the fundraising course survey",
    "error": {
      "invalid": "Incorrect username or password.",
      "rateLimited": "Too many requests, please try again later.",
      "failedDetail": "Login failed: {{detail}}",
      "noBackend": "Cannot reach backend, please confirm the service is running (18080) and the frontend proxy works.",
      "generic": "Login failed, please check your credentials."
    }
  }
}
```

- [ ] **Step 5: common.json 新增 key（zh-TW）**

修改 `frontend/src/i18n/locales/zh-TW/common.json`，同樣位置插入：

```jsonc
{
  "lang": { "label": "語言", "en": "English", "zh-TW": "繁體中文" },
  "noData": "尚無資料",
  "notFilled": "未填",
  "enums": {
    "risk": { "LOW": "低風險", "MEDIUM": "中風險", "HIGH": "高風險" },
    "intent": {
      "ASK_PRICING": "詢價", "COMPARE_COMPETITOR": "競品比較", "CHURN_SIGNAL": "流失信號",
      "RENEWAL_INTEREST": "續約意願", "UPSELL_SIGNAL": "加購", "COMPLAINT": "客訴"
    },
    "stage": { "QUALIFICATION": "資格評估", "PROPOSAL": "提案", "NEGOTIATION": "議價", "CLOSED_WON": "已成交", "CLOSED_LOST": "已流失" },
    "leadSource": { "INBOUND": "主動上門", "OUTBOUND": "業務開發", "REFERRAL": "推薦轉介" },
    "closeReason": {
      "WON_PRICE": "贏-價格", "WON_FEATURE": "贏-功能", "WON_RELATIONSHIP": "贏-關係", "WON_TIMING": "贏-時機",
      "LOST_PRICE": "輸-價格", "LOST_COMPETITOR": "輸-競品", "LOST_NO_BUDGET": "輸-無預算",
      "LOST_NO_DECISION": "輸-未決策", "LOST_NO_RESPONSE": "輸-無回應"
    },
    "rfmSegment": { "champion": "冠軍客戶", "loyal": "忠誠客戶", "potential": "具潛力", "attention": "需關注", "atRisk": "瀕危流失" }
  },
  "login": {
    "badge": "Unit 4 + Unit 5",
    "title": "登入 AI CRM 工作台",
    "intro": "使用教學 seed 帳號進入完整工作台，驗證 JWT、角色權限、Dashboard、客戶資料、AI 助理與 Agent Trace。",
    "username": "帳號",
    "password": "密碼",
    "submit": "登入",
    "accounts": "可用帳號：sales@aurora.local / manager@aurora.local / admin@aurora.local，密碼皆為 password123。",
    "surveyLink": "📋 填寫募資課程問卷",
    "error": {
      "invalid": "帳號或密碼錯誤。",
      "rateLimited": "請求過於頻繁，請稍後再試。",
      "failedDetail": "登入失敗：{{detail}}",
      "noBackend": "無法連線後端，請確認服務已啟動（18080）且前端代理正常。",
      "generic": "登入失敗，請確認帳號與密碼。"
    }
  }
}
```

- [ ] **Step 6: 執行確認通過**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm test -- format`
Expected: PASS（全部案例）

- [ ] **Step 7: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（注意：此時所有呼叫 `formatMoney`/`riskLabel` 等的既有檔案會出現型別錯誤，這是預期的——後續 Task 會逐一修正呼叫端。此步驟只需確認 `format.ts`/`format.test.ts` 本身無型別錯誤；若 `tsc --noEmit` 因下游呼叫端報錯而非 exit 0，記錄下錯誤清單，於 Task 2 起逐一消除，不可回頭修改 format.ts 簽章妥協。）

- [ ] **Step 8: Commit**

```bash
git add frontend/src/lib/format.ts frontend/src/lib/format.test.ts frontend/src/i18n/locales/en/common.json frontend/src/i18n/locales/zh-TW/common.json
git commit -m "feat(i18n): refactor format.ts to pure functions (explicit locale + key-returning labels)"
```

---

## Step B: dashboard feature

### Task 2: 建立 dashboard namespace 資源並註冊

**Files:**
- Create: `frontend/src/i18n/locales/en/dashboard.json`
- Create: `frontend/src/i18n/locales/zh-TW/dashboard.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Consumes: 無
- Produces: `dashboard` namespace 的完整 key 集合（後續 Task 3-11 消費，本 Task 一次建齊，避免後續每個 Task 重複改動同一組 JSON 造成衝突）

- [ ] **Step 1: 建立英文資源**

`frontend/src/i18n/locales/en/dashboard.json`：

```json
{
  "topbar": {
    "title": "Dashboard",
    "surveyLink": "📋 Fill out the fundraising course survey",
    "layoutButton": "⊞ Layout (hidden {{count}})",
    "generateDemo": "🧪 Generate demo data",
    "generatingDemo": "Generating…",
    "portfolioAssess": "📊 Portfolio assessment (company-wide)",
    "aiHistory": "🕘 AI history"
  },
  "grid": { "dragHandle": "Drag to move", "closeBlock": "Close block" },
  "portfolio": {
    "assessTitle": "Portfolio assessment (company-wide)",
    "assessError": "⚠️ Failed to generate assessment, please try again later.",
    "historyTitle": "Company-wide assessment AI history"
  },
  "loading": "Loading...",
  "kpi": {
    "customers": { "title": "KPI · Customers", "label": "Customers" },
    "activeOpps": { "title": "KPI · Active opportunities", "label": "Active opportunities" },
    "pipeline": { "title": "KPI · Pipeline amount", "label": "Pipeline amount" },
    "highRisk": { "title": "KPI · High-risk customers", "label": "High-risk customers" }
  },
  "charts": {
    "pipeline": {
      "title": "Sales funnel · Pipeline", "subtitle": "Opportunity (top) → Won (bottom)",
      "sourceAll": "All", "sourceInbound": "Inbound", "sourceOutbound": "Outbound", "sourceReferral": "Referral",
      "hoverTitle": "Click to view {{stage}}'s {{count}} opportunities", "avgDaysSuffix": " · avg {{days}} days in stage", "overdueSuffix": " · {{count}} overdue",
      "countSuffix": "{{count}}", "avgDaysInline": " · avg {{days}} days"
    },
    "forecast": {
      "title": "Monthly revenue forecast", "subtitle": "Solid = total · Dashed = weighted forecast",
      "ariaLabel": "Monthly revenue forecast line chart", "hoverTitle": "Click to view opportunities expected to close in {{month}}"
    },
    "industry": { "title": "Revenue by industry", "subtitle": "Click to view customers", "drillTitle": "Industry · {{label}}" },
    "risk": { "title": "Customer risk breakdown", "subtitle": "Click to view customers", "drillTitle": "Risk · {{label}}" },
    "renewal": { "title": "Renewal forecast", "subtitle": "Click month to view", "hoverTitle": "Click to view customers renewing in {{month}}", "drillTitle": "Renewal due · {{label}}" },
    "leaderboard": { "title": "Owner leaderboard", "subtitle": "Click to view customers", "drillTitle": "Owner · {{label}}", "customersSuffix": "{{count}} customers", "highRiskSuffix": "high-risk {{count}}" },
    "activity": { "title": "Recent key activity", "subtitle": "Click to jump to customer" }
  },
  "rfm": {
    "title": "RFM customer segments", "subtitle": "Click to jump to customer · R recency / F frequency / M monetary",
    "colCustomer": "Customer", "colSegment": "Segment", "colR": "R", "colF": "F", "colM": "M", "colAmount": "Amount",
    "recencyTitle": "{{days}} days since last interaction", "frequencyTitle": "{{count}} interactions"
  },
  "usage": {
    "title": "AI usage governance", "subtitle": "Manager / Admin only",
    "totalCalls": "AI calls", "totalTokens": "Token usage", "realVsFallback": "Real LLM / Fallback",
    "adopted": "Adopted", "rejected": "Rejected"
  },
  "sentiment": {
    "intentTitle": "Intent distribution", "intentSubtitle": "Interaction intent breakdown", "intentEmpty": "No intent data yet", "intentOther": "Other",
    "trendTitle": "Sentiment trend", "trendSubtitle": "Last 12 months · Positive / Neutral / Negative", "trendEmpty": "No sentiment trend data yet",
    "trendTooltip": "{{month}}｜Positive {{positive}} / Neutral {{neutral}} / Negative {{negative}}",
    "legendPositive": "Positive", "legendNeutral": "Neutral", "legendNegative": "Negative",
    "highRiskTitle": "High-risk interactions", "highRiskSubtitle": "Negative sentiment with churn/complaint intent · Click to jump to customer", "highRiskEmpty": "No high-risk interactions right now",
    "churnTitle": "Churn radar", "churnSubtitle": "Sorted by negative / churn / complaint weighted score · Click to jump to customer", "churnEmpty": "No churn-risk customers yet",
    "churnMetaTitle": "Negative {{negative}} · Churn signal {{churn}} · Complaint {{complaint}}", "churnMetaInline": "Neg {{negative}} · Churn {{churn}} · Compl {{complaint}}",
    "careTitle": "Priority care", "careSubtitle": "Suggested customers to contact first · Click to jump to customer", "careEmpty": "No priority care targets yet"
  },
  "drawer": {
    "title": "Layout settings", "close": "Close", "hint": "Hidden blocks (click ＋ to add back)",
    "allShown": "All blocks are currently shown", "add": "＋ Add back", "reset": "↺ Reset to default layout"
  }
}
```

- [ ] **Step 2: 建立繁中資源**

`frontend/src/i18n/locales/zh-TW/dashboard.json`：

```json
{
  "topbar": {
    "title": "儀表板",
    "surveyLink": "📋 募資課程問卷",
    "layoutButton": "⊞ 版面（隱藏 {{count}}）",
    "generateDemo": "🧪 產生示範資料",
    "generatingDemo": "產生中…",
    "portfolioAssess": "📊 整體評估（全公司）",
    "aiHistory": "🕘 AI 歷程"
  },
  "grid": { "dragHandle": "拖拉移動", "closeBlock": "關閉區塊" },
  "portfolio": {
    "assessTitle": "Portfolio 整體評估（全公司）",
    "assessError": "⚠️ 產生評估失敗，請稍後再試。",
    "historyTitle": "全公司評估 AI 歷程"
  },
  "loading": "載入中...",
  "kpi": {
    "customers": { "title": "KPI · 客戶數", "label": "客戶數" },
    "activeOpps": { "title": "KPI · 活躍商機", "label": "活躍商機" },
    "pipeline": { "title": "KPI · 商機總額", "label": "商機總額" },
    "highRisk": { "title": "KPI · 高風險客戶", "label": "高風險客戶" }
  },
  "charts": {
    "pipeline": {
      "title": "銷售漏斗 Pipeline", "subtitle": "機會(上)→ 成交(下)",
      "sourceAll": "全部", "sourceInbound": "主動上門", "sourceOutbound": "業務開發", "sourceReferral": "推薦轉介",
      "hoverTitle": "點擊查看 {{stage}} 的 {{count}} 筆商機", "avgDaysSuffix": " · 平均停留 {{days}} 天", "overdueSuffix": " · {{count}} 筆超時",
      "countSuffix": "{{count}} 筆", "avgDaysInline": " · 均 {{days}} 天"
    },
    "forecast": {
      "title": "月度營收 Forecast", "subtitle": "實線=總額 · 虛線=加權預測",
      "ariaLabel": "月度營收預測折線圖", "hoverTitle": "點擊查看 {{month}} 預計成交商機"
    },
    "industry": { "title": "產業營收分布", "subtitle": "點擊查看客戶", "drillTitle": "產業 · {{label}}" },
    "risk": { "title": "客戶風險結構", "subtitle": "點擊查看客戶", "drillTitle": "風險 · {{label}}" },
    "renewal": { "title": "續約到期預測", "subtitle": "點擊月份查看", "hoverTitle": "點擊查看 {{month}} 續約客戶", "drillTitle": "續約到期 · {{label}}" },
    "leaderboard": { "title": "業務排行榜", "subtitle": "點擊查看客戶", "drillTitle": "業務 · {{label}}", "customersSuffix": "{{count}} 客戶", "highRiskSuffix": "高風險 {{count}}" },
    "activity": { "title": "近期關鍵互動", "subtitle": "點擊跳到客戶" }
  },
  "rfm": {
    "title": "RFM 客戶分群", "subtitle": "點擊跳到客戶 · R 近期 / F 頻率 / M 金額",
    "colCustomer": "客戶", "colSegment": "分群", "colR": "R", "colF": "F", "colM": "M", "colAmount": "金額",
    "recencyTitle": "距上次互動 {{days}} 天", "frequencyTitle": "互動 {{count}} 次"
  },
  "usage": {
    "title": "AI 用量治理", "subtitle": "僅主管 / 管理員可見",
    "totalCalls": "AI 呼叫次數", "totalTokens": "Token 用量", "realVsFallback": "真實 LLM / Fallback",
    "adopted": "採納", "rejected": "拒絕"
  },
  "sentiment": {
    "intentTitle": "意圖分布", "intentSubtitle": "互動意圖分類統計", "intentEmpty": "尚無意圖資料", "intentOther": "其他",
    "trendTitle": "情緒趨勢", "trendSubtitle": "近 12 月 · 正向 / 中性 / 負向", "trendEmpty": "尚無情緒趨勢資料",
    "trendTooltip": "{{month}}｜正 {{positive}} / 中 {{neutral}} / 負 {{negative}}",
    "legendPositive": "正向", "legendNeutral": "中性", "legendNegative": "負向",
    "highRiskTitle": "高風險互動", "highRiskSubtitle": "負向情緒且意圖為流失 / 客訴 · 點擊跳客戶", "highRiskEmpty": "目前無高風險互動",
    "churnTitle": "流失雷達", "churnSubtitle": "負面 / 流失 / 客訴加權排序 · 點擊跳客戶", "churnEmpty": "尚無流失風險客戶",
    "churnMetaTitle": "負面 {{negative}} · 流失 {{churn}} · 客訴 {{complaint}}", "churnMetaInline": "負 {{negative}} · 流 {{churn}} · 訴 {{complaint}}",
    "careTitle": "優先關懷", "careSubtitle": "建議優先聯繫客戶 · 點擊跳客戶", "careEmpty": "尚無優先關懷對象"
  },
  "drawer": {
    "title": "版面設定", "close": "關閉", "hint": "隱藏的區塊（點＋加回儀表板）",
    "allShown": "目前所有區塊都已顯示", "add": "＋ 加回", "reset": "↺ 還原預設版面"
  }
}
```

- [ ] **Step 3: 在 i18n/index.ts 註冊 dashboard namespace**

修改 `frontend/src/i18n/index.ts`：

(a) 於檔案頂部 import 區塊，`import zhTW from "./locales/zh-TW/common.json";` 之後加入：
```ts
import enDashboard from "./locales/en/dashboard.json";
import zhTWDashboard from "./locales/zh-TW/dashboard.json";
```

(b) 將 `resources` 物件由：
```ts
    resources: {
      en: { common: en },
      "zh-TW": { common: zhTW }
    },
```
改為：
```ts
    resources: {
      en: { common: en, dashboard: enDashboard },
      "zh-TW": { common: zhTW, dashboard: zhTWDashboard }
    },
```

- [ ] **Step 4: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（僅新增 namespace 資源與註冊，未變更任何呼叫端）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/dashboard.json frontend/src/i18n/locales/zh-TW/dashboard.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add dashboard namespace resources"
```

---

### Task 3: `components/common/PaginatedList.tsx` i18n 化

**Files:**
- Modify: `frontend/src/components/common/PaginatedList.tsx`
- Modify: `frontend/src/i18n/locales/en/common.json`
- Modify: `frontend/src/i18n/locales/zh-TW/common.json`

**Interfaces:**
- Consumes: `common:noData`（Task 1 已建立）
- Produces: `PaginatedList` 元件行為不變，僅內部文字改用 `useTranslation()`

- [ ] **Step 1: common.json 補 pagination key（en）**

修改 `frontend/src/i18n/locales/en/common.json`，在 `"notFilled"` 之後加入：
```jsonc
  "pagination": { "prev": "Previous page", "next": "Next page", "pageOf": "Page {{page}} of {{total}}" },
```

- [ ] **Step 2: common.json 補 pagination key（zh-TW）**

修改 `frontend/src/i18n/locales/zh-TW/common.json`，在 `"notFilled"` 之後加入：
```jsonc
  "pagination": { "prev": "上一頁", "next": "下一頁", "pageOf": "第 {{page}} / {{total}} 頁" },
```

- [ ] **Step 3: 改寫 PaginatedList.tsx**

完整覆寫 `frontend/src/components/common/PaginatedList.tsx`：

```tsx
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * 通用前端分頁清單：把超量資料切成固定每頁筆數，底部提供上一頁/下一頁。
 * 函式級註解：純前端分頁（資料已在記憶體），用於儀表板清單卡固定高度；
 * 資料量小於等於 pageSize 時不顯示分頁列。emptyText 未提供時預設用 common:noData。
 */
interface PaginatedListProps<T> {
  /** 全部資料 */
  items: T[];
  /** 每頁筆數，預設 5 */
  pageSize?: number;
  /** 單列渲染 */
  renderRow: (item: T, index: number) => React.ReactNode;
  /** React key 產生器 */
  rowKey: (item: T, index: number) => string;
  /** 無資料時顯示文字；未提供則用 common:noData */
  emptyText?: string;
}

export function PaginatedList<T>({ items, pageSize = 5, renderRow, rowKey, emptyText }: PaginatedListProps<T>) {
  const { t } = useTranslation("common");
  // 當前頁碼（0 起算）
  const [page, setPage] = useState(0);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));

  // 資料量變動導致當前頁超界時，夾回最後一頁，避免顯示空白頁（函數式更新，只需依賴 totalPages）
  useEffect(() => {
    setPage((p) => Math.min(p, totalPages - 1));
  }, [totalPages]);

  if (items.length === 0) {
    return <div className="sr-empty">{emptyText ?? t("noData")}</div>;
  }

  const start = page * pageSize;
  const pageItems = items.slice(start, start + pageSize);

  return (
    <div className="paginated-list">
      <div className="sr-list">
        {pageItems.map((item, i) => (
          <div key={rowKey(item, start + i)}>{renderRow(item, start + i)}</div>
        ))}
      </div>
      {items.length > pageSize ? (
        <div className="pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>{t("pagination.prev")}</button>
          <span>{t("pagination.pageOf", { page: page + 1, total: totalPages })}</span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}>{t("pagination.next")}</button>
        </div>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 4: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/common/PaginatedList.tsx frontend/src/i18n/locales/en/common.json frontend/src/i18n/locales/zh-TW/common.json
git commit -m "feat(i18n): migrate PaginatedList to react-i18next"
```

---

### Task 4: `blockTypes.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/dashboard/blockTypes.tsx`

**Interfaces:**
- Consumes: `dashboard:loading`（Task 2 已建立）
- Produces: `LoadingCard` 新增可選 prop `loadingText`（由呼叫端傳入已翻譯文字，元件本身不呼叫 `useTranslation()`——`LoadingCard` 被非元件的純函式如 `reportBlocks` 在其回傳的 `render()` 內呼叫，為保持一致的「顯式傳入」風格且元件本身仍可獨立在其他情境使用，改為 prop 預設值）

- [ ] **Step 1: 修改 blockTypes.tsx**

修改 `frontend/src/features/dashboard/blockTypes.tsx` 第 21-29 行的 `LoadingCard`：

```tsx
/**
 * 資料尚未載入時的佔位卡片，維持區塊在網格中的位置。
 * @param loadingText 載入中提示文字（已翻譯）；未提供時 fallback 英文 "Loading..."，
 * 避免呼叫端忘記傳入時整頁空白（正式呼叫端一律會傳 t('dashboard.loading')）。
 */
export function LoadingCard({ title, wide, loadingText = "Loading..." }: { title: string; wide?: boolean; loadingText?: string }) {
  return (
    <article className={`panel report-card${wide ? " wide" : ""}`}>
      <div className="loading-line">{title}{loadingText}</div>
    </article>
  );
}
```

注意：原文字為 `{title}載入中...`（無空格黏著），故 `loadingText` 應包含開頭語意上等同「載入中...」的完整字串（呼叫端傳入 `t('dashboard.loading')`，其英文值為 `"Loading..."`、中文值為 `"載入中..."`，與原行為一致）。

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（`LoadingCard` 呼叫端此刻皆未傳 `loadingText`，因預設值存在不會報錯；後續 Task 5-10 會逐一補上 `loadingText={t('dashboard.loading')}`）

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/dashboard/blockTypes.tsx
git commit -m "feat(i18n): add loadingText prop to LoadingCard"
```

---

### Task 5: `DashboardCards.tsx` i18n 化 + `DashboardPage.tsx` 接上 `useTranslation`

**Files:**
- Modify: `frontend/src/features/dashboard/components/DashboardCards.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `dashboard:kpi.*`（Task 2）、`formatMoney(value, locale)`（Task 1）
- Produces: `kpiBlocks(dashboard: DashboardSummary | null, riskCounts: Record<string, number>, t: TFunction, locale: string): DashboardBlock[]`（簽章新增 `t`、`locale` 兩參數，放在既有參數之後）

此為第一個「區塊產生器」任務，同時在 `DashboardPage.tsx` 加入 `useTranslation(["dashboard", "common"])`（後續 Task 6-10 都會沿用此處已加入的 `t`/`i18n`，不重複加 hook）。

- [ ] **Step 1: 改寫 DashboardCards.tsx**

完整覆寫 `frontend/src/features/dashboard/components/DashboardCards.tsx`：

```tsx
import type { TFunction } from "i18next";
import type { DashboardSummary } from "../../../types";
import { formatMoney } from "../../../lib/format";
import type { DashboardBlock } from "../blockTypes";

/**
 * 單張 KPI 統計小卡。
 */
function KpiCard({ label, value }: { label: string; value: string | number }) {
  return (
    <article className="metric-card">
      <small>{label}</small>
      <strong>{value}</strong>
    </article>
  );
}

/**
 * 產生 4 張獨立的 KPI 統計區塊（客戶數 / 活躍商機 / 商機總額 / 高風險），各自可拖拉與關閉。
 * 函式級註解：dashboard 為 null 時以 0 / 預設值呈現，不阻斷渲染。非 React 元件，不可呼叫
 * useTranslation()，故 t（與需要格式化金額的 locale）皆由呼叫端（DashboardPage）傳入。
 *
 * @param dashboard 儀表板摘要（可為 null）
 * @param riskCounts 風險統計（高風險 fallback 用）
 * @param t react-i18next 的 t 函式（呼叫端已 scope 到 ["dashboard","common"]）
 * @param locale 目前語言（i18n.language），供 formatMoney 格式化用
 * @returns 4 個 KPI 區塊
 */
export function kpiBlocks(
  dashboard: DashboardSummary | null,
  riskCounts: Record<string, number>,
  t: TFunction,
  locale: string
): DashboardBlock[] {
  return [
    { id: "kpi-customers", title: t("dashboard:kpi.customers.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.customers.label")} value={dashboard?.customerCount ?? 0} /> },
    { id: "kpi-active-opps", title: t("dashboard:kpi.activeOpps.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.activeOpps.label")} value={dashboard?.activeOpportunityCount ?? 0} /> },
    { id: "kpi-pipeline", title: t("dashboard:kpi.pipeline.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.pipeline.label")} value={formatMoney(dashboard?.opportunityAmount ?? 0, locale)} /> },
    { id: "kpi-high-risk", title: t("dashboard:kpi.highRisk.title"), short: true, render: () => <KpiCard label={t("dashboard:kpi.highRisk.label")} value={dashboard?.highRiskCount ?? riskCounts.HIGH ?? 0} /> }
  ];
}
```

- [ ] **Step 2: 在 DashboardPage.tsx 加入 useTranslation 並更新 kpiBlocks 呼叫**

修改 `frontend/src/features/dashboard/DashboardPage.tsx`：

(a) 頂部 import 區塊，`import { useEffect, useMemo, useRef, useState } from "react";` 之後加入：
```tsx
import { useTranslation } from "react-i18next";
```

(b) 函式本體開頭（`const navigate = useNavigate();` 之前）加入：
```tsx
  const { t, i18n } = useTranslation(["dashboard", "common"]);
```

(c) 將 `fullCatalog` 組裝處（原本）：
```tsx
  const fullCatalog: DashboardBlock[] = [
    ...kpiBlocks(dashboard, riskCounts),
    ...reportBlocks(reports, openDrilldown, jumpToCustomer),
    ...sentimentBlocks(sentiment, jumpToCustomer),
    rfmBlock(rfm, jumpToCustomer),
    usageBlock(usage)
  ];
```
改為（本 Task 只改第一行 `kpiBlocks(...)` 呼叫，其餘三行維持原樣，將於 Task 6-10 逐一更新）：
```tsx
  const fullCatalog: DashboardBlock[] = [
    ...kpiBlocks(dashboard, riskCounts, t, i18n.language),
    ...reportBlocks(reports, openDrilldown, jumpToCustomer),
    ...sentimentBlocks(sentiment, jumpToCustomer),
    rfmBlock(rfm, jumpToCustomer),
    usageBlock(usage)
  ];
```

- [ ] **Step 3: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: 仍會因 `reportBlocks`/`sentimentBlocks`/`rfmBlock`/`usageBlock` 尚未更新簽章而報錯（這三行呼叫維持舊簽章、對應檔案也還沒改，兩邊一致，不應報錯；若報錯，確認是否誤改了尚未輪到的檔案）。`kpiBlocks` 與 `DashboardCards.tsx` 之間應無型別錯誤。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/dashboard/components/DashboardCards.tsx frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(i18n): migrate DashboardCards to react-i18next"
```

---

### Task 6: `RfmSection.tsx` i18n 化（含 rfmSegmentLabel）

**Files:**
- Modify: `frontend/src/features/dashboard/components/RfmSection.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `dashboard:rfm.*`（Task 2）、`rfmSegmentLabel`/`formatCompactMoney`（Task 1）
- Produces: `rfmBlock(data: RfmResponse[] | null, onSelectCustomer: (id: number, source: DrilldownSource) => void, t: TFunction, locale: string): DashboardBlock`

- [ ] **Step 1: 改寫 RfmSection.tsx**

完整覆寫 `frontend/src/features/dashboard/components/RfmSection.tsx`：

```tsx
import type { TFunction } from "i18next";
import type { DrilldownSource, RfmResponse } from "../../../types";
import { formatCompactMoney, rfmSegmentLabel } from "../../../lib/format";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * 將分群標籤對應到 CSS class（用於不同顏色的色票）。分群文字本身來自後端固定中文字面值，
 * class 對照與翻譯（rfmSegmentLabel）各自獨立維護，互不影響。
 */
function segmentClass(segment: string) {
  const map: Record<string, string> = {
    "冠軍客戶": "champion",
    "忠誠客戶": "loyal",
    "具潛力": "potential",
    "需關注": "attention",
    "瀕危流失": "atrisk"
  };
  return map[segment] || "potential";
}

/**
 * RFM 客戶分群區塊：列出每位客戶的 R/F/M 分數與分群標籤，點擊跳到操作頁客戶詳情。
 * 函式級註解：R 為距今最後互動天數（越小越好）、F 為互動次數、M 為商機金額；rScore/fScore/mScore 為 1-5 分級。
 */
function RfmCard({ data, onSelectCustomer, t, locale }: {
  data: RfmResponse[];
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
  locale: string;
}) {
  return (
    <article className="panel report-card wide" data-promo-chart="rfm">
      <div className="panel-title">
        <h3>{t("dashboard:rfm.title")}</h3>
        <span>{t("dashboard:rfm.subtitle")}</span>
      </div>
      <div className="rfm-table">
        <div className="rfm-head">
          <span>{t("dashboard:rfm.colCustomer")}</span>
          <span>{t("dashboard:rfm.colSegment")}</span>
          <span>{t("dashboard:rfm.colR")}</span>
          <span>{t("dashboard:rfm.colF")}</span>
          <span>{t("dashboard:rfm.colM")}</span>
          <span>{t("dashboard:rfm.colAmount")}</span>
        </div>
        {data.map((row) => (
          <button type="button" className="rfm-row clickable" key={row.customerId} onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: t("dashboard:rfm.title"), blockId: "rfm" })}>
            <strong>{row.name}</strong>
            <span className={`rfm-seg ${segmentClass(row.segment)}`}>{t(rfmSegmentLabel(row.segment))}</span>
            <em title={t("dashboard:rfm.recencyTitle", { days: row.recencyDays })}>{row.rScore}</em>
            <em title={t("dashboard:rfm.frequencyTitle", { count: row.frequency })}>{row.fScore}</em>
            <em>{row.mScore}</em>
            <b>{formatCompactMoney(row.monetary, locale)}</b>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 產生 RFM 客戶分群區塊（可拖拉與關閉）。非 React 元件，t/locale 由呼叫端傳入。
 *
 * @param data RFM 分群清單（可為 null）
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 formatCompactMoney 用
 * @returns RFM 區塊
 */
export function rfmBlock(
  data: RfmResponse[] | null,
  onSelectCustomer: (id: number, source: DrilldownSource) => void,
  t: TFunction,
  locale: string
): DashboardBlock {
  return {
    id: "rfm",
    title: t("dashboard:rfm.title"),
    wide: true,
    render: () => data
      ? <RfmCard data={data} onSelectCustomer={onSelectCustomer} t={t} locale={locale} />
      : <LoadingCard title={t("dashboard:rfm.title")} wide loadingText={t("dashboard:loading")} />
  };
}
```

- [ ] **Step 2: 更新 DashboardPage.tsx 的 rfmBlock 呼叫**

修改 `frontend/src/features/dashboard/DashboardPage.tsx`，將：
```tsx
    rfmBlock(rfm, jumpToCustomer),
```
改為：
```tsx
    rfmBlock(rfm, jumpToCustomer, t, i18n.language),
```

- [ ] **Step 3: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: `RfmSection.tsx`/`rfmBlock` 呼叫處無型別錯誤（`reportBlocks`/`sentimentBlocks`/`usageBlock` 三行仍是舊簽章，維持一致不報錯）

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/dashboard/components/RfmSection.tsx frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(i18n): migrate RfmSection to react-i18next"
```

---

### Task 7: `SentimentRadarSection.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/dashboard/components/SentimentRadarSection.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `dashboard:sentiment.*`（Task 2）、`formatDateTime(value, locale, noDataLabel)`/`intentLabel`（Task 1）、`common:noData`（Task 1）
- Produces: `sentimentBlocks(data: SentimentRadarResponse | null, onSelectCustomer: (id, source) => void, t: TFunction, locale: string): DashboardBlock[]`

- [ ] **Step 1: 改寫 SentimentRadarSection.tsx**

完整覆寫 `frontend/src/features/dashboard/components/SentimentRadarSection.tsx`：

```tsx
import type { TFunction } from "i18next";
import type { DrilldownSource, SentimentRadarResponse } from "../../../types";
import { formatDateTime, intentLabel } from "../../../lib/format";
import { PaginatedList } from "../../../components/common/PaginatedList";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * 將情緒值（POSITIVE / NEUTRAL / NEGATIVE）轉成色點 CSS class。
 */
function sentimentClass(sentiment: string | null | undefined) {
  const map: Record<string, string> = { POSITIVE: "pos", NEUTRAL: "neu", NEGATIVE: "neg" };
  return map[sentiment || ""] || "neu";
}

/**
 * 視覺 1：意圖分布長條卡。
 */
function IntentDistributionCard({ data, t }: { data: SentimentRadarResponse; t: TFunction }) {
  // 意圖分布長條的最大值，用於計算每列長條寬度比例
  const intentMax = Math.max(1, ...data.intentDistribution.map((i) => i.count));
  return (
    <article className="panel report-card sr-card" data-promo-chart="sentiment-intent">
      <div className="panel-title"><h3>{t("dashboard:sentiment.intentTitle")}</h3><span>{t("dashboard:sentiment.intentSubtitle")}</span></div>
      <div className="sr-intent-list">
        {data.intentDistribution.length === 0 ? (
          <div className="sr-empty">{t("dashboard:sentiment.intentEmpty")}</div>
        ) : (
          data.intentDistribution.map((row) => {
            const key = intentLabel(row.intent);
            const label = key ? t(key) : (row.intent === "OTHER" ? t("dashboard:sentiment.intentOther") : row.intent);
            return (
              <div className="sr-intent-row" key={row.intent}>
                <span className="sr-intent-label">{label}</span>
                <span className="sr-bar-track"><span className="sr-bar-fill" style={{ width: `${(row.count / intentMax) * 100}%` }} /></span>
                <b className="sr-intent-count">{row.count}</b>
              </div>
            );
          })
        )}
      </div>
    </article>
  );
}

/**
 * 視覺 2：近 12 月情緒趨勢（正/中/負三色堆疊長條）。
 */
function SentimentTrendCard({ data, t }: { data: SentimentRadarResponse; t: TFunction }) {
  // 情緒趨勢每月三色堆疊長條的最大總量，用於正規化高度
  const trendMax = Math.max(1, ...data.sentimentTrend.map((p) => p.positive + p.neutral + p.negative));
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.trendTitle")}</h3><span>{t("dashboard:sentiment.trendSubtitle")}</span></div>
      {data.sentimentTrend.length === 0 ? (
        <div className="sr-empty">{t("dashboard:sentiment.trendEmpty")}</div>
      ) : (
        <>
          <div className="sr-trend-chart">
            {data.sentimentTrend.map((p) => {
              const total = p.positive + p.neutral + p.negative;
              return (
                <div className="sr-trend-col" key={p.month} title={t("dashboard:sentiment.trendTooltip", { month: p.month, positive: p.positive, neutral: p.neutral, negative: p.negative })}>
                  <div className="sr-trend-stack" style={{ height: `${(total / trendMax) * 100}%` }}>
                    <span className="sr-seg neg" style={{ flexGrow: p.negative }} />
                    <span className="sr-seg neu" style={{ flexGrow: p.neutral }} />
                    <span className="sr-seg pos" style={{ flexGrow: p.positive }} />
                  </div>
                  <small className="sr-trend-x">{p.month.slice(5)}</small>
                </div>
              );
            })}
          </div>
          <div className="sr-legend">
            <span><i className="sr-dot pos" />{t("dashboard:sentiment.legendPositive")}</span>
            <span><i className="sr-dot neu" />{t("dashboard:sentiment.legendNeutral")}</span>
            <span><i className="sr-dot neg" />{t("dashboard:sentiment.legendNegative")}</span>
          </div>
        </>
      )}
    </article>
  );
}

/**
 * 視覺 3：高風險互動清單卡。
 */
function HighRiskCard({ data, onSelectCustomer, t, locale }: {
  data: SentimentRadarResponse;
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
  locale: string;
}) {
  return (
    <article className="panel report-card wide sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.highRiskTitle")}</h3><span>{t("dashboard:sentiment.highRiskSubtitle")}</span></div>
      <PaginatedList
        items={data.highRiskInteractions}
        pageSize={5}
        emptyText={t("dashboard:sentiment.highRiskEmpty")}
        rowKey={(item, i) => `${item.customerId}-${i}`}
        renderRow={(item) => (
          <button type="button" className="sr-row clickable" onClick={() => onSelectCustomer(item.customerId, { from: "dashboard", section: t("dashboard:sentiment.highRiskTitle"), blockId: "sr-highrisk" })}>
            <span className={`sr-dot ${sentimentClass(item.sentiment)}`} />
            <strong className="sr-row-name">{item.customerName}</strong>
            {intentLabel(item.intent) ? <span className="sr-tag">{t(intentLabel(item.intent))}</span> : null}
            <span className="sr-row-content">{item.content}</span>
            <em className="sr-row-time">{formatDateTime(item.occurredAt, locale, t("common:noData"))}</em>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 視覺 4：流失雷達（依加權分數排序）。
 */
function ChurnRadarCard({ data, onSelectCustomer, t }: {
  data: SentimentRadarResponse;
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
}) {
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.churnTitle")}</h3><span>{t("dashboard:sentiment.churnSubtitle")}</span></div>
      <PaginatedList
        items={data.churnRadar}
        pageSize={5}
        emptyText={t("dashboard:sentiment.churnEmpty")}
        rowKey={(row) => String(row.customerId)}
        renderRow={(row) => (
          <button type="button" className="sr-row sr-churn-row clickable" onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: t("dashboard:sentiment.churnTitle"), blockId: "sr-churn" })}>
            <strong className="sr-row-name">{row.name}</strong>
            <span className="sr-churn-meta" title={t("dashboard:sentiment.churnMetaTitle", { negative: row.negativeCount, churn: row.churnSignalCount, complaint: row.complaintCount })}>
              {t("dashboard:sentiment.churnMetaInline", { negative: row.negativeCount, churn: row.churnSignalCount, complaint: row.complaintCount })}
            </span>
            <b className="sr-score">{row.score}</b>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 視覺 5：優先關懷清單卡。
 */
function PriorityCareCard({ data, onSelectCustomer, t }: {
  data: SentimentRadarResponse;
  onSelectCustomer: (id: number, source: DrilldownSource) => void;
  t: TFunction;
}) {
  return (
    <article className="panel report-card sr-card">
      <div className="panel-title"><h3>{t("dashboard:sentiment.careTitle")}</h3><span>{t("dashboard:sentiment.careSubtitle")}</span></div>
      <PaginatedList
        items={data.priorityCare}
        pageSize={5}
        emptyText={t("dashboard:sentiment.careEmpty")}
        rowKey={(row) => String(row.customerId)}
        renderRow={(row) => (
          <button type="button" className="sr-row sr-care-row clickable" onClick={() => onSelectCustomer(row.customerId, { from: "dashboard", section: t("dashboard:sentiment.careTitle"), blockId: "sr-care" })}>
            <strong className="sr-row-name">{row.name}</strong>
            <span className="sr-row-content">{row.reason}</span>
          </button>
        )}
      />
    </article>
  );
}

/**
 * 產生 5 個獨立的情緒意圖視覺區塊（各自可拖拉與關閉）。非 React 元件，t/locale 由呼叫端傳入。
 * 函式級註解：data 為 null 時各區塊以 LoadingCard 佔位；清單卡內部分頁，點列帶來源區塊跳客戶。
 *
 * @param data 情緒雷達聚合資料（可為 null）
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 formatDateTime 用
 * @returns 5 個情緒視覺區塊
 */
export function sentimentBlocks(
  data: SentimentRadarResponse | null,
  onSelectCustomer: (id: number, source: DrilldownSource) => void,
  t: TFunction,
  locale: string
): DashboardBlock[] {
  return [
    { id: "sr-intent", title: t("dashboard:sentiment.intentTitle"), render: () => data ? <IntentDistributionCard data={data} t={t} /> : <LoadingCard title={t("dashboard:sentiment.intentTitle")} loadingText={t("dashboard:loading")} /> },
    { id: "sr-trend", title: t("dashboard:sentiment.trendTitle"), render: () => data ? <SentimentTrendCard data={data} t={t} /> : <LoadingCard title={t("dashboard:sentiment.trendTitle")} loadingText={t("dashboard:loading")} /> },
    { id: "sr-highrisk", title: t("dashboard:sentiment.highRiskTitle"), wide: true, render: () => data ? <HighRiskCard data={data} onSelectCustomer={onSelectCustomer} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:sentiment.highRiskTitle")} wide loadingText={t("dashboard:loading")} /> },
    { id: "sr-churn", title: t("dashboard:sentiment.churnTitle"), render: () => data ? <ChurnRadarCard data={data} onSelectCustomer={onSelectCustomer} t={t} /> : <LoadingCard title={t("dashboard:sentiment.churnTitle")} loadingText={t("dashboard:loading")} /> },
    { id: "sr-care", title: t("dashboard:sentiment.careTitle"), render: () => data ? <PriorityCareCard data={data} onSelectCustomer={onSelectCustomer} t={t} /> : <LoadingCard title={t("dashboard:sentiment.careTitle")} loadingText={t("dashboard:loading")} /> }
  ];
}
```

- [ ] **Step 2: 更新 DashboardPage.tsx 的 sentimentBlocks 呼叫**

修改 `frontend/src/features/dashboard/DashboardPage.tsx`，將：
```tsx
    ...sentimentBlocks(sentiment, jumpToCustomer),
```
改為：
```tsx
    ...sentimentBlocks(sentiment, jumpToCustomer, t, i18n.language),
```

- [ ] **Step 3: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（`reportBlocks`/`usageBlock` 兩行仍是舊簽章，維持一致不報錯）

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/dashboard/components/SentimentRadarSection.tsx frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(i18n): migrate SentimentRadarSection to react-i18next"
```

---

### Task 8: `AiUsageCard.tsx` i18n 化（含 toLocaleString 修正）

**Files:**
- Modify: `frontend/src/features/dashboard/components/AiUsageCard.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `dashboard:usage.*`（Task 2）
- Produces: `usageBlock(usage: UsageSummaryResponse | null, t: TFunction, locale: string): DashboardBlock`

- [ ] **Step 1: 改寫 AiUsageCard.tsx**

完整覆寫 `frontend/src/features/dashboard/components/AiUsageCard.tsx`：

```tsx
import type { TFunction } from "i18next";
import type { UsageSummaryResponse } from "../../../types";
import { AiBadge } from "../../../components/common/AiBadge";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/**
 * AI 用量治理卡（SP4，MANAGER/ADMIN 可見）：呈現呼叫次數、token 用量、真實/fallback 比、採納/拒絕統計。
 */
function UsageCard({ usage, t, locale }: { usage: UsageSummaryResponse; t: TFunction; locale: string }) {
  const cells: [string, string | number][] = [
    [t("dashboard:usage.totalCalls"), usage.totalCalls],
    [t("dashboard:usage.totalTokens"), usage.totalTokens.toLocaleString(locale)],
    [t("dashboard:usage.realVsFallback"), `${usage.realCalls} / ${usage.fallbackCalls}`],
    [t("dashboard:usage.adopted"), usage.adopted],
    [t("dashboard:usage.rejected"), usage.rejected]
  ];
  return (
    <article className="panel report-card wide" data-promo-chart="ai-usage">
      <div className="panel-title">
        <h3>{t("dashboard:usage.title")} <AiBadge /></h3>
        <span>{t("dashboard:usage.subtitle")}</span>
      </div>
      <div className="ai-usage-grid">
        {cells.map(([label, value]) => (
          <div className="ai-usage-cell" key={label}>
            <small>{label}</small>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
    </article>
  );
}

/**
 * 產生 AI 用量治理區塊（可拖拉與關閉；僅 MANAGER / ADMIN 載入）。非 React 元件，t/locale 由呼叫端傳入。
 *
 * @param usage 用量彙總（可為 null）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 toLocaleString 用
 * @returns AI 用量區塊
 */
export function usageBlock(usage: UsageSummaryResponse | null, t: TFunction, locale: string): DashboardBlock {
  return {
    id: "usage",
    title: t("dashboard:usage.title"),
    wide: true,
    render: () => usage
      ? <UsageCard usage={usage} t={t} locale={locale} />
      : <LoadingCard title={t("dashboard:usage.title")} wide loadingText={t("dashboard:loading")} />
  };
}
```

- [ ] **Step 2: 更新 DashboardPage.tsx 的 usageBlock 呼叫**

修改 `frontend/src/features/dashboard/DashboardPage.tsx`，將：
```tsx
    usageBlock(usage)
```
改為：
```tsx
    usageBlock(usage, t, i18n.language)
```

- [ ] **Step 3: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（僅 `reportBlocks` 一行仍是舊簽章）

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/dashboard/components/AiUsageCard.tsx frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(i18n): migrate AiUsageCard to react-i18next"
```

---

### Task 9: `LayoutDrawer.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/dashboard/components/LayoutDrawer.tsx`

**Interfaces:**
- Consumes: `dashboard:drawer.*`（Task 2）
- Produces: `LayoutDrawer` 元件對外 props 不變（本身是元件，直接用 `useTranslation()`，不需呼叫端傳入 `t`）

- [ ] **Step 1: 改寫 LayoutDrawer.tsx**

完整覆寫 `frontend/src/features/dashboard/components/LayoutDrawer.tsx`：

```tsx
import { useTranslation } from "react-i18next";

/**
 * 版面抽屜：右側滑入，列出目前隱藏的儀表板區塊，可逐一加回，並提供還原預設順序。
 * 函式級註解：加回與還原都委派給父層（DashboardPage）統一走存檔邏輯，本元件只負責呈現與觸發。
 * 本身是 React 元件（非純函式產生器），直接用 useTranslation()。
 */
interface HiddenBlock {
  /** 區塊 id */
  id: string;
  /** 區塊顯示名（已由呼叫端翻譯） */
  title: string;
}

export function LayoutDrawer({ hiddenBlocks, onAdd, onReset, onClose }: {
  hiddenBlocks: HiddenBlock[];
  onAdd: (id: string) => void;
  onReset: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("dashboard");
  return (
    <div className="drawer-overlay" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header">
          <strong>{t("drawer.title")}</strong>
          <button type="button" className="chat-close" onClick={onClose} aria-label={t("drawer.close")}>✕</button>
        </div>
        <div className="drawer-body">
          <p className="drawer-hint">{t("drawer.hint")}</p>
          {hiddenBlocks.length === 0 ? (
            <div className="sr-empty">{t("drawer.allShown")}</div>
          ) : (
            hiddenBlocks.map((b) => (
              <div className="drawer-item" key={b.id}>
                <span>{b.title}</span>
                <button type="button" className="btn-secondary" onClick={() => onAdd(b.id)}>{t("drawer.add")}</button>
              </div>
            ))
          )}
        </div>
        <div className="drawer-footer">
          <button type="button" className="btn-reset-layout" onClick={onReset}>{t("drawer.reset")}</button>
        </div>
      </aside>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/dashboard/components/LayoutDrawer.tsx
git commit -m "feat(i18n): migrate LayoutDrawer to react-i18next"
```

---

### Task 10: `ReportsSection.tsx` i18n 化（7 個內嵌圖表元件）

**Files:**
- Modify: `frontend/src/features/dashboard/components/ReportsSection.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `dashboard:charts.*`（Task 2）、`riskLabel`/`stageLabel`/`formatCompactMoney`/`formatDateTime`（Task 1）、`common:noData`
- Produces: `reportBlocks(reports: DashboardReports | null, onDrill: DrillFn, onSelectCustomer: (id, source) => void, t: TFunction, locale: string): DashboardBlock[]`（`DrillFn` 型別不變）

- [ ] **Step 1: 改寫 ReportsSection.tsx**

完整覆寫 `frontend/src/features/dashboard/components/ReportsSection.tsx`：

```tsx
import { useEffect, useState } from "react";
import type { TFunction } from "i18next";
import type { DashboardReports, DrilldownSource } from "../../../types";
import { formatCompactMoney, formatDateTime, riskLabel, stageLabel } from "../../../lib/format";
import { fetchDashboardReports } from "../../../api";
import type { DashboardBlock } from "../blockTypes";
import { LoadingCard } from "../blockTypes";

/** 圖表下鑽回呼型別。 */
export type DrillFn = (type: string, key: string, title: string) => void;

/**
 * 產生 7 個獨立的 CRM 報表圖表區塊（各自可拖拉與關閉）。非 React 元件，t/locale 由呼叫端傳入。
 * 函式級註解：reports 為 null 時各區塊以 LoadingCard 佔位；近期關鍵互動點列帶來源區塊跳客戶。
 *
 * @param reports 報表資料（可為 null）
 * @param onDrill 圖表下鑽回呼
 * @param onSelectCustomer 跳客戶回呼（帶來源區塊）
 * @param t react-i18next 的 t 函式
 * @param locale 目前語言，供 formatCompactMoney/formatDateTime 用
 * @returns 7 個報表區塊
 */
export function reportBlocks(
  reports: DashboardReports | null,
  onDrill: DrillFn,
  onSelectCustomer: (id: number, source: DrilldownSource) => void,
  t: TFunction,
  locale: string
): DashboardBlock[] {
  const loadingText = t("dashboard:loading");
  return [
    { id: "chart-pipeline", title: t("dashboard:charts.pipeline.title"), wide: true, render: () => reports ? <PipelineFunnel data={reports.pipelineByStage} onDrill={onDrill} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.pipeline.title")} wide loadingText={loadingText} /> },
    { id: "chart-forecast", title: t("dashboard:charts.forecast.title"), wide: true, render: () => reports ? <MonthlyForecastChart data={reports.monthlyForecast} onDrill={onDrill} t={t} /> : <LoadingCard title={t("dashboard:charts.forecast.title")} wide loadingText={loadingText} /> },
    { id: "chart-industry", title: t("dashboard:charts.industry.title"), render: () => reports ? <IndustryBreakdown data={reports.industryBreakdown} onDrill={onDrill} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.industry.title")} loadingText={loadingText} /> },
    { id: "chart-risk", title: t("dashboard:charts.risk.title"), render: () => reports ? <RiskBreakdown data={reports.riskBreakdown} onDrill={onDrill} t={t} /> : <LoadingCard title={t("dashboard:charts.risk.title")} loadingText={loadingText} /> },
    { id: "chart-renewal", title: t("dashboard:charts.renewal.title"), render: () => reports ? <RenewalForecast data={reports.renewalForecast} onDrill={onDrill} t={t} /> : <LoadingCard title={t("dashboard:charts.renewal.title")} loadingText={loadingText} /> },
    { id: "chart-leaderboard", title: t("dashboard:charts.leaderboard.title"), render: () => reports ? <OwnerLeaderboard data={reports.ownerLeaderboard} onDrill={onDrill} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.leaderboard.title")} loadingText={loadingText} /> },
    { id: "chart-activity", title: t("dashboard:charts.activity.title"), wide: true, render: () => reports ? <ActivityReportList data={reports.recentActivities} onSelectCustomer={(id) => onSelectCustomer(id, { from: "dashboard", section: t("dashboard:charts.activity.title"), blockId: "chart-activity" })} t={t} locale={locale} /> : <LoadingCard title={t("dashboard:charts.activity.title")} wide loadingText={loadingText} /> }
  ];
}

/** 漏斗呈現的階段順序(由上而下):早期機會在上、實際成交在最底。 */
const FUNNEL_STAGE_ORDER = ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON"];

/**
 * 銷售漏斗圖，以商機階段呈現 pipeline 金額與筆數。
 * 函式級註解：漏斗代表「商機推進」的轉化路徑,故上寬下窄、上方為早期機會(資格評估)、
 * 最底為實際成交(已成交)。「已流失」是掉出漏斗、非更深階段,故不納入漏斗(可在商機看板查看)。
 * 各層寬度由 CSS 階梯固定(funnel-layer-N),金額大小以橫向漸層填充比例直觀呈現。
 */
function PipelineFunnel({ data, onDrill, t, locale }: { data: DashboardReports["pipelineByStage"]; onDrill: DrillFn; t: TFunction; locale: string }) {
  // 來源切換:""=全部、INBOUND=主動上門、OUTBOUND=業務開發；非全部時 fetch 該來源的漏斗子集
  const [source, setSource] = useState<"" | "INBOUND" | "OUTBOUND" | "REFERRAL">("");
  const [stageData, setStageData] = useState(data);
  // props data 變動（重新載入）時同步
  useEffect(() => { setStageData(data); }, [data]);
  // 切換來源:全部用 props data，其餘 fetch /dashboard/reports?leadSource= 取 pipelineByStage
  useEffect(() => {
    if (!source) { setStageData(data); return; }
    let cancelled = false;
    fetchDashboardReports(source).then((r) => { if (!cancelled) setStageData(r.pipelineByStage); }).catch(() => {});
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  // 僅保留漏斗階段並依「資格評估→提案→議價→已成交」由上而下排序(排除已流失)
  const funnelData = FUNNEL_STAGE_ORDER
    .map((stage) => stageData.find((item) => item.stage === stage))
    .filter((item): item is DashboardReports["pipelineByStage"][number] => Boolean(item));

  // 取得漏斗各階段中的最大金額以作為比例計算基準，最小設為 1 避免除以零
  const max = Math.max(...funnelData.map((item) => item.amount), 1);

  // 各階段填充色(由早期到成交,綠色漸深;不含已流失)
  const colorConfigs = [
    { fill: "#14b8a6", bg: "rgba(15, 118, 110, 0.08)" }, // 資格評估：薄荷綠
    { fill: "#2dd4bf", bg: "rgba(13, 148, 136, 0.08)" }, // 提案：亮薄荷綠
    { fill: "#5eead4", bg: "rgba(13, 148, 136, 0.08)" }, // 議價：粉薄荷綠
    { fill: "#34d399", bg: "rgba(16, 185, 129, 0.08)" }  // 已成交：翡翠綠(漏斗最底=實際成交)
  ];

  // 來源切換分頁定義（value, i18n key）
  const sourceTabs: readonly [typeof source, string][] = [
    ["", "dashboard:charts.pipeline.sourceAll"],
    ["INBOUND", "dashboard:charts.pipeline.sourceInbound"],
    ["OUTBOUND", "dashboard:charts.pipeline.sourceOutbound"],
    ["REFERRAL", "dashboard:charts.pipeline.sourceReferral"]
  ];

  return (
    <article className="panel report-card wide" data-promo-chart="pipeline">
      <div className="panel-title">
        <h3>{t("dashboard:charts.pipeline.title")}</h3>
        <span>{t("dashboard:charts.pipeline.subtitle")}</span>
      </div>
      {/* 來源切換:全部 / 主動上門 / 業務開發 */}
      <div className="funnel-source-tabs">
        {sourceTabs.map(([v, labelKey]) => (
          <button
            type="button"
            key={v}
            className={`source-tab ${source === v ? "active" : ""}`}
            onClick={() => setSource(v)}
          >{t(labelKey)}</button>
        ))}
      </div>
      <div className="funnel-container">
        {funnelData.map((item, index) => {
          // 計算當前階段的金額比例，底限設為 6% 確保即使零元也有一點點點綴，上限 100%
          const amountPercent = Math.min(100, Math.max(6, (item.amount / max) * 100));

          // 取得該層專屬的色彩配置，重要物件取用
          const colors = colorConfigs[index] || colorConfigs[0];

          // 組裝 CSS 漸層背景：左側亮色代表已填充數據，右側半透明代表未填充容量
          const backgroundGradient = `linear-gradient(90deg, ${colors.fill} 0%, ${colors.fill} ${amountPercent}%, ${colors.bg} ${amountPercent}%, ${colors.bg} 100%)`;

          const stageText = t(stageLabel(item.stage));
          const hoverTitle = t("dashboard:charts.pipeline.hoverTitle", { stage: stageText, count: item.count }) +
            (item.avgDaysInStage != null ? t("dashboard:charts.pipeline.avgDaysSuffix", { days: item.avgDaysInStage }) : "") +
            (item.overdueCount ? t("dashboard:charts.pipeline.overdueSuffix", { count: item.overdueCount }) : "");
          const drillTitle = `${t("dashboard:charts.pipeline.title")} · ${stageText}`;

          return (
            <div
              className={`funnel-stage-wrapper funnel-layer-${index} clickable`}
              key={item.stage}
              style={{ background: backgroundGradient }}
              title={hoverTitle}
              role="button"
              tabIndex={0}
              onClick={() => onDrill("stage", item.stage, drillTitle)}
              onKeyDown={(e) => { if (e.key === "Enter") onDrill("stage", item.stage, drillTitle); }}
            >
              <div className="funnel-content">
                <strong>{stageText}</strong>
                <span>
                  {t("dashboard:charts.pipeline.countSuffix", { count: item.count })}
                  {item.avgDaysInStage != null && item.count > 0 ? t("dashboard:charts.pipeline.avgDaysInline", { days: item.avgDaysInStage }) : ""}
                  {item.overdueCount ? ` · ⚠${item.overdueCount}` : ""}
                </span>
                <b>{formatCompactMoney(item.amount, locale)}</b>
              </div>
            </div>
          );
        })}
      </div>
    </article>
  );
}

/**
 * 月度營收預測折線圖。
 */
function MonthlyForecastChart({ data, onDrill, t }: { data: DashboardReports["monthlyForecast"]; onDrill: DrillFn; t: TFunction }) {
  // 以總額為比例基準（總額 >= 加權，確保兩條線都在繪圖範圍內）
  const max = Math.max(...data.map((item) => item.totalAmount), 1);
  const xOf = (index: number) => 24 + (index * 320) / Math.max(data.length - 1, 1);
  const yOf = (value: number) => 150 - (value / max) * 110;
  const totalPoints = data.map((item, i) => `${xOf(i)},${yOf(item.totalAmount)}`).join(" ");
  const weightedPoints = data.map((item, i) => `${xOf(i)},${yOf(item.weightedAmount)}`).join(" ");
  return (
    <article className="panel report-card wide" data-promo-chart="forecast">
      <div className="panel-title"><h3>{t("dashboard:charts.forecast.title")}</h3><span>{t("dashboard:charts.forecast.subtitle")}</span></div>
      <svg className="line-chart" viewBox="0 0 368 180" role="img" aria-label={t("dashboard:charts.forecast.ariaLabel")}>
        {/* 總額 pipeline（實線） */}
        <polyline points={totalPoints} fill="none" stroke="#0f766e" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        {/* 機率加權預測（橘色虛線） */}
        <polyline points={weightedPoints} fill="none" stroke="#f59e0b" strokeWidth="3" strokeDasharray="6 4" strokeLinecap="round" strokeLinejoin="round" />
        {data.map((item, index) => (
          <circle key={item.label} cx={xOf(index)} cy={yOf(item.totalAmount)} r="5" fill="#102b40" />
        ))}
        {data.map((item, index) => (
          <text key={item.label} x={xOf(index)} y="172" textAnchor="middle">{item.label.slice(5)}</text>
        ))}
        {/* 透明點擊熱區：每月一條，方便點選下鑽 */}
        {data.map((item, index) => (
          <rect
            key={`hit-${item.label}`}
            className="chart-hit"
            x={xOf(index) - 16} y="0" width="32" height="180"
            onClick={() => onDrill("forecastMonth", item.label, `${t("dashboard:charts.forecast.title")} · ${item.label}`)}
          >
            <title>{t("dashboard:charts.forecast.hoverTitle", { month: item.label })}</title>
          </rect>
        ))}
      </svg>
    </article>
  );
}

/**
 * 產業分布長條圖。
 */
function IndustryBreakdown({ data, onDrill, t, locale }: { data: DashboardReports["industryBreakdown"]; onDrill: DrillFn; t: TFunction; locale: string }) {
  const top = [...data].sort((a, b) => b.amount - a.amount).slice(0, 8);
  const max = Math.max(...top.map((item) => item.amount), 1);
  return (
    <article className="panel report-card" data-promo-chart="industry">
      <div className="panel-title"><h3>{t("dashboard:charts.industry.title")}</h3><span>{t("dashboard:charts.industry.subtitle")}</span></div>
      <div className="bar-list">
        {top.map((item) => (
          <button type="button" className="bar-row clickable" key={item.label} onClick={() => onDrill("industry", item.label, t("dashboard:charts.industry.drillTitle", { label: item.label }))}>
            <span>{item.label}</span>
            <div><i style={{ width: `${Math.max(8, (item.amount / max) * 100)}%` }} /></div>
            <b>{formatCompactMoney(item.amount, locale)}</b>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 客戶風險結構甜甜圈替代圖。
 */
function RiskBreakdown({ data, onDrill, t }: { data: DashboardReports["riskBreakdown"]; onDrill: DrillFn; t: TFunction }) {
  const total = data.reduce((sum, item) => sum + item.value, 0) || 1;
  return (
    <article className="panel report-card" data-promo-chart="risk">
      <div className="panel-title"><h3>{t("dashboard:charts.risk.title")}</h3><span>{t("dashboard:charts.risk.subtitle")}</span></div>
      <div className="risk-report">
        {data.map((item) => {
          const label = t(riskLabel(item.label));
          return (
            <button type="button" className={`risk-chip clickable ${item.label.toLowerCase()}`} key={item.label} onClick={() => onDrill("risk", item.label, t("dashboard:charts.risk.drillTitle", { label }))}>
              <strong>{label}</strong>
              <b>{item.value}</b>
              <small>{Math.round((item.value / total) * 100)}%</small>
            </button>
          );
        })}
      </div>
    </article>
  );
}

/**
 * 續約預測圖表。
 */
function RenewalForecast({ data, onDrill, t }: { data: DashboardReports["renewalForecast"]; onDrill: DrillFn; t: TFunction }) {
  const top = data.slice(0, 8);
  const max = Math.max(...top.map((item) => item.count), 1);
  return (
    <article className="panel report-card" data-promo-chart="renewal">
      <div className="panel-title"><h3>{t("dashboard:charts.renewal.title")}</h3><span>{t("dashboard:charts.renewal.subtitle")}</span></div>
      <div className="renewal-bars">
        {top.map((item) => (
          <button type="button" className="renewal-bar clickable" key={item.label} title={t("dashboard:charts.renewal.hoverTitle", { month: item.label })} onClick={() => onDrill("renewalMonth", item.label, t("dashboard:charts.renewal.drillTitle", { label: item.label }))}>
            <span style={{ height: `${Math.max(18, (item.count / max) * 110)}px` }} />
            <small>{item.label.slice(5)}</small>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 業務排行榜報表。
 */
function OwnerLeaderboard({ data, onDrill, t, locale }: { data: DashboardReports["ownerLeaderboard"]; onDrill: DrillFn; t: TFunction; locale: string }) {
  return (
    <article className="panel report-card" data-promo-chart="leaderboard">
      <div className="panel-title"><h3>{t("dashboard:charts.leaderboard.title")}</h3><span>{t("dashboard:charts.leaderboard.subtitle")}</span></div>
      <div className="leaderboard">
        {data.map((owner, index) => (
          <button type="button" className="leader-row clickable" key={owner.ownerName} onClick={() => onDrill("owner", owner.ownerName, t("dashboard:charts.leaderboard.drillTitle", { label: owner.ownerName }))}>
            <b>{index + 1}</b>
            <span>{owner.ownerName}<small>{t("dashboard:charts.leaderboard.customersSuffix", { count: owner.customerCount })} / {t("dashboard:charts.leaderboard.highRiskSuffix", { count: owner.highRiskCount })}</small></span>
            <strong>{formatCompactMoney(owner.opportunityAmount, locale)}</strong>
          </button>
        ))}
      </div>
    </article>
  );
}

/**
 * 近期活動報表。
 */
function ActivityReportList({ data, onSelectCustomer, t, locale }: { data: DashboardReports["recentActivities"]; onSelectCustomer: (id: number) => void; t: TFunction; locale: string }) {
  return (
    <article className="panel report-card wide" data-promo-chart="activity">
      <div className="panel-title"><h3>{t("dashboard:charts.activity.title")}</h3><span>{t("dashboard:charts.activity.subtitle")}</span></div>
      <div className="activity-report">
        {data.map((activity) => (
          <button type="button" className="activity-item clickable" key={`${activity.customerId}-${activity.occurredAt}`} onClick={() => onSelectCustomer(activity.customerId)}>
            <strong>{activity.customerName}</strong>
            <span>{activity.type} / {formatDateTime(activity.occurredAt, locale, t("common:noData"))}</span>
            <p>{activity.content}</p>
          </button>
        ))}
      </div>
    </article>
  );
}
```

- [ ] **Step 2: 更新 DashboardPage.tsx 的 reportBlocks 呼叫**

修改 `frontend/src/features/dashboard/DashboardPage.tsx`，將：
```tsx
    ...reportBlocks(reports, openDrilldown, jumpToCustomer),
```
改為：
```tsx
    ...reportBlocks(reports, openDrilldown, jumpToCustomer, t, i18n.language),
```

此時 `fullCatalog` 五行呼叫應已全部帶上 `t`（與需要的 `i18n.language`），與 Task 5-9 逐步完成的簽章一致。

- [ ] **Step 3: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0（`fullCatalog` 五行呼叫全數更新完畢，此時應無殘留的舊簽章呼叫）

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/dashboard/components/ReportsSection.tsx frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(i18n): migrate ReportsSection to react-i18next"
```

---

### Task 11: `DashboardPage.tsx` 本體收尾 + 整合測試

**Files:**
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`
- Create: `frontend/src/features/dashboard/DashboardPage.test.tsx`

**Interfaces:**
- Consumes: `dashboard:topbar.*`/`dashboard:grid.*`/`dashboard:portfolio.*`（Task 2）、`useTranslation`（Task 5 已加入 hook）
- Produces: `DashboardPage` 對外行為不變（仍是路由頁面元件，無 props）

- [ ] **Step 1: 遷移 DashboardPage.tsx 自身文字**

修改 `frontend/src/features/dashboard/DashboardPage.tsx`，將 `openPortfolioAssessment` 函式內容：
```tsx
  function openPortfolioAssessment() {
    // 立即顯示 modal（loading=true），第一個 token 到達後轉為逐字渲染
    setReport({ open: true, title: "Portfolio 整體評估（全公司）", loading: true, streaming: true, markdown: "" });
    streamPortfolioAssessment(
      (chunk) => {
        if (chunk.type === "content" && chunk.delta) {
          setReport((prev) => prev
            ? { ...prev, loading: false, streaming: true, markdown: (prev.markdown ?? "") + chunk.delta }
            : prev);
        } else if (chunk.type === "callId" && chunk.callId) {
          setReport((prev) => prev ? { ...prev, callId: chunk.callId } : prev);
        }
      },
      () => setReport((prev) => prev ? { ...prev, loading: false, streaming: false } : prev),
      (err) => {
        console.error("Portfolio 整體評估串流失敗:", err);
        setReport({ open: true, title: "Portfolio 整體評估（全公司）", loading: false, streaming: false, markdown: "⚠️ 產生評估失敗，請稍後再試。" });
      }
    );
  }
```
改為：
```tsx
  function openPortfolioAssessment() {
    // 立即顯示 modal（loading=true），第一個 token 到達後轉為逐字渲染
    setReport({ open: true, title: t("dashboard:portfolio.assessTitle"), loading: true, streaming: true, markdown: "" });
    streamPortfolioAssessment(
      (chunk) => {
        if (chunk.type === "content" && chunk.delta) {
          setReport((prev) => prev
            ? { ...prev, loading: false, streaming: true, markdown: (prev.markdown ?? "") + chunk.delta }
            : prev);
        } else if (chunk.type === "callId" && chunk.callId) {
          setReport((prev) => prev ? { ...prev, callId: chunk.callId } : prev);
        }
      },
      () => setReport((prev) => prev ? { ...prev, loading: false, streaming: false } : prev),
      (err) => {
        console.error("Portfolio 整體評估串流失敗:", err);
        setReport({ open: true, title: t("dashboard:portfolio.assessTitle"), loading: false, streaming: false, markdown: t("dashboard:portfolio.assessError") });
      }
    );
  }
```

- [ ] **Step 2: 遷移 topbar 與 GridLayout 區塊文字**

修改 `frontend/src/features/dashboard/DashboardPage.tsx` 的 `return (...)` 區塊，將：
```tsx
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>儀表板</h2>
        </div>
        <div className="topbar-actions">
          {/* 募資課程問卷：儀表板首頁快捷入口，新分頁開啟 */}
          <a
            className="survey-link survey-link--topbar"
            href="https://survey.springai.world/"
            target="_blank"
            rel="noopener noreferrer"
          >
            📋 募資課程問卷
          </a>
          <button type="button" className="layout-btn" onClick={() => setDrawerOpen(true)}>⊞ 版面（隱藏 {hiddenBlocks.length}）</button>
          {isAdmin ? (
            <button type="button" className="btn-assess" onClick={handleGenerateDemo} disabled={generatingDemo}>
              {generatingDemo ? "產生中…" : "🧪 產生示範資料"}
            </button>
          ) : null}
          <button type="button" className="btn-assess topbar-assess" onClick={openPortfolioAssessment}>📊 整體評估（全公司）<AiBadge onDark /></button>
          <button type="button" className="btn-secondary" onClick={openPortfolioHistory}>🕘 AI 歷程</button>
        </div>
      </section>
```
改為：
```tsx
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>{t("dashboard:topbar.title")}</h2>
        </div>
        <div className="topbar-actions">
          {/* 募資課程問卷：儀表板首頁快捷入口，新分頁開啟 */}
          <a
            className="survey-link survey-link--topbar"
            href="https://survey.springai.world/"
            target="_blank"
            rel="noopener noreferrer"
          >
            {t("dashboard:topbar.surveyLink")}
          </a>
          <button type="button" className="layout-btn" onClick={() => setDrawerOpen(true)}>{t("dashboard:topbar.layoutButton", { count: hiddenBlocks.length })}</button>
          {isAdmin ? (
            <button type="button" className="btn-assess" onClick={handleGenerateDemo} disabled={generatingDemo}>
              {generatingDemo ? t("dashboard:topbar.generatingDemo") : t("dashboard:topbar.generateDemo")}
            </button>
          ) : null}
          <button type="button" className="btn-assess topbar-assess" onClick={openPortfolioAssessment}>{t("dashboard:topbar.portfolioAssess")}<AiBadge onDark /></button>
          <button type="button" className="btn-secondary" onClick={openPortfolioHistory}>{t("dashboard:topbar.aiHistory")}</button>
        </div>
      </section>
```

- [ ] **Step 3: 遷移拖拉手把、關閉按鈕與 AI 歷程 Modal 標題**

修改同檔，將：
```tsx
              <div className="block-toolbar">
                <span className="block-drag-handle" title="拖拉移動">⠿</span>
                <button type="button" className="block-close" title="關閉區塊" onClick={() => closeBlock(it.i)}>✕</button>
              </div>
```
改為：
```tsx
              <div className="block-toolbar">
                <span className="block-drag-handle" title={t("dashboard:grid.dragHandle")}>⠿</span>
                <button type="button" className="block-close" title={t("dashboard:grid.closeBlock")} onClick={() => closeBlock(it.i)}>✕</button>
              </div>
```

並將：
```tsx
      {portfolioHistoryOpen ? (
        <AiCallHistoryModal
          title="全公司評估 AI 歷程"
          calls={portfolioCalls}
          loading={portfolioCallsLoading}
          onClose={() => setPortfolioHistoryOpen(false)}
        />
      ) : null}
```
改為：
```tsx
      {portfolioHistoryOpen ? (
        <AiCallHistoryModal
          title={t("dashboard:portfolio.historyTitle")}
          calls={portfolioCalls}
          loading={portfolioCallsLoading}
          onClose={() => setPortfolioHistoryOpen(false)}
        />
      ) : null}
```

並將抽屜 `hiddenBlocks` 傳入 `LayoutDrawer` 的既有寫法（不變，`title` 已在 Task 5-10 各區塊產生器內翻譯完成，這裡只是原樣傳遞）：
```tsx
        <LayoutDrawer hiddenBlocks={hiddenBlocks.map((b) => ({ id: b.id, title: b.title }))} onAdd={addBlock} onReset={resetLayout} onClose={() => setDrawerOpen(false)} />
```
維持不變（無需修改，`b.title` 此時已是各 Task 5-10 產生的已翻譯字串）。

- [ ] **Step 4: 型別檢查 + build**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit && pnpm build`
Expected: 兩者皆 exit 0

- [ ] **Step 5: 寫 DashboardPage.test.tsx（頁面級整合測試）**

`frontend/src/features/dashboard/DashboardPage.test.tsx`：

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import i18n from "../../i18n";
import { DashboardPage } from "./DashboardPage";

// 隔離 auth：僅需 SALES 角色即可渲染主要區塊（不觸發 usage 區塊的 MANAGER/ADMIN 限定請求）
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: 1, displayName: "Sales", role: "SALES" } })
}));

// 隔離所有 API 呼叫，回傳最小可渲染資料，避免測試依賴真實後端
vi.mock("../../api", () => ({
  fetchDashboard: vi.fn().mockResolvedValue({ customerCount: 3, activeOpportunityCount: 2, opportunityAmount: 100000, highRiskCount: 1 }),
  fetchDashboardReports: vi.fn().mockResolvedValue({
    pipelineByStage: [], monthlyForecast: [], industryBreakdown: [], riskBreakdown: [],
    renewalForecast: [], ownerLeaderboard: [], recentActivities: []
  }),
  fetchDashboardLayout: vi.fn().mockResolvedValue([]),
  fetchRfm: vi.fn().mockResolvedValue([]),
  fetchSentimentRadar: vi.fn().mockResolvedValue({
    intentDistribution: [], sentimentTrend: [], highRiskInteractions: [], churnRadar: [], priorityCare: []
  }),
  fetchAiUsage: vi.fn().mockResolvedValue({ totalCalls: 0, totalTokens: 0, realCalls: 0, fallbackCalls: 0, adopted: 0, rejected: 0 }),
  fetchPortfolioCalls: vi.fn().mockResolvedValue([]),
  saveDashboardLayout: vi.fn().mockResolvedValue(undefined),
  generateDemoData: vi.fn().mockResolvedValue(undefined),
  fetchDrilldown: vi.fn().mockResolvedValue(null),
  streamPortfolioAssessment: vi.fn()
}));

describe("DashboardPage i18n", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("預設英文顯示頁面標題與 KPI 標籤", async () => {
    await i18n.changeLanguage("en");
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Customers")).toBeInTheDocument();
    });
    expect(screen.getByText("Active opportunities")).toBeInTheDocument();
    expect(screen.getByText("Pipeline amount")).toBeInTheDocument();
  });

  it("切換繁中顯示頁面標題與 KPI 標籤", async () => {
    await i18n.changeLanguage("zh-TW");
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "儀表板" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("客戶數")).toBeInTheDocument();
    });
    expect(screen.getByText("活躍商機")).toBeInTheDocument();
    expect(screen.getByText("高風險客戶")).toBeInTheDocument();
  });
});
```

- [ ] **Step 6: 執行確認通過**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm test -- DashboardPage`
Expected: PASS（兩案例皆過；若 KPI 標籤因非同步載入尚未出現，`waitFor` 已涵蓋）

- [ ] **Step 7: Step B 全量驗證**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm test && pnpm exec tsc --noEmit && pnpm build`
Expected: 全綠 + exit 0（涵蓋 Task 1-11 全部變更；`format.test.ts`、`DashboardPage.test.tsx`、既有 `LoginPage.test.tsx`/`AppShell.test.tsx`/`LanguageSwitcher.test.tsx`/`detect.test.ts` 皆需通過）

- [ ] **Step 8: Commit**

```bash
git add frontend/src/features/dashboard/DashboardPage.tsx frontend/src/features/dashboard/DashboardPage.test.tsx
git commit -m "feat(i18n): migrate DashboardPage to react-i18next (Step B complete)"
```

---

**Step B（dashboard）至此完成。**

---

## Step C: customers feature

### Task 12: 建立 customers namespace 資源、註冊，並補齊 common.json 通用動作字串

**Files:**
- Create: `frontend/src/i18n/locales/en/customers.json`
- Create: `frontend/src/i18n/locales/zh-TW/customers.json`
- Modify: `frontend/src/i18n/index.ts`
- Modify: `frontend/src/i18n/locales/en/common.json`
- Modify: `frontend/src/i18n/locales/zh-TW/common.json`

**Interfaces:**
- Consumes: 無
- Produces: `customers` namespace 完整 key 集合（Task 13-27 消費）；`common:actions.*` 通用動詞（跨 modal 共用，避免 15 個檔案各自重複「編輯/刪除/儲存/取消」翻譯）

- [ ] **Step 1: common.json 補通用動作字串（en）**

修改 `frontend/src/i18n/locales/en/common.json`，在 `"pagination"` 之後加入：
```jsonc
  "actions": { "edit": "Edit", "delete": "Delete", "save": "Save", "cancel": "Cancel", "close": "Close", "add": "Add" },
```

- [ ] **Step 2: common.json 補通用動作字串（zh-TW）**

修改 `frontend/src/i18n/locales/zh-TW/common.json`，在 `"pagination"` 之後加入：
```jsonc
  "actions": { "edit": "編輯", "delete": "刪除", "save": "儲存", "cancel": "取消", "close": "關閉", "add": "新增" },
```

- [ ] **Step 3: 建立英文資源**

`frontend/src/i18n/locales/en/customers.json`：

```json
{
  "topbar": { "title": "Customer Workbench", "searchPlaceholder": "Name / Email / Phone / Tax ID" },
  "filters": {
    "allIndustries": "All industries", "allOwners": "All owners", "allStatuses": "All statuses",
    "allRisk": "All risk levels", "renewalFrom": "Renewal due from", "renewalTo": "Renewal due to",
    "search": "Search", "clear": "Clear"
  },
  "actionBar": {
    "aiSuggestions": "✨ AI work suggestions", "addCustomer": "+ Add customer", "businessCard": "📇 Business card intake",
    "addInteraction": "+ Add interaction", "addOpportunity": "+ Add opportunity", "scheduleCall": "☎ Schedule call",
    "meetingCopilot": "🎙 Meeting copilot", "followUpEmail": "✉️ AI follow-up email", "stakeholderMap": "🕸 Stakeholder map"
  },
  "breadcrumb": { "dashboard": "Dashboard", "customerFallback": "Customer" },
  "list": { "title": "Customer list", "countSuffix": "{{count}}", "emptyTitle": "No customers match the filters", "emptyHint": "Adjust the search or clear the filters" },
  "pagination": { "pageOfWithTotal": "{{page}} / {{total}} (total {{count}})" },
  "detail": {
    "empty": "No customer selected", "loadingAria": "Loading customer detail", "updating": "Updating...",
    "kpiAmount": "Opportunity amount", "kpiRenewal": "Renewal due", "kpiLastInteraction": "Last interaction", "kpiStatus": "Status",
    "assess": "🩺 Overall assessment", "askAi": "💬 Ask AI assistant", "aiHistory": "🧭 AI history", "editCustomer": "✏️ Edit customer", "deleteCustomer": "Delete customer"
  },
  "enums": { "status": { "ACTIVE": "Active", "INACTIVE": "Inactive", "LEVERAGED": "Key account" } },
  "confirm": {
    "deleteCustomer": "Delete this customer? This cannot be undone.",
    "deleteContact": "Delete contact \"{{name}}\"? This cannot be undone.",
    "deleteInteraction": "Delete this interaction record? This cannot be undone.",
    "deleteOpportunity": "Delete opportunity \"{{name}}\"? This cannot be undone."
  },
  "assessment": { "title": "Overall assessment — {{name}}", "error": "⚠️ Failed to generate assessment, please try again later.", "riskMeta": "Churn risk {{churn}} · Renewal delay risk {{renewal}}" },
  "contacts": { "title": "Contacts", "add": "+ Add contact", "empty": "No contacts yet", "name": "Name", "title_": "Title", "email": "Email" },
  "contactModal": { "editTitle": "Edit contact", "addTitle": "Add contact" },
  "opportunityBoard": { "title": "Opportunity board", "subtitle": "By stage", "intelligence": "Opportunity intelligence", "editOpportunity": "Edit opportunity", "deleteOpportunity": "Delete opportunity", "ownerPrefix": "Owner: " },
  "timeline": {
    "title": "Interaction timeline", "recentMonths": "Last {{months}} months", "hiddenSuffix": " · {{count}} earlier",
    "empty": "No interactions in the last {{months}} months.", "monthTick": "{{month}}",
    "hint": "Click a dot on the timeline to view that interaction (color = sentiment)."
  },
  "upcoming": {
    "title": "This week's follow-ups", "aheadDays": "Next {{days}} days", "empty": "No interactions, renewals, or opportunities due in the next {{days}} days.",
    "kindInteraction": "Upcoming interaction", "kindRenewal": "Renewal due", "kindOpportunity": "Opportunity closing",
    "renewalLabel": "Contract renewal due", "interactionLabel": "{{type}}: {{content}}", "opportunityLabel": "{{name}} ({{stage}} · {{amount}})"
  },
  "form": {
    "name": "Name", "email": "Email", "phone": "Phone", "taxId": "Tax ID", "industry": "Industry", "owner": "Owner",
    "phonePlaceholder": "0912345678", "taxIdPlaceholder": "12345678", "selectIndustry": "Select industry", "noAssignableOwner": "No assignable owner",
    "ownerSelf": "{{name}} (me)", "contractStart": "Contract start date", "contractEnd": "Contract end date", "renewalDue": "Renewal due date",
    "type": "Type", "occurredAt": "Time", "content": "Content", "opportunityName": "Opportunity name", "opportunityNamePlaceholder": "e.g. Smart factory license expansion",
    "stage": "Stage", "amount": "Amount", "expectedCloseDate": "Expected close date", "leadSource": "Source", "probability": "Win probability (%)",
    "amountPlaceholder": "e.g. 1500000", "probabilityPlaceholder": "Leave blank to use stage default", "closeReason": "Reason", "closeReasonNote": "Note", "closeReasonNotePlaceholder": "Optional", "actualCloseDate": "Actual close date"
  },
  "addCustomerModal": { "title": "Add customer", "submit": "Create" },
  "editCustomerModal": { "title": "Edit customer — {{name}}" },
  "addInteractionModal": { "title": "Add interaction — {{name}}", "submit": "Add" },
  "editInteractionModal": { "title": "Edit interaction" },
  "addOpportunityModal": { "title": "Add opportunity — {{name}}", "submit": "Add" },
  "editOpportunityModal": { "title": "Edit opportunity — {{name}}" },
  "closeOpportunityModal": { "wonTitle": "Mark as won", "lostTitle": "Mark as lost", "submit": "Confirm" },
  "enumsInteractionType": { "PHONE": "Phone", "MEETING": "Meeting", "EMAIL": "Email", "SUPPORT_TICKET": "Support ticket" },
  "enumsOpportunityType": { "NEW_BUSINESS": "New business", "RENEWAL": "Renewal" },
  "closeReasonsShort": {
    "won": { "WON_PRICE": "Price", "WON_FEATURE": "Feature", "WON_RELATIONSHIP": "Relationship", "WON_TIMING": "Timing" },
    "lost": { "LOST_PRICE": "Too expensive", "LOST_COMPETITOR": "Lost to competitor", "LOST_NO_BUDGET": "No budget", "LOST_NO_DECISION": "No decision", "LOST_NO_RESPONSE": "No response" }
  },
  "aiHistory": {
    "title": "AI history — {{name}}", "subtitle": "Call history and Agent decision steps", "callsHeading": "AI call history ({{count}})",
    "loading": "Loading", "empty": "No AI calls for this customer yet. Try \"Overall assessment\" or \"Ask AI assistant\".",
    "fallbackMode": "Template fallback", "traceHeading": "Agent decision trace",
    "traceIntro": "How the AI assistant retrieved data, assessed risk, and produced its recommendation, step by step.",
    "traceEmpty": "No Agent decision trace yet.",
    "callTypes": { "CHAT": "Chat", "ASSESSMENT": "Overall assessment", "PORTFOLIO": "Portfolio assessment" }
  }
}
```

- [ ] **Step 4: 建立繁中資源**

`frontend/src/i18n/locales/zh-TW/customers.json`：

```json
{
  "topbar": { "title": "客戶工作台", "searchPlaceholder": "名稱 / Email / 電話 / 統編" },
  "filters": {
    "allIndustries": "全部產業", "allOwners": "全部業務", "allStatuses": "全部狀態",
    "allRisk": "全部風險", "renewalFrom": "續約到期日(起)", "renewalTo": "續約到期日(迄)",
    "search": "搜尋", "clear": "清除"
  },
  "actionBar": {
    "aiSuggestions": "✨ AI 工作建議", "addCustomer": "+ 新增客戶", "businessCard": "📇 名片建檔",
    "addInteraction": "+ 新增互動", "addOpportunity": "+ 新增商機", "scheduleCall": "☎ 安排電話",
    "meetingCopilot": "🎙 會議 Copilot", "followUpEmail": "✉️ AI 跟進信", "stakeholderMap": "🕸 決策鏈"
  },
  "breadcrumb": { "dashboard": "儀表板", "customerFallback": "客戶" },
  "list": { "title": "客戶列表", "countSuffix": "{{count}} 筆", "emptyTitle": "查無符合條件的客戶", "emptyHint": "請調整搜尋條件或清除篩選" },
  "pagination": { "pageOfWithTotal": "{{page}} / {{total}}（共 {{count}} 筆）" },
  "detail": {
    "empty": "尚未選取客戶", "loadingAria": "載入客戶詳情中", "updating": "資料更新中...",
    "kpiAmount": "商機金額", "kpiRenewal": "合約到期", "kpiLastInteraction": "最近互動", "kpiStatus": "客戶狀態",
    "assess": "🩺 整體評估", "askAi": "💬 詢問 AI 助理", "aiHistory": "🧭 AI 歷程", "editCustomer": "✏️ 編輯客戶", "deleteCustomer": "刪除客戶"
  },
  "enums": { "status": { "ACTIVE": "使用中", "INACTIVE": "停用", "LEVERAGED": "重點客戶" } },
  "confirm": {
    "deleteCustomer": "確定刪除此客戶?此動作無法復原。",
    "deleteContact": "確定刪除聯絡人「{{name}}」?此動作無法復原。",
    "deleteInteraction": "確定刪除此互動紀錄?此動作無法復原。",
    "deleteOpportunity": "確定刪除商機「{{name}}」?此動作無法復原。"
  },
  "assessment": { "title": "整體評估 — {{name}}", "error": "⚠️ 產生評估失敗，請稍後再試。", "riskMeta": "流失風險 {{churn}} · 續約延遲 {{renewal}}" },
  "contacts": { "title": "聯絡人", "add": "+ 新增聯絡人", "empty": "尚無聯絡人", "name": "姓名", "title_": "職稱", "email": "Email" },
  "contactModal": { "editTitle": "編輯聯絡人", "addTitle": "新增聯絡人" },
  "opportunityBoard": { "title": "商機看板", "subtitle": "依階段分欄", "intelligence": "商機智能", "editOpportunity": "編輯商機", "deleteOpportunity": "刪除商機", "ownerPrefix": "負責：" },
  "timeline": {
    "title": "互動時間線", "recentMonths": "近 {{months}} 個月", "hiddenSuffix": " · 另有 {{count}} 筆較早",
    "empty": "近 {{months}} 個月內無互動紀錄。", "monthTick": "{{month}}月",
    "hint": "點時間軸上的色點查看該次互動內容（顏色代表情緒）。"
  },
  "upcoming": {
    "title": "本週待跟進", "aheadDays": "未來 {{days}} 天", "empty": "未來 {{days}} 天沒有排定的互動、續約或商機到期。",
    "kindInteraction": "即將互動", "kindRenewal": "續約到期", "kindOpportunity": "商機成交",
    "renewalLabel": "合約續約到期日", "interactionLabel": "{{type}}：{{content}}", "opportunityLabel": "{{name}}（{{stage}}・{{amount}}）"
  },
  "form": {
    "name": "名稱", "email": "Email", "phone": "電話", "taxId": "統編", "industry": "產業", "owner": "負責業務",
    "phonePlaceholder": "0912345678", "taxIdPlaceholder": "12345678", "selectIndustry": "請選擇產業", "noAssignableOwner": "無可指派業務",
    "ownerSelf": "{{name}}（我）", "contractStart": "合約起始日", "contractEnd": "合約到期日", "renewalDue": "續約日",
    "type": "類型", "occurredAt": "時間", "content": "內容", "opportunityName": "商機名稱", "opportunityNamePlaceholder": "例:智慧工廠擴充授權",
    "stage": "階段", "amount": "金額(元)", "expectedCloseDate": "預計成交日", "leadSource": "來源", "probability": "成交機率(%)",
    "amountPlaceholder": "例:1500000", "probabilityPlaceholder": "留空則依階段預設", "closeReason": "原因", "closeReasonNote": "備註", "closeReasonNotePlaceholder": "選填", "actualCloseDate": "實際成交日"
  },
  "addCustomerModal": { "title": "新增客戶", "submit": "建立" },
  "editCustomerModal": { "title": "編輯客戶 — {{name}}" },
  "addInteractionModal": { "title": "新增互動 — {{name}}", "submit": "新增" },
  "editInteractionModal": { "title": "編輯互動" },
  "addOpportunityModal": { "title": "新增商機 — {{name}}", "submit": "新增" },
  "editOpportunityModal": { "title": "編輯商機 — {{name}}" },
  "closeOpportunityModal": { "wonTitle": "成交結案", "lostTitle": "失單結案", "submit": "確認結案" },
  "enumsInteractionType": { "PHONE": "電話", "MEETING": "會議", "EMAIL": "Email", "SUPPORT_TICKET": "客服工單" },
  "enumsOpportunityType": { "NEW_BUSINESS": "新單", "RENEWAL": "續約" },
  "closeReasonsShort": {
    "won": { "WON_PRICE": "價格", "WON_FEATURE": "功能", "WON_RELATIONSHIP": "關係", "WON_TIMING": "時機" },
    "lost": { "LOST_PRICE": "價格太高", "LOST_COMPETITOR": "輸給競品", "LOST_NO_BUDGET": "無預算", "LOST_NO_DECISION": "未決策", "LOST_NO_RESPONSE": "無回應" }
  },
  "aiHistory": {
    "title": "AI 歷程 — {{name}}", "subtitle": "歷次 AI 呼叫與 Agent 決策步驟", "callsHeading": "AI 呼叫歷史（{{count}}）",
    "loading": "載入中", "empty": "此客戶尚無 AI 呼叫紀錄。點「整體評估」或「詢問 AI 助理」後即會記錄。",
    "fallbackMode": "樣板 fallback", "traceHeading": "Agent 決策歷程",
    "traceIntro": "AI 助理分析此客戶的決策步驟,顯示如何一步步檢索資料、評估風險並產生建議。",
    "traceEmpty": "尚無 Agent 決策歷程。",
    "callTypes": { "CHAT": "對話", "ASSESSMENT": "整體評估", "PORTFOLIO": "Portfolio 評估" }
  }
}
```

- [ ] **Step 5: 在 i18n/index.ts 註冊 customers namespace**

修改 `frontend/src/i18n/index.ts`：

(a) 於 import 區塊 `import zhTWDashboard from "./locales/zh-TW/dashboard.json";` 之後加入：
```ts
import enCustomers from "./locales/en/customers.json";
import zhTWCustomers from "./locales/zh-TW/customers.json";
```

(b) `resources` 物件由：
```ts
    resources: {
      en: { common: en, dashboard: enDashboard },
      "zh-TW": { common: zhTW, dashboard: zhTWDashboard }
    },
```
改為：
```ts
    resources: {
      en: { common: en, dashboard: enDashboard, customers: enCustomers },
      "zh-TW": { common: zhTW, dashboard: zhTWDashboard, customers: zhTWCustomers }
    },
```

- [ ] **Step 6: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 7: Commit**

```bash
git add frontend/src/i18n/locales/en/customers.json frontend/src/i18n/locales/zh-TW/customers.json frontend/src/i18n/index.ts frontend/src/i18n/locales/en/common.json frontend/src/i18n/locales/zh-TW/common.json
git commit -m "feat(i18n): add customers namespace resources and common actions"
```

---

> **設計備註（適用 Task 13-27）**：customers 目錄下所有元件皆為 React 元件（非 dashboard 那種被純函式呼叫的「區塊產生器」），因此每個元件可直接呼叫 `useTranslation()`，不需要像 dashboard 一樣把 `t`/`locale` 一路往上傳、也不需擔心父子元件簽章耦合順序——各 Task 彼此獨立，可任意順序完成，不會互相破壞編譯。

### Task 13: `CustomerList.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/CustomerList.tsx`

**Interfaces:**
- Consumes: `customers:list.*`（Task 12）、`riskLabel`（Task 1，需 `t()` 包裹）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 CustomerList.tsx**

完整覆寫 `frontend/src/features/customers/components/CustomerList.tsx`：

```tsx
import { useTranslation } from "react-i18next";
import type { CustomerSummary } from "../../../types";
import { riskLabel } from "../../../lib/format";

/**
 * 客戶列表，支援選取客戶與空結果提示。
 */
export function CustomerList({ customers, selectedId, onSelect, loading }: { customers: CustomerSummary[]; selectedId?: number; onSelect: (id: number) => void; loading?: boolean }) {
  const { t } = useTranslation(["customers", "common"]);
  return (
    <section className="panel customer-list">
      <div className="panel-title">
        <h3>{t("customers:list.title")}</h3>
        <span>{t("customers:list.countSuffix", { count: customers.length })}</span>
      </div>
      {loading ? (
        <div className="skeleton-list">
          {[1, 2, 3].map((n) => <div className="skeleton-row" key={n} />)}
        </div>
      ) : customers.length === 0 ? (
        <div className="empty-state-box">
          <p>{t("customers:list.emptyTitle")}</p>
          <small>{t("customers:list.emptyHint")}</small>
        </div>
      ) : (
        customers.map((customer) => (
          <button className={customer.id === selectedId ? "customer-row active" : "customer-row"} type="button" onClick={() => onSelect(customer.id)} key={customer.id}>
            <span className={`risk-dot ${customer.riskLevel.toLowerCase()}`} />
            <div>
              <strong>{customer.name}</strong>
              <small>{customer.industry} / {customer.ownerName}</small>
            </div>
            <em>{t(riskLabel(customer.riskLevel))}</em>
          </button>
        ))
      )}
    </section>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/CustomerList.tsx
git commit -m "feat(i18n): migrate CustomerList to react-i18next"
```

---

### Task 14: `Pagination.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/Pagination.tsx`

**Interfaces:**
- Consumes: `customers:pagination.pageOfWithTotal`（Task 12）、`common:pagination.prev/next`（Task 3）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 Pagination.tsx**

完整覆寫 `frontend/src/features/customers/components/Pagination.tsx`：

```tsx
import { useTranslation } from "react-i18next";

/**
 * 分頁控制元件。
 */
export function Pagination({ page, totalPages, totalElements, onPageChange }: { page: number; totalPages: number; totalElements: number; onPageChange: (p: number) => void }) {
  const { t } = useTranslation(["customers", "common"]);
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button type="button" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>{t("common:pagination.prev")}</button>
      <span>{t("customers:pagination.pageOfWithTotal", { page: page + 1, total: totalPages, count: totalElements })}</span>
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>{t("common:pagination.next")}</button>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/Pagination.tsx
git commit -m "feat(i18n): migrate customers Pagination to react-i18next"
```

---

### Task 15: `Timeline.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/Timeline.tsx`

**Interfaces:**
- Consumes: `customers:timeline.*`（Task 12）、`formatDateTime(value, locale, noDataLabel)`/`intentLabel`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 Timeline.tsx**

完整覆寫 `frontend/src/features/customers/components/Timeline.tsx`：

```tsx
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { CustomerDetail } from "../../../types";
import { formatDateTime, intentLabel } from "../../../lib/format";

/** 預設呈現的時間窗(月):近 N 個月。 */
const MONTHS_WINDOW = 6;

/**
 * 將情緒值（POSITIVE / NEUTRAL / NEGATIVE）轉成色點 CSS class（SP6）。
 */
function sentimentClass(sentiment: string | null | undefined) {
  const map: Record<string, string> = { POSITIVE: "pos", NEUTRAL: "neu", NEGATIVE: "neg" };
  return map[sentiment || ""] || "neu";
}

/**
 * 互動時間線（橫向時間軸）：把近 6 個月的互動依日期定位在一條水平時間軸上,
 * 以情緒顏色標示;點選軸上的點即展開該次互動內容並提供編輯 / 刪除。
 * 函式級註解：相較原本逐列清單,橫向軸能一眼看出互動的疏密與分布,且不會因筆數多而過長。
 *
 * @param interactions 互動清單
 * @param onEdit 點擊編輯某互動 callback
 * @param onDelete 點擊刪除某互動 callback
 */
export function Timeline({
  interactions,
  onEdit,
  onDelete
}: {
  interactions: CustomerDetail["interactions"];
  onEdit: (interaction: CustomerDetail["interactions"][number]) => void;
  onDelete: (interaction: CustomerDetail["interactions"][number]) => void;
}) {
  const { t, i18n } = useTranslation(["customers", "common"]);
  // 目前選取查看的互動 id（null 表示未選）
  const [selectedId, setSelectedId] = useState<number | null>(null);

  // 計算時間窗、軸上各互動的水平位置(left%)與月份刻度
  const { dots, ticks, hiddenCount } = useMemo(() => {
    const end = new Date();
    const start = new Date();
    start.setMonth(start.getMonth() - MONTHS_WINDOW);
    const span = end.getTime() - start.getTime();
    // 落在時間窗內的互動 → 換算 left 百分比
    const dots = interactions
      .map((i) => ({ item: i, t: new Date(i.occurredAt).getTime() }))
      .filter((d) => d.t >= start.getTime() && d.t <= end.getTime())
      .map((d) => ({ item: d.item, left: ((d.t - start.getTime()) / span) * 100 }));
    // 月份刻度:自 start 後的每個月初
    const ticks: { left: number; label: string }[] = [];
    const cursor = new Date(start.getFullYear(), start.getMonth() + 1, 1);
    while (cursor.getTime() <= end.getTime()) {
      ticks.push({ left: ((cursor.getTime() - start.getTime()) / span) * 100, label: t("customers:timeline.monthTick", { month: cursor.getMonth() + 1 }) });
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return { dots, ticks, hiddenCount: interactions.length - dots.length };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [interactions]);

  // 目前選取的互動(供下方詳情卡)
  const selected = interactions.find((i) => i.id === selectedId) ?? null;

  return (
    <section className="panel">
      <div className="panel-title">
        <h3>{t("customers:timeline.title")}</h3>
        <span>{t("customers:timeline.recentMonths", { months: MONTHS_WINDOW })}{hiddenCount > 0 ? t("customers:timeline.hiddenSuffix", { count: hiddenCount }) : ""}</span>
      </div>

      {dots.length === 0 ? (
        <p className="trace-empty">{t("customers:timeline.empty", { months: MONTHS_WINDOW })}</p>
      ) : (
        <>
          {/* 橫向時間軸:水平線 + 月份刻度 + 互動色點 */}
          <div className="tl-axis">
            <div className="tl-line" />
            {ticks.map((tick, i) => (
              <span className="tl-tick" key={i} style={{ left: `${tick.left}%` }}>{tick.label}</span>
            ))}
            {dots.map((d) => (
              <button
                type="button"
                key={d.item.id}
                className={`tl-dot ${sentimentClass(d.item.sentiment)} ${selectedId === d.item.id ? "sel" : ""}`}
                style={{ left: `${d.left}%` }}
                title={`${formatDateTime(d.item.occurredAt, i18n.language, t("common:noData"))}｜${d.item.type}`}
                onClick={() => setSelectedId(selectedId === d.item.id ? null : d.item.id)}
                aria-label={`${formatDateTime(d.item.occurredAt, i18n.language, t("common:noData"))} ${d.item.type}`}
              />
            ))}
          </div>

          {/* 選取後顯示該次互動的完整內容與操作;未選時提示 */}
          {selected ? (
            <article className="tl-detail">
              <span className="timeline-meta">
                {selected.sentiment ? <i className={`sr-dot ${sentimentClass(selected.sentiment)}`} title={selected.sentiment} /> : null}
                {selected.type}
                {intentLabel(selected.intent) ? <span className="sr-tag">{t(intentLabel(selected.intent))}</span> : null}
              </span>
              <strong>{formatDateTime(selected.occurredAt, i18n.language, t("common:noData"))}</strong>
              <p>{selected.content}</p>
              <div className="row-actions">
                <button type="button" className="row-btn" onClick={() => onEdit(selected)}>{t("common:actions.edit")}</button>
                <button type="button" className="row-btn row-btn-danger" onClick={() => onDelete(selected)}>{t("common:actions.delete")}</button>
              </div>
            </article>
          ) : (
            <p className="tl-hint">{t("customers:timeline.hint")}</p>
          )}
        </>
      )}
    </section>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/Timeline.tsx
git commit -m "feat(i18n): migrate Timeline to react-i18next"
```

---

### Task 16: `CloseOpportunityModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/CloseOpportunityModal.tsx`

**Interfaces:**
- Consumes: `customers:closeOpportunityModal.*`/`customers:closeReasonsShort.*`/`customers:form.*`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 CloseOpportunityModal.tsx**

完整覆寫 `frontend/src/features/customers/components/CloseOpportunityModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";

/**
 * 結案原因 Modal：商機階段選到 CLOSED_WON / CLOSED_LOST 時，收集輸贏原因、備註與實際成交日。
 * 函式級註解：依 won/lost 顯示對應的 closeReason 子集；實際成交日預設本地今天（避免 UTC 位移）。
 * 這裡的原因短標籤（closeReasonsShort）是給選單用的精簡版，與 format.ts 的 closeReasonLabel
 * （完整版，如「贏-價格」，用於其他頁面顯示已結案商機）是兩組獨立維護的翻譯，故意不共用。
 *
 * @param stage 結案階段（CLOSED_WON 或 CLOSED_LOST）
 * @param onSubmit 送出 callback（回傳 closeReason / closeReasonNote / actualCloseDate）
 * @param onClose 關閉 callback
 */
export function CloseOpportunityModal({ stage, onSubmit, onClose }: {
  stage: "CLOSED_WON" | "CLOSED_LOST";
  onSubmit: (data: { closeReason: string; closeReasonNote: string; actualCloseDate: string }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("customers");
  const won = stage === "CLOSED_WON";
  // 依輸贏顯示對應原因子集
  const options: [string, string][] = won
    ? [["WON_PRICE", t("closeReasonsShort.won.WON_PRICE")], ["WON_FEATURE", t("closeReasonsShort.won.WON_FEATURE")], ["WON_RELATIONSHIP", t("closeReasonsShort.won.WON_RELATIONSHIP")], ["WON_TIMING", t("closeReasonsShort.won.WON_TIMING")]]
    : [["LOST_PRICE", t("closeReasonsShort.lost.LOST_PRICE")], ["LOST_COMPETITOR", t("closeReasonsShort.lost.LOST_COMPETITOR")], ["LOST_NO_BUDGET", t("closeReasonsShort.lost.LOST_NO_BUDGET")], ["LOST_NO_DECISION", t("closeReasonsShort.lost.LOST_NO_DECISION")], ["LOST_NO_RESPONSE", t("closeReasonsShort.lost.LOST_NO_RESPONSE")]];
  // 本地今天日期（yyyy-MM-dd），避免 toISOString 的 UTC 位移；en-CA 純為取格式，非顯示用途，不需 i18n 化
  const today = new Date().toLocaleDateString("en-CA");

  /** 解析表單並回傳結案資料；備註空字串、日期預設今天。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      closeReason: String(fd.get("closeReason")),
      closeReasonNote: String(fd.get("closeReasonNote") || ""),
      actualCloseDate: String(fd.get("actualCloseDate") || today)
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{won ? t("closeOpportunityModal.wonTitle") : t("closeOpportunityModal.lostTitle")}</h3>
        <label>{t("form.closeReason")}
          <select name="closeReason" required defaultValue={options[0][0]}>
            {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </label>
        <label>{t("form.closeReasonNote")} <input name="closeReasonNote" type="text" placeholder={t("form.closeReasonNotePlaceholder")} /></label>
        <label>{t("form.actualCloseDate")} <input name="actualCloseDate" type="date" defaultValue={today} /></label>
        <div className="modal-actions">
          <button type="submit">{t("closeOpportunityModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/CloseOpportunityModal.tsx
git commit -m "feat(i18n): migrate CloseOpportunityModal to react-i18next"
```

---

### Task 17: `OpportunityBoard.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/OpportunityBoard.tsx`

**Interfaces:**
- Consumes: `customers:opportunityBoard.*`（Task 12）、`stageLabel`/`formatMoney`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 OpportunityBoard.tsx**

完整覆寫 `frontend/src/features/customers/components/OpportunityBoard.tsx`：

```tsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import type { CustomerDetail } from "../../../types";
import { formatMoney, stageLabel } from "../../../lib/format";
import { updateOpportunityStage } from "../../../api";
import { CloseOpportunityModal } from "./CloseOpportunityModal";

/** 商機階段全集(依銷售流程排序);看板依此分欄,下拉亦用此清單。 */
const STAGES = ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON", "CLOSED_LOST"] as const;

/** 結案階段集合（選到時需先收集輸贏原因）。 */
const CLOSE_STAGES = ["CLOSED_WON", "CLOSED_LOST"];

/**
 * 商機看板:依階段分欄呈現 pipeline,改階段以卡片下拉選單操作(可靠、支援觸控與鍵盤)。
 * 選到結案階段(CLOSED_WON/CLOSED_LOST)時先彈出結案原因 Modal 收集 closeReason,再送出。
 *
 * @param opportunities 商機清單
 * @param onStageChange 階段變更(樂觀更新本地)callback
 * @param onEdit 點擊編輯 callback
 * @param onDelete 點擊刪除 callback
 */
export function OpportunityBoard({ customerId, opportunities, onStageChange, onEdit, onDelete }: {
  customerId: number;
  opportunities: CustomerDetail["opportunities"];
  onStageChange: (opportunityId: number, newStage: string) => void;
  onEdit: (opportunity: CustomerDetail["opportunities"][number]) => void;
  onDelete: (opportunity: CustomerDetail["opportunities"][number]) => void;
}) {
  const { t, i18n } = useTranslation(["customers", "common"]);
  /** 待結案的商機（選到結案階段時暫存,待 Modal 填原因後送出）。 */
  const [pendingClose, setPendingClose] = useState<{ id: number; stage: "CLOSED_WON" | "CLOSED_LOST"; current: string } | null>(null);
  const navigate = useNavigate();

  /**
   * 樂觀更新本地並呼叫 API;失敗回滾。close 為結案資訊(選填)。
   *
   * @param opportunityId 商機 ID
   * @param newStage 新階段
   * @param currentStage 原階段(回滾用)
   * @param close 結案資訊(closeReason/closeReasonNote/actualCloseDate),非結案時不帶
   */
  function commitStage(opportunityId: number, newStage: string, currentStage: string,
                       close?: { closeReason: string; closeReasonNote: string; actualCloseDate: string }) {
    onStageChange(opportunityId, newStage);
    updateOpportunityStage(opportunityId, newStage, close).catch(() => {
      // API 失敗回滾本地狀態,避免畫面與後端不一致
      onStageChange(opportunityId, currentStage);
    });
  }

  /**
   * 變更商機階段:結案階段先開 Modal 收原因,其餘直接送出。
   *
   * @param opportunityId 商機 ID
   * @param newStage 新階段
   * @param currentStage 原階段(回滾用)
   */
  function changeStage(opportunityId: number, newStage: string, currentStage: string) {
    if (newStage === currentStage) return;
    if (CLOSE_STAGES.includes(newStage)) {
      // 結案階段:暫存待 Modal 填原因(此時不改本地,select 維持原值)
      setPendingClose({ id: opportunityId, stage: newStage as "CLOSED_WON" | "CLOSED_LOST", current: currentStage });
      return;
    }
    commitStage(opportunityId, newStage, currentStage);
  }

  return (
    <section className="panel">
      <div className="panel-title"><h3>{t("customers:opportunityBoard.title")}</h3><span>{t("customers:opportunityBoard.subtitle")}</span></div>
      <div className="kanban">
        {STAGES.map((stage) => {
          const items = opportunities.filter((o) => o.stage === stage);
          return (
            <div className="kanban-col" key={stage}>
              <strong>{t(stageLabel(stage))}<span className="kanban-count">{items.length}</span></strong>
              {items.map((opportunity) => (
                <article className="opportunity-card" key={opportunity.id}>
                  <div className="card-actions">
                    <button type="button" className="card-icon-btn" title={t("customers:opportunityBoard.intelligence")} data-testid={`oi-open-${opportunity.id}`} onClick={() => navigate(`/opportunities/${opportunity.id}/intelligence?customerId=${customerId}`)}>📊</button>
                    <button type="button" className="card-icon-btn" title={t("customers:opportunityBoard.editOpportunity")} onClick={() => onEdit(opportunity)}>✏️</button>
                    <button type="button" className="card-icon-btn" title={t("customers:opportunityBoard.deleteOpportunity")} onClick={() => onDelete(opportunity)}>🗑️</button>
                  </div>
                  <span>{opportunity.type}</span>
                  <b>{opportunity.name}</b>
                  <small>{formatMoney(opportunity.amount, i18n.language)}</small>
                  {/* 負責業務(SP8);未指派時不顯示 */}
                  {opportunity.ownerName ? <small className="opportunity-owner">{t("customers:opportunityBoard.ownerPrefix")}{opportunity.ownerName}</small> : null}
                  {/* 階段下拉:取代拖拽,改階段即送出(結案階段先彈 Modal) */}
                  <select
                    className="stage-select"
                    value={opportunity.stage}
                    onChange={(e) => changeStage(opportunity.id, e.target.value, opportunity.stage)}
                  >
                    {STAGES.map((s) => (
                      <option key={s} value={s}>{t(stageLabel(s))}</option>
                    ))}
                  </select>
                </article>
              ))}
            </div>
          );
        })}
      </div>
      {/* 結案原因 Modal:選到結案階段時出現,確認後帶 closeReason 送出 */}
      {pendingClose ? (
        <CloseOpportunityModal
          stage={pendingClose.stage}
          onSubmit={(data) => {
            commitStage(pendingClose.id, pendingClose.stage, pendingClose.current, data);
            setPendingClose(null);
          }}
          onClose={() => setPendingClose(null)}
        />
      ) : null}
    </section>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/OpportunityBoard.tsx
git commit -m "feat(i18n): migrate OpportunityBoard to react-i18next"
```

---

### Task 18: `CustomerDetailPanel.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/CustomerDetailPanel.tsx`

**Interfaces:**
- Consumes: `customers:detail.*`/`customers:enums.status.*`（Task 12）、`riskLabel`/`formatMoney`/`formatDate`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 CustomerDetailPanel.tsx**

完整覆寫 `frontend/src/features/customers/components/CustomerDetailPanel.tsx`：

```tsx
import { useTranslation } from "react-i18next";
import type { ContactResponse, CustomerDetail, OpportunityResponse } from "../../../types";
import { riskLabel, formatMoney, formatDate } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";
import { Timeline } from "./Timeline";
import { OpportunityBoard } from "./OpportunityBoard";
import { ContactsPanel } from "./ContactsPanel";
import { UpcomingPanel } from "./UpcomingPanel";

/**
 * 客戶詳情、聯絡人、商機、互動、AI 與 Trace 的主內容。
 * 函式級註解：所有 CRUD 操作的入口（編輯 / 刪除客戶、聯絡人、商機、互動）皆在此鋪設，
 * 實際邏輯與重載集中於上層 CustomersPage，透過 props 傳入。
 */
export function CustomerDetailPanel({
  detail,
  loading,
  onStageChange,
  onOpenChat,
  onAssess,
  onOpenAiHistory,
  onEditCustomer,
  onDeleteCustomer,
  onAddContact,
  onEditContact,
  onDeleteContact,
  onEditOpportunity,
  onDeleteOpportunity,
  onEditInteraction,
  onDeleteInteraction,
  userRole
}: {
  detail: CustomerDetail | null;
  loading: boolean;
  onStageChange: (opportunityId: number, newStage: string) => void;
  onOpenChat: () => void;
  onAssess: () => void;
  onOpenAiHistory: () => void;
  onEditCustomer: () => void;
  onDeleteCustomer: () => void;
  onAddContact: () => void;
  onEditContact: (contact: ContactResponse) => void;
  onDeleteContact: (contact: ContactResponse) => void;
  onEditOpportunity: (opportunity: OpportunityResponse) => void;
  onDeleteOpportunity: (opportunity: OpportunityResponse) => void;
  onEditInteraction: (interaction: CustomerDetail["interactions"][number]) => void;
  onDeleteInteraction: (interaction: CustomerDetail["interactions"][number]) => void;
  userRole?: string;
}) {
  const { t, i18n } = useTranslation(["customers", "common"]);
  // 載入中且尚無詳情：顯示 skeleton，避免空白久候
  if (!detail && loading) {
    return (
      <section className="detail-stack" aria-busy="true" aria-label={t("customers:detail.loadingAria")}>
        <div className="panel customer-hero skeleton-block">
          <div className="skeleton-line w-30" />
          <div className="skeleton-line w-50 h-lg" />
          <div className="skeleton-line w-40" />
        </div>
        <div className="kpi-row">
          {[0, 1, 2, 3].map((i) => (
            <div className="kpi-card skeleton-block" key={i}>
              <div className="skeleton-line w-40" />
              <div className="skeleton-line w-60 h-lg" />
            </div>
          ))}
        </div>
        <div className="panel skeleton-block">
          <div className="skeleton-line w-30" />
          <div className="skeleton-line" />
          <div className="skeleton-line" />
          <div className="skeleton-line w-70" />
        </div>
      </section>
    );
  }
  if (!detail) {
    return <section className="panel empty-state">{t("customers:detail.empty")}</section>;
  }
  return (
    <section className="detail-stack">
      <div className="panel customer-hero">
        <div>
          <small>{detail.customer.industry}</small>
          <h3>{detail.customer.name}</h3>
          <p>{detail.customer.email} / {detail.customer.phone}</p>
        </div>
        <div className="hero-actions">
          <span className={`risk-badge ${detail.customer.riskLevel.toLowerCase()}`}>{t(riskLabel(detail.customer.riskLevel))}</span>
          <button type="button" className="btn-primary" onClick={onAssess}>{t("customers:detail.assess")}<AiBadge onDark /></button>
          <button type="button" className="btn-primary" onClick={onOpenChat}>{t("customers:detail.askAi")}<AiBadge onDark /></button>
          <button type="button" className="btn-secondary" onClick={onOpenAiHistory}>{t("customers:detail.aiHistory")}</button>
          <button type="button" className="btn-secondary" onClick={onEditCustomer}>{t("customers:detail.editCustomer")}</button>
          {userRole === "ADMIN" ? <button type="button" className="btn-danger" onClick={onDeleteCustomer}>{t("customers:detail.deleteCustomer")}</button> : null}
        </div>
      </div>
      {loading ? <div className="loading-line">{t("customers:detail.updating")}</div> : null}
      {/* KPI 摘要卡：橫向凸顯商機金額 / 合約到期 / 最近互動 / 客戶狀態四個關鍵指標 */}
      <div className="kpi-row">
        <div className="kpi-card">
          <span className="kpi-label">{t("customers:detail.kpiAmount")}</span>
          <span className="kpi-value kpi-value-accent">{formatMoney(detail.customer.opportunityAmount, i18n.language)}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">{t("customers:detail.kpiRenewal")}</span>
          <span className="kpi-value">{formatDate(detail.customer.renewalDueDate, i18n.language, t("common:noData"))}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">{t("customers:detail.kpiLastInteraction")}</span>
          <span className="kpi-value">{formatDate(detail.customer.lastInteractionAt, i18n.language, t("common:noData"))}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">{t("customers:detail.kpiStatus")}</span>
          <span className="kpi-value">{t(`customers:enums.status.${detail.customer.status}`, { defaultValue: detail.customer.status })}</span>
        </div>
      </div>
      {/* 本週待跟進:未來 7 天的即將互動 / 續約到期 / 商機成交,置於上方提醒主動跟進 */}
      <UpcomingPanel detail={detail} />
      <ContactsPanel
        contacts={detail.contacts}
        onAdd={onAddContact}
        onEdit={onEditContact}
        onDelete={onDeleteContact}
      />
      {/* 時間線改橫向 banner、商機看板含 5 欄,皆改整列全寬呈現(原本半寬欄會擠) */}
      <Timeline interactions={detail.interactions} onEdit={onEditInteraction} onDelete={onDeleteInteraction} />
      <OpportunityBoard customerId={detail.customer.id} opportunities={detail.opportunities} onStageChange={onStageChange} onEdit={onEditOpportunity} onDelete={onDeleteOpportunity} />
    </section>
  );
}
```

注意：客戶狀態改用 `t(\`customers:enums.status.${status}\`, { defaultValue: status })`——i18next 支援 `defaultValue` 選項，key 不存在時回退原始值，等同原本 `statusLabels[status] ?? status` 的語意，且不需要在元件內維護一份重複的對照表。

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/CustomerDetailPanel.tsx
git commit -m "feat(i18n): migrate CustomerDetailPanel to react-i18next"
```

---

### Task 19: `ContactsPanel.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/ContactsPanel.tsx`

**Interfaces:**
- Consumes: `customers:contacts.*`（Task 12）、`common:actions.edit/delete`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 ContactsPanel.tsx**

完整覆寫 `frontend/src/features/customers/components/ContactsPanel.tsx`：

```tsx
import { useTranslation } from "react-i18next";
import type { ContactResponse } from "../../../types";

/**
 * 聯絡人區塊：列出客戶聯絡人，提供新增 / 編輯 / 刪除入口。
 * 函式級註解：標題列有「+ 新增聯絡人」；每筆顯示姓名 / 職稱 / Email 與「編輯」「刪除」小按鈕。
 *
 * @param contacts 聯絡人清單
 * @param onAdd 點擊新增 callback
 * @param onEdit 點擊編輯某聯絡人 callback
 * @param onDelete 點擊刪除某聯絡人 callback
 */
export function ContactsPanel({
  contacts,
  onAdd,
  onEdit,
  onDelete
}: {
  contacts: ContactResponse[];
  onAdd: () => void;
  onEdit: (contact: ContactResponse) => void;
  onDelete: (contact: ContactResponse) => void;
}) {
  const { t } = useTranslation(["customers", "common"]);
  return (
    <section className="panel">
      <div className="panel-title">
        <h3>{t("customers:contacts.title")}</h3>
        <button type="button" className="btn-secondary" onClick={onAdd}>{t("customers:contacts.add")}</button>
      </div>
      {contacts.length === 0 ? (
        <p className="contact-empty">{t("customers:contacts.empty")}</p>
      ) : (
        <div className="contact-list">
          {contacts.map((c) => (
            <article className="contact-item" key={c.id}>
              <div className="contact-info">
                <strong>{c.name}</strong>
                {c.title ? <span className="contact-title">{c.title}</span> : null}
                <small>{c.email}</small>
              </div>
              <div className="contact-actions">
                <button type="button" className="row-btn" onClick={() => onEdit(c)}>{t("common:actions.edit")}</button>
                <button type="button" className="row-btn row-btn-danger" onClick={() => onDelete(c)}>{t("common:actions.delete")}</button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/ContactsPanel.tsx
git commit -m "feat(i18n): migrate ContactsPanel to react-i18next"
```

---

### Task 20: `ContactModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/ContactModal.tsx`

**Interfaces:**
- Consumes: `customers:contactModal.*`/`customers:contacts.{name,title_,email}`（Task 12）、`common:actions.save/cancel`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 ContactModal.tsx**

完整覆寫 `frontend/src/features/customers/components/ContactModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { ContactResponse } from "../../../types";

/**
 * 聯絡人 Modal（單一元件同時支援新增與編輯）。
 * 函式級註解：有傳入 contact 則為編輯模式（預填現有值並顯示「編輯聯絡人」），否則為新增模式。
 * 欄位為姓名 / 職稱 / Email；送出後由上層呼叫對應的 createContact 或 updateContact。
 *
 * @param contact 欲編輯的聯絡人；未傳則為新增
 * @param onSubmit 送出 callback（回傳表單資料）
 * @param onClose 關閉 callback
 */
export function ContactModal({
  contact,
  onSubmit,
  onClose
}: {
  contact?: ContactResponse | null;
  onSubmit: (data: { name: string; title: string; email: string }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);
  // 是否為編輯模式（用於標題與按鈕文字）
  const isEdit = Boolean(contact);

  /** 解析表單並回傳聯絡人資料。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      name: String(fd.get("name")),
      title: String(fd.get("title")),
      email: String(fd.get("email"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{isEdit ? t("customers:contactModal.editTitle") : t("customers:contactModal.addTitle")}</h3>
        <label>{t("customers:contacts.name")} <input name="name" required defaultValue={contact?.name ?? ""} /></label>
        <label>{t("customers:contacts.title_")} <input name="title" defaultValue={contact?.title ?? ""} /></label>
        <label>{t("customers:contacts.email")} <input name="email" type="email" required defaultValue={contact?.email ?? ""} /></label>
        <div className="modal-actions">
          <button type="submit">{isEdit ? t("common:actions.save") : t("common:actions.add")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/ContactModal.tsx
git commit -m "feat(i18n): migrate ContactModal to react-i18next"
```

---

### Task 21: `UpcomingPanel.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/UpcomingPanel.tsx`

**Interfaces:**
- Consumes: `customers:upcoming.*`（Task 12）、`formatDate`/`formatMoney`/`stageLabel`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 UpcomingPanel.tsx**

完整覆寫 `frontend/src/features/customers/components/UpcomingPanel.tsx`：

```tsx
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { CustomerDetail } from "../../../types";
import { formatDate, formatMoney, stageLabel } from "../../../lib/format";

/** 未來幾天視為「即將」。 */
const AHEAD_DAYS = 7;

/**
 * 本週待跟進：彙整未來 7 天內的「即將互動、續約到期、商機預計成交」三類事件,依日期排序。
 * 函式級註解：完全以前端現有的 CustomerDetail 計算(互動 occurredAt、客戶 renewalDueDate、
 * 商機 expectedCloseDate),不需後端;讓業務一眼看出本週該主動跟進的事項。
 *
 * @param detail 客戶詳情
 */
export function UpcomingPanel({ detail }: { detail: CustomerDetail }) {
  const { t, i18n } = useTranslation(["customers", "common"]);

  // 事件種類 → CSS class 與 i18n key 對照（元件內部常數，依賴 t，故放函式體內而非模組層級）
  const KIND_INTERACTION = { labelKey: "customers:upcoming.kindInteraction", cls: "interaction" } as const;
  const KIND_RENEWAL = { labelKey: "customers:upcoming.kindRenewal", cls: "renewal" } as const;
  const KIND_OPPORTUNITY = { labelKey: "customers:upcoming.kindOpportunity", cls: "opportunity" } as const;

  const events = useMemo(() => {
    const now = new Date();
    const end = new Date();
    end.setDate(now.getDate() + AHEAD_DAYS);
    // 判斷日期字串是否落在 [now, now+7d]
    const within = (dateStr: string | null | undefined) => {
      if (!dateStr) return false;
      const t2 = new Date(dateStr).getTime();
      return t2 >= now.getTime() && t2 <= end.getTime();
    };
    const list: { kind: { labelKey: string; cls: string }; date: string; label: string }[] = [];
    // ① 未來日期的互動
    detail.interactions.filter((i) => within(i.occurredAt)).forEach((i) =>
      list.push({ kind: KIND_INTERACTION, date: i.occurredAt, label: t("customers:upcoming.interactionLabel", { type: i.type, content: i.content }) })
    );
    // ② 續約日落在本週
    if (within(detail.customer.renewalDueDate)) {
      list.push({ kind: KIND_RENEWAL, date: detail.customer.renewalDueDate as string, label: t("customers:upcoming.renewalLabel") });
    }
    // ③ 商機預計成交日落在本週
    detail.opportunities.filter((o) => within(o.expectedCloseDate)).forEach((o) =>
      list.push({ kind: KIND_OPPORTUNITY, date: o.expectedCloseDate as string, label: t("customers:upcoming.opportunityLabel", { name: o.name, stage: t(stageLabel(o.stage)), amount: formatMoney(o.amount, i18n.language) }) })
    );
    // 依日期由近到遠排序
    return list.sort((a, b) => a.date.localeCompare(b.date));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail, i18n.language]);

  return (
    <section className="panel upcoming-panel">
      <div className="panel-title"><h3>{t("customers:upcoming.title")}</h3><span>{t("customers:upcoming.aheadDays", { days: AHEAD_DAYS })}</span></div>
      {events.length === 0 ? (
        <p className="trace-empty">{t("customers:upcoming.empty", { days: AHEAD_DAYS })}</p>
      ) : (
        <div className="upcoming-list">
          {events.map((e, i) => (
            <div className="upcoming-item" key={i}>
              <span className={`upcoming-kind k-${e.kind.cls}`}>{t(e.kind.labelKey)}</span>
              <strong>{formatDate(e.date, i18n.language, t("common:noData"))}</strong>
              <p>{e.label}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/UpcomingPanel.tsx
git commit -m "feat(i18n): migrate UpcomingPanel to react-i18next"
```

---

### Task 22: `AddCustomerModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/AddCustomerModal.tsx`

**Interfaces:**
- Consumes: `customers:addCustomerModal.*`/`customers:form.*`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 AddCustomerModal.tsx**

完整覆寫 `frontend/src/features/customers/components/AddCustomerModal.tsx`：

```tsx
import { FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchCustomerOptions } from "../../../api";
import type { OwnerOption } from "../../../types";

/**
 * 新增客戶 Modal。
 * 產業為下拉選單（來源：現有客戶的不重複產業）；
 * 負責業務為下拉選單，選項為 SALES 登入帳號（正規關聯），預設帶登入者本人（currentUserId）。
 */
export function AddCustomerModal({
  currentUserId,
  onSubmit,
  onClose
}: {
  currentUserId: number;
  onSubmit: (data: { name: string; email: string; phone: string; taxId: string; industry: string; ownerId: number }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);
  // 下拉選項：產業清單與可指派業務（帳號）清單
  const [industries, setIndustries] = useState<string[]>([]);
  const [owners, setOwners] = useState<OwnerOption[]>([]);
  // 已選負責業務帳號 id（字串以利 <select> 綁定）
  const [ownerId, setOwnerId] = useState("");

  // 進場載入下拉選項；負責業務預設為登入者本人（若本人為可指派業務），否則第一個
  useEffect(() => {
    void (async () => {
      try {
        const options = await fetchCustomerOptions();
        setIndustries(options.industries);
        setOwners(options.owners);
        const self = options.owners.find((o) => o.id === currentUserId);
        setOwnerId(String(self ? self.id : options.owners[0]?.id ?? ""));
      } catch (e) {
        console.error("載入客戶表單選項失敗:", e);
      }
    })();
  }, [currentUserId]);

  /** 送出表單：彙整欄位值，產業取自下拉，負責業務取自帳號下拉（ownerId）。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      name: String(fd.get("name")),
      email: String(fd.get("email")),
      phone: String(fd.get("phone")),
      taxId: String(fd.get("taxId")),
      industry: String(fd.get("industry")),
      ownerId: Number(fd.get("ownerId"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:addCustomerModal.title")}</h3>
        <label>{t("customers:form.name")} <input name="name" required /></label>
        <label>{t("customers:form.email")} <input name="email" type="email" required /></label>
        <label>{t("customers:form.phone")} <input name="phone" placeholder={t("customers:form.phonePlaceholder")} required /></label>
        <label>{t("customers:form.taxId")} <input name="taxId" placeholder={t("customers:form.taxIdPlaceholder")} required /></label>
        <label>
          {t("customers:form.industry")}
          <select name="industry" required defaultValue="">
            <option value="" disabled>{t("customers:form.selectIndustry")}</option>
            {industries.map((it) => (
              <option key={it} value={it}>{it}</option>
            ))}
          </select>
        </label>
        <label>
          {t("customers:form.owner")}
          <select name="ownerId" required value={ownerId} onChange={(e) => setOwnerId(e.target.value)}>
            {owners.length === 0 ? <option value="" disabled>{t("customers:form.noAssignableOwner")}</option> : null}
            {owners.map((o) => (
              <option key={o.id} value={o.id}>{o.id === currentUserId ? t("customers:form.ownerSelf", { name: o.displayName }) : o.displayName}</option>
            ))}
          </select>
        </label>
        <div className="modal-actions">
          <button type="submit">{t("customers:addCustomerModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/AddCustomerModal.tsx
git commit -m "feat(i18n): migrate AddCustomerModal to react-i18next"
```

---

### Task 23: `EditCustomerModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/EditCustomerModal.tsx`

**Interfaces:**
- Consumes: `customers:editCustomerModal.*`/`customers:form.*`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 EditCustomerModal.tsx**

完整覆寫 `frontend/src/features/customers/components/EditCustomerModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { CustomerSummary, OwnerOption } from "../../../types";

/**
 * 編輯客戶 Modal。
 * 函式級註解：仿 AddCustomerModal 範式，但以現有客戶值預填欄位。
 * 產業沿用文字輸入以容許新值；負責業務為帳號下拉（值為 ownerId）。
 * 日期欄位（合約起始 / 到期 / 續約）為 date input，空值送 null。
 *
 * 注意：CustomerSummary 僅提供 renewalDueDate，合約起始 / 到期日後端未回傳，
 * 故預設留空；使用者可自行填入後送出更新。
 */
export function EditCustomerModal({
  customer,
  owners,
  onSubmit,
  onClose
}: {
  customer: CustomerSummary;
  owners: OwnerOption[];
  onSubmit: (data: {
    name: string;
    email: string;
    phone: string;
    taxId: string;
    industry: string;
    ownerId: number;
    contractStartDate: string | null;
    contractEndDate: string | null;
    renewalDueDate: string | null;
  }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);
  // 找出目前負責業務帳號 id（以顯示名稱比對 owners 清單），供下拉預設值使用
  const currentOwner = owners.find((o) => o.displayName === customer.ownerName);

  /** 將後端日期時間字串轉為 date input 可用的 yyyy-MM-dd（無值回空字串）。 */
  function toDateInput(value: string | null): string {
    if (!value) return "";
    // 後端日期可能帶時間，僅取日期部分
    return value.slice(0, 10);
  }

  /** 送出表單：彙整欄位值，金額外的日期欄位空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const start = String(fd.get("contractStartDate") || "");
    const end = String(fd.get("contractEndDate") || "");
    const renewal = String(fd.get("renewalDueDate") || "");
    onSubmit({
      name: String(fd.get("name")),
      email: String(fd.get("email")),
      phone: String(fd.get("phone")),
      taxId: String(fd.get("taxId")),
      industry: String(fd.get("industry")),
      ownerId: Number(fd.get("ownerId")),
      // date input 空值送 null，避免後端解析空字串失敗
      contractStartDate: start ? start : null,
      contractEndDate: end ? end : null,
      renewalDueDate: renewal ? renewal : null
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:editCustomerModal.title", { name: customer.name })}</h3>
        <label>{t("customers:form.name")} <input name="name" required defaultValue={customer.name} /></label>
        <label>{t("customers:form.email")} <input name="email" type="email" required defaultValue={customer.email} /></label>
        <label>{t("customers:form.phone")} <input name="phone" placeholder={t("customers:form.phonePlaceholder")} required defaultValue={customer.phone} /></label>
        <label>{t("customers:form.taxId")} <input name="taxId" placeholder={t("customers:form.taxIdPlaceholder")} required defaultValue={customer.taxId} /></label>
        <label>{t("customers:form.industry")} <input name="industry" required defaultValue={customer.industry} /></label>
        <label>
          {t("customers:form.owner")}
          <select name="ownerId" required defaultValue={currentOwner ? String(currentOwner.id) : ""}>
            {owners.length === 0 ? <option value="" disabled>{t("customers:form.noAssignableOwner")}</option> : null}
            {owners.map((o) => (
              <option key={o.id} value={o.id}>{o.displayName}</option>
            ))}
          </select>
        </label>
        <label>{t("customers:form.contractStart")} <input name="contractStartDate" type="date" defaultValue="" /></label>
        <label>{t("customers:form.contractEnd")} <input name="contractEndDate" type="date" defaultValue="" /></label>
        <label>{t("customers:form.renewalDue")} <input name="renewalDueDate" type="date" defaultValue={toDateInput(customer.renewalDueDate)} /></label>
        <div className="modal-actions">
          <button type="submit">{t("common:actions.save")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/EditCustomerModal.tsx
git commit -m "feat(i18n): migrate EditCustomerModal to react-i18next"
```

---

### Task 24: `AddInteractionModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/AddInteractionModal.tsx`

**Interfaces:**
- Consumes: `customers:addInteractionModal.*`/`customers:form.*`/`customers:enumsInteractionType.*`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 AddInteractionModal.tsx**

完整覆寫 `frontend/src/features/customers/components/AddInteractionModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";

/**
 * 新增互動紀錄 Modal。
 */
export function AddInteractionModal({ customerName, onSubmit, onClose }: { customerName: string; onSubmit: (data: { type: string; occurredAt: string; content: string }) => void; onClose: () => void }) {
  const { t } = useTranslation(["customers", "common"]);
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      type: String(fd.get("type")),
      // datetime-local 的值即為 LocalDateTime 格式（yyyy-MM-ddTHH:mm），直接送；
      // 不可用 new Date().toISOString()，否則會轉成 UTC 並附帶 Z，造成時間時區位移
      occurredAt: String(fd.get("occurredAt")),
      content: String(fd.get("content"))
    });
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:addInteractionModal.title", { name: customerName })}</h3>
        <label>{t("customers:form.type")}
          <select name="type" required>
            <option value="PHONE">{t("customers:enumsInteractionType.PHONE")}</option>
            <option value="MEETING">{t("customers:enumsInteractionType.MEETING")}</option>
            <option value="EMAIL">{t("customers:enumsInteractionType.EMAIL")}</option>
            <option value="SUPPORT_TICKET">{t("customers:enumsInteractionType.SUPPORT_TICKET")}</option>
          </select>
        </label>
        <label>{t("customers:form.occurredAt")} <input name="occurredAt" type="datetime-local" required /></label>
        <label>{t("customers:form.content")} <textarea name="content" rows={3} required /></label>
        <div className="modal-actions">
          <button type="submit">{t("customers:addInteractionModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/AddInteractionModal.tsx
git commit -m "feat(i18n): migrate AddInteractionModal to react-i18next"
```

---

### Task 25: `EditInteractionModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/EditInteractionModal.tsx`

**Interfaces:**
- Consumes: `customers:editInteractionModal.*`/`customers:form.*`/`customers:enumsInteractionType.*`（Task 12）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 EditInteractionModal.tsx**

完整覆寫 `frontend/src/features/customers/components/EditInteractionModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { InteractionResponse } from "../../../types";

/**
 * 編輯互動紀錄 Modal。
 * 函式級註解：仿 AddInteractionModal，但以現有互動值預填類型 / 時間 / 內容。
 * occurredAt 直接送 datetime-local 值（yyyy-MM-ddTHH:mm），不轉 ISO/UTC，以免時區位移。
 *
 * @param interaction 欲編輯的互動
 * @param onSubmit 送出 callback（回傳表單資料）
 * @param onClose 關閉 callback
 */
export function EditInteractionModal({
  interaction,
  onSubmit,
  onClose
}: {
  interaction: InteractionResponse;
  onSubmit: (data: { type: string; occurredAt: string; content: string }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);

  /** 將後端時間字串轉為 datetime-local 可用的 yyyy-MM-ddTHH:mm（取前 16 字元）。 */
  function toLocalInput(value: string): string {
    return value ? value.slice(0, 16) : "";
  }

  /** 解析表單並回傳互動資料。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      type: String(fd.get("type")),
      // datetime-local 的值即為 LocalDateTime 格式（yyyy-MM-ddTHH:mm），直接送，不可轉 UTC
      occurredAt: String(fd.get("occurredAt")),
      content: String(fd.get("content"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:editInteractionModal.title")}</h3>
        <label>{t("customers:form.type")}
          <select name="type" required defaultValue={interaction.type}>
            <option value="PHONE">{t("customers:enumsInteractionType.PHONE")}</option>
            <option value="MEETING">{t("customers:enumsInteractionType.MEETING")}</option>
            <option value="EMAIL">{t("customers:enumsInteractionType.EMAIL")}</option>
            <option value="SUPPORT_TICKET">{t("customers:enumsInteractionType.SUPPORT_TICKET")}</option>
          </select>
        </label>
        <label>{t("customers:form.occurredAt")} <input name="occurredAt" type="datetime-local" required defaultValue={toLocalInput(interaction.occurredAt)} /></label>
        <label>{t("customers:form.content")} <textarea name="content" rows={3} required defaultValue={interaction.content} /></label>
        <div className="modal-actions">
          <button type="submit">{t("common:actions.save")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/EditInteractionModal.tsx
git commit -m "feat(i18n): migrate EditInteractionModal to react-i18next"
```

---

### Task 26: `AddOpportunityModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/AddOpportunityModal.tsx`

**Interfaces:**
- Consumes: `customers:addOpportunityModal.*`/`customers:form.*`/`customers:enumsOpportunityType.*`（Task 12）、`common:enums.stage/leadSource`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 AddOpportunityModal.tsx**

完整覆寫 `frontend/src/features/customers/components/AddOpportunityModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";

/**
 * 新增商機 Modal。
 *
 * <p>提供商機名稱、階段、金額、預計成交日與類型輸入;送出後由上層呼叫 API 建立。</p>
 *
 * @param customerName 所屬客戶名稱(顯示用)
 * @param onSubmit 送出 callback(回傳表單資料)
 * @param onClose 關閉 callback
 */
export function AddOpportunityModal({
  customerName,
  onSubmit,
  onClose,
  initialValues
}: {
  customerName: string;
  onSubmit: (data: { name: string; stage: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) => void;
  onClose: () => void;
  /** 選填預填值（供 AI 建議商機草稿帶入名稱/階段；其餘維持原預設）。 */
  initialValues?: { name?: string; stage?: string };
}) {
  const { t } = useTranslation(["customers", "common"]);
  /** 解析表單並回傳資料;金額轉數字,預計成交日空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const closeDate = String(fd.get("expectedCloseDate") || "");
    const prob = String(fd.get("probability") || "");
    onSubmit({
      name: String(fd.get("name")),
      stage: String(fd.get("stage")),
      amount: Number(fd.get("amount")),
      // date input 空值送 null,避免後端解析空字串失敗
      expectedCloseDate: closeDate ? closeDate : null,
      type: String(fd.get("type")),
      leadSource: String(fd.get("leadSource")),
      // 機率留空時送 null，由後端依階段帶預設
      probability: prob ? Number(prob) : null
    });
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:addOpportunityModal.title", { name: customerName })}</h3>
        <label>{t("customers:form.opportunityName")} <input name="name" type="text" required placeholder={t("customers:form.opportunityNamePlaceholder")} defaultValue={initialValues?.name ?? ""} /></label>
        <label>{t("customers:form.stage")}
          <select name="stage" required defaultValue={initialValues?.stage ?? "QUALIFICATION"}>
            <option value="QUALIFICATION">{t("common:enums.stage.QUALIFICATION")}</option>
            <option value="PROPOSAL">{t("common:enums.stage.PROPOSAL")}</option>
            <option value="NEGOTIATION">{t("common:enums.stage.NEGOTIATION")}</option>
            <option value="CLOSED_WON">{t("common:enums.stage.CLOSED_WON")}</option>
            <option value="CLOSED_LOST">{t("common:enums.stage.CLOSED_LOST")}</option>
          </select>
        </label>
        <label>{t("customers:form.amount")} <input name="amount" type="number" min="0" step="1000" required placeholder={t("customers:form.amountPlaceholder")} /></label>
        <label>{t("customers:form.expectedCloseDate")} <input name="expectedCloseDate" type="date" /></label>
        <label>{t("customers:form.type")}
          <select name="type" required defaultValue="NEW_BUSINESS">
            <option value="NEW_BUSINESS">{t("customers:enumsOpportunityType.NEW_BUSINESS")}</option>
            <option value="RENEWAL">{t("customers:enumsOpportunityType.RENEWAL")}</option>
          </select>
        </label>
        <label>{t("customers:form.leadSource")}
          <select name="leadSource" required defaultValue="OUTBOUND">
            <option value="OUTBOUND">{t("common:enums.leadSource.OUTBOUND")}</option>
            <option value="INBOUND">{t("common:enums.leadSource.INBOUND")}</option>
            <option value="REFERRAL">{t("common:enums.leadSource.REFERRAL")}</option>
          </select>
        </label>
        <label>{t("customers:form.probability")} <input name="probability" type="number" min="0" max="100" placeholder={t("customers:form.probabilityPlaceholder")} /></label>
        <div className="modal-actions">
          <button type="submit">{t("customers:addOpportunityModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/AddOpportunityModal.tsx
git commit -m "feat(i18n): migrate AddOpportunityModal to react-i18next"
```

---

### Task 27: `EditOpportunityModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/EditOpportunityModal.tsx`

**Interfaces:**
- Consumes: `customers:editOpportunityModal.*`/`customers:form.*`（Task 12）、`common:enums.leadSource`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 EditOpportunityModal.tsx**

完整覆寫 `frontend/src/features/customers/components/EditOpportunityModal.tsx`：

```tsx
import { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { OpportunityResponse } from "../../../types";

/**
 * 編輯商機 Modal。
 * 函式級註解：仿 AddOpportunityModal，但以現有商機值預填；階段不在此修改（由看板拖拽處理）。
 * 欄位為名稱 / 金額 / 預計成交日 / 類型；預計成交日空值送 null。
 *
 * @param opportunity 欲編輯的商機
 * @param onSubmit 送出 callback（回傳表單資料）
 * @param onClose 關閉 callback
 */
export function EditOpportunityModal({
  opportunity,
  onSubmit,
  onClose
}: {
  opportunity: OpportunityResponse;
  onSubmit: (data: { name: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);
  /** 解析表單並回傳資料；金額轉數字，預計成交日空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const closeDate = String(fd.get("expectedCloseDate") || "");
    const prob = String(fd.get("probability") || "");
    onSubmit({
      name: String(fd.get("name")),
      amount: Number(fd.get("amount")),
      // date input 空值送 null，避免後端解析空字串失敗
      expectedCloseDate: closeDate ? closeDate : null,
      type: String(fd.get("type")),
      leadSource: String(fd.get("leadSource")),
      // 機率留空時送 null，由後端依階段帶預設
      probability: prob ? Number(prob) : null
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:editOpportunityModal.title", { name: opportunity.name })}</h3>
        <label>{t("customers:form.opportunityName")} <input name="name" type="text" required defaultValue={opportunity.name} /></label>
        <label>{t("customers:form.amount")} <input name="amount" type="number" min="0" step="1000" required defaultValue={opportunity.amount} /></label>
        <label>{t("customers:form.expectedCloseDate")} <input name="expectedCloseDate" type="date" defaultValue={opportunity.expectedCloseDate ?? ""} /></label>
        <label>{t("customers:form.type")}
          <select name="type" required defaultValue={opportunity.type}>
            <option value="NEW_BUSINESS">{t("customers:enumsOpportunityType.NEW_BUSINESS")}</option>
            <option value="RENEWAL">{t("customers:enumsOpportunityType.RENEWAL")}</option>
            {/* 若現有類型不在標準選項中，補一個保留原值的選項，避免下拉預設值失效 */}
            {opportunity.type !== "NEW_BUSINESS" && opportunity.type !== "RENEWAL" ? (
              <option value={opportunity.type}>{opportunity.type}</option>
            ) : null}
          </select>
        </label>
        <label>{t("customers:form.leadSource")}
          <select name="leadSource" required defaultValue={opportunity.leadSource}>
            <option value="OUTBOUND">{t("common:enums.leadSource.OUTBOUND")}</option>
            <option value="INBOUND">{t("common:enums.leadSource.INBOUND")}</option>
            <option value="REFERRAL">{t("common:enums.leadSource.REFERRAL")}</option>
          </select>
        </label>
        <label>{t("customers:form.probability")} <input name="probability" type="number" min="0" max="100" defaultValue={opportunity.probability ?? ""} placeholder={t("customers:form.probabilityPlaceholder")} /></label>
        <div className="modal-actions">
          <button type="submit">{t("common:actions.save")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/EditOpportunityModal.tsx
git commit -m "feat(i18n): migrate EditOpportunityModal to react-i18next"
```

---

### Task 28: `AiHistoryModal.tsx` i18n 化

**Files:**
- Modify: `frontend/src/features/customers/components/AiHistoryModal.tsx`

**Interfaces:**
- Consumes: `customers:aiHistory.*`（Task 12）、`formatDateTime`（Task 1）
- Produces: 元件 props 不變

- [ ] **Step 1: 改寫 AiHistoryModal.tsx**

完整覆寫 `frontend/src/features/customers/components/AiHistoryModal.tsx`：

```tsx
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useTranslation } from "react-i18next";
import type { AgentTraceResponse, AiCallHistoryItem } from "../../../types";
import { formatDateTime } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";

/**
 * AI 歷程 Modal：列出該客戶歷次 AI 呼叫(新到舊)與 Agent 決策歷程(GOAP 步驟)。
 * 函式級註解：取代原本固定佔版面的 Agent Trace 區塊,改為按鈕點選才開啟;
 * 並補上「歷史每一次呼叫」的完整內容(原本只看得到最後一次)。
 *
 * @param customerName 客戶名稱(標題用)
 * @param calls AI 呼叫歷史清單
 * @param trace Agent 決策歷程(可為 null)
 * @param loading 是否載入中
 * @param onClose 關閉 callback
 */
export function AiHistoryModal({ customerName, calls, trace, loading, onClose }: {
  customerName: string;
  calls: AiCallHistoryItem[];
  trace: AgentTraceResponse | null;
  loading: boolean;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation(["customers", "common"]);
  // 目前展開中的呼叫 id(清單預設收合答案,點選展開,避免一次塞入大量長文)
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{t("customers:aiHistory.title", { name: customerName })} <AiBadge onDark /></h3>
            <small>{t("customers:aiHistory.subtitle")}</small>
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label={t("common:actions.close")}>✕</button>
        </div>

        <div className="report-body">
          {/* 區段一:歷次 AI 呼叫紀錄 */}
          <h4 className="ai-history-section">{t("customers:aiHistory.callsHeading", { count: calls.length })}</h4>
          {loading ? (
            <p className="chat-typing">{t("customers:aiHistory.loading")}<span>…</span></p>
          ) : calls.length === 0 ? (
            <p className="trace-empty">{t("customers:aiHistory.empty")}</p>
          ) : (
            <div className="ai-history-list">
              {calls.map((call) => {
                const open = expandedId === call.id;
                const callTypeLabel = t(`customers:aiHistory.callTypes.${call.callType}`, { defaultValue: call.callType });
                return (
                  <article className={`ai-history-item ${open ? "open" : ""}`} key={call.id}>
                    <button type="button" className="ai-history-head" onClick={() => setExpandedId(open ? null : call.id)}>
                      <span className="ai-history-type">{callTypeLabel}</span>
                      <span className="ai-history-time">{formatDateTime(call.createdAt, i18n.language, t("common:noData"))}</span>
                      {/* 模型 / fallback 標記:真實呼叫顯示模型名,否則標示為樣板回覆 */}
                      <span className={`ai-history-mode ${call.aiEnabled ? "real" : "fallback"}`}>
                        {call.aiEnabled ? (call.model ?? "LLM") : t("customers:aiHistory.fallbackMode")}
                      </span>
                      <span className="ai-history-toggle">{open ? "▲" : "▼"}</span>
                    </button>
                    {open ? (
                      <div className="ai-history-answer markdown-body">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{call.answer}</ReactMarkdown>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          )}

          {/* 區段二:Agent 決策歷程(GOAP 步驟) */}
          <h4 className="ai-history-section">{t("customers:aiHistory.traceHeading")}</h4>
          <p className="trace-intro">{t("customers:aiHistory.traceIntro")}</p>
          {trace ? (
            <>
              <p className="recommendation">{trace.finalRecommendation}</p>
              <div className="trace-list">
                {trace.steps.map((step) => (
                  <article className="trace-step" key={`${step.order}-${step.action}`}>
                    <b className="trace-action">{step.order}. {step.action}</b>
                    <span className="trace-meta">{step.status} / {step.durationMs}ms</span>
                    <p>{step.output}</p>
                  </article>
                ))}
              </div>
            </>
          ) : <p className="trace-empty">{t("customers:aiHistory.traceEmpty")}</p>}
        </div>

        <div className="report-footer">
          <button type="button" onClick={onClose}>{t("common:actions.close")}</button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 型別檢查**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/customers/components/AiHistoryModal.tsx
git commit -m "feat(i18n): migrate AiHistoryModal to react-i18next"
```

---

### Task 29: `CustomersPage.tsx` 本體 i18n 化 + 整合測試

**Files:**
- Modify: `frontend/src/features/customers/CustomersPage.tsx`
- Create: `frontend/src/features/customers/CustomersPage.test.tsx`

**Interfaces:**
- Consumes: `customers:topbar.*`/`customers:filters.*`/`customers:actionBar.*`/`customers:breadcrumb.*`/`customers:confirm.*`/`customers:assessment.*`（Task 12）、`common:enums.risk`（Task 1）
- Produces: `CustomersPage` 對外行為不變（仍是路由頁面元件，無 props）

`CustomersPage.tsx` 引用了本次未遷移的其他 feature 元件（`ChatLauncher`/`ChatWindow`、`WorkspaceAiModal`、`TaskFormModal`/`TaskPanel`）——這些元件內部文字維持中文，屬於下一批次範圍，符合 Global Constraints「不遷移其餘 feature」。本 Task 只翻譯 `CustomersPage.tsx` 自身的文字。

- [ ] **Step 1: 加入 useTranslation 並更新頂部 import**

修改 `frontend/src/features/customers/CustomersPage.tsx` 頂部 import 區塊，`import { useLocation, useNavigate, useParams } from "react-router-dom";` 之後加入：
```tsx
import { useTranslation } from "react-i18next";
```

函式本體開頭（`const { user } = useAuth();` 之前）加入：
```tsx
  const { t, i18n } = useTranslation(["customers", "common"]);
```

- [ ] **Step 2: 遷移刪除確認對話框與 AI 評估文字**

將以下四處 `window.confirm(...)` 呼叫：
```tsx
    if (!window.confirm("確定刪除此客戶?此動作無法復原。")) return;
```
```tsx
    if (!window.confirm(`確定刪除聯絡人「${contact.name}」?此動作無法復原。`)) return;
```
```tsx
    if (!window.confirm(`確定刪除商機「${opportunity.name}」?此動作無法復原。`)) return;
```
```tsx
    if (!window.confirm("確定刪除此互動紀錄?此動作無法復原。")) return;
```
分別改為：
```tsx
    if (!window.confirm(t("customers:confirm.deleteCustomer"))) return;
```
```tsx
    if (!window.confirm(t("customers:confirm.deleteContact", { name: contact.name }))) return;
```
```tsx
    if (!window.confirm(t("customers:confirm.deleteOpportunity", { name: opportunity.name }))) return;
```
```tsx
    if (!window.confirm(t("customers:confirm.deleteInteraction"))) return;
```

並將 `openCustomerAssessment` 函式內容：
```tsx
  function openCustomerAssessment() {
    if (!selected) return;
    const name = selected.customer.name;
    const title = `整體評估 — ${name}`;
    setReport({ open: true, title, loading: true, streaming: true, markdown: "" });
    let acc = "";
    fetchCustomerAssessmentStream(
      selected.customer.id,
      (chunk) => {
        if (chunk.type === "content" && chunk.delta) {
          acc += chunk.delta;
          setReport((r) => (r ? { ...r, loading: false, streaming: true, markdown: acc } : r));
        } else if (chunk.type === "risk" && chunk.risk) {
          setReport((r) => (r ? { ...r, meta: `流失風險 ${chunk.risk.churnRisk} · 續約延遲 ${chunk.risk.renewalDelayRisk}` } : r));
        } else if (chunk.type === "callId") {
          setReport((r) => (r ? { ...r, callId: chunk.callId } : r));
        }
      },
      () => setReport((r) => (r ? { ...r, loading: false, streaming: false } : r)),
      (e) => {
        console.error("客戶整體評估失敗:", e);
        setReport((r) => (r ? { ...r, loading: false, streaming: false, markdown: acc || "⚠️ 產生評估失敗，請稍後再試。" } : r));
      }
    );
  }
```
改為：
```tsx
  function openCustomerAssessment() {
    if (!selected) return;
    const name = selected.customer.name;
    const title = t("customers:assessment.title", { name });
    setReport({ open: true, title, loading: true, streaming: true, markdown: "" });
    let acc = "";
    fetchCustomerAssessmentStream(
      selected.customer.id,
      (chunk) => {
        if (chunk.type === "content" && chunk.delta) {
          acc += chunk.delta;
          setReport((r) => (r ? { ...r, loading: false, streaming: true, markdown: acc } : r));
        } else if (chunk.type === "risk" && chunk.risk) {
          setReport((r) => (r ? { ...r, meta: t("customers:assessment.riskMeta", { churn: chunk.risk.churnRisk, renewal: chunk.risk.renewalDelayRisk }) } : r));
        } else if (chunk.type === "callId") {
          setReport((r) => (r ? { ...r, callId: chunk.callId } : r));
        }
      },
      () => setReport((r) => (r ? { ...r, loading: false, streaming: false } : r)),
      (e) => {
        console.error("客戶整體評估失敗:", e);
        setReport((r) => (r ? { ...r, loading: false, streaming: false, markdown: acc || t("customers:assessment.error") } : r));
      }
    );
  }
```

- [ ] **Step 3: 遷移 return JSX 區塊**

將 `return (...)` 開頭的麵包屑與 topbar/搜尋區：
```tsx
      {source?.from === "dashboard" ? (
        <Breadcrumb
          crumbs={[
            { label: "儀表板", onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: source.section, onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: selected?.customer.name ?? "客戶" }
          ]}
        />
      ) : null}
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>客戶工作台</h2>
        </div>
        <form className="search-box" onSubmit={handleSearch}>
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="名稱 / Email / 電話 / 統編" />
          <select value={industry} onChange={(e) => setIndustry(e.target.value)}>
            <option value="">全部產業</option>
            {filterOptions.industries.map((it) => (
              <option key={it} value={it}>{it}</option>
            ))}
          </select>
          <select value={owner} onChange={(e) => setOwner(e.target.value)}>
            <option value="">全部業務</option>
            {filterOptions.owners.map((o) => (
              <option key={o.id} value={o.displayName}>{o.displayName}</option>
            ))}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">全部狀態</option>
            <option value="ACTIVE">使用中</option>
            <option value="INACTIVE">停用</option>
            <option value="LEVERAGED">重點客戶</option>
          </select>
          <select value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}>
            <option value="">全部風險</option>
            <option value="HIGH">高風險</option>
            <option value="MEDIUM">中風險</option>
            <option value="LOW">低風險</option>
          </select>
          {/* 續約到期日區間 */}
          <input type="date" value={renewalFrom} onChange={(e) => setRenewalFrom(e.target.value)} title="續約到期日(起)" />
          <input type="date" value={renewalTo} onChange={(e) => setRenewalTo(e.target.value)} title="續約到期日(迄)" />
          <button type="submit">搜尋</button>
          <button type="button" className="btn-secondary" onClick={handleResetFilters}>清除</button>
        </form>
      </section>

      <div className="action-bar">
        <button type="button" className="btn-assess" onClick={() => setAiModalOpen(true)}>✨ AI 工作建議</button>
        <button type="button" onClick={() => setShowAddCustomer(true)}>+ 新增客戶</button>
        <button type="button" onClick={() => navigate("/business-cards/new")}>📇 名片建檔</button>
        {selected ? <button type="button" onClick={() => setShowAddInteraction(true)}>+ 新增互動</button> : null}
        {selected ? <button type="button" onClick={() => setShowAddOpportunity(true)}>+ 新增商機</button> : null}
        {selected ? <button type="button" onClick={() => setShowTaskForm(true)}>☎ 安排電話</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/meeting-copilot`)}>🎙 會議 Copilot</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/follow-up`)}>✉️ AI 跟進信</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/stakeholder-map`)}>🕸 決策鏈</button> : null}
      </div>
```
改為：
```tsx
      {source?.from === "dashboard" ? (
        <Breadcrumb
          crumbs={[
            { label: t("customers:breadcrumb.dashboard"), onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: source.section, onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: selected?.customer.name ?? t("customers:breadcrumb.customerFallback") }
          ]}
        />
      ) : null}
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>{t("customers:topbar.title")}</h2>
        </div>
        <form className="search-box" onSubmit={handleSearch}>
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder={t("customers:topbar.searchPlaceholder")} />
          <select value={industry} onChange={(e) => setIndustry(e.target.value)}>
            <option value="">{t("customers:filters.allIndustries")}</option>
            {filterOptions.industries.map((it) => (
              <option key={it} value={it}>{it}</option>
            ))}
          </select>
          <select value={owner} onChange={(e) => setOwner(e.target.value)}>
            <option value="">{t("customers:filters.allOwners")}</option>
            {filterOptions.owners.map((o) => (
              <option key={o.id} value={o.displayName}>{o.displayName}</option>
            ))}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">{t("customers:filters.allStatuses")}</option>
            <option value="ACTIVE">{t("customers:enums.status.ACTIVE")}</option>
            <option value="INACTIVE">{t("customers:enums.status.INACTIVE")}</option>
            <option value="LEVERAGED">{t("customers:enums.status.LEVERAGED")}</option>
          </select>
          <select value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}>
            <option value="">{t("customers:filters.allRisk")}</option>
            <option value="HIGH">{t("common:enums.risk.HIGH")}</option>
            <option value="MEDIUM">{t("common:enums.risk.MEDIUM")}</option>
            <option value="LOW">{t("common:enums.risk.LOW")}</option>
          </select>
          {/* 續約到期日區間 */}
          <input type="date" value={renewalFrom} onChange={(e) => setRenewalFrom(e.target.value)} title={t("customers:filters.renewalFrom")} />
          <input type="date" value={renewalTo} onChange={(e) => setRenewalTo(e.target.value)} title={t("customers:filters.renewalTo")} />
          <button type="submit">{t("customers:filters.search")}</button>
          <button type="button" className="btn-secondary" onClick={handleResetFilters}>{t("customers:filters.clear")}</button>
        </form>
      </section>

      <div className="action-bar">
        <button type="button" className="btn-assess" onClick={() => setAiModalOpen(true)}>{t("customers:actionBar.aiSuggestions")}</button>
        <button type="button" onClick={() => setShowAddCustomer(true)}>{t("customers:actionBar.addCustomer")}</button>
        <button type="button" onClick={() => navigate("/business-cards/new")}>{t("customers:actionBar.businessCard")}</button>
        {selected ? <button type="button" onClick={() => setShowAddInteraction(true)}>{t("customers:actionBar.addInteraction")}</button> : null}
        {selected ? <button type="button" onClick={() => setShowAddOpportunity(true)}>{t("customers:actionBar.addOpportunity")}</button> : null}
        {selected ? <button type="button" onClick={() => setShowTaskForm(true)}>{t("customers:actionBar.scheduleCall")}</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/meeting-copilot`)}>{t("customers:actionBar.meetingCopilot")}</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/follow-up`)}>{t("customers:actionBar.followUpEmail")}</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/stakeholder-map`)}>{t("customers:actionBar.stakeholderMap")}</button> : null}
      </div>
```

注意：`i18n` 變數（來自 Step 1 的 `useTranslation`）在本檔目前無其他直接消費者（`formatMoney` 等呼叫都在子元件內部），若 `tsc --noEmit`／ESLint 對未使用變數報錯，改為只解構 `t`（`const { t } = useTranslation(...)`）即可，不影響其餘變更。

- [ ] **Step 4: 型別檢查 + build**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm exec tsc --noEmit && pnpm build`
Expected: 兩者皆 exit 0

- [ ] **Step 5: 寫 CustomersPage.test.tsx（頁面級整合測試）**

`frontend/src/features/customers/CustomersPage.test.tsx`：

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import i18n from "../../i18n";
import { CustomersPage } from "./CustomersPage";

// 隔離 auth：SALES 角色即可渲染主要區塊
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: 1, displayName: "Sales", role: "SALES" } })
}));

// 隔離 AI 對話 hook，避免測試觸發真實聊天狀態
vi.mock("../ai-assistant/useAiChat", () => ({
  useAiChat: () => ({
    messages: [], chatSending: false, chatOpen: false, setChatOpen: vi.fn(),
    sendChat: vi.fn(), resetChat: vi.fn(), loadHistory: vi.fn().mockResolvedValue(undefined), historyLoading: false
  })
}));

// 隔離所有 API 呼叫，回傳最小可渲染資料
vi.mock("../../api", () => ({
  fetchCustomers: vi.fn().mockResolvedValue({ items: [{ id: 1, name: "Acme", industry: "Tech", ownerName: "Sales", riskLevel: "LOW" }], totalPages: 1, totalElements: 1 }),
  fetchCustomerOptions: vi.fn().mockResolvedValue({ industries: ["Tech"], owners: [{ id: 1, displayName: "Sales" }] }),
  fetchCustomerDetail: vi.fn().mockResolvedValue({
    customer: { id: 1, name: "Acme", email: "a@acme.com", phone: "0900000000", industry: "Tech", riskLevel: "LOW", opportunityAmount: 0, renewalDueDate: null, lastInteractionAt: null, status: "ACTIVE" },
    contacts: [], interactions: [], opportunities: []
  }),
  fetchAgentTrace: vi.fn().mockResolvedValue({ finalRecommendation: "", steps: [] }),
  addInteraction: vi.fn(), createContact: vi.fn(), createCustomer: vi.fn(), createOpportunity: vi.fn(),
  deleteContact: vi.fn(), deleteCustomer: vi.fn(), deleteInteraction: vi.fn(), deleteOpportunity: vi.fn(),
  fetchCustomerAiCalls: vi.fn().mockResolvedValue([]), fetchCustomerAssessmentStream: vi.fn(),
  updateContact: vi.fn(), updateCustomer: vi.fn(), updateInteraction: vi.fn(), updateOpportunity: vi.fn(), updateOpportunityStage: vi.fn()
}));

describe("CustomersPage i18n", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("預設英文顯示頁面標題與客戶列表標題", async () => {
    await i18n.changeLanguage("en");
    render(
      <MemoryRouter initialEntries={["/customers/1"]}>
        <Routes>
          <Route path="/customers/:id" element={<CustomersPage />} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "Customer Workbench" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Customer list")).toBeInTheDocument();
    });
  });

  it("切換繁中顯示頁面標題與客戶列表標題", async () => {
    await i18n.changeLanguage("zh-TW");
    render(
      <MemoryRouter initialEntries={["/customers/1"]}>
        <Routes>
          <Route path="/customers/:id" element={<CustomersPage />} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "客戶工作台" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("客戶列表")).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 6: 執行確認通過**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm test -- CustomersPage`
Expected: PASS

- [ ] **Step 7: Step C 全量驗證**

Run: `export PATH="/d/nodejs:$PATH"; cd frontend && pnpm test && pnpm exec tsc --noEmit && pnpm build`
Expected: 全綠 + exit 0（涵蓋 Task 1-29 全部變更）

- [ ] **Step 8: Commit**

```bash
git add frontend/src/features/customers/CustomersPage.tsx frontend/src/features/customers/CustomersPage.test.tsx
git commit -m "feat(i18n): migrate CustomersPage to react-i18next (Step C complete)"
```

---

## 驗收（人工/e2e）

- `pnpm dev` 後開啟 `/dashboard`：預設瀏覽器非中文時應為英文（KPI 標籤、圖表標題、Portfolio 評估、AI 用量治理皆為英文）；側邊欄切 zh-TW 後全頁（含拖拉版面抽屜、RFM 分群、情緒雷達）應完整變為中文，無殘留英文或中文混雜。
- 開啟 `/customers/:id`：客戶列表、搜尋篩選、詳情 KPI、聯絡人、時間線、商機看板、各 Modal（新增/編輯客戶、聯絡人、互動、商機、結案原因、AI 歷程）皆隨語言切換完整翻譯；business-card/meeting-copilot/tasks/stakeholder-map/follow-up 等按鈕文字本身已翻譯，但點擊後導向的頁面（下一批次範圍）仍為中文，此為預期的過渡狀態。
- 既有 Playwright 煙霧測試（`pnpm exec playwright test`，需後端 18080）：若斷言硬編碼中文文字（例如客戶頁「客戶工作台」、「新增客戶」等），需確認測試是否在啟動時已固定 zh-TW（Phase 1 備註中列為執行時檢查項），必要時同步調整或於測試中显式 `i18n.changeLanguage("zh-TW")`。
- `mvn -pl backend test`（若有觸碰後端相關檔案，本次 Phase 2 未觸碰後端，理論上不需重跑，但依專案慣例仍建議跑一次確認無關聯迴歸）。

## 備註：日後分批（本次不做）

- 其餘 feature（business-card、meeting-copilot、tasks、stakeholder-map、opportunity-intelligence、follow-up、team、admin、ai-assistant、my-workspace）依 feature 逐頁遷移，遷移時依循本計畫已建立的慣例：新增對應 namespace、`format.ts` 標籤沿用既有 key、格式化函式沿用 `(value, locale)` 簽章。
- `components/common/` 下其餘尚未遷移的共用元件（`AiBadge`、`AiCallHistoryModal`、`Breadcrumb`、`DrilldownModal`、`FeedbackButtons`、`ReportModal`）留待對應 feature 遷移時一併處理（各自僅被特定 feature 使用，不像 `PaginatedList` 是 dashboard 的硬相依）。
