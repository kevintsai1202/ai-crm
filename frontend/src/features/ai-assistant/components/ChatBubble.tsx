import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AiBadge } from "../../../components/common/AiBadge";
import { AiThinkingIndicator } from "../../../components/common/AiThinkingIndicator";
import { FeedbackButtons } from "../../../components/common/FeedbackButtons";
import type { ChatMessage } from "../useAiChat";

/**
 * 單則聊天氣泡：使用者為純文字，AI 以 Markdown 渲染並附風險與引用。
 */
export function ChatBubble({ msg }: { msg: ChatMessage }) {
  if (msg.role === "user") {
    return (
      <div className="chat-msg user">
        <div className="chat-bubble">{msg.content}</div>
      </div>
    );
  }
  const hasRisk = msg.risk && (msg.risk.churnRisk > 0 || msg.risk.renewalDelayRisk > 0);
  return (
    <div className="chat-msg assistant">
      <span className="chat-author"><AiBadge /> AI 助理</span>
      <div className="chat-bubble">
        {msg.content ? (
          <div className={msg.pending ? "markdown-body ai-streaming-body" : "markdown-body"}>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
            {msg.pending && <span className="ai-stream-cursor" />}
          </div>
        ) : (
          <AiThinkingIndicator label="AI 正在查詢資料庫" />
        )}
        {hasRisk ? (
          <div className="chat-risk">
            <span>流失 {msg.risk!.churnRisk}</span>
            <span>續約延遲 {msg.risk!.renewalDelayRisk}</span>
          </div>
        ) : null}
        {msg.citations && msg.citations.length > 0 ? (
          <div className="chat-citations">
            {msg.citations.map((c) => (
              <details key={c.title}>
                <summary>{c.title} · {c.docType} · {Number(c.similarity).toFixed(2)}</summary>
                <p>{c.content}</p>
              </details>
            ))}
          </div>
        ) : null}
        {!msg.pending ? <FeedbackButtons callId={msg.callId} /> : null}
      </div>
    </div>
  );
}
