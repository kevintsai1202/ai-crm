import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import type { CustomerSummary } from "../../../types";
import { AiBadge } from "../../../components/common/AiBadge";
import { ChatBubble } from "./ChatBubble";
import type { ChatMessage } from "../useAiChat";

/**
 * 預設提問建議，協助使用者快速開始對話。
 */
const CHAT_SUGGESTIONS = [
  "分析這位客戶的續約風險並建議下一步",
  "用三點條列總結最近的互動重點",
  "這位客戶最大的成交障礙是什麼？"
];

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
  const [input, setInput] = useState("");
  const bodyRef = useRef<HTMLDivElement>(null);

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
    <section className="chat-window" role="dialog" aria-label="AI 助理聊天視窗">
      <header className="chat-header">
        <div>
          <strong>AI 助理 <AiBadge onDark /></strong>
          <small>{customer ? customer.name : "尚未選取客戶"} · RAG + 對話記憶</small>
        </div>
        <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
      </header>

      <div className="chat-body" ref={bodyRef}>
        {historyLoading ? (
          <div className="chat-empty">
            <p>載入對話紀錄中…</p>
          </div>
        ) : messages.length === 0 ? (
          <div className="chat-empty">
            <p>{customer ? `向 AI 助理詢問關於「${customer.name}」的問題` : "請先在左側選取客戶"}</p>
            {customer ? (
              <div className="chat-suggestions">
                {CHAT_SUGGESTIONS.map((s) => (
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
          placeholder={customer ? "輸入問題後按 Enter 送出（Shift+Enter 換行）" : "請先選取客戶"}
          disabled={!customer || sending}
        />
        <button type="submit" disabled={!customer || !input.trim() || sending}>{sending ? "回應中…" : "送出"}</button>
      </form>
    </section>
  );
}
