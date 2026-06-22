import axios from "axios";
import type {
  AdminUser,
  AgentTraceResponse,
  AiCallHistoryItem,
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
} from "./types";

/**
 * 共用 Axios client：baseURL 預設 /api（Caddy 代理至後端），
 * withCredentials 讓瀏覽器隨請求帶上 httpOnly cookie。
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 30000,
  withCredentials: true
});

/**
 * AI 端點專用逾時（毫秒）。
 * 一般 CRUD 用全域 10 秒即可,但 AI 評估/對話需做 embedding + 產生長報告,
 * 動輒數十秒,沿用 10 秒會在後端還在跑時就被 axios abort,前端誤判「失敗」。
 * 故 AI 同步端點 per-request 放寬到 120 秒(串流對話走原生 fetch 不受此限)。
 */
const AI_TIMEOUT = 120000;

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event("auth:logout"));
    }
    return Promise.reject(error);
  }
);

/**
 * 呼叫健康檢查 API。
 */
export async function fetchHealth() {
  const { data } = await apiClient.get<HealthResponse>("/health");
  return data;
}

/**
 * 登入：後端在 Set-Cookie 設定 httpOnly token，response body 僅含 user 資訊。
 */
export async function login(username: string, password: string) {
  const { data } = await apiClient.post<LoginResponse>("/auth/login", { username, password });
  return data;
}

/**
 * 登出：呼叫後端清除 httpOnly cookie。
 */
export async function logout() {
  await apiClient.post("/auth/logout");
}

/**
 * 讀取團隊診斷快取（未產生回 null）。
 */
export async function fetchTeamInsight() {
  const { data } = await apiClient.get<ManagerInsightResponse | "">("/manager/insights/team");
  return data || null;
}

/**
 * 重新產生團隊整體診斷 + 逐業務點評（呼叫 LLM，逾時放寬）。
 */
export async function generateTeamInsight() {
  const { data } = await apiClient.post<ManagerInsightResponse>("/manager/insights/team", null, { timeout: AI_TIMEOUT });
  return data;
}

/**
 * 讀取個別業務 coaching 快取（未產生回 null）。
 *
 * @param owner 業務名
 */
export async function fetchOwnerInsight(owner: string) {
  const { data } = await apiClient.get<ManagerInsightResponse | "">("/manager/insights/owner", { params: { owner } });
  return data || null;
}

/**
 * 產生個別業務 coaching 輔導報告（呼叫 LLM，逾時放寬）。
 *
 * @param owner 業務名
 */
export async function generateOwnerInsight(owner: string) {
  const { data } = await apiClient.post<ManagerInsightResponse>("/manager/insights/owner", null, { params: { owner }, timeout: AI_TIMEOUT });
  return data;
}

/**
 * 讀取 Manager 業務分析（團隊總覽 + 各業務統計，依成交金額降序）。
 */
export async function fetchManagerAnalytics() {
  const { data } = await apiClient.get<ManagerAnalyticsResponse>("/manager/analytics");
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
export async function fetchCustomers(params: {
  keyword?: string; industry?: string; owner?: string; page?: number; size?: number;
  status?: string; riskLevel?: string; renewalFrom?: string; renewalTo?: string;
}) {
  // 僅帶有值的條件(空字串視為未填,不送出),配合後端動態 Specification 組條件
  const query: Record<string, string | number> = { page: params.page ?? 0, size: params.size ?? 10 };
  if (params.keyword) query.keyword = params.keyword;
  if (params.industry) query.industry = params.industry;
  if (params.owner) query.owner = params.owner;
  if (params.status) query.status = params.status;
  if (params.riskLevel) query.riskLevel = params.riskLevel;
  if (params.renewalFrom) query.renewalFrom = params.renewalFrom;
  if (params.renewalTo) query.renewalTo = params.renewalTo;
  const { data } = await apiClient.get<PageResponse<CustomerSummary>>("/customers", { params: query });
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
  const { data } = await apiClient.post<ChatResponse>("/ai/chat", { customerId, message }, { timeout: AI_TIMEOUT });
  return data;
}

/** SSE 串流資料區段：content（內容 delta）、citations（引用）、risk（風險）、callId（呼叫紀錄 id）。 */
export type SseChunk = { type: "content" | "citations" | "risk" | "callId"; delta?: string; citations?: any[]; risk?: any; callId?: number };

/**
 * 共用：讀取一個 SSE (text/event-stream) Response 並逐塊回呼。
 * 函式級註解：解析後端每行 `data: <json>`，遇 `[DONE]` 結束；JSON 解析失敗只記 log 不中斷整體串流。
 * 對話與整體評估共用此讀取器，避免重複維護解析邏輯。
 *
 * @param response fetch 取得的串流 Response（呼叫端須已確認 response.ok）
 * @param onChunk 收到每一個資料區段時的回呼
 * @param onDone 串流完成時的回呼
 */
async function readSseStream(response: Response, onChunk: (chunk: SseChunk) => void, onDone: () => void) {
  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error("回應的主體 (response body) 為空或無法讀取");
  }
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith("data:")) continue;
      const dataContent = trimmed.substring(5).trim();
      if (dataContent === "[DONE]") {
        onDone();
        return;
      }
      try {
        onChunk(JSON.parse(dataContent));
      } catch (e) {
        console.error("解析 SSE JSON 片段失敗：", dataContent, e);
      }
    }
  }
  onDone();
}

