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
