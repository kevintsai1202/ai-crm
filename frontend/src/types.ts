export type Role = "SALES" | "MANAGER" | "ADMIN";
export type CustomerStatus = "ACTIVE" | "INACTIVE" | "LEVERAGED";
export type InteractionType = "PHONE" | "MEETING" | "EMAIL" | "SUPPORT_TICKET";
export type OpportunityStage = "QUALIFICATION" | "PROPOSAL" | "NEGOTIATION" | "CLOSED_WON" | "CLOSED_LOST";

export interface UserResponse {
  id: number;
  username: string;
  displayName: string;
  role: Role;
}

/** 可指派的負責業務選項（帳號 id + 顯示名稱）。 */
export interface OwnerOption {
  id: number;
  displayName: string;
}

/** 新增客戶表單下拉選項：現有的不重複產業與可指派的負責業務（SALES 帳號）清單。 */
export interface CustomerOptionsResponse {
  industries: string[];
  owners: OwnerOption[];
}

/** 管理員視角的帳號資料（含啟用狀態與建立時間）。 */
export interface AdminUser {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  user: UserResponse;
}

export interface HealthResponse {
  status: "UP" | string;
  timestamp: string;
  features: Record<string, string>;
  /** 後端啟動時間（Unix ms），後端重新部署後值會改變，供前端偵測後端更新。 */
  serverStartTime?: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CustomerSummary {
  id: number;
  name: string;
  email: string;
  phone: string;
  taxId: string;
  industry: string;
  ownerName: string;
  status: CustomerStatus;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  renewalDueDate: string | null;
  lastInteractionAt: string | null;
  opportunityAmount: number;
}

export interface ContactResponse {
  id: number;
  name: string;
  title: string;
  email: string;
}

export interface InteractionResponse {
  id: number;
  type: InteractionType;
  occurredAt: string;
  content: string;
  /** 情緒分析結果：POSITIVE / NEUTRAL / NEGATIVE（SP6），未分析時為 null。 */
  sentiment?: string | null;
  /** 意圖分類結果（SP6 intent enum），未分析或無意圖時為 null。 */
  intent?: string | null;
}

export interface OpportunityResponse {
  id: number;
  name: string;
  stage: OpportunityStage;
  amount: number;
  expectedCloseDate: string | null;
  type: string;
  /** 負責業務帳號 id（SP8）。 */
  ownerId: number | null;
  /** 負責業務顯示名稱（SP8）。 */
  ownerName: string | null;
  /** 商機來源（SP8）。 */
  leadSource: "INBOUND" | "OUTBOUND" | "REFERRAL";
  /** 成交機率 0–100（SP8）。 */
  probability: number | null;
  /** 結案原因 enum 名（SP8；未結案為 null）。 */
  closeReason: string | null;
  /** 結案補充說明（SP8）。 */
  closeReasonNote: string | null;
  /** 實際成交/結案日（SP8）。 */
  actualCloseDate: string | null;
}

export interface CustomerDetail {
  customer: CustomerSummary;
  contacts: ContactResponse[];
  interactions: InteractionResponse[];
  opportunities: OpportunityResponse[];
}

export interface DashboardSummary {
  customerCount: number;
  activeOpportunityCount: number;
  opportunityAmount: number;
  highRiskCount: number;
}

export interface StageReport {
  stage: OpportunityStage;
  count: number;
  amount: number;
  /** 平均停留天數（SP13；舊後端可能缺省） */
  avgDaysInStage?: number;
  /** 超時筆數（SP13） */
  overdueCount?: number;
}

export interface MoneyChartPoint {
  label: string;
  amount: number;
  count: number;
}

/** 月營收預測點：總額 pipeline 與機率加權預測（SP8）。 */
export interface ForecastPoint {
  label: string;
  totalAmount: number;
  weightedAmount: number;
  count: number;
}

export interface ChartPoint {
  label: string;
  value: number;
}

export interface OwnerReport {
  ownerName: string;
  customerCount: number;
  opportunityAmount: number;
  highRiskCount: number;
}

export interface ActivityReport {
  customerId: number;
  customerName: string;
  type: InteractionType;
  occurredAt: string;
  content: string;
}

export interface DrilldownItem {
  customerId: number;
  customerName: string;
  industry: string;
  ownerName: string;
  riskLevel: string | null;
  title: string | null;
  stage: string | null;
  amount: number;
  date: string | null;
  status: string | null;
}

export interface DrilldownResponse {
  type: string;
  key: string;
  label: string;
  totalAmount: number;
  count: number;
  items: DrilldownItem[];
}

export interface DashboardReports {
  pipelineByStage: StageReport[];
  monthlyForecast: ForecastPoint[];
  industryBreakdown: MoneyChartPoint[];
  riskBreakdown: ChartPoint[];
  ownerLeaderboard: OwnerReport[];
  renewalForecast: MoneyChartPoint[];
  recentActivities: ActivityReport[];
}

/** RFM 客戶分群單筆：R/F/M 原始值、各維 1-5 分與分群標籤。 */
export interface RfmResponse {
  customerId: number;
  name: string;
  recencyDays: number;
  frequency: number;
  monetary: number;
  rScore: number;
  fScore: number;
  mScore: number;
  segment: string;
}

/** 意圖分布單筆（SP6）：意圖名稱與筆數。 */
export interface IntentCount {
  intent: string;
  count: number;
}

/** 月情緒趨勢單點（SP6）：月份 yyyy-MM 與三種情緒筆數。 */
export interface SentimentTrendPoint {
  month: string;
  positive: number;
  neutral: number;
  negative: number;
}

/** 高風險互動單筆（SP6）：NEGATIVE 且意圖為流失 / 客訴。 */
export interface HighRiskInteraction {
  customerId: number;
  customerName: string;
  occurredAt: string;
  type: string;
  intent: string;
  sentiment: string;
  content: string;
}

/** 流失雷達單筆（SP6）：負面 / 流失訊號 / 客訴計數與加權分數。 */
export interface ChurnRadarItem {
  customerId: number;
  name: string;
  negativeCount: number;
  churnSignalCount: number;
  complaintCount: number;
  score: number;
}

/** 優先關懷單筆（SP6）：客戶與中文關懷理由。 */
export interface PriorityCareItem {
  customerId: number;
  name: string;
  reason: string;
}

/** 情緒意圖雷達聚合結果（SP6），含 5 區塊。 */
export interface SentimentRadarResponse {
  intentDistribution: IntentCount[];
  sentimentTrend: SentimentTrendPoint[];
  highRiskInteractions: HighRiskInteraction[];
  churnRadar: ChurnRadarItem[];
  priorityCare: PriorityCareItem[];
}

export interface CitationResponse {
  title: string;
  docType: string;
  content: string;
  similarity: number;
}

export interface RiskResponse {
  churnRisk: number;
  renewalDelayRisk: number;
  reasons: string[];
}

export interface ChatResponse {
  answer: string;
  citations: CitationResponse[];
  risk: RiskResponse;
  /** AI 呼叫紀錄 id，供採納/拒絕回饋使用（SP4）。 */
  callId?: number | null;
}

export interface PortfolioAssessment {
  assessment: string;
  customerCount: number;
  highRiskCount: number;
  totalPipeline: number;
  activeOpportunityCount: number;
  /** AI 呼叫紀錄 id，供採納/拒絕回饋使用（SP4）。 */
  callId?: number | null;
}

/** AI 用量彙總（SP4，MANAGER/ADMIN 可見）。 */
export interface UsageSummaryResponse {
  totalCalls: number;
  totalTokens: number;
  realCalls: number;
  fallbackCalls: number;
  adopted: number;
  rejected: number;
}

export interface AgentStepResponse {
  order: number;
  action: string;
  status: string;
  durationMs: number;
  input: string;
  output: string;
}

export interface AgentTraceResponse {
  customerId: number;
  route: string;
  finalRecommendation: string;
  steps: AgentStepResponse[];
}

/** 單一客戶的 AI 呼叫歷史項目（對應後端 Dtos.AiCallHistoryItem）。 */
export interface AiCallHistoryItem {
  id: number;
  callType: string;
  model: string | null;
  aiEnabled: boolean;
  totalTokens: number;
  answer: string;
  createdAt: string;
}

/** 客戶對話室歷史單則（chat_messages，舊→新）。 */
export interface ChatMessageHistoryItem {
  id: number;
  /** USER 或 ASSISTANT */
  role: string;
  content: string;
  createdAt: string;
}

// ===== 我的工作檯個人 AI (SP9-B) =====

/** 工作檯待辦項目（純 DB 規則計算）。type: HIGH_RISK / RENEWAL_DUE / STALE_OPPORTUNITY。 */
export interface WorkspaceTodoItem {
  type: string;
  customerId: number;
  customerName: string;
  reason: string;
  severity: string;
}

/** AI 建議商機草稿（使用者確認後才建立）。 */
export interface SuggestedOpportunityDraft {
  customerId: number;
  customerName: string;
  name: string;
  suggestedStage: string;
  amount: number | null;
  rationale: string;
}

/** 工作推薦回應（GET 讀快取用）；summary 可能為 null（尚未產生）。 */
export interface WorkspaceRecommendation {
  summary: string | null;
  model: string | null;
  generatedAt: string | null;
  todos: WorkspaceTodoItem[];
  drafts: SuggestedOpportunityDraft[];
}

/** 下鑽來源（由儀表板 navigate 帶入 location state），供客戶頁麵包屑與返回定位使用（SP7）。 */
export interface DrilldownSource {
  from: "dashboard";
  /** 來源區塊中文名，如「流失雷達」 */
  section: string;
  /** 來源區塊 id，返回時用於捲動定位（對應 DashboardPage 區塊容器 id="block-{blockId}"） */
  blockId: string;
}

/** 儀表板版面回應（SP7）：可見區塊有序 id 陣列；尚未設定時 visibleOrder 為 null。 */
export interface DashboardLayoutResponse {
  visibleOrder: string[] | null;
}

/** 單一業務績效統計（模組 B，對應後端 Dtos.OwnerStats）。 */
export interface OwnerStats {
  ownerId: number | null;
  ownerName: string;
  customerCount: number;
  highRiskCount: number;
  pipelineAmount: number;
  activeOpportunityCount: number;
  wonAmount: number;
  wonCount: number;
  /** 成交率 0~1。 */
  winRate: number;
  /** 平均互動間隔天數（全無互動則 null）。 */
  avgDaysSinceInteraction: number | null;
  /** 平均情緒分數（無分析則 null）。 */
  avgSentimentScore: number | null;
  renewalsThisMonth: number;
  renewalsThisQuarter: number;
}

/** 團隊總覽（模組 B，對應後端 Dtos.TeamSummary）。 */
export interface TeamSummary {
  totalCustomers: number;
  totalWonAmount: number;
  totalPipeline: number;
  totalHighRisk: number;
  /** 各業務成交率平均 0~1。 */
  avgWinRate: number;
  ownerCount: number;
}

/** Manager 業務分析回應（模組 B）。 */
export interface ManagerAnalyticsResponse {
  team: TeamSummary;
  owners: OwnerStats[];
}

/** Manager AI 分析回應（模組 C，對應後端 Dtos.ManagerInsightResponse）。 */
export interface ManagerInsightResponse {
  scope: "TEAM" | "OWNER";
  ownerName: string | null;
  content: string;
  /** 產出模型；deterministic fallback 時為 null。 */
  model: string | null;
  generatedAt: string;
}


/** 後端可治理的模型輸入能力。 */
export type ModelCapability = "VISION" | "AUDIO_TRANSCRIPTION";

/** 模型能力的可信來源；UNKNOWN 不得由模型名稱推測。 */
export type CapabilitySource = "AUTO" | "MANUAL" | "UNKNOWN";

/** 模型設定項目（含供應商關聯與已確認能力）。 */
export interface ModelOptionItem {
  model: string;
  providerId: number | null;
  capabilities: ModelCapability[];
  capabilitySource: CapabilitySource;
}

/** CRM 正式任務，狀態唯一來自 `/api/tasks`。 */
export interface CrmTask {
  id: number;
  customerId: number;
  opportunityId: number | null;
  contactId: number | null;
  type: "PHONE_CALL" | "EMAIL" | "MEETING" | "GENERAL";
  status: "OPEN" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  priority: "LOW" | "NORMAL" | "HIGH" | "URGENT";
  title: string;
  description: string | null;
  assigneeId: number;
  assigneeName: string;
  scheduledStart: string;
  scheduledEnd: string;
  completedAt: string | null;
  postponeCount: number;
  source: "MANUAL" | "BUSINESS_CARD" | "MEETING_AI" | "FOLLOW_UP_AI";
  version: number;
  revisionTimestamp: string;
}

/** 建立正式 CRM 任務的輸入。 */
export interface CreateCrmTaskRequest {
  customerId: number;
  opportunityId: number | null;
  contactId: number | null;
  type: CrmTask["type"];
  priority: CrmTask["priority"];
  title: string;
  description: string | null;
  assigneeId: number;
  scheduledStart: string;
  scheduledEnd: string;
  source: CrmTask["source"];
}

/** 名片辨識與人工確認生命週期，對應後端 BusinessCardStatus。 */
export type BusinessCardStatus = "PROCESSING" | "REVIEW_PENDING" | "FAILED" | "CONFIRMED";

/** Vision 模型辨識後的標準化名片欄位；confidence 以 0–1 表示各欄位信心。 */
export interface RecognizedBusinessCard {
  personName: string | null;
  title: string | null;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  website: string | null;
  confidence: Record<string, number>;
  warnings: string[];
}

/** 可能重複的既有客戶候選，僅供人工選擇合併，不自動套用。 */
export interface BusinessCardDuplicateCandidate {
  customerId: number;
  customerName: string;
  matchedBy: string[];
}

/** 名片 intake 建立與輪詢回應。 */
export interface BusinessCardIntakeResponse {
  id: number;
  status: BusinessCardStatus;
  mediaId: number | null;
  recognized: RecognizedBusinessCard | null;
  duplicateCandidates: BusinessCardDuplicateCandidate[];
  errorSummary: string | null;
}

/** 人工確認名片建檔請求；customerAction 必須明確為 CREATE 或 MERGE。 */
export interface ConfirmBusinessCardRequest {
  customerAction: "CREATE" | "MERGE";
  customerId: number | null;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  taxId: string;
  industry: string;
  contactName: string;
  contactTitle: string;
  contactEmail: string;
  opportunityName: string;
  opportunityAmount: number;
  expectedCloseDate: string | null;
  callAt: string;
}

/** 名片確認後所有正式 CRM 關聯 ID。 */
export interface BusinessCardConfirmResponse {
  intakeId: number;
  customerId: number;
  contactId: number;
  opportunityId: number;
  taskId: number;
}

/** 會議 Copilot session 生命週期，對應後端 MeetingCopilotStatus。 */
export type MeetingCopilotStatus = "UPLOADED" | "PROCESSING" | "REVIEW_PENDING" | "FAILED" | "CONFIRMED";

/** 會議草稿中的單一 CRM 變更；低信心的 STAKEHOLDER 建議預設不勾選。 */
export interface MeetingChange {
  changeId: string;
  type: "INTERACTION" | "TASK" | "OPPORTUNITY_PATCH" | "STAKEHOLDER_SUGGESTION";
  description: string;
  lowConfidence: boolean;
  selectedByDefault: boolean;
  detail: Record<string, string>;
}

/** 會議 Copilot session 建立與輪詢回應。 */
export interface MeetingCopilotSessionResponse {
  id: number;
  status: MeetingCopilotStatus;
  mediaId: number | null;
  customerId: number;
  opportunityId: number | null;
  transcript: string | null;
  summary: string | null;
  changes: MeetingChange[];
  errorSummary: string | null;
}

/** 會議 Copilot 選擇性確認結果。 */
export interface MeetingCopilotConfirmResponse {
  sessionId: number;
  appliedChangeIds: string[];
  interactionId: number | null;
  taskIds: number[];
  opportunityId: number | null;
  stakeholderSuggestionCount: number;
}

/** AI 跟進信草稿；人工修改會產生新版本（versionNumber 遞增、parentId 指向前版）。 */
export interface FollowUpDraftResponse {
  id: number;
  customerId: number;
  opportunityId: number | null;
  versionNumber: number;
  parentId: number | null;
  model: string | null;
  grounding: string;
  subject: string;
  body: string;
  edited: boolean;
  approvedBy: string | null;
  approvedAt: string | null;
}

/** 寄出郵件紀錄；狀態 QUEUED/SENT/FAILED，憑證不回傳。 */
export interface OutboundEmailResponse {
  id: number;
  draftId: number;
  from: string;
  replyTo: string;
  recipient: string;
  subject: string;
  body: string;
  status: "QUEUED" | "SENT" | "FAILED";
  messageId: string | null;
  retryCount: number;
  errorSummary: string | null;
  sentAt: string | null;
}

/** 商機健康度單一分項（可解釋加/扣分）。 */
export interface HealthComponentDto {
  key: string;
  label: string;
  score: number;
  maxScore: number;
  reason: string;
  evidence: string;
}

/** 健康度趨勢點（歷史 snapshot）。 */
export interface HealthTrendPoint {
  totalScore: number;
  calculatedAt: string;
}

/** 商機健康度回應（GET / recalculate 共用）。 */
export interface OpportunityHealthResponse {
  opportunityId: number;
  totalScore: number;
  components: HealthComponentDto[];
  nextBestAction: string;
  ruleVersion: string;
  model: string | null;
  calculatedAt: string;
  trend: HealthTrendPoint[];
}

/** Stakeholder 建議/確認狀態；enum 以字串序列化。 */
export type StakeholderSuggestionStatus = "SUGGESTED" | "CONFIRMED" | "REJECTED";
/** 資料來源：AI 建議或人工。 */
export type StakeholderSource = "AI" | "MANUAL";

/** Stakeholder 決策角色（綁定 Contact）。 */
export interface StakeholderRoleDto {
  id: number;
  contactId: number;
  contactName: string;
  contactTitle: string | null;
  roleType: string;
  influence: string;
  stance: string;
  confidence: number;
  source: StakeholderSource;
  status: StakeholderSuggestionStatus;
}

/** Stakeholder 關係（兩位同客戶 Contact）。 */
export interface StakeholderRelationDto {
  id: number;
  fromContactId: number;
  fromContactName: string;
  toContactId: number;
  toContactName: string;
  relationType: string;
  source: StakeholderSource;
  status: StakeholderSuggestionStatus;
}

/** 待確認建議（角色或關係擇一）。 */
export interface StakeholderSuggestionDto {
  suggestionId: string;
  kind: "ROLE" | "RELATION";
  status: StakeholderSuggestionStatus;
  role: StakeholderRoleDto | null;
  relation: StakeholderRelationDto | null;
}

/** 決策鏈圖回應：已確認事實與待確認建議分開。 */
export interface StakeholderMapResponse {
  customerId: number;
  confirmedRoles: StakeholderRoleDto[];
  confirmedRelations: StakeholderRelationDto[];
  suggestions: StakeholderSuggestionDto[];
}

/** AI 供應商資訊（前端不顯示 apiKey 原文）。 */
export interface AiProviderItem {
  id: number;
  name: string;
  baseUrl: string | null;
  apiKeySet: boolean;
}

/** AI 設定回應（含 providers 清單與帶 providerId 的 modelOptions）。 */
export interface AiSettingsResponse {
  currentModel: string;
  currentProviderId: number | null;
  modelOptions: ModelOptionItem[];
  providers: AiProviderItem[];
  envDefaultModel: string;
  source: string;
  /** 可編輯模型參數（null = 未設定，沿用預設） */
  temperature: number | null;
  maxCompletionTokens: number | null;
  reasoningEffort: string | null;
  ocrModel: string | null;
  ocrProviderId: number | null;
  transcriptionModel: string | null;
  transcriptionProviderId: number | null;
}

/** 單一模型競速測試結果（供評分 API 傳送）。 */
export interface ModelResultItem {
  model: string;
  firstTokenMs: number;
  totalMs: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  content: string;
}

/** 儲存單一模型測試結果的請求（對應後端 Dtos.AiTestLogRequest）。 */
export interface AiTestLogRequest {
  model: string;
  sessionId: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  answer: string;
}
