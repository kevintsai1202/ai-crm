import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { fetchHealth, login as apiLogin, logout as apiLogout, TOKEN_KEY } from "../api";
import type { HealthResponse, UserResponse } from "../types";

/** Auth 與 health 全域狀態介面。 */
interface AuthContextValue {
  user: UserResponse | null;
  health: HealthResponse | null;
  healthError: boolean;
  isAuthed: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshHealth: () => Promise<void>;
}

/** sessionStorage key：存放非敏感 user 資訊，供頁面重整後還原 UI。 */
const USER_KEY = "ai-crm-user";

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 全域 Auth/Health Provider：集中管理登入態與後端健康檢查。
 * 函式級註解：JWT 改存 httpOnly cookie，JS 不可讀；user 資訊存 sessionStorage，
 * 頁面重整後可還原顯示名稱 / 角色，401 事件自動清除。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  // 初始化時同步還原 session（避免登入後 navigate 時 setState 尚未 flush 被 ProtectedRoute 踢回 /login）
  const [user, setUser] = useState<UserResponse | null>(() => {
    try {
      const stored = sessionStorage.getItem(USER_KEY);
      return stored ? (JSON.parse(stored) as UserResponse) : null;
    } catch {
      return null;
    }
  });
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthError, setHealthError] = useState(false);

  /** 重新讀取健康檢查，失敗採 fail-closed 紅燈。 */
  async function refreshHealth() {
    try {
      const result = await fetchHealth();
      setHealth(result);
      setHealthError(result.status !== "UP");
    } catch {
      setHealth(null);
      setHealthError(true);
    }
  }

  /** 登入：將 token 存入 sessionStorage（供 axios 攔截器自動加 Bearer header），並存 user 資訊供 UI 使用。 */
  async function login(username: string, password: string) {
    const result = await apiLogin(username, password);
    if (result.token) {
      sessionStorage.setItem(TOKEN_KEY, result.token);
    }
    sessionStorage.setItem(USER_KEY, JSON.stringify(result.user));
    // 先寫 session 再 setState：ProtectedRoute 可同步以 token/user 判定已登入
    setUser(result.user);
  }

  /** 登出：清除 token 與 user 資訊，呼叫後端清除 cookie（相容舊 cookie session）。 */
  async function logout() {
    try {
      await apiLogout();
    } catch {
      // 登出失敗仍清除前端狀態
    }
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    setUser(null);
  }

  // 啟動時測健康；從 sessionStorage 還原 user（重整後不致遺失登入卡與角色）；監聽 401 自動登出
  useEffect(() => {
    void refreshHealth();
    // 頁面重整後從 sessionStorage 還原 user 資訊（token 由 cookie 自動帶入每次請求）
    const stored = sessionStorage.getItem(USER_KEY);
    if (stored) {
      try {
        setUser(JSON.parse(stored) as UserResponse);
      } catch {
        sessionStorage.removeItem(USER_KEY);
      }
    }
    const onLogout = () => {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USER_KEY);
      setUser(null);
    };
    window.addEventListener("auth:logout", onLogout);
    return () => window.removeEventListener("auth:logout", onLogout);
  }, []);

  // isAuthed 同時看 React state 與 sessionStorage token，
  // 避免 await login() 後立即 navigate 時 setUser 尚未 commit 被誤判未登入。
  const hasSessionToken =
    typeof sessionStorage !== "undefined" && !!sessionStorage.getItem(TOKEN_KEY);
  const value: AuthContextValue = {
    user,
    health,
    healthError,
    isAuthed: !!user || hasSessionToken,
    login,
    logout,
    refreshHealth
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** 取用 Auth context 的 hook。 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth 必須在 AuthProvider 內使用");
  return ctx;
}
