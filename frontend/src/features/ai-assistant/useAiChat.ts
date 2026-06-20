import { useState } from "react";
import { askAssistantStream } from "../../api";
import type { CitationResponse, RiskResponse } from "../../types";

/**
 * 聊天訊息模型：一則使用者或 AI 助理的訊息，AI 訊息可附帶引用與風險。
 */
export interface ChatMessage {
  /** 發話角色。 */
  role: "user" | "assistant";
  /** 訊息內容（AI 為 Markdown 文字）。 */
  content: string;
  /** AI 引用來源（選填）。 */
  citations?: CitationResponse[];
  /** AI 風險評分（選填）。 */
  risk?: RiskResponse;
  /** 是否仍在串流產生中。 */
  pending?: boolean;
  /** AI 呼叫紀錄 id（串流結束時取得，供採納/拒絕回饋）。 */
  callId?: number;
}

/**
 * AI 助理對話 hook：管理對話歷史、開關、送出與 SSE 串流。
 * 函式級註解：把原 App.tsx 的 messages/chatSending/chatOpen 狀態與 handleAiChat 邏輯集中於此。
 */
export function useAiChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatSending, setChatSending] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);

  /** 清空對話（切換客戶時呼叫）。 */
  function resetChat() {
    setMessages([]);
  }

  /**
   * 送出問題並以 SSE 串流逐字填入最後一則 AI 訊息。
   * @param customerId 目標客戶 ID
   * @param message 使用者輸入
   */
  function sendChat(customerId: number, message: string) {
    if (chatSending) return;
    setMessages((prev) => [
      ...prev,
      { role: "user", content: message },
      { role: "assistant", content: "", citations: [], pending: true }
    ]);
    setChatSending(true);

    const patchLastAssistant = (patch: (m: ChatMessage) => ChatMessage) => {
      setMessages((prev) => {
        const copy = [...prev];
        const lastIndex = copy.length - 1;
        if (lastIndex >= 0 && copy[lastIndex].role === "assistant") {
          copy[lastIndex] = patch(copy[lastIndex]);
        }
        return copy;
      });
    };

    askAssistantStream(
      customerId,
      message,
      (chunk) => {
        if (chunk.type === "content" && chunk.delta !== undefined) {
          patchLastAssistant((m) => ({ ...m, content: m.content + chunk.delta, pending: false }));
        } else if (chunk.type === "citations" && chunk.citations !== undefined) {
          patchLastAssistant((m) => ({ ...m, citations: chunk.citations }));
        } else if (chunk.type === "risk" && chunk.risk !== undefined) {
          patchLastAssistant((m) => ({ ...m, risk: chunk.risk }));
        } else if (chunk.type === "callId" && chunk.callId !== undefined) {
          patchLastAssistant((m) => ({ ...m, callId: chunk.callId }));
        }
      },
      () => {
        patchLastAssistant((m) => ({ ...m, pending: false }));
        setChatSending(false);
      },
      (err) => {
        console.error("AI 助理串流失敗:", err);
        patchLastAssistant((m) => ({ ...m, content: m.content || "⚠️ AI 助理連線失敗，請稍後再試。", pending: false }));
        setChatSending(false);
      }
    );
  }

  return { messages, chatSending, chatOpen, setChatOpen, sendChat, resetChat };
}
