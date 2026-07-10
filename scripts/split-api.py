# -*- coding: utf-8 -*-
"""一次性將 frontend/src/api.ts 拆成 api/client.ts + api/rest.ts + api/index.ts。"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
src = root / "frontend" / "src" / "api.ts"
out = root / "frontend" / "src" / "api"
out.mkdir(exist_ok=True)

text = src.read_text(encoding="utf-8")

client = '''import axios from "axios";

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
'''

# 從 fetchHealth 起為業務 API
m = re.search(r"/\*\*\s*\n \* 呼叫健康檢查", text)
if not m:
    m = re.search(r"export async function fetchHealth", text)
if not m:
    raise SystemExit("cannot find fetchHealth")

body = text[m.start() :]

# 移除原檔內的 private helpers 重複：getAuthHeaders 已在 client
# body 中 readSseStream 仍需要

rest_header = '''import type {
  AdminUser,
  AgentTraceResponse,
  AiCallHistoryItem,
  ChatMessageHistoryItem,
  ChatResponse,
  ContactResponse,
  CustomerDetail,
  CustomerOptionsResponse,
  CustomerSummary,
  DashboardLayoutResponse,
  DashboardReports,
  DashboardSummary,
  HealthResponse,
  InteractionResponse,
  LoginResponse,
  ManagerAnalyticsResponse,
  ManagerInsightResponse,
  OpportunityResponse,
  PageResponse,
  Role,
  PortfolioAssessment,
  DrilldownResponse,
  RfmResponse,
  SentimentRadarResponse,
  UsageSummaryResponse
} from "../types";
import { apiClient, getAuthHeaders, AI_TIMEOUT } from "./client";

'''

# rest 若仍含 function getAuthHeaders 定義會衝突——原 body 從 fetchHealth 起，不應再有
# 但 readSse 之前的 SseChunk 型別在 askAssistant 之後——OK

(out / "client.ts").write_text(client, encoding="utf-8")
(out / "rest.ts").write_text(rest_header + body, encoding="utf-8")
(out / "index.ts").write_text(
    '''/** API 模組入口：client + rest re-export，維持 `from \"../api\"` 相容。 */
export { TOKEN_KEY, apiClient, getAuthHeaders, AI_TIMEOUT } from "./client";
export * from "./rest";
''',
    encoding="utf-8",
)
src.unlink()
print("OK: api split, rest bytes", (out / "rest.ts").stat().st_size)
