import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useTranslation } from "react-i18next";
import type { AgentTraceResponse, AiCallHistoryItem } from "../../../types";
import { formatDateTime } from "../../../lib/format";
import { AiBadge } from "../../../components/common/AiBadge";

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
  const { t, i18n } = useTranslation(["customers", "common"]);
  // 目前展開中的呼叫 id(清單預設收合答案,點選展開,避免一次塞入大量長文)
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{t("customers:aiHistory.title", { name: customerName })} <AiBadge onDark /></h3>
            <small>{t("customers:aiHistory.subtitle")}</small>
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label={t("common:actions.close")}>✕</button>
        </div>

        <div className="report-body">
          {/* 區段一:歷次 AI 呼叫紀錄 */}
          <h4 className="ai-history-section">{t("customers:aiHistory.callsHeading", { count: calls.length })}</h4>
          {loading ? (
            <p className="chat-typing">{t("customers:aiHistory.loading")}<span>…</span></p>
          ) : calls.length === 0 ? (
            <p className="trace-empty">{t("customers:aiHistory.empty")}</p>
          ) : (
            <div className="ai-history-list">
              {calls.map((call) => {
                const open = expandedId === call.id;
                const callTypeLabel = t(`customers:aiHistory.callTypes.${call.callType}`, { defaultValue: call.callType });
                return (
                  <article className={`ai-history-item ${open ? "open" : ""}`} key={call.id}>
                    <button type="button" className="ai-history-head" onClick={() => setExpandedId(open ? null : call.id)}>
                      <span className="ai-history-type">{callTypeLabel}</span>
                      <span className="ai-history-time">{formatDateTime(call.createdAt, i18n.language, t("common:noData"))}</span>
                      {/* 模型 / fallback 標記:真實呼叫顯示模型名,否則標示為樣板回覆 */}
                      <span className={`ai-history-mode ${call.aiEnabled ? "real" : "fallback"}`}>
                        {call.aiEnabled ? (call.model ?? "LLM") : t("customers:aiHistory.fallbackMode")}
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
          <h4 className="ai-history-section">{t("customers:aiHistory.traceHeading")}</h4>
          <p className="trace-intro">{t("customers:aiHistory.traceIntro")}</p>
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
          ) : <p className="trace-empty">{t("customers:aiHistory.traceEmpty")}</p>}
        </div>

        <div className="report-footer">
          <button type="button" onClick={onClose}>{t("common:actions.close")}</button>
        </div>
      </div>
    </div>
  );
}
