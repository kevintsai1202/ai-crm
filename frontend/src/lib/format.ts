/**
 * 將金額格式化為台幣樣式。
 */
export function formatMoney(value: number) {
  return new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(value);
}

/**
 * 將金額縮短為報表圖表適合閱讀的單位。
 */
export function formatCompactMoney(value: number) {
  return new Intl.NumberFormat("zh-TW", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

/**
 * 將日期時間轉為本地可讀格式。
 */
export function formatDateTime(value: string | null) {
  if (!value) return "尚無資料";
  return new Intl.DateTimeFormat("zh-TW", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

/**
 * 將日期轉為只到「日」的精簡格式（不含時間），供 KPI 卡等空間有限處使用，避免折行。
 */
export function formatDate(value: string | null) {
  if (!value) return "尚無資料";
  return new Intl.DateTimeFormat("zh-TW", { dateStyle: "medium" }).format(new Date(value));
}

/**
 * 將風險等級轉成中文標籤。
 */
export function riskLabel(level: string) {
  const labels: Record<string, string> = { LOW: "低風險", MEDIUM: "中風險", HIGH: "高風險" };
  return labels[level] || level;
}

/**
 * 將互動意圖（SP6 intent enum）轉成中文標籤；OTHER 或 null 回傳空字串（不顯示）。
 */
export function intentLabel(intent: string | null | undefined) {
  if (!intent) return "";
  const labels: Record<string, string> = {
    ASK_PRICING: "詢價",
    COMPARE_COMPETITOR: "競品比較",
    CHURN_SIGNAL: "流失信號",
    RENEWAL_INTEREST: "續約意願",
    UPSELL_SIGNAL: "加購",
    COMPLAINT: "客訴",
    OTHER: ""
  };
  return labels[intent] ?? "";
}

/**
 * 將商機階段轉成中文圖表標籤。
 */
export function stageLabel(stage: string) {
  const labels: Record<string, string> = {
    QUALIFICATION: "資格評估",
    PROPOSAL: "提案",
    NEGOTIATION: "議價",
    CLOSED_WON: "已成交",
    CLOSED_LOST: "已流失"
  };
  return labels[stage] || stage;
}

/**
 * 將商機來源（leadSource）轉成中文標籤。
 */
export function leadSourceLabel(source: string) {
  const labels: Record<string, string> = { INBOUND: "主動上門", OUTBOUND: "業務開發", REFERRAL: "推薦轉介" };
  return labels[source] || source;
}

/**
 * 將結案原因（closeReason enum 名）轉成中文標籤；null 回傳「未填」。
 */
export function closeReasonLabel(reason: string | null) {
  if (!reason) return "未填";
  const labels: Record<string, string> = {
    WON_PRICE: "贏-價格", WON_FEATURE: "贏-功能", WON_RELATIONSHIP: "贏-關係", WON_TIMING: "贏-時機",
    LOST_PRICE: "輸-價格", LOST_COMPETITOR: "輸-競品", LOST_NO_BUDGET: "輸-無預算",
    LOST_NO_DECISION: "輸-未決策", LOST_NO_RESPONSE: "輸-無回應"
  };
  return labels[reason] || reason;
}
