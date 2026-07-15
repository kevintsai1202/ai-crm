import { FormEvent } from "react";
import { useTranslation } from "react-i18next";

/**
 * 結案原因 Modal：商機階段選到 CLOSED_WON / CLOSED_LOST 時，收集輸贏原因、備註與實際成交日。
 * 函式級註解：依 won/lost 顯示對應的 closeReason 子集；實際成交日預設本地今天（避免 UTC 位移）。
 * 這裡的原因短標籤（closeReasonsShort）是給選單用的精簡版，與 format.ts 的 closeReasonLabel
 * （完整版，如「贏-價格」，用於其他頁面顯示已結案商機）是兩組獨立維護的翻譯，故意不共用。
 *
 * @param stage 結案階段（CLOSED_WON 或 CLOSED_LOST）
 * @param onSubmit 送出 callback（回傳 closeReason / closeReasonNote / actualCloseDate）
 * @param onClose 關閉 callback
 */
export function CloseOpportunityModal({ stage, onSubmit, onClose }: {
  stage: "CLOSED_WON" | "CLOSED_LOST";
  onSubmit: (data: { closeReason: string; closeReasonNote: string; actualCloseDate: string }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("customers");
  const won = stage === "CLOSED_WON";
  // 依輸贏顯示對應原因子集
  const options: [string, string][] = won
    ? [["WON_PRICE", t("closeReasonsShort.won.WON_PRICE")], ["WON_FEATURE", t("closeReasonsShort.won.WON_FEATURE")], ["WON_RELATIONSHIP", t("closeReasonsShort.won.WON_RELATIONSHIP")], ["WON_TIMING", t("closeReasonsShort.won.WON_TIMING")]]
    : [["LOST_PRICE", t("closeReasonsShort.lost.LOST_PRICE")], ["LOST_COMPETITOR", t("closeReasonsShort.lost.LOST_COMPETITOR")], ["LOST_NO_BUDGET", t("closeReasonsShort.lost.LOST_NO_BUDGET")], ["LOST_NO_DECISION", t("closeReasonsShort.lost.LOST_NO_DECISION")], ["LOST_NO_RESPONSE", t("closeReasonsShort.lost.LOST_NO_RESPONSE")]];
  // 本地今天日期（yyyy-MM-dd），避免 toISOString 的 UTC 位移；en-CA 純為取格式，非顯示用途，不需 i18n 化
  const today = new Date().toLocaleDateString("en-CA");

  /** 解析表單並回傳結案資料；備註空字串、日期預設今天。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      closeReason: String(fd.get("closeReason")),
      closeReasonNote: String(fd.get("closeReasonNote") || ""),
      actualCloseDate: String(fd.get("actualCloseDate") || today)
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{won ? t("closeOpportunityModal.wonTitle") : t("closeOpportunityModal.lostTitle")}</h3>
        <label>{t("form.closeReason")}
          <select name="closeReason" required defaultValue={options[0][0]}>
            {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </label>
        <label>{t("form.closeReasonNote")} <input name="closeReasonNote" type="text" placeholder={t("form.closeReasonNotePlaceholder")} /></label>
        <label>{t("form.actualCloseDate")} <input name="actualCloseDate" type="date" defaultValue={today} /></label>
        <div className="modal-actions">
          <button type="submit">{t("closeOpportunityModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
