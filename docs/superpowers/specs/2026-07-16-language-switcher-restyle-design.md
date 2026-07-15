# 設計：LanguageSwitcher 樣式重製

- **日期**：2026-07-16
- **範圍**：`frontend/src/components/common/LanguageSwitcher.tsx` 外觀樣式（登入頁 + AppShell 側邊欄兩處用法）
- **背景**：Phase 1 i18n 基建時，`LanguageSwitcher` 用原生 `<select>` 且刻意不加樣式（`.lang-switcher` class 在 `styles.css` 中從未定義任何規則），實際截圖確認：登入頁上緊貼「Unit 4 + Unit 5」徽章、無間距；側邊欄底部呈現方角純白選單，與深色毛玻璃卡片風格（`.health-card`/`.user-card`）完全不搭。

## 已確認決策

| 項目 | 決策 |
|------|------|
| 元件型態 | 保留原生 `<select>`（無障礙、鍵盤操作免費），僅用 CSS 重製外觀 |
| 視覺風格 | 圓角矩形（`border-radius: 6px`，與 `.survey-link`/`.login-form input` 等現有圓角尺度一致），前綴 🌐 圖示輔助識別 |
| 登入頁配色 | 淡綠邊框 + 白底 + 深綠字，呼應 `.survey-link` 徽章配色 |
| 側邊欄配色 | 半透明白邊框 + 深藍半透明底 + 淺色字，呼應 `.user-card`/`.health-card` 毛玻璃質感 |
| 登入頁位置 | 維持現有 DOM 位置（`.login-copy` 頂部），新增 `margin-bottom` 與下方「Unit 4 + Unit 5」徽章文字分隔 |

## 實作方式

`LanguageSwitcher.tsx` 由「裸 `<select>`」改為「外層 `<span>` 包 🌐 圖示 + `<select>`」：
- 外層 `<span>` 承接原本傳入的 `className`（呼叫端 `LoginPage.tsx`/`AppShell.tsx` 皆傳 `"lang-switcher"`，維持不變，不需修改呼叫端），CSS 既有的 `.login-copy .lang-switcher` / `.sidebar .lang-switcher` 描述子選擇器繼續有效（只是現在選中的是 `<span>` 而非 `<select>`本身）。
- `<select>` 本身改用固定的內部 class `lang-switcher-select`（`appearance: none` 移除瀏覽器原生外觀，改用 CSS `background-image`（SVG data URI）畫下拉箭頭，因登入頁/側邊欄背景明暗不同，箭頭顏色需各自覆寫）。
- 🌐 圖示用 `position: absolute` 疊在 `<select>` 左側（`aria-hidden="true"`，純視覺裝飾，不影響 `aria-label` 已提供的無障礙標籤語意）。
- `<option>` 顯式設定淺底深字（`background-color`/`color`），避免深色側邊欄情境下瀏覽器原生下拉選單清單本身變成深底深字看不清楚。

## 非目標（Out of Scope）

- 不新增/移除語言選項，不改變 `detect.ts`/`i18n/index.ts` 邏輯。
- 不改成自訂 dropdown 元件（已確認保留原生 `<select>`）。
- 不調整側邊欄語言切換的 DOM 位置（維持在使用者卡片下方），只調整登入頁的間距。
