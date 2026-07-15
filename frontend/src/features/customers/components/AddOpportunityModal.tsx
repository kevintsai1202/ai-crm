import { FormEvent } from "react";
import { useTranslation } from "react-i18next";

/**
 * 新增商機 Modal。
 *
 * <p>提供商機名稱、階段、金額、預計成交日與類型輸入;送出後由上層呼叫 API 建立。</p>
 *
 * @param customerName 所屬客戶名稱(顯示用)
 * @param onSubmit 送出 callback(回傳表單資料)
 * @param onClose 關閉 callback
 */
export function AddOpportunityModal({
  customerName,
  onSubmit,
  onClose,
  initialValues
}: {
  customerName: string;
  onSubmit: (data: { name: string; stage: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) => void;
  onClose: () => void;
  /** 選填預填值（供 AI 建議商機草稿帶入名稱/階段；其餘維持原預設）。 */
  initialValues?: { name?: string; stage?: string };
}) {
  const { t } = useTranslation(["customers", "common"]);
  /** 解析表單並回傳資料;金額轉數字,預計成交日空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const closeDate = String(fd.get("expectedCloseDate") || "");
    const prob = String(fd.get("probability") || "");
    onSubmit({
      name: String(fd.get("name")),
      stage: String(fd.get("stage")),
      amount: Number(fd.get("amount")),
      // date input 空值送 null,避免後端解析空字串失敗
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
        <h3>{t("customers:addOpportunityModal.title", { name: customerName })}</h3>
        <label>{t("customers:form.opportunityName")} <input name="name" type="text" required placeholder={t("customers:form.opportunityNamePlaceholder")} defaultValue={initialValues?.name ?? ""} /></label>
        <label>{t("customers:form.stage")}
          <select name="stage" required defaultValue={initialValues?.stage ?? "QUALIFICATION"}>
            <option value="QUALIFICATION">{t("common:enums.stage.QUALIFICATION")}</option>
            <option value="PROPOSAL">{t("common:enums.stage.PROPOSAL")}</option>
            <option value="NEGOTIATION">{t("common:enums.stage.NEGOTIATION")}</option>
            <option value="CLOSED_WON">{t("common:enums.stage.CLOSED_WON")}</option>
            <option value="CLOSED_LOST">{t("common:enums.stage.CLOSED_LOST")}</option>
          </select>
        </label>
        <label>{t("customers:form.amount")} <input name="amount" type="number" min="0" step="1000" required placeholder={t("customers:form.amountPlaceholder")} /></label>
        <label>{t("customers:form.expectedCloseDate")} <input name="expectedCloseDate" type="date" /></label>
        <label>{t("customers:form.type")}
          <select name="type" required defaultValue="NEW_BUSINESS">
            <option value="NEW_BUSINESS">{t("customers:enumsOpportunityType.NEW_BUSINESS")}</option>
            <option value="RENEWAL">{t("customers:enumsOpportunityType.RENEWAL")}</option>
          </select>
        </label>
        <label>{t("customers:form.leadSource")}
          <select name="leadSource" required defaultValue="OUTBOUND">
            <option value="OUTBOUND">{t("common:enums.leadSource.OUTBOUND")}</option>
            <option value="INBOUND">{t("common:enums.leadSource.INBOUND")}</option>
            <option value="REFERRAL">{t("common:enums.leadSource.REFERRAL")}</option>
          </select>
        </label>
        <label>{t("customers:form.probability")} <input name="probability" type="number" min="0" max="100" placeholder={t("customers:form.probabilityPlaceholder")} /></label>
        <div className="modal-actions">
          <button type="submit">{t("customers:addOpportunityModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
