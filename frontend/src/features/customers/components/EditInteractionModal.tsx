import { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { InteractionResponse } from "../../../types";

/**
 * 編輯互動紀錄 Modal。
 * 函式級註解：仿 AddInteractionModal，但以現有互動值預填類型 / 時間 / 內容。
 * occurredAt 直接送 datetime-local 值（yyyy-MM-ddTHH:mm），不轉 ISO/UTC，以免時區位移。
 *
 * @param interaction 欲編輯的互動
 * @param onSubmit 送出 callback（回傳表單資料）
 * @param onClose 關閉 callback
 */
export function EditInteractionModal({
  interaction,
  onSubmit,
  onClose
}: {
  interaction: InteractionResponse;
  onSubmit: (data: { type: string; occurredAt: string; content: string }) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(["customers", "common"]);

  /** 將後端時間字串轉為 datetime-local 可用的 yyyy-MM-ddTHH:mm（取前 16 字元）。 */
  function toLocalInput(value: string): string {
    return value ? value.slice(0, 16) : "";
  }

  /** 解析表單並回傳互動資料。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      type: String(fd.get("type")),
      // datetime-local 的值即為 LocalDateTime 格式（yyyy-MM-ddTHH:mm），直接送，不可轉 UTC
      occurredAt: String(fd.get("occurredAt")),
      content: String(fd.get("content"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("customers:editInteractionModal.title")}</h3>
        <label>{t("customers:form.type")}
          <select name="type" required defaultValue={interaction.type}>
            <option value="PHONE">{t("customers:enumsInteractionType.PHONE")}</option>
            <option value="MEETING">{t("customers:enumsInteractionType.MEETING")}</option>
            <option value="EMAIL">{t("customers:enumsInteractionType.EMAIL")}</option>
            <option value="SUPPORT_TICKET">{t("customers:enumsInteractionType.SUPPORT_TICKET")}</option>
          </select>
        </label>
        <label>{t("customers:form.occurredAt")} <input name="occurredAt" type="datetime-local" required defaultValue={toLocalInput(interaction.occurredAt)} /></label>
        <label>{t("customers:form.content")} <textarea name="content" rows={3} required defaultValue={interaction.content} /></label>
        <div className="modal-actions">
          <button type="submit">{t("common:actions.save")}</button>
          <button type="button" onClick={onClose}>{t("common:actions.cancel")}</button>
        </div>
      </form>
    </div>
  );
}
