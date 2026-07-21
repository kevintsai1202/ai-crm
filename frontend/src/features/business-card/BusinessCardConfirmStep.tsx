import type { BusinessCardForm } from "./businessCardState";
import { useTranslation } from "react-i18next";

/** 確認步驟屬性。 */
interface ConfirmStepProps {
  /** 目前表單值。 */
  form: BusinessCardForm;
  /** 更新單一表單欄位。 */
  onChange: (field: keyof BusinessCardForm, value: string) => void;
  /** 是否送出中。 */
  submitting: boolean;
  /** 錯誤訊息。 */
  error: string | null;
  /** 送出確認建檔。 */
  onConfirm: () => void;
  /** 返回審核步驟。 */
  onBack: () => void;
}

/** 名片精靈第三步：補齊商機與電話任務資訊並確認建檔。 */
export function BusinessCardConfirmStep({ form, onChange, submitting, error, onConfirm, onBack }: ConfirmStepProps) {
  const { t } = useTranslation("operations");
  // 送出前檢查：商機名稱與預定通話時間為必填。
  const ready = form.opportunityName.trim() !== "" && form.callAt.trim() !== "";

  return (
    <div data-testid="bc-confirm-step" style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
          <span>{t("businessCard.opportunityName")} <span style={{ color: "#b91c1c" }}>*</span></span>
          <input name="bc-opportunityName" value={form.opportunityName}
            onChange={(e) => onChange("opportunityName", e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #d1e0db", borderRadius: 6, fontSize: 14 }} />
        </label>
        <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
          <span>{t("businessCard.amount")}</span>
          <input name="bc-opportunityAmount" type="number" min="0" value={form.opportunityAmount}
            onChange={(e) => onChange("opportunityAmount", e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #d1e0db", borderRadius: 6, fontSize: 14 }} />
        </label>
        <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
          <span>{t("businessCard.closeDate")}</span>
          <input name="bc-expectedCloseDate" type="date" value={form.expectedCloseDate}
            onChange={(e) => onChange("expectedCloseDate", e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #d1e0db", borderRadius: 6, fontSize: 14 }} />
        </label>
        <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
          <span>{t("businessCard.callAt")} <span style={{ color: "#b91c1c" }}>*</span></span>
          <input name="bc-callAt" type="datetime-local" value={form.callAt}
            onChange={(e) => onChange("callAt", e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #d1e0db", borderRadius: 6, fontSize: 14 }} />
        </label>
      </div>

      {error && <div data-testid="bc-confirm-error" style={{ color: "#b91c1c", fontSize: 13 }}>⚠️ {error}</div>}

      <div style={{ display: "flex", gap: 8 }}>
        <button type="button" className="btn-secondary" onClick={onBack} disabled={submitting}
          style={{ padding: "8px 16px" }}>{t("businessCard.back")}</button>
        <button type="button" className="btn-primary" data-testid="bc-confirm-submit"
          disabled={submitting || !ready} onClick={onConfirm}
          style={{ padding: "8px 20px", fontWeight: 700 }}>
          {submitting ? t("businessCard.creating") : t("businessCard.confirm")}
        </button>
      </div>
    </div>
  );
}
