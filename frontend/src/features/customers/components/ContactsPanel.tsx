import type { ContactResponse } from "../../../types";

/**
 * 聯絡人區塊：列出客戶聯絡人，提供新增 / 編輯 / 刪除入口。
 * 函式級註解：標題列有「+ 新增聯絡人」；每筆顯示姓名 / 職稱 / Email 與「編輯」「刪除」小按鈕。
 *
 * @param contacts 聯絡人清單
 * @param onAdd 點擊新增 callback
 * @param onEdit 點擊編輯某聯絡人 callback
 * @param onDelete 點擊刪除某聯絡人 callback
 */
export function ContactsPanel({
  contacts,
  onAdd,
  onEdit,
  onDelete
}: {
  contacts: ContactResponse[];
  onAdd: () => void;
  onEdit: (contact: ContactResponse) => void;
  onDelete: (contact: ContactResponse) => void;
}) {
  return (
    <section className="panel">
      <div className="panel-title">
        <h3>聯絡人</h3>
        <button type="button" className="btn-secondary" onClick={onAdd}>+ 新增聯絡人</button>
      </div>
      {contacts.length === 0 ? (
        <p className="contact-empty">尚無聯絡人</p>
      ) : (
        <div className="contact-list">
          {contacts.map((c) => (
            <article className="contact-item" key={c.id}>
              <div className="contact-info">
                <strong>{c.name}</strong>
                {c.title ? <span className="contact-title">{c.title}</span> : null}
                <small>{c.email}</small>
              </div>
              <div className="contact-actions">
                <button type="button" className="row-btn" onClick={() => onEdit(c)}>編輯</button>
                <button type="button" className="row-btn row-btn-danger" onClick={() => onDelete(c)}>刪除</button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
