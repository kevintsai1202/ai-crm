# 規格：前端全站 i18n 遷移

- **日期**：2026-07-18
- **範圍**：`frontend/` 所有正式使用者介面
- **語言**：英文（en，fallback）、繁體中文（zh-TW）
- **延續規格**：`2026-07-15-frontend-i18n-design.md` 的基建與 LoginPage 試點

## 1. 目標

語言切換不再只影響登入頁、側邊欄、儀表板與客戶頁。所有正式 TSX 頁面、Modal、按鈕、表單欄位、空狀態、錯誤提示、日期與金額格式，都必須依 `i18n.language` 顯示。

本次涵蓋：

- 團隊業務分析與 AI 輔導報告
- 使用者管理、AI 供應商與模型設定
- 我的工作檯、AI 助理與共用 AI Modal
- 名片建檔、會議 Copilot、AI 跟進信
- CRM 任務、商機智能與決策鏈
- 儀表板、客戶工作台與共用下鑽元件

## 2. 翻譯資源

資源依領域拆為 `common`、`app`、`dashboard`、`customers`、`operations` namespace；en 與 zh-TW 必須維持相同 key 結構。`operations` 收納管理、團隊與 AI 工作流程，避免把所有頁面文字重新塞回 `common`。

API 回傳的使用者資料、AI 生成內容及後端 `detail` 維持原樣；前端自有的標籤、狀態包裝與 fallback 訊息必須翻譯。

## 3. 實作約束

- React 元件以 `useTranslation` 取得文字；非 React 區塊 builder 由呼叫端傳入 `t` 與 locale。
- `Intl`、金額與日期格式使用目前的 `i18n.language`。
- 下載檔名、`aria-label`、tooltip、placeholder、確認視窗與錯誤提示均屬 i18n 範圍。
- 既有後端固定中文狀態只可作為正規化常數，不可直接顯示；顯示值須經翻譯資源轉換。

## 4. 防漏驗證

`frontend/src/i18n/i18nCoverage.test.ts` 以 TypeScript AST 掃描所有正式 `.tsx`：

- JSX 直接文字不得含硬編碼中文。
- JSX attribute 與 render expression 不得直接寫中文。
- UI error/message setter、`alert`、`confirm` 不得直接寫中文。

另外保留 Login、AppShell、Dashboard、Customers 與 Team Analytics 的英／繁中整合測試；交付前必須通過全套 Vitest、TypeScript 型別檢查與 Vite build。