/**
 * 共用：401 時廣播登出事件（cookie 由後端清除，前端只需清除 UI 狀態）。
 *
 * @param response fetch 回應
 */
function handleStreamUnauthorized(response: Response) {
  if (response.status === 401) {
    window.dispatchEvent(new Event("auth:logout"));
  }
}

/**
 * 呼叫 AI 助理分析客戶 (SSE 串流版)。
 * 函式級註解：透過 fetch + ReadableStream 讀取後端 SSE 串流，逐步解碼 JSON 回呼 UI 做打字機渲染。
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
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/ai/chat`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream"
      },
      body: JSON.stringify({ customerId, message })
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
  } catch (error) {
    onError(error);
  }
}

/**
 * 取得客戶 360 度整體評估報告 (SSE 串流版)：邊產生邊渲染，避免長報告撞前端/閘道逾時。
 * 函式級註解：GET + Accept: text/event-stream 命中後端串流端點，與對話共用 readSseStream。
 *
 * @param customerId 客戶 ID
 * @param onChunk 收到每一個資料區段時的回呼函式
 * @param onDone 串流發送完成時的回呼函式
 * @param onError 發生錯誤時的回呼函式
 */
export async function fetchCustomerAssessmentStream(
  customerId: number,
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/ai/customers/${customerId}/assessment`, {
      method: "GET",
      credentials: "include",
      headers: {
        Accept: "text/event-stream"
      }
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
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

/**
 * 更新客戶完整資料（PUT /customers/{id}）。
 * 函式級註解：日期欄位（合約起始 / 到期 / 續約）空值請傳 null，後端不接受空字串。
 *
 * @param id 客戶 ID
 * @param data 客戶完整欄位（含選填日期）
 * @returns 更新後的客戶摘要
 */
export async function updateCustomer(
  id: number,
  data: {
    name: string;
    email: string;
    phone: string;
    taxId: string;
    industry: string;
    ownerId: number;
    contractStartDate?: string | null;
    contractEndDate?: string | null;
    renewalDueDate?: string | null;
  }
) {
  const { data: result } = await apiClient.put<CustomerSummary>(`/customers/${id}`, data);
  return result;
}

/**
 * 刪除客戶（DELETE /customers/{id}，回 204）。
 *
 * @param id 客戶 ID
 */
export async function deleteCustomer(id: number) {
  await apiClient.delete(`/customers/${id}`);
}

/**
 * 為指定客戶新增聯絡人（POST /customers/{id}/contacts）。
 *
 * @param customerId 客戶 ID
 * @param data 聯絡人資料（姓名 / 職稱 / Email）
 * @returns 新增後的聯絡人
 */
export async function createContact(customerId: number, data: { name: string; title: string; email: string }) {
  const { data: result } = await apiClient.post<ContactResponse>(`/customers/${customerId}/contacts`, data);
  return result;
}

/**
 * 更新聯絡人（PUT /contacts/{id}）。
 *
 * @param id 聯絡人 ID
 * @param data 聯絡人資料（姓名 / 職稱 / Email）
 * @returns 更新後的聯絡人
 */
export async function updateContact(id: number, data: { name: string; title: string; email: string }) {
  const { data: result } = await apiClient.put<ContactResponse>(`/contacts/${id}`, data);
  return result;
}

/**
 * 刪除聯絡人（DELETE /contacts/{id}，回 204）。
 *
 * @param id 聯絡人 ID
 */
export async function deleteContact(id: number) {
  await apiClient.delete(`/contacts/${id}`);
}

/**
 * 更新商機（PUT /opportunities/{id}）；階段不在此更新。
 * 函式級註解：預計成交日空值請傳 null，避免後端解析空字串失敗。
 *
 * @param id 商機 ID
 * @param data 商機欄位（名稱 / 金額 / 預計成交日 / 類型）
 * @returns 更新後的商機
 */
export async function updateOpportunity(
  id: number,
  data: { name: string; amount: number; expectedCloseDate: string | null; type: string }
) {
  const { data: result } = await apiClient.put<OpportunityResponse>(`/opportunities/${id}`, data);
  return result;
}

/**
 * 刪除商機（DELETE /opportunities/{id}，回 204）。
 *
 * @param id 商機 ID
 */
export async function deleteOpportunity(id: number) {
  await apiClient.delete(`/opportunities/${id}`);
}

/**
 * 更新互動紀錄（PUT /interactions/{id}）。
 * 函式級註解：occurredAt 直接送 datetime-local 值（yyyy-MM-ddTHH:mm），不要轉 ISO/UTC。
 *
 * @param id 互動 ID
 * @param data 互動欄位（類型 / 發生時間 / 內容）
 * @returns 更新後的互動
 */
export async function updateInteraction(id: number, data: { type: string; occurredAt: string; content: string }) {
  const { data: result } = await apiClient.put<InteractionResponse>(`/interactions/${id}`, data);
  return result;
}

/**
 * 刪除互動紀錄（DELETE /interactions/{id}，回 204）。
 *
 * @param id 互動 ID
 */
export async function deleteInteraction(id: number) {
  await apiClient.delete(`/interactions/${id}`);
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
 * 查詢某客戶的歷次 AI 呼叫紀錄（新到舊），供「AI 歷程」Modal 呈現。
 */
export async function fetchCustomerAiCalls(customerId: number) {
  const { data } = await apiClient.get<AiCallHistoryItem[]>(`/ai/customers/${customerId}/calls`);
  return data;
}

/**
 * 取得團隊診斷（TEAM_ANALYSIS）的 AI 呼叫歷程。
 */
export async function fetchTeamInsightCalls() {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/manager/insights/team/calls");
  return data;
}

/**
 * 取得指定業務 coaching（OWNER_COACHING）的 AI 呼叫歷程。
 *
 * @param owner 業務顯示名稱
 */
export async function fetchOwnerInsightCalls(owner: string) {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/manager/insights/owner/calls", { params: { owner } });
  return data;
}

/**
 * 取得全公司 Portfolio 評估（PORTFOLIO）的 AI 呼叫歷程。
 */
export async function fetchPortfolioCalls() {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/ai/portfolio/calls");
  return data;
}

/**
 * 取得指定客戶的 360 度整體評估報告。
 *
 * @param customerId 客戶 ID
 * @returns 含評估報告（Markdown）、引用與風險
 */
export async function fetchCustomerAssessment(customerId: number) {
  const { data } = await apiClient.get<ChatResponse>(`/ai/customers/${customerId}/assessment`, { timeout: AI_TIMEOUT });
  return data;
}

/**
 * 取得 Portfolio 跨客戶整體評估報告。
 *
 * @returns 含評估報告（Markdown）與彙總統計
 */
export async function fetchPortfolioAssessment() {
  const { data } = await apiClient.get<PortfolioAssessment>("/ai/portfolio/assessment", { timeout: AI_TIMEOUT });
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

/**
 * 取得 Portfolio 全公司整體評估報告（SSE 串流版）。
 * 函式級註解：GET + Accept: text/event-stream，與 fetchCustomerAssessmentStream 共用 readSseStream 解析器。
 *
 * @param onChunk 收到每一個資料區段時的回呼
 * @param onDone 串流完成時的回呼
 * @param onError 發生錯誤時的回呼
 */
export async function streamPortfolioAssessment(
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/ai/portfolio/assessment`, {
      method: "GET",
      credentials: "include",
      headers: {
        Accept: "text/event-stream"
      }
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
  } catch (error) {
    onError(error);
  }
}

