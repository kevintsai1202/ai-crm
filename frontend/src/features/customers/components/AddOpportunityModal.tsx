import { FormEvent } from "react";

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
  onClose
}: {
  customerName: string;
  onSubmit: (data: { name: string; stage: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) => void;
  onClose: () => void;
}) {
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
        <h3>新增商機 — {customerName}</h3>
        <label>商機名稱 <input name="name" type="text" required placeholder="例:智慧工廠擴充授權" /></label>
        <label>階段
          <select name="stage" required defaultValue="QUALIFICATION">
            <option value="QUALIFICATION">資格評估</option>
            <option value="PROPOSAL">提案</option>
            <option value="NEGOTIATION">議價</option>
            <option value="CLOSED_WON">已成交</option>
            <option value="CLOSED_LOST">已流失</option>
          </select>
        </label>
        <label>金額(元) <input name="amount" type="number" min="0" step="1000" required placeholder="例:1500000" /></label>
        <label>預計成交日 <input name="expectedCloseDate" type="date" /></label>
        <label>類型
          <select name="type" required defaultValue="NEW_BUSINESS">
            <option value="NEW_BUSINESS">新單</option>
            <option value="RENEWAL">續約</option>
          </select>
        </label>
        <label>來源
          <select name="leadSource" required defaultValue="OUTBOUND">
            <option value="OUTBOUND">業務開發</option>
            <option value="INBOUND">主動上門</option>
            <option value="REFERRAL">推薦轉介</option>
          </select>
        </label>
        <label>成交機率(%) <input name="probability" type="number" min="0" max="100" placeholder="留空則依階段預設" /></label>
        <div className="modal-actions">
          <button type="submit">新增</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
