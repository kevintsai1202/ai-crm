import { FormEvent, useEffect, useState } from "react";
import { fetchCustomerOptions } from "../../../api";
import type { OwnerOption } from "../../../types";

/**
 * 新增客戶 Modal。
 * 產業為下拉選單（來源：現有客戶的不重複產業）；
 * 負責業務為下拉選單，選項為 SALES 登入帳號（正規關聯），預設帶登入者本人（currentUserId）。
 */
export function AddCustomerModal({
  currentUserId,
  onSubmit,
  onClose
}: {
  currentUserId: number;
  onSubmit: (data: { name: string; email: string; phone: string; taxId: string; industry: string; ownerId: number }) => void;
  onClose: () => void;
}) {
  // 下拉選項：產業清單與可指派業務（帳號）清單
  const [industries, setIndustries] = useState<string[]>([]);
  const [owners, setOwners] = useState<OwnerOption[]>([]);
  // 已選負責業務帳號 id（字串以利 <select> 綁定）
  const [ownerId, setOwnerId] = useState("");

  // 進場載入下拉選項；負責業務預設為登入者本人（若本人為可指派業務），否則第一個
  useEffect(() => {
    void (async () => {
      try {
        const options = await fetchCustomerOptions();
        setIndustries(options.industries);
        setOwners(options.owners);
        const self = options.owners.find((o) => o.id === currentUserId);
        setOwnerId(String(self ? self.id : options.owners[0]?.id ?? ""));
      } catch (e) {
        console.error("載入客戶表單選項失敗:", e);
      }
    })();
  }, [currentUserId]);

  /** 送出表單：彙整欄位值，產業取自下拉，負責業務取自帳號下拉（ownerId）。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      name: String(fd.get("name")),
      email: String(fd.get("email")),
      phone: String(fd.get("phone")),
      taxId: String(fd.get("taxId")),
      industry: String(fd.get("industry")),
      ownerId: Number(fd.get("ownerId"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>新增客戶</h3>
        <label>名稱 <input name="name" required /></label>
        <label>Email <input name="email" type="email" required /></label>
        <label>電話 <input name="phone" placeholder="0912345678" required /></label>
        <label>統編 <input name="taxId" placeholder="12345678" required /></label>
        <label>
          產業
          <select name="industry" required defaultValue="">
            <option value="" disabled>請選擇產業</option>
            {industries.map((it) => (
              <option key={it} value={it}>{it}</option>
            ))}
          </select>
        </label>
        <label>
          負責業務
          <select name="ownerId" required value={ownerId} onChange={(e) => setOwnerId(e.target.value)}>
            {owners.length === 0 ? <option value="" disabled>無可指派業務</option> : null}
            {owners.map((o) => (
              <option key={o.id} value={o.id}>{o.id === currentUserId ? `${o.displayName}（我）` : o.displayName}</option>
            ))}
          </select>
        </label>
        <div className="modal-actions">
          <button type="submit">建立</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
