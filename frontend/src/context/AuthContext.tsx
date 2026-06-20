import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { clearToken, fetchHealth, getToken, login as apiLogin, saveToken } from "../api";
import type { HealthResponse, UserResponse } from "../types";

/** Auth 與 health 全域狀態介面。 */
interface AuthContextValue {
  user: UserResponse | null;
  health: HealthResponse | null;
  healthError: boolean;
  isAuthed: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshHealth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 解碼 Base64Url 字串為 UTF-8 文字（JWT payload 用；displayName 為中文需走 UTF-8）。
 */
function base64UrlDecode(input: string): string {
  const b64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = b64 + (b64.length % 4 ? "=".repeat(4 - (b64.length % 4)) : "");
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder("utf-8").decode(bytes);
}

/**
 * 從 JWT 還原使用者（不驗章，僅供前端重整後還原顯示用；伺服器仍會驗證每次請求）。
 * 函式級註解：payload 含 sub/uid/name/role/exp；過期或格式錯誤回 null。
 */
function decodeUserFromToken(token: string): UserResponse | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(base64UrlDecode(parts[1]));
    // 已過期視為無效
    if (payload.exp && payload.exp * 1000 < Date.now()) return null;
    return {
      id: typeof payload.uid === "number" ? payload.uid : 0,
      username: payload.sub ?? "",
      displayName: payload.name ?? payload.sub ?? "",
      role: payload.role
    };
  } catch {
    return null;
  }
}

/**
 * 全域 Auth/Health Provider：集中管理登入態與後端健康檢查。
 * 函式級註解：監聽 api.ts 派發的 auth:logout 事件，401 時自動清除使用者狀態。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
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

  /** 登入並保存 token。 */
  async function login(username: string, password: string) {
    const result = await apiLogin(username, password);
    saveToken(result.token);
    setUser(result.user);
  }

  /** 登出並清除全部 auth 狀態。 */
  function logout() {
    clearToken();
    setUser(null);
  }

  // 啟動時測健康、從既有 token 還原使用者（重整後不致遺失登入卡與角色功能）；監聽 401 事件以自動登出
  useEffect(() => {
    void refreshHealth();
    // 重整後 token 仍在但 user 為 null 時，從 token 還原使用者；過期/壞 token 則清除
    const token = getToken();
    if (token) {
      const restored = decodeUserFromToken(token);
      if (restored) setUser(restored);
      else clearToken();
    }
    const onLogout = () => setUser(null);
    window.addEventListener("auth:logout", onLogout);
    return () => window.removeEventListener("auth:logout", onLogout);
  }, []);

  const value: AuthContextValue = {
    user,
    health,
    healthError,
    isAuthed: !!getToken(),
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
