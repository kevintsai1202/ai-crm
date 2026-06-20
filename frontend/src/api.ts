import axios from "axios";
import type {
  AdminUser,
  AgentTraceResponse,
  ChatResponse,
  CustomerDetail,
  CustomerOptionsResponse,
  CustomerSummary,
  DashboardLayoutResponse,
  DashboardReports,
  DashboardSummary,
  HealthResponse,
  InteractionResponse,
  LoginResponse,
  OpportunityResponse,
  PageResponse,
  Role,
  PortfolioAssessment,
  DrilldownResponse,
  RfmResponse,
  SentimentRadarResponse,
  UsageSummaryResponse
} from "./types";

const TOKEN_KEY = "ai-crm-token";

/**
 * 共用 Axios client，集中處理 baseURL、JWT 注入與 401 清理。
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 10000
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event("auth:logout"));
    }
    return Promise.reject(error);
  }
);

/**
 * 儲存 JWT token。
 */
export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * 讀取目前 JWT token。
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * 清除 JWT token。
 */
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * 呼叫健康檢查 API。
 */
export async function fetchHealth() {
  const { data } = await apiClient.get<HealthResponse>("/health");
  return data;
}

/**
 * 登入並取得 JWT。
 */
export async function login(username: string, password: string) {
  const { data } = await apiClient.post<LoginResponse>("/auth/login", { username, password });
  return data;
}

/**
 * 讀取 Dashboard 統計。
 */
export async function fetchDashboard() {
  const { data } = await apiClient.get<DashboardSummary>("/dashboard/summary");
  return data;
}

/**
 * 讀取 CRM 經典圖表報表資料。
 */
export async function fetchDashboardReports() {
  const { data } = await apiClient.get<DashboardReports>("/dashboard/reports");
  return data;
}

/**
 * 查詢客戶列表，支援多條件篩選與分頁。
 *
 * @param params 查詢參數
 * @returns 分頁客戶摘要
 */
export async function fetchCustomers(params: { keyword?: string; industry?: string; owner?: string; page?: number; size?: number }) {
  const { data } = await apiClient.get<PageResponse<CustomerSummary>>("/customers", {
    params: { page: params.page ?? 0, size: params.size ?? 10, keyword: params.keyword, industry: params.industry, owner: params.owner }
  });
  return data;
}

/**
 * 查詢客戶詳情。
 */
export async function fetchCustomerDetail(id: number) {
  const { data } = await apiClient.get<CustomerDetail>(`/customers/${id}`);
  return data;
}

/**
 * 呼叫 AI 助理分析客戶。
 */
export async function askAssistant(customerId: number, message: string) {
  const { data } = await apiClient.post<ChatResponse>("/ai/chat", { customerId, message });
  return data;
}

/**
 * 呼叫 AI 助理分析客戶 (SSE 串流版)。
 * 函式級註解：透過 fetch 與 ReadableStream 實現對後端 SSE 串流的讀取，逐步解碼 JSON 並回傳給 UI 做打字機渲染。
 * 
 * @param customerId 客戶 ID
 * @param message 聊天訊息
 * @param onChunk 收到每一個資料區段時的回呼函式
 * @param onDone 串流發送完成時的回呼函式
 * @param onError 發生錯誤時的回呼函式
 */
