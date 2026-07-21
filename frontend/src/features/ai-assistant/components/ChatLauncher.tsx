/**
 * 浮動 AI 聊天啟動鈕（右下角），聊天視窗開啟時隱藏。
 */
export function ChatLauncher({ open, unread, customerName, onOpen }: { open: boolean; unread: number; customerName?: string; onOpen: () => void }) {
  const { t } = useTranslation("operations");
  if (open) return null;
  return (
    <button type="button" className="chat-launcher" onClick={onOpen} title={customerName ? t("assistant.askTitle", { customer: customerName }) : t("assistant.selectCustomer")}>
      <span className="chat-launcher-icon">💬</span>
      <span className="chat-launcher-label">{t("assistant.name")}</span>
      {unread > 0 ? <span className="chat-launcher-badge">{Math.ceil(unread / 2)}</span> : null}
    </button>
  );
}
import { useTranslation } from "react-i18next";
