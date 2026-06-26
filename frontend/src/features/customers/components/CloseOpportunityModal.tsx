import { FormEvent } from "react";

/**
 * 結案原因 Modal：商機階段選到 CLOSED_WON / CLOSED_LOST 時，收集輸贏原因、備註與實際成交日。
 * 函式級註解：依 won/lost 顯示對應的 closeReason 子集；實際成交日預設本地今天（避免 UTC 位移）。
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
  const won = stage === "CLOSED_WON";
  // 依輸贏顯示對應原因子集
  const options: [string, string][] = won
    ? [["WON_PRICE", "價格"], ["WON_FEATURE", "功能"], ["WON_RELATIONSHIP", "關係"], ["WON_TIMING", "時機"]]
    : [["LOST_PRICE", "價格太高"], ["LOST_COMPETITOR", "輸給競品"], ["LOST_NO_BUDGET", "無預算"], ["LOST_NO_DECISION", "未決策"], ["LOST_NO_RESPONSE", "無回應"]];
  // 本地今天日期（yyyy-MM-dd），避免 toISOString 的 UTC 位移
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
        <h3>{won ? "成交結案" : "失單結案"}</h3>
        <label>原因
          <select name="closeReason" required defaultValue={options[0][0]}>
            {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </label>
        <label>備註 <input name="closeReasonNote" type="text" placeholder="選填" /></label>
        <label>實際成交日 <input name="actualCloseDate" type="date" defaultValue={today} /></label>
        <div className="modal-actions">
          <button type="submit">確認結案</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
