import { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { OpportunityResponse } from "../../../types";

/**
 * 編輯商機 Modal。
 * 函式級註解：仿 AddOpportunityModal，但以現有商機值預填；階段不在此修改（由看板拖拽處理）。
 * 欄位為名稱 / 金額 / 預計成交日 / 類型；預計成交日空值送 null。
 *
 * @param opportunity 欲編輯的商機
 * @param onSubmit 送出 callback（回傳表單資料）
 * @param onClose 關閉 callback
 */
export function EditOpportunityModal({
  opportunity,
  onSubmit,
  onClose
}: {
  opportunity: OpportunityResponse;
  onSubmit: (data: { name: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);
  /** 解析表單並回傳資料；金額轉數字，預計成交日空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const closeDate = String(fd.get("expectedCloseDate") || "");
    const prob = String(fd.get("probability") || "");
    onSubmit({
      name: String(fd.get("name")),
      amount: Number(fd.get("amount")),
      // date input 空值送 null，避免後端解析空字串失敗
      expectedCloseDate: closeDate ? closeDate : null,
      type: String(fd.get("type")),
      leadSource: String(fd.get("leadSource")),
      // 機率留空時送 null，由後端依階段帶預設
      probability: prob ? Number(prob) : null
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:editOpportunityModal.title", { name: opportunity.name })}</h3>
        <label>{t("customers:form.opportunityName")} <input name="name" type="text" required defaultValue={opportunity.name} /></label>
        <label>{t("customers:form.amount")} <input name="amount" type="number" min="0" step="1000" required defaultValue={opportunity.amount} /></label>
        <label>{t("customers:form.expectedCloseDate")} <input name="expectedCloseDate" type="date" defaultValue={opportunity.expectedCloseDate ?? ""} /></label>
        <label>{t("customers:form.type")}
          <select name="type" required defaultValue={opportunity.type}>
            <option value="NEW_BUSINESS">{t("customers:enumsOpportunityType.NEW_BUSINESS")}</option>
            <option value="RENEWAL">{t("customers:enumsOpportunityType.RENEWAL")}</option>
            {/* 若現有類型不在標準選項中，補一個保留原值的選項，避免下拉預設值失效 */}
            {opportunity.type !== "NEW_BUSINESS" && opportunity.type !== "RENEWAL" ? (
              <option value={opportunity.type}>{opportunity.type}</option>
            ) : null}
          </select>
        </label>
        <label>{t("customers:form.leadSource")}
          <select name="leadSource" required defaultValue={opportunity.leadSource}>
            <option value="OUTBOUND">{t("common:enums.leadSource.OUTBOUND")}</option>
            <option value="INBOUND">{t("common:enums.leadSource.INBOUND")}</option>
            <option value="REFERRAL">{t("common:enums.leadSource.REFERRAL")}</option>
          </select>
        </label>
        <label>{t("customers:form.probability")} <input name="probability" type="number" min="0" max="100" defaultValue={opportunity.probability ?? ""} placeholder={t("customers:form.probabilityPlaceholder")} /></label>
        <div className="modal-actions">
          <button type="submit">{t("common:actions.save")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
