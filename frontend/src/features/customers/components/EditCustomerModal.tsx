import { FormEvent } from "react";
import type { CustomerSummary, OwnerOption } from "../../../types";

/**
 * 編輯客戶 Modal。
 * 函式級註解：仿 AddCustomerModal 範式，但以現有客戶值預填欄位。
 * 產業沿用文字輸入以容許新值；負責業務為帳號下拉（值為 ownerId）。
 * 日期欄位（合約起始 / 到期 / 續約）為 date input，空值送 null。
 *
 * 注意：CustomerSummary 僅提供 renewalDueDate，合約起始 / 到期日後端未回傳，
 * 故預設留空；使用者可自行填入後送出更新。
 */
export function EditCustomerModal({
  customer,
  owners,
  onSubmit,
  onClose
}: {
  customer: CustomerSummary;
  owners: OwnerOption[];
  onSubmit: (data: {
    name: string;
    email: string;
    phone: string;
    taxId: string;
    industry: string;
    ownerId: number;
    contractStartDate: string | null;
    contractEndDate: string | null;
    renewalDueDate: string | null;
  }) => void;
  onClose: () => void;
}) {
  // 找出目前負責業務帳號 id（以顯示名稱比對 owners 清單），供下拉預設值使用
  const currentOwner = owners.find((o) => o.displayName === customer.ownerName);

  /** 將後端日期時間字串轉為 date input 可用的 yyyy-MM-dd（無值回空字串）。 */
  function toDateInput(value: string | null): string {
    if (!value) return "";
    // 後端日期可能帶時間，僅取日期部分
    return value.slice(0, 10);
  }

  /** 送出表單：彙整欄位值，金額外的日期欄位空字串轉 null。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const start = String(fd.get("contractStartDate") || "");
    const end = String(fd.get("contractEndDate") || "");
    const renewal = String(fd.get("renewalDueDate") || "");
    onSubmit({
      name: String(fd.get("name")),
      email: String(fd.get("email")),
      phone: String(fd.get("phone")),
      taxId: String(fd.get("taxId")),
      industry: String(fd.get("industry")),
      ownerId: Number(fd.get("ownerId")),
      // date input 空值送 null，避免後端解析空字串失敗
      contractStartDate: start ? start : null,
      contractEndDate: end ? end : null,
      renewalDueDate: renewal ? renewal : null
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>編輯客戶 — {customer.name}</h3>
        <label>名稱 <input name="name" required defaultValue={customer.name} /></label>
        <label>Email <input name="email" type="email" required defaultValue={customer.email} /></label>
        <label>電話 <input name="phone" placeholder="0912345678" required defaultValue={customer.phone} /></label>
        <label>統編 <input name="taxId" placeholder="12345678" required defaultValue={customer.taxId} /></label>
        <label>產業 <input name="industry" required defaultValue={customer.industry} /></label>
        <label>
          負責業務
          <select name="ownerId" required defaultValue={currentOwner ? String(currentOwner.id) : ""}>
            {owners.length === 0 ? <option value="" disabled>無可指派業務</option> : null}
            {owners.map((o) => (
              <option key={o.id} value={o.id}>{o.displayName}</option>
            ))}
          </select>
        </label>
        <label>合約起始日 <input name="contractStartDate" type="date" defaultValue="" /></label>
        <label>合約到期日 <input name="contractEndDate" type="date" defaultValue="" /></label>
        <label>續約日 <input name="renewalDueDate" type="date" defaultValue={toDateInput(customer.renewalDueDate)} /></label>
        <div className="modal-actions">
          <button type="submit">儲存</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
