import { FormEvent } from "react";
import { useTranslation } from "react-i18next";

/**
 * 新增互動紀錄 Modal。
 */
export function AddInteractionModal({ customerName, onSubmit, onClose }: { customerName: string; onSubmit: (data: { type: string; occurredAt: string; content: string }) => void; onClose: () => void }) {
  const { t } = useTranslation(["customers", "common"]);
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      type: String(fd.get("type")),
      // datetime-local 的值即為 LocalDateTime 格式（yyyy-MM-ddTHH:mm），直接送；
      // 不可用 new Date().toISOString()，否則會轉成 UTC 並附帶 Z，造成時間時區位移
      occurredAt: String(fd.get("occurredAt")),
      content: String(fd.get("content"))
    });
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:addInteractionModal.title", { name: customerName })}</h3>
        <label>{t("customers:form.type")}
          <select name="type" required>
            <option value="PHONE">{t("customers:enumsInteractionType.PHONE")}</option>
            <option value="MEETING">{t("customers:enumsInteractionType.MEETING")}</option>
            <option value="EMAIL">{t("customers:enumsInteractionType.EMAIL")}</option>
            <option value="SUPPORT_TICKET">{t("customers:enumsInteractionType.SUPPORT_TICKET")}</option>
          </select>
        </label>
        <label>{t("customers:form.occurredAt")} <input name="occurredAt" type="datetime-local" required /></label>
        <label>{t("customers:form.content")} <textarea name="content" rows={3} required /></label>
        <div className="modal-actions">
          <button type="submit">{t("customers:addInteractionModal.submit")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
