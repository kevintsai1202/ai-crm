import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

/**
 * Vitest 設定：改用 jsdom 以支援元件測試（含 i18n 初始化需要的 window/localStorage）。
 * 收錄 .ts 與 .tsx 測試；setup 檔註冊 jest-dom matcher 與每次測試後 cleanup。
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test/setup.ts"]
  }
});
