/**
 * 浮動 AI 聊天啟動鈕（右下角），聊天視窗開啟時隱藏。
 */
export function ChatLauncher({ open, unread, customerName, onOpen }: { open: boolean; unread: number; customerName?: string; onOpen: () => void }) {
  if (open) return null;
  return (
    <button type="button" className="chat-launcher" onClick={onOpen} title={customerName ? `詢問 AI 助理：${customerName}` : "請先選取客戶"}>
      <span className="chat-launcher-icon">💬</span>
      <span className="chat-launcher-label">AI 助理</span>
      {unread > 0 ? <span className="chat-launcher-badge">{Math.ceil(unread / 2)}</span> : null}
    </button>
  );
}
