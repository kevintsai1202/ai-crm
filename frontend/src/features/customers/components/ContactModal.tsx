import { FormEvent } from "react";
import type { ContactResponse } from "../../../types";

/**
 * 聯絡人 Modal（單一元件同時支援新增與編輯）。
 * 函式級註解：有傳入 contact 則為編輯模式（預填現有值並顯示「編輯聯絡人」），否則為新增模式。
 * 欄位為姓名 / 職稱 / Email；送出後由上層呼叫對應的 createContact 或 updateContact。
 *
 * @param contact 欲編輯的聯絡人；未傳則為新增
 * @param onSubmit 送出 callback（回傳表單資料）
 * @param onClose 關閉 callback
 */
export function ContactModal({
  contact,
  onSubmit,
  onClose
}: {
  contact?: ContactResponse | null;
  onSubmit: (data: { name: string; title: string; email: string }) => void;
  onClose: () => void;
}) {
  // 是否為編輯模式（用於標題與按鈕文字）
  const isEdit = Boolean(contact);

  /** 解析表單並回傳聯絡人資料。 */
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    onSubmit({
      name: String(fd.get("name")),
      title: String(fd.get("title")),
      email: String(fd.get("email"))
    });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{isEdit ? "編輯聯絡人" : "新增聯絡人"}</h3>
        <label>姓名 <input name="name" required defaultValue={contact?.name ?? ""} /></label>
        <label>職稱 <input name="title" defaultValue={contact?.title ?? ""} /></label>
        <label>Email <input name="email" type="email" required defaultValue={contact?.email ?? ""} /></label>
        <div className="modal-actions">
          <button type="submit">{isEdit ? "儲存" : "新增"}</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