export async function askAssistantStream(
  customerId: number,
  message: string,
  onChunk: (chunk: { type: "content" | "citations" | "risk" | "callId"; delta?: string; citations?: any[]; risk?: any; callId?: number }) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const token = getToken();
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  
  try {
    const response = await fetch(`${baseUrl}/ai/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ customerId, message })
    });

    if (!response.ok) {
      if (response.status === 401) {
        clearToken();
        window.dispatchEvent(new Event("auth:logout"));
      }
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }

    const reader = response.body?.getReader();
    if (!reader) {
      throw new Error("回應的主體 (response body) 為空或無法讀取");
    }

    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        
        if (trimmed.startsWith("data:")) {
          const dataContent = trimmed.substring(5).trim();
          if (dataContent === "[DONE]") {
            onDone();
            return;
          }
          try {
            const parsed = JSON.parse(dataContent);
            onChunk(parsed);
          } catch (e) {
            console.error("解析 SSE JSON 片段失敗：", dataContent, e);
          }
        }
      }
    }
    
    onDone();
  } catch (error) {
    onError(error);
  }
}

/**
 * 取得新增客戶表單的下拉選項（現有產業與負責業務清單）。
 */
export async function fetchCustomerOptions() {
  const { data } = await apiClient.get<CustomerOptionsResponse>("/customers/options");
  return data;
}

/**
 * 新增客戶。
 */
export async function createCustomer(data: { name: string; email: string; phone: string; taxId: string; industry: string; ownerId: number }) {
  const { data: result } = await apiClient.post<CustomerSummary>("/customers", data);
  return result;
}

/** 管理員：列出所有帳號。 */
export async function fetchAdminUsers() {
  const { data } = await apiClient.get<AdminUser[]>("/admin/users");
  return data;
}

/** 管理員：新增帳號。 */
export async function createAdminUser(data: { username: string; displayName: string; role: Role; password: string }) {
  const { data: result } = await apiClient.post<AdminUser>("/admin/users", data);
  return result;
}

/** 管理員：編輯帳號顯示名稱與角色。 */
export async function updateAdminUser(id: number, data: { displayName: string; role: Role }) {
  const { data: result } = await apiClient.put<AdminUser>(`/admin/users/${id}`, data);
  return result;
}

/** 管理員：重設帳號密碼。 */
export async function resetAdminUserPassword(id: number, password: string) {
  const { data: result } = await apiClient.put<AdminUser>(`/admin/users/${id}/password`, { password });
  return result;
}

/** 管理員：啟用/停用帳號。 */
export async function setAdminUserEnabled(id: number, enabled: boolean) {
  const { data: result } = await apiClient.put<AdminUser>(`/admin/users/${id}/enabled`, { enabled });
  return result;
}

/**
 * 新增客戶互動紀錄。
 */
export async function addInteraction(customerId: number, data: { type: string; occurredAt: string; content: string }) {
  const { data: result } = await apiClient.post<InteractionResponse>(`/customers/${customerId}/interactions`, data);
  return result;
}

/**
 * 更新商機階段（Kanban 拖拽）。
 *
 * @param id 商機 ID
 * @param stage 新階段
 * @returns 更新後的商機資料
 */
export async function updateOpportunityStage(id: number, stage: string) {
  const { data } = await apiClient.put<OpportunityResponse>(`/opportunities/${id}/stage`, { stage });
  return data;
}

/**
 * 新增商機（掛在指定客戶下）。
 *
 * @param payload 新增商機資料（customerId / 名稱 / 階段 / 金額 / 預計成交日 / 類型）
 * @returns 新增後的商機資料
 */
export async function createOpportunity(payload: {
  customerId: number;
  name: string;
  stage: string;
  amount: number;
  expectedCloseDate: string | null;
  type: string;
}) {
  const { data } = await apiClient.post<OpportunityResponse>(`/opportunities`, payload);
  return data;
}

/**
 * 查詢 Agent Trace。
 */
export async function fetchAgentTrace(customerId: number) {
  const { data } = await apiClient.get<AgentTraceResponse>(`/agent/customers/${customerId}/trace`);
  return data;
}

/**
 * 取得指定客戶的 360 度整體評估報告。
 *
 * @param customerId 客戶 ID
 * @returns 含評估報告（Markdown）、引用與風險
 */
export async function fetchCustomerAssessment(customerId: number) {
  const { data } = await apiClient.get<ChatResponse>(`/ai/customers/${customerId}/assessment`);
  return data;
}

/**
 * 取得 Portfolio 跨客戶整體評估報告。
 *
 * @returns 含評估報告（Markdown）與彙總統計
 */
export async function fetchPortfolioAssessment() {
  const { data } = await apiClient.get<PortfolioAssessment>("/ai/portfolio/assessment");
  return data;
}

/**
 * 取得儀表板圖表的下鑽明細。
 *
 * @param type 下鑽類型（stage / forecastMonth / renewalMonth / industry / owner / risk）
 * @param key 對應鍵值
 * @returns 下鑽明細清單
 */
export async function fetchDrilldown(type: string, key: string) {
  const { data } = await apiClient.get<DrilldownResponse>("/dashboard/drilldown", { params: { type, key } });
  return data;
}

/**
 * 取得 RFM 客戶分群清單（R/F/M 分數與分群標籤）。
 *
 * @returns RFM 分群清單
 */
export async function fetchRfm() {
  const { data } = await apiClient.get<RfmResponse[]>("/dashboard/rfm");
  return data;
}

/**
 * 取得 AI 用量彙總（限 MANAGER / ADMIN）。
 *
 * @returns 用量彙總
 */
export async function fetchAiUsage() {
  const { data } = await apiClient.get<UsageSummaryResponse>("/ai/usage");
  return data;
}

/**
 * 取得情緒意圖雷達聚合資料（SP6，5 區塊）。
 *
 * @returns 意圖分布、情緒趨勢、高風險互動、流失雷達、優先關懷
 */
export async function fetchSentimentRadar() {
  const { data } = await apiClient.get<SentimentRadarResponse>("/dashboard/sentiment");
  return data;
}

/**
 * 產生示範資料（限 ADMIN）：依指定客戶數生成含情緒 / 意圖的互動樣本。
 *
 * @param customers 欲生成的客戶數量
 */
export async function generateDemoData(customers: number) {
  await apiClient.post("/dev/generate-demo-data", null, { params: { customers } });
}

/**
 * 對某筆 AI 回答送出採納 / 拒絕回饋。
 *
 * @param callId AI 呼叫紀錄 id
 * @param decision ADOPTED 或 REJECTED
 * @param note 選填備註
 */
export async function sendAiFeedback(callId: number, decision: "ADOPTED" | "REJECTED", note?: string) {
  await apiClient.post(`/ai/calls/${callId}/feedback`, { decision, note });
}

/**
 * 取得本人儀表板版面（可見區塊有序 id 陣列）；未設定時回 null。
 */
export async function fetchDashboardLayout() {
  const { data } = await apiClient.get<DashboardLayoutResponse>("/me/preferences/dashboard-layout");
  return data.visibleOrder;
}

/**
 * 儲存本人儀表板版面（可見區塊有序 id 陣列）。
 *
 * @param visibleOrder 區塊 id 有序陣列
 */
export async function saveDashboardLayout(visibleOrder: string[]) {
  await apiClient.put("/me/preferences/dashboard-layout", { visibleOrder });
}
