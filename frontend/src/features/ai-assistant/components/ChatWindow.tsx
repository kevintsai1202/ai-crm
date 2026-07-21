import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import type { CustomerSummary } from "../../../types";
import { AiBadge } from "../../../components/common/AiBadge";
import { ChatBubble } from "./ChatBubble";
import type { ChatMessage } from "../useAiChat";
import { useTranslation } from "react-i18next";

/**
 * 浮動 AI 聊天視窗：多輪對話歷史、Markdown 渲染、串流打字機。
 * 函式級註解：右下角獨立彈出視窗，輸入框支援 Enter 送出 / Shift+Enter 換行，新訊息自動捲動到底。
 */
export function ChatWindow({
  customer,
  messages,
  sending,
  historyLoading = false,
  onSend,
  onClose
}: {
  customer: CustomerSummary | null;
  messages: ChatMessage[];
  sending: boolean;
  /** 伺服器歷史載入中 */
  historyLoading?: boolean;
  onSend: (message: string) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("operations");
  const [input, setInput] = useState("");
  const bodyRef = useRef<HTMLDivElement>(null);
  /** 依目前語言顯示快速提問，不把中文固定在元件模組中。 */
  const suggestions = t("assistant.suggestions", { returnObjects: true }) as string[];

  // 訊息更新時自動捲動到底部
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  /** 送出目前輸入內容。 */
  function submit() {
    const text = input.trim();
    if (!text || !customer || sending) return;
    onSend(text);
    setInput("");
  }

  /** 表單送出（按鈕）。 */
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    submit();
  }

  /** Enter 送出、Shift+Enter 換行。 */
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <section className="chat-window" role="dialog" aria-label={t("assistant.dialog")}>
      <header className="chat-header">
        <div>
          <strong>{t("assistant.name")} <AiBadge onDark /></strong>
          <small>{customer ? customer.name : t("assistant.noCustomer")} · {t("assistant.memory")}</small>
        </div>
        <button type="button" className="chat-close" onClick={onClose} aria-label={t("assistant.close")}>✕</button>
      </header>

      <div className="chat-body" ref={bodyRef}>
        {historyLoading ? (
          <div className="chat-empty">
            <p>{t("assistant.loading")}</p>
          </div>
        ) : messages.length === 0 ? (
          <div className="chat-empty">
            <p>{customer ? t("assistant.askCustomer", { customer: customer.name }) : t("assistant.selectLeft")}</p>
            {customer ? (
              <div className="chat-suggestions">
                {suggestions.map((s) => (
                  <button type="button" key={s} onClick={() => onSend(s)} disabled={sending}>{s}</button>
                ))}
              </div>
            ) : null}
          </div>
        ) : (
          messages.map((msg, index) => <ChatBubble key={index} msg={msg} />)
        )}
      </div>

      <form className="chat-footer" onSubmit={handleSubmit}>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          rows={2}
          placeholder={customer ? t("assistant.placeholder") : t("assistant.selectCustomer")}
          disabled={!customer || sending}
        />
        <button type="submit" disabled={!customer || !input.trim() || sending}>{sending ? t("assistant.responding") : t("assistant.send")}</button>
      </form>
    </section>
  );
}