/**
 * 以 SSE 串流推送團隊整體診斷（POST + Accept: text/event-stream）。
 *
 * @param onChunk 收到每一個資料區段時的回呼
 * @param onDone 串流完成時的回呼
 * @param onError 發生錯誤時的回呼
 */
export async function streamTeamInsight(
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/manager/insights/team`, {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "text/event-stream"
      }
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
  } catch (error) {
    onError(error);
  }
}

/**
 * 以 SSE 串流推送個別業務 coaching（POST + Accept: text/event-stream）。
 *
 * @param owner 業務顯示名稱
 * @param onChunk 收到每一個資料區段時的回呼
 * @param onDone 串流完成時的回呼
 * @param onError 發生錯誤時的回呼
 */
export async function streamOwnerInsight(
  owner: string,
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/manager/insights/owner?owner=${encodeURIComponent(owner)}`, {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "text/event-stream"
      }
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
  } catch (error) {
    onError(error);
  }
}

/** 取得 AI 設定（限 ADMIN）。 */
export async function fetchAiSettings() {
  const { data } = await apiClient.get<import("./types").AiSettingsResponse>("/admin/settings/ai");
  return data;
}

/** 更新 AI 設定（限 ADMIN），回傳更新後的設定。 */
export async function saveAiSettings(
  model: string,
  providerId: number | null,
  modelOptions: import("./types").ModelOptionItem[]
) {
  const { data } = await apiClient.put<import("./types").AiSettingsResponse>(
    "/admin/settings/ai",
    { model, providerId, modelOptions }
  );
  return data;
}

