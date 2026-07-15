/**
 * 將金額格式化為貨幣樣式；locale 由呼叫端傳入（不在此耦合 i18n）。
 */
export function formatMoney(value: number, locale: string) {
  return new Intl.NumberFormat(locale, { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(value);
}

/**
 * 將金額縮短為報表圖表適合閱讀的單位；locale 由呼叫端傳入。
 */
export function formatCompactMoney(value: number, locale: string) {
  return new Intl.NumberFormat(locale, { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

/**
 * 將日期時間轉為本地可讀格式；無值時回傳呼叫端提供的 noDataLabel（已翻譯文字）。
 */
export function formatDateTime(value: string | null, locale: string, noDataLabel: string) {
  if (!value) return noDataLabel;
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

/**
 * 將日期轉為只到「日」的精簡格式（不含時間），供 KPI 卡等空間有限處使用，避免折行。
 */
export function formatDate(value: string | null, locale: string, noDataLabel: string) {
  if (!value) return noDataLabel;
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(new Date(value));
}

/** 風險等級 → i18n key 對照表（common namespace）。 */
const RISK_KEYS: Record<string, string> = { LOW: "common:enums.risk.LOW", MEDIUM: "common:enums.risk.MEDIUM", HIGH: "common:enums.risk.HIGH" };

/**
 * 將風險等級轉成 i18n key；呼叫端需自行 `t(riskLabel(level))`。未知值原樣回傳。
 */
export function riskLabel(level: string) {
  return RISK_KEYS[level] || level;
}

/** 互動意圖（SP6 intent enum）→ i18n key 對照表。 */
const INTENT_KEYS: Record<string, string> = {
  ASK_PRICING: "common:enums.intent.ASK_PRICING",
  COMPARE_COMPETITOR: "common:enums.intent.COMPARE_COMPETITOR",
  CHURN_SIGNAL: "common:enums.intent.CHURN_SIGNAL",
  RENEWAL_INTEREST: "common:enums.intent.RENEWAL_INTEREST",
  UPSELL_SIGNAL: "common:enums.intent.UPSELL_SIGNAL",
  COMPLAINT: "common:enums.intent.COMPLAINT"
};

/**
 * 將互動意圖轉成 i18n key；OTHER 或 null/undefined 回傳空字串（不顯示，維持原行為）。
 */
export function intentLabel(intent: string | null | undefined) {
  if (!intent || intent === "OTHER") return "";
  return INTENT_KEYS[intent] ?? "";
}

/** 商機階段 → i18n key 對照表。 */
const STAGE_KEYS: Record<string, string> = {
  QUALIFICATION: "common:enums.stage.QUALIFICATION",
  PROPOSAL: "common:enums.stage.PROPOSAL",
  NEGOTIATION: "common:enums.stage.NEGOTIATION",
  CLOSED_WON: "common:enums.stage.CLOSED_WON",
  CLOSED_LOST: "common:enums.stage.CLOSED_LOST"
};

/**
 * 將商機階段轉成 i18n key；呼叫端自行 `t(stageLabel(stage))`。未知值原樣回傳。
 */
export function stageLabel(stage: string) {
  return STAGE_KEYS[stage] || stage;
}

/** 商機來源（leadSource）→ i18n key 對照表。 */
const LEAD_SOURCE_KEYS: Record<string, string> = {
  INBOUND: "common:enums.leadSource.INBOUND",
  OUTBOUND: "common:enums.leadSource.OUTBOUND",
  REFERRAL: "common:enums.leadSource.REFERRAL"
};

/**
 * 將商機來源轉成 i18n key。未知值原樣回傳。
 */
export function leadSourceLabel(source: string) {
  return LEAD_SOURCE_KEYS[source] || source;
}

/** 結案原因（closeReason enum 名）→ i18n key 對照表。 */
const CLOSE_REASON_KEYS: Record<string, string> = {
  WON_PRICE: "common:enums.closeReason.WON_PRICE", WON_FEATURE: "common:enums.closeReason.WON_FEATURE",
  WON_RELATIONSHIP: "common:enums.closeReason.WON_RELATIONSHIP", WON_TIMING: "common:enums.closeReason.WON_TIMING",
  LOST_PRICE: "common:enums.closeReason.LOST_PRICE", LOST_COMPETITOR: "common:enums.closeReason.LOST_COMPETITOR",
  LOST_NO_BUDGET: "common:enums.closeReason.LOST_NO_BUDGET", LOST_NO_DECISION: "common:enums.closeReason.LOST_NO_DECISION",
  LOST_NO_RESPONSE: "common:enums.closeReason.LOST_NO_RESPONSE"
};

/**
 * 將結案原因轉成 i18n key；null 回傳「未填」對應 key。未知值原樣回傳。
 */
export function closeReasonLabel(reason: string | null) {
  if (!reason) return "common:notFilled";
  return CLOSE_REASON_KEYS[reason] || reason;
}

/**
 * RFM 分群 → i18n key 對照表。後端 RfmService.decideSegment 回傳固定中文字面值
 * （非英文 enum code），故以字面值本身作為對照表的 key。
 */
const RFM_SEGMENT_KEYS: Record<string, string> = {
  "冠軍客戶": "common:enums.rfmSegment.champion",
  "忠誠客戶": "common:enums.rfmSegment.loyal",
  "具潛力": "common:enums.rfmSegment.potential",
  "需關注": "common:enums.rfmSegment.attention",
  "瀕危流失": "common:enums.rfmSegment.atRisk"
};

/**
 * 將後端 RFM 分群字面值轉成 i18n key；呼叫端自行 `t(rfmSegmentLabel(segment))`。未知值原樣回傳。
 */
export function rfmSegmentLabel(segment: string) {
  return RFM_SEGMENT_KEYS[segment] || segment;
}
