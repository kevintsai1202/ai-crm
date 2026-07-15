import { FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation(["customers", "common"]);
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
        <h3>{t("customers:addCustomerModal.title")}</h3>
        <label>{t("customers:form.name")} <input name="name" required /></label>
        <label>{t("customers:form.email")} <input name="email" type="email" required /></label>
        <label>{t("customers:form.phone")} <input name="phone" placeholder={t("customers:form.phonePlaceholder")} required /></label>
        <label>{t("customers:form.taxId")} <input name="taxId" placeholder={t("customers:form.taxIdPlaceholder")} required /></label>
        <label>
          {t("customers:form.industry")}
          <select name="industry" required defaultValue="">
            <option value="" disabled>{t("customers:form.selectIndustry")}</option>
            {industries.map((it) => (
              <option key={it} value={it}>{it}</option>
            ))}
          </select>
        </label>
        <label>
          {t("customers:form.owner")}
          <select name="ownerId" required value={ownerId} onChange={(e) => setOwnerId(e.target.value)}>
            {owners.length === 0 ? <option value="" disabled>{t("customers:form.noAssignableOwner")}</option> : null}
            {owners.map((o) => (
              <option key={o.id} value={o.id}>{o.id === currentUserId ? t("customers:form.ownerSelf", { name: o.displayName }) : o.displayName}</option>
            ))}
          </select>
        </label>
        <div className="modal-actions">
          <button type="submit">{t("customers:addCustomerModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
