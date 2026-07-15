import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { AgentTraceResponse, AiCallHistoryItem } from "../../../types";
import { formatDateTime } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";

/** AI 呼叫類型的中文標籤。 */
const CALL_TYPE_LABELS: Record<string, string> = {
  CHAT: "對話",
  ASSESSMENT: "整體評估",
  PORTFOLIO: "Portfolio 評估"
};

/**
 * AI 歷程 Modal：列出該客戶歷次 AI 呼叫(新到舊)與 Agent 決策歷程(GOAP 步驟)。
 * 函式級註解：取代原本固定佔版面的 Agent Trace 區塊,改為按鈕點選才開啟;
 * 並補上「歷史每一次呼叫」的完整內容(原本只看得到最後一次)。
 *
 * @param customerName 客戶名稱(標題用)
 * @param calls AI 呼叫歷史清單
 * @param trace Agent 決策歷程(可為 null)
 * @param loading 是否載入中
 * @param onClose 關閉 callback
 */
export function AiHistoryModal({ customerName, calls, trace, loading, onClose }: {
  customerName: string;
  calls: AiCallHistoryItem[];
  trace: AgentTraceResponse | null;
  loading: boolean;
  onClose: () => void;
}) {
  // 目前展開中的呼叫 id(清單預設收合答案,點選展開,避免一次塞入大量長文)
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>AI 歷程 — {customerName} <AiBadge onDark /></h3>
            <small>歷次 AI 呼叫與 Agent 決策步驟</small>
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>

        <div className="report-body">
          {/* 區段一:歷次 AI 呼叫紀錄 */}
          <h4 className="ai-history-section">AI 呼叫歷史（{calls.length}）</h4>
          {loading ? (
            <p className="chat-typing">載入中<span>…</span></p>
          ) : calls.length === 0 ? (
            <p className="trace-empty">此客戶尚無 AI 呼叫紀錄。點「整體評估」或「詢問 AI 助理」後即會記錄。</p>
          ) : (
            <div className="ai-history-list">
              {calls.map((call) => {
                const open = expandedId === call.id;
                return (
                  <article className={`ai-history-item ${open ? "open" : ""}`} key={call.id}>
                    <button type="button" className="ai-history-head" onClick={() => setExpandedId(open ? null : call.id)}>
                      <span className="ai-history-type">{CALL_TYPE_LABELS[call.callType] ?? call.callType}</span>
                      <span className="ai-history-time">{formatDateTime(call.createdAt, "zh-TW", "-")}</span>
                      {/* 模型 / fallback 標記:真實呼叫顯示模型名,否則標示為樣板回覆 */}
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

          {/* 區段二:Agent 決策歷程(GOAP 步驟) */}
          <h4 className="ai-history-section">Agent 決策歷程</h4>
          <p className="trace-intro">AI 助理分析此客戶的決策步驟,顯示如何一步步檢索資料、評估風險並產生建議。</p>
          {trace ? (
            <>
              <p className="recommendation">{trace.finalRecommendation}</p>
              <div className="trace-list">
                {trace.steps.map((step) => (
                  <article className="trace-step" key={`${step.order}-${step.action}`}>
                    <b className="trace-action">{step.order}. {step.action}</b>
                    <span className="trace-meta">{step.status} / {step.durationMs}ms</span>
                    <p>{step.output}</p>
                  </article>
                ))}
              </div>
            </>
          ) : <p className="trace-empty">尚無 Agent 決策歷程。</p>}
        </div>

        <div className="report-footer">
          <button type="button" onClick={onClose}>關閉</button>
        </div>
      </div>
    </div>
  );
}
