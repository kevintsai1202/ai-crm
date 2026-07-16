import { defineConfig } from "@playwright/test";

/**
 * Playwright 設定：對本機 dev server 跑 SP1 煙霧測試。
 * 函式級註解：自動啟動 vite dev（127.0.0.1:5173），測試前等待就緒；後端需另行啟動（18080）。
 */
export default defineConfig({
  testDir: "./e2e",
  // 放寬至 90s：dev 後端走真 LLM，SSE 以 40ms/字串流，長答案 + callId(串流末) 需較久
  timeout: 90000,
  // 序列執行：V21–V27 E2E 皆操作同一份後端全域 AI 設定（provider/模型/用途 assignment）
  // 與共享 DB，平行會互相干擾；固定單一 worker 確保階段間彼此隔離。
  workers: 1,
  // locale: "zh-TW" — 未明確指定語言的既有測試（如 sp1-smoke、sp7-layout）沿用中文斷言；
  // mobile-rwd.spec.ts 以 addInitScript 寫入 localStorage 指定語言，優先權高於瀏覽器語系，不受影響。
  use: { baseURL: "http://127.0.0.1:5173", locale: "zh-TW" },
  webServer: {
    command: "pnpm run dev",
    url: "http://127.0.0.1:5173",
    reuseExistingServer: true,
    timeout: 60000
  }
});
