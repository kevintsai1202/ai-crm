import { useEffect, useState } from "react";
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
  const [insight, setInsight] = useState<ManagerInsightResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  // AI 歷程彈窗狀態
  const [historyOpen, setHistoryOpen] = useState(false);
  const [calls, setCalls] = useState<AiCallHistoryItem[]>([]);
  const [callsLoading, setCallsLoading] = useState(false);

  const title = scope === "TEAM" ? "團隊整體診斷" : `${owner} 的輔導報告`;

  // 開啟先讀快取
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setErr(null);
    const p = scope === "TEAM" ? fetchTeamInsight() : fetchOwnerInsight(owner as string);
    p.then((r) => { if (alive) setInsight(r); })
      .catch((e) => { console.error("讀取 AI 分析快取失敗:", e); if (alive) setErr("讀取失敗"); })
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
      console.error("產生 AI 分析串流失敗:", e);
      setErr("產生失敗，請稍後再試");
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
      console.error("讀取 AI 歷程失敗:", e);
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
              {insight ? <small>上次分析：{formatDateTime(insight.generatedAt)}{insight.model ? `（${insight.model}）` : "（教學版摘要）"}</small> : null}
            </div>
            <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
          </div>
          <div className="report-body">
            {loading ? (
              <AiThinkingIndicator label="載入分析中" />
            ) : err ? (
              <p className="trace-empty">{err}</p>
            ) : insight ? (
              /* generating 為 true 時：有內容但仍在串流 */
              <div className={generating && insight.content ? "markdown-body ai-streaming-body" : "markdown-body"}>
                {!insight.content && generating
                  ? <AiThinkingIndicator label="AI 正在分析" />
                  : <ReactMarkdown remarkPlugins={[remarkGfm]}>{insight.content}</ReactMarkdown>
                }
                {generating && insight.content && <span className="ai-stream-cursor" />}
              </div>
            ) : (
              generating
                ? <AiThinkingIndicator label="AI 正在分析" />
                : <p className="trace-empty">尚未產生分析，點「重新分析」由 AI 產出。</p>
            )}
          </div>
          <div className="report-footer">
            <button type="button" className="btn-secondary" onClick={openHistory}>🕘 AI 歷程</button>
            {insight?.content && !generating ? (
              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: 13 }}
                onClick={() => downloadMarkdown(title, insight.content)}
              >
                ⬇ 下載 MD
              </button>
            ) : null}
            <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
              {generating ? "分析中…" : "重新分析"}
            </button>
            <button type="button" onClick={onClose}>關閉</button>
          </div>
        </div>
      </div>
      {historyOpen ? (
        <AiCallHistoryModal
          title={`${title} AI 歷程`}
          calls={calls}
          loading={callsLoading}
          onClose={() => setHistoryOpen(false)}
        />
      ) : null}
    </>
  );
}
