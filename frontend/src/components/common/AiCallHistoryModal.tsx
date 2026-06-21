import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { AiCallHistoryItem } from "../../types";
import { formatDateTime } from "../../lib/format";
import { AiBadge } from "./AiBadge";

/** AI 呼叫類型的中文標籤（涵蓋客戶與管理層各類型）。 */
const CALL_TYPE_LABELS: Record<string, string> = {
  CHAT: "對話",
  ASSESSMENT: "整體評估",
  PORTFOLIO: "全公司評估",
  TEAM_ANALYSIS: "團隊診斷",
  OWNER_COACHING: "業務輔導"
};

/**
 * 通用 AI 歷程 Modal：列出一組 AI 呼叫紀錄（新到舊，可展開看完整答案）。
 * 函式級註解：與客戶版 AiHistoryModal 同樣式，但不含 Agent trace，供團隊診斷 / 業務 coaching / 全公司評估共用。
 *
 * @param title 標題（如「團隊診斷 AI 歷程」）
 * @param calls AI 呼叫歷史清單
 * @param loading 是否載入中
 * @param onClose 關閉 callback
 */
export function AiCallHistoryModal({ title, calls, loading, onClose }: {
  title: string;
  calls: AiCallHistoryItem[];
  loading: boolean;
  onClose: () => void;
}) {
  // 目前展開中的呼叫 id（預設收合，點選展開）
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{title} <AiBadge onDark /></h3>
            <small>歷次 AI 呼叫紀錄</small>
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>
        <div className="report-body">
          <h4 className="ai-history-section">AI 呼叫歷史（{calls.length}）</h4>
          {loading ? (
            <p className="chat-typing">載入中<span>…</span></p>
          ) : calls.length === 0 ? (
            <p className="trace-empty">尚無 AI 呼叫紀錄。點「重新分析」後即會記錄。</p>
          ) : (
            <div className="ai-history-list">
              {calls.map((call) => {
                const open = expandedId === call.id;
                return (
                  <article className={`ai-history-item ${open ? "open" : ""}`} key={call.id}>
                    <button type="button" className="ai-history-head" onClick={() => setExpandedId(open ? null : call.id)}>
                      <span className="ai-history-type">{CALL_TYPE_LABELS[call.callType] ?? call.callType}</span>
                      <span className="ai-history-time">{formatDateTime(call.createdAt)}</span>
                      <span className={`ai-history-mode ${call.aiEnabled ? "real" : "fallback"}`}>
                        {call.aiEnabled ? (call.model ?? "LLM") : "樣板 fallback"}
                      </span>
                      <span className="ai-history-toggle">{open ? "▲" : "▼"}</span>
                    </button>
                    {open ? (
                      <div className="ai-history-answer markdown-body">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{call.answer}</ReactMarkdown>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          )}
        </div>
        <div className="report-footer">
          <button type="button" onClick={onClose}>關閉</button>
        </div>
      </div>
    </div>
  );
}
