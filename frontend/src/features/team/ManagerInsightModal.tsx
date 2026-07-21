import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  fetchTeamInsight,
  fetchOwnerInsight,
  streamTeamInsight,
  streamOwnerInsight,
  fetchTeamInsightCalls,
  fetchOwnerInsightCalls
} from "../../api";
import type { ManagerInsightResponse, AiCallHistoryItem } from "../../types";
import { formatDateTime } from "../../lib/format";
import { downloadMarkdown } from "../../lib/download";
import { AiBadge } from "../../components/common/AiBadge";
import { AiThinkingIndicator } from "../../components/common/AiThinkingIndicator";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";

/**
 * Manager AI 分析彈窗：團隊診斷（scope=TEAM）或個別業務 coaching（scope=OWNER）。
 * 函式級註解：開啟先讀快取顯示報告 + 上次分析時間；「重新分析」呼叫 LLM；「AI 歷程」開歷程彈窗。
 *
 * @param scope TEAM 或 OWNER
 * @param owner OWNER 時的業務名
 * @param onClose 關閉 callback
 */
export function ManagerInsightModal({ scope, owner, onClose }: {
  scope: "TEAM" | "OWNER";
  owner?: string;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation(["operations", "common"]);
  const [insight, setInsight] = useState<ManagerInsightResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  // AI 歷程彈窗狀態
  const [historyOpen, setHistoryOpen] = useState(false);
  const [calls, setCalls] = useState<AiCallHistoryItem[]>([]);
  const [callsLoading, setCallsLoading] = useState(false);

  const title = scope === "TEAM"
    ? t("team.insight.teamTitle")
    : t("team.insight.ownerTitle", { owner });

  // 開啟先讀快取
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setErr(null);
    const p = scope === "TEAM" ? fetchTeamInsight() : fetchOwnerInsight(owner as string);
    p.then((r) => { if (alive) setInsight(r); })
      .catch((e) => { console.error("Failed to load cached AI analysis:", e); if (alive) setErr("team.insight.readError"); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [scope, owner]);

  /** 點按生成：以 SSE 串流推送，邊產生邊渲染。 */
  function handleGenerate() {
    setGenerating(true);
    setErr(null);
    // 清空舊內容，準備接收串流
    setInsight((prev) => prev ? { ...prev, content: "" } : { scope, ownerName: owner ?? null, content: "", model: null, generatedAt: new Date().toISOString() });

    const onChunk = (chunk: { type: string; delta?: string }) => {
      if (chunk.type === "content" && chunk.delta) {
        setInsight((prev) => prev ? { ...prev, content: prev.content + chunk.delta } : prev);
      }
    };
    const onDone = () => {
      setGenerating(false);
      // 完成後重讀快取，確保 generatedAt / model 是後端最新值
      const p = scope === "TEAM" ? fetchTeamInsight() : fetchOwnerInsight(owner as string);
      p.then((r) => { if (r) setInsight(r); }).catch(() => {});
    };
    const onError = (e: any) => {
      console.error("Failed to stream AI analysis:", e);
      setErr("team.insight.generateError");
      setGenerating(false);
    };

    if (scope === "TEAM") {
      streamTeamInsight(onChunk, onDone, onError);
    } else {
      streamOwnerInsight(owner as string, onChunk, onDone, onError);
    }
  }

  /** 開啟 AI 歷程：載入對應範圍的歷次呼叫。 */
  async function openHistory() {
    setHistoryOpen(true);
    setCallsLoading(true);
    try {
      const list = scope === "TEAM" ? await fetchTeamInsightCalls() : await fetchOwnerInsightCalls(owner as string);
      setCalls(list);
    } catch (e) {
      console.error("Failed to load AI history:", e);
      setCalls([]);
    } finally {
      setCallsLoading(false);
    }
  }

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
          <div className="report-header">
            <div>
              <h3>{title} <AiBadge onDark /></h3>
              {insight ? <small>
                {t("team.insight.lastAnalyzed", { date: formatDateTime(insight.generatedAt, i18n.language, t("noData", { ns: "common" })) })}
                {insight.model ? ` (${insight.model})` : ` (${t("team.insight.teachingSummary")})`}
              </small> : null}
            </div>
            <button type="button" className="chat-close" onClick={onClose} aria-label={t("actions.close", { ns: "common" })}>✕</button>
          </div>
          <div className="report-body">
            {loading ? (
              <AiThinkingIndicator label={t("team.insight.loading")} />
            ) : err ? (
              <p className="trace-empty">{t(err)}</p>
            ) : insight ? (
              /* generating 為 true 時：有內容但仍在串流 */
              <div className={generating && insight.content ? "markdown-body ai-streaming-body" : "markdown-body"}>
                {!insight.content && generating
                  ? <AiThinkingIndicator label={t("team.insight.analyzing")} />
                  : <ReactMarkdown remarkPlugins={[remarkGfm]}>{insight.content}</ReactMarkdown>
                }
                {generating && insight.content && <span className="ai-stream-cursor" />}
              </div>
            ) : (
              generating
                ? <AiThinkingIndicator label={t("team.insight.analyzing")} />
                : <p className="trace-empty">{t("team.insight.empty")}</p>
            )}
          </div>
          <div className="report-footer">
            <button type="button" className="btn-secondary" onClick={openHistory}>{t("team.insight.history")}</button>
            {insight?.content && !generating ? (
              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: 13 }}
                onClick={() => downloadMarkdown(title, insight.content)}
              >
                {t("team.insight.download")}
              </button>
            ) : null}
            <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
              {generating ? t("team.insight.generating") : t("team.insight.regenerate")}
            </button>
            <button type="button" onClick={onClose}>{t("actions.close", { ns: "common" })}</button>
          </div>
        </div>
      </div>
      {historyOpen ? (
        <AiCallHistoryModal
          title={t("team.insight.historyTitle", { title })}
          calls={calls}
          loading={callsLoading}
          onClose={() => setHistoryOpen(false)}
        />
      ) : null}
    </>
  );
}
