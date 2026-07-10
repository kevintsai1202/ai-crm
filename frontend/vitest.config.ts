import { defineConfig } from "vitest/config";

/**
 * Vitest 設定：純函式單元測試（不需瀏覽器）。
 */
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"]
  }
});
