import { FormEvent } from "react";
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
  onSubmit: (data: { name: string; amount: number; expectedCloseDate: string | null; type: string }) => void;
  onClose: () => void;
}) {
  /** 解析表單並回傳資料；金額轉數字，預計成交日空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const closeDate = String(fd.get("expectedCloseDate") || "");
    onSubmit({
      name: String(fd.get("name")),
      amount: Number(fd.get("amount")),
      // date input 空值送 null，避免後端解析空字串失敗
      expectedCloseDate: closeDate ? closeDate : null,
      type: String(fd.get("type"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>編輯商機 — {opportunity.name}</h3>
        <label>商機名稱 <input name="name" type="text" required defaultValue={opportunity.name} /></label>
        <label>金額(元) <input name="amount" type="number" min="0" step="1000" required defaultValue={opportunity.amount} /></label>
        <label>預計成交日 <input name="expectedCloseDate" type="date" defaultValue={opportunity.expectedCloseDate ?? ""} /></label>
        <label>類型
          <select name="type" required defaultValue={opportunity.type}>
            <option value="NEW_BUSINESS">新單</option>
            <option value="RENEWAL">續約</option>
            {/* 若現有類型不在標準選項中，補一個保留原值的選項，避免下拉預設值失效 */}
            {opportunity.type !== "NEW_BUSINESS" && opportunity.type !== "RENEWAL" ? (
              <option value={opportunity.type}>{opportunity.type}</option>
            ) : null}
          </select>
        </label>
        <div className="modal-actions">
          <button type="submit">儲存</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
