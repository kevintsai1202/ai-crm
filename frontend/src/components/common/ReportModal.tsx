import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AiBadge } from "./AiBadge";
import { FeedbackButtons } from "./FeedbackButtons";

/**
 * 整體評估報告 Modal：渲染 AI 產出的 Markdown 報告（單客戶 360° 或 Portfolio 共用）。
 */
export function ReportModal({ report, onClose }: { report: { title: string; loading: boolean; markdown: string; meta?: string; callId?: number | null }; onClose: () => void }) {
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
            <p className="chat-typing">AI 正在綜合分析<span>…</span></p>
          ) : (
            <div className="markdown-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{report.markdown}</ReactMarkdown>
            </div>
          )}
        </div>
        <div className="report-footer">
          {!report.loading ? <FeedbackButtons callId={report.callId} /> : null}
          <button type="button" onClick={onClose}>關閉</button>
        </div>
      </div>
    </div>
  );
}