/** 新增 AI 供應商（限 ADMIN）。 */
export async function createAiProvider(name: string, baseUrl: string | null, apiKey: string) {
  const { data } = await apiClient.post<import("./types").AiProviderItem>(
    "/admin/settings/ai/providers",
    { name, baseUrl, apiKey }
  );
  return data;
}

/** 更新 AI 供應商；apiKey 為 null 代表保留現有金鑰。 */
export async function updateAiProvider(
  id: number,
  name: string,
  baseUrl: string,
  apiKey: string | null
) {
  const { data } = await apiClient.put<import("./types").AiProviderItem>(
    `/admin/settings/ai/providers/${id}`,
    { name, baseUrl, apiKey }
  );
  return data;
}

/** 刪除 AI 供應商（限 ADMIN）。 */
export async function deleteAiProvider(id: number) {
  await apiClient.delete(`/admin/settings/ai/providers/${id}`);
}

/**
 * 模型競速測試（SSE 串流）：以指定 model 對問題發起 LLM 呼叫，限 ADMIN。
 * 函式級註解：message 與 model 均傳入後端；前端並行呼叫多次以比較不同模型速度。
 *
 * @param message 測試問題
 * @param model 要測試的模型名
 * @param onChunk 收到內容 chunk 的回呼
 * @param onDone 串流結束回呼
 * @param onError 錯誤回呼
 */
export async function streamModelTest(
  message: string,
  model: string,
  providerId: number | null,
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/admin/settings/ai/test`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream"
      },
      // _t 時間戳確保每次 POST 請求唯一，避免 CDN / proxy 回傳 cached response
      body: JSON.stringify({ message, model, providerId, _t: new Date().getTime() })
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
  } catch (error) {
    onError(error);
  }
}

/**
 * 多模型競速評分（SSE 串流）：以 claude-opus-4-8 評審速度、token 效率與回答品質。
 */
export async function streamModelScore(
  results: import("./types").ModelResultItem[],
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  try {
    const response = await fetch(`${baseUrl}/admin/settings/ai/score`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream"
      },
      body: JSON.stringify({ results })
    });
    if (!response.ok) {
      handleStreamUnauthorized(response);
      throw new Error(`HTTP 錯誤！狀態碼：${response.status}`);
    }
    await readSseStream(response, onChunk, onDone);
  } catch (error) {
    onError(error);
  }
}

/** 取得模型評分 AI 歷程（MODEL_EVAL）。 */
export async function fetchModelScoreCalls() {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/admin/settings/ai/score/calls");
  return data;
}
