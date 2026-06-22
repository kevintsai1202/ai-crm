import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AiBadge } from "./AiBadge";
import { FeedbackButtons } from "./FeedbackButtons";
import { AiThinkingIndicator } from "./AiThinkingIndicator";
import { downloadMarkdown } from "../../lib/download";

/**
 * 整體評估報告 Modal：渲染 AI 產出的 Markdown 報告（單客戶 360° 或 Portfolio 共用）。
 * 函式級註解：loading=true 等待首字；streaming=true 串流進行中；兩者皆 false 為完成狀態。
 */
export function ReportModal({ report, onClose }: {
  report: {
    title: string;
    loading: boolean;
    /** 串流中（有內容但尚未完成）。 */
    streaming?: boolean;
    markdown: string;
    meta?: string;
    callId?: number | null;
  };
  onClose: () => void;
}) {
  const isStreaming = report.streaming ?? false;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{report.title} <AiBadge onDark /></h3>
            {report.meta ? <small>{report.meta}</small> : null}
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>
        <div className="report-body">
          {report.loading ? (
            /* 等待首字 */
            <AiThinkingIndicator label="AI 正在綜合分析" />
          ) : (
            /* 串流中或完成：顯示 Markdown 內容 */
            <div className={isStreaming ? "markdown-body ai-streaming-body" : "markdown-body"}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{report.markdown}</ReactMarkdown>
              {isStreaming && <span className="ai-stream-cursor" />}
            </div>
          )}
        </div>
        <div className="report-footer">
          {!report.loading && !isStreaming ? <FeedbackButtons callId={report.callId} /> : null}
          {!report.loading && report.markdown ? (
            <button
              type="button"
              className="btn-secondary"
              style={{ fontSize: 13 }}
              onClick={() => downloadMarkdown(report.title, report.markdown)}
            >
              ⬇ 下載 MD
            </button>
          ) : null}
          <button type="button" onClick={onClose}>關閉</button>
        </div>
      </div>
    </div>
  );
}
