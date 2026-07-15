import type { BusinessCardDuplicateCandidate, RecognizedBusinessCard } from "../../types";
import { lowConfidenceFields, type BusinessCardForm, type DuplicateStrategy } from "./businessCardState";

/** 審核步驟屬性。 */
interface ReviewStepProps {
  /** Vision 辨識結果，用於低信心欄位提示。 */
  recognized: RecognizedBusinessCard | null;
  /** 目前表單值。 */
  form: BusinessCardForm;
  /** 更新單一表單欄位。 */
  onChange: (field: keyof BusinessCardForm, value: string) => void;
  /** 後端回傳的重複客戶候選。 */
  candidates: BusinessCardDuplicateCandidate[];
  /** 目前選定的重複處理策略。 */
  strategy: DuplicateStrategy | null;
  /** 變更重複處理策略。 */
  onStrategyChange: (strategy: DuplicateStrategy) => void;
  /** 是否可進入下一步（由 canProceedFromReview 計算）。 */
  canProceed: boolean;
  /** 進入確認步驟。 */
  onNext: () => void;
}

/** 辨識欄位鍵對應的表單欄位，用於低信心標示。 */
const FIELD_MAP: Record<string, keyof BusinessCardForm> = {
  personName: "contactName",
  title: "contactTitle",
  email: "customerEmail",
  phone: "customerPhone",
  companyName: "customerName",
};

/** 名片精靈第二步：校正辨識欄位並解決重複客戶。 */
export function BusinessCardReviewStep({
  recognized, form, onChange, candidates, strategy, onStrategyChange, canProceed, onNext,
}: ReviewStepProps) {
  // 需人工複查的表單欄位集合。
  const lowConfFields = new Set(
    lowConfidenceFields(recognized ?? { personName: null, title: null, email: null, phone: null, companyName: null, website: null, confidence: {}, warnings: [] })
      .map((key) => FIELD_MAP[key])
      .filter(Boolean),
  );

  /** 渲染單一可編輯欄位，低信心時加上提示。 */
  function field(label: string, key: keyof BusinessCardForm, required = true) {
    const low = lowConfFields.has(key);
    return (
      <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
        <span>
          {label}{required && <span style={{ color: "#b91c1c" }}> *</span>}
          {low && (
            <span data-testid={`bc-lowconf-${key}`} style={{ marginLeft: 6, fontSize: 11, color: "#b45309", background: "#fef3c7", padding: "1px 6px", borderRadius: 4 }}>
              信心偏低，請確認
            </span>
          )}
        </span>
        <input
          name={`bc-${key}`}
          value={form[key]}
          onChange={(e) => onChange(key, e.target.value)}
          style={{ padding: "7px 10px", border: `1px solid ${low ? "#fbbf24" : "#d1e0db"}`, borderRadius: 6, fontSize: 14 }}
        />
      </label>
    );
  }

  return (
    <div data-testid="bc-review-step" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        {field("公司名稱", "customerName")}
        {field("統一編號", "taxId")}
        {field("產業", "industry")}
        {field("客戶 Email", "customerEmail")}
        {field("客戶電話", "customerPhone")}
        {field("聯絡人姓名", "contactName")}
        {field("聯絡人職稱", "contactTitle", false)}
        {field("聯絡人 Email", "contactEmail")}
      </div>

      {candidates.length > 0 && (
        <div data-testid="bc-duplicates" style={{ border: "1px solid #fcd34d", background: "#fffbeb", borderRadius: 8, padding: 12 }}>
          <div style={{ fontWeight: 700, fontSize: 14, color: "#92400e", marginBottom: 8 }}>
            ⚠️ 偵測到 {candidates.length} 筆可能重複的客戶，請選擇處理方式：
          </div>
          <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 14, marginBottom: 6 }}>
            <input type="radio" name="duplicateStrategy" value="CREATE"
              checked={strategy?.action === "CREATE"}
              onChange={() => onStrategyChange({ action: "CREATE" })} />
            仍建立為新客戶
          </label>
          {candidates.map((candidate) => (
            <label key={candidate.customerId} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 14, marginBottom: 6 }}>
              <input type="radio" name="duplicateStrategy" value={`MERGE-${candidate.customerId}`}
                checked={strategy?.action === "MERGE" && strategy.customerId === candidate.customerId}
                onChange={() => onStrategyChange({ action: "MERGE", customerId: candidate.customerId })} />
              合併至「{candidate.customerName}」
              <span style={{ fontSize: 11, color: "#a16207" }}>（{candidate.matchedBy.join("、")}）</span>
            </label>
          ))}
        </div>
      )}

      <button
        type="button"
        className="btn-primary"
        data-testid="bc-review-next"
        disabled={!canProceed}
        onClick={onNext}
        style={{ alignSelf: "flex-start", padding: "8px 20px", fontWeight: 700 }}
      >
        下一步：商機與任務
      </button>
    </div>
  );
}
