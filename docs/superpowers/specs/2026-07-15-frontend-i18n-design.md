# 設計：ai-crm 前端 i18n 基建 + LoginPage 試點

- **日期**：2026-07-15
- **範圍**：前端（`frontend/`）i18n 基礎建設 + 試點頁遷移
- **語言**：英文（en，fallback 主語言）、繁體中文（zh-TW）
- **函式庫**：react-i18next

## 1. 背景與目標

ai-crm 前端（React 19 + TypeScript + Vite MPA，react-router-dom v7）目前**未安裝任何 i18n 套件**，約 60 個 `.tsx/.ts` 檔含硬編碼中文（約 1387 行含中文）。本次目標為**建立可擴充的 i18n 基建**，並**完整遷移一個試點頁（LoginPage）**作為範本，其餘頁面日後分批遷移。

一次全量遷移（60 檔）工量大且易漏字，故採「基建 + 試點頁」策略，先驗證基建正確性，再逐頁擴散。

### 已確認決策

| 項目 | 決策 |
|------|------|
| 導入範圍 | 基建 + 試點頁（LoginPage） |
| 函式庫 | react-i18next |
| 預設語言/偵測 | en 為主 + 偵測；fallback en |
| 切換元件位置 | 登入頁 + AppShell 側邊欄兩處 |

## 2. 架構與檔案佈局

新增相依：`i18next`、`react-i18next`、`i18next-browser-languagedetector`（約 40KB gzip）。

```text
frontend/src/
  i18n/
    index.ts            # i18next 初始化（在 main.tsx import 一次）
    detect.ts           # detectLanguage 純函式（可單元測試）
    locales/
      en/common.json    # 英文資源（fallback 主語言）
      zh-TW/common.json # 繁中資源
  components/common/
    LanguageSwitcher.tsx # 語言切換元件（登入頁 + 側邊欄共用）
```

- 初始化在 `main.tsx` 頂層以 `import "./i18n"` 引入（早於 `createRoot(...).render(...)`）。
- `<App/>` 外層**不需**額外 Provider——react-i18next 使用預設全域 i18n 實例即可。

## 3. 偵測與持久化

`detect.ts` 提供純函式：

```ts
detectLanguage(browserLang: string | undefined, stored: string | null): "en" | "zh-TW"
```

判斷順序：
1. `stored`（localStorage，key：`ai-crm-lang`，比照既有 `ai-crm-token` 命名）存在且屬 supported → 直接採用。
2. 否則看 `browserLang`：`zh*` → `zh-TW`，其餘 → `en`。
3. 皆無 → fallback `en`。

i18next 設定：
- `fallbackLng: "en"`
- `supportedLngs: ["en", "zh-TW"]`
- `interpolation.escapeValue: false`（React 已防 XSS）
- languageDetector：`order: ['localStorage', 'navigator']`、`lookupLocalStorage: 'ai-crm-lang'`、`caches: ['localStorage']`

切換語言時 i18next 自動寫回 localStorage。`detect.ts` 作為可測試的純函式，語意須與 languageDetector 的行為一致（供測試與必要時手動初始化用）。

## 4. 翻譯 key 結構（單一 namespace `common`）

試點階段僅用一個 `common.json`，以區塊命名避免衝突：

```jsonc
{
  "lang": { "en": "English", "zh-TW": "繁體中文" },
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

`zh-TW/common.json` 以現有中文字串一一對應。日後分批遷移時再依 feature 拆 namespace（如 `dashboard`、`customers`）。

## 5. 語言切換元件

`LanguageSwitcher.tsx`：

- 以 `useTranslation()` 取得 `i18n`，呼叫 `i18n.changeLanguage(next)`。
- UI 採用原生 `<select>`（無障礙佳、零額外樣式負擔），選項文字讀 `t('lang.en')` / `t('lang.zh-TW')`，value 為語言碼。
- 目前選中值 = `i18n.resolvedLanguage`。
- 放置：
  - LoginPage：`login-panel` 右上角。
  - AppShell：側邊欄底部（登入後）。

## 6. LoginPage 遷移（試點）

- 將 12 處硬編碼中文改為 `t('login.xxx')`。
- 含插值的錯誤訊息改 `t('login.error.failedDetail', { detail })`。
- 中文函式級註解依 `frontend/CLAUDE.md` 規範保留。
- **不動** `lib/format.ts`（LoginPage 未使用）。

### 日後分批項目（本次不做，設計記錄）

- `lib/format.ts` 的列舉→標籤純函式（`riskLabel` / `stageLabel` / `intentLabel` / `leadSourceLabel` / `closeReasonLabel`）建議改為**回傳 i18n key**（於呼叫端 `t(...)`）或改為吃 `t` 參數。
- `Intl.NumberFormat` / `Intl.DateTimeFormat` 寫死的 `"zh-TW"` locale 改讀 `i18n.language`（en 對應 `en-US` 或 `en`）。
- 其餘 59 個含中文檔案依 feature 逐頁遷移，每頁遷移時擴充對應 namespace。

## 7. 測試

- `detect.test.ts`：`it.each` 覆蓋 `zh-TW` / `zh-CN` / `zh-HK` / `en` / 空值 / 已存 localStorage 等分支（TDD，先寫測試確認 FAIL，再實作）。
- `LanguageSwitcher.test.tsx`：切換觸發 `changeLanguage`，且 localStorage（`ai-crm-lang`）更新。
- `LoginPage.test.tsx`：預設 en 顯示英文關鍵字；切 zh-TW 後顯示中文關鍵字。
- 驗收：`pnpm exec tsc --noEmit` exit 0 + `pnpm test` 全綠。

## 8. 非目標（Out of Scope）

- 不遷移 LoginPage 以外的頁面。
- 不做 `lib/format.ts` 的 locale 化。
- 不新增 en / zh-TW 以外的語言。
- 後端訊息（如 API `detail`）不在本次 i18n 範圍，維持後端原樣透傳。
