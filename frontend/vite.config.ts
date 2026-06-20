import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/**
 * Vite 設定，將 /api 代理到 Spring Boot 後端。
 */
export default defineConfig({
  plugins: [react()],
  // react-draggable 4.7.0 的 log() 會在每次拖曳/縮放開始時讀取 process.env.DRAGGABLE_DEBUG，
  // 但瀏覽器無 process 物件，Vite 也不會自動替換此鍵 → 丟出 "process is not defined" 而中斷整個
  // 拖放/縮放事件。靜態替換為 false 即可消除此參照，恢復 react-grid-layout 的拖曳與角落縮放。
  define: {
    "process.env.DRAGGABLE_DEBUG": "false"
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:18080",
        changeOrigin: true
      }
    }
  }
});
