import { FormEvent } from "react";

/**
 * 新增互動紀錄 Modal。
 */
export function AddInteractionModal({ customerName, onSubmit, onClose }: { customerName: string; onSubmit: (data: { type: string; occurredAt: string; content: string }) => void; onClose: () => void }) {
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
        <h3>新增互動 — {customerName}</h3>
        <label>類型
          <select name="type" required>
            <option value="PHONE">電話</option>
            <option value="MEETING">會議</option>
            <option value="EMAIL">Email</option>
            <option value="SUPPORT_TICKET">客服工單</option>
          </select>
        </label>
        <label>時間 <input name="occurredAt" type="datetime-local" required /></label>
        <label>內容 <textarea name="content" rows={3} required /></label>
        <div className="modal-actions">
          <button type="submit">新增</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}
