import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { AiCallHistoryItem } from "../../types";
import { formatDateTime } from "../../lib/format";
import { AiBadge } from "./AiBadge";
import { useTranslation } from "react-i18next";

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
  const { t, i18n } = useTranslation("common");
  // 目前展開中的呼叫 id（預設收合，點選展開）
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{title} <AiBadge onDark /></h3>
            <small>{t("ai.historySubtitle")}</small>
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label={t("actions.close")}>✕</button>
        </div>
        <div className="report-body">
          <h4 className="ai-history-section">{t("ai.historyCount", { count: calls.length })}</h4>
          {loading ? (
            <p className="chat-typing">{t("common:loading", { defaultValue: "Loading" })}<span>…</span></p>
          ) : calls.length === 0 ? (
            <p className="trace-empty">{t("ai.historyEmpty")}</p>
          ) : (
            <div className="ai-history-list">
              {calls.map((call) => {
                const open = expandedId === call.id;
                return (
                  <article className={`ai-history-item ${open ? "open" : ""}`} key={call.id}>
                    <button type="button" className="ai-history-head" onClick={() => setExpandedId(open ? null : call.id)}>
                      <span className="ai-history-type">{t(`ai.callTypes.${call.callType}`, { defaultValue: call.callType })}</span>
                      <span className="ai-history-time">{formatDateTime(call.createdAt, i18n.language, t("noData"))}</span>
                      <span className={`ai-history-mode ${call.aiEnabled ? "real" : "fallback"}`}>
                        {call.aiEnabled ? (call.model ?? "LLM") : t("ai.fallback")}
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
          <button type="button" onClick={onClose}>{t("actions.close")}</button>
        </div>
      </div>
    </div>
  );
}
