import { defineConfig } from "@playwright/test";

/**
 * Playwright 設定：對本機 dev server 跑 SP1 煙霧測試。
 * 函式級註解：自動啟動 vite dev（127.0.0.1:5173），測試前等待就緒；後端需另行啟動（18080）。
 */
export default defineConfig({
  testDir: "./e2e",
  // 放寬至 90s：dev 後端走真 LLM，SSE 以 40ms/字串流，長答案 + callId(串流末) 需較久
  timeout: 90000,
  use: { baseURL: "http://127.0.0.1:5173" },
  webServer: {
    command: "pnpm run dev",
    url: "http://127.0.0.1:5173",
    reuseExistingServer: true,
    timeout: 60000
  }
});
