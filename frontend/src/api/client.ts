import axios from "axios";

/** sessionStorage key：存放 JWT，由 axios 攔截器自動加入 Authorization header。 */
export const TOKEN_KEY = "ai-crm-token";

/**
 * 共用 Axios client：baseURL 預設 /api。
 * 不使用 withCredentials，改以 Bearer token（sessionStorage）做跨域認證。
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 30000
});

/** 從 sessionStorage 讀取 JWT，回傳含 Authorization header 的物件。 */
export function getAuthHeaders(): Record<string, string> {
  const token = sessionStorage.getItem(TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// 請求攔截器：自動將 sessionStorage 的 JWT 加入 Authorization header
apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * AI 端點專用逾時（毫秒）。
 */
export const AI_TIMEOUT = 120000;

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event("auth:logout"));
    }
    return Promise.reject(error);
  }
);
