import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import "./styles.css";
import "./i18n"; // 觸發 i18next 全域初始化（須早於首次 render）

/**
 * React 入口，掛載 AI CRM 工作台。
 * 函式級註解：以 BrowserRouter 提供路由、AuthProvider 提供全域 auth/health 狀態。
 */
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>
);
