import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  fetchWorkspaceRecommendation,
  streamWorkspaceRecommendation,
  fetchWorkspaceHistory,
  createOpportunity,
  type SseChunk
} from "../../api";
import type { WorkspaceTodoItem, SuggestedOpportunityDraft, AiCallHistoryItem } from "../../types";
import { useAuth } from "../../context/AuthContext";
import { AiBadge } from "../../components/common/AiBadge";
import { AiThinkingIndicator } from "../../components/common/AiThinkingIndicator";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";
import { AddOpportunityModal } from "../customers/components/AddOpportunityModal";

/** 待辦類型對應的中文標籤與色票 class。 */
const TODO_META: Record<string, { label: string; cls: string }> = {
  HIGH_RISK: { label: "高風險", cls: "high" },
  RENEWAL_DUE: { label: "即將續約", cls: "medium" },
  STALE_OPPORTUNITY: { label: "停滯商機", cls: "medium" }
};

/**
 * 我的工作檯「AI 工作建議」對話框：待辦清單（可點跳客戶）+ AI 總結（逐字串流）+ AI 建議商機草稿。
 * 以 Modal 呈現（由按鈕開啟），不常駐頁面；個人問答另由浮動對話視窗負責。
 *
 * @param onClose 關閉 callback
 */
export function WorkspaceAiModal({ onClose }: { onClose: () => void }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canSwitchScope = user?.role !== "SALES";
  const [scope, setScope] = useState<"self" | "all">("self");

  const [todos, setTodos] = useState<WorkspaceTodoItem[]>([]);
  const [drafts, setDrafts] = useState<SuggestedOpportunityDraft[]>([]);
  const [summary, setSummary] = useState("");
  const [generating, setGenerating] = useState(false);
  const [model, setModel] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [draftFor, setDraftFor] = useState<SuggestedOpportunityDraft | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [calls, setCalls] = useState<AiCallHistoryItem[]>([]);
  const [callsLoading, setCallsLoading] = useState(false);

  // 開啟 / 切換範圍：讀上次總結 + 即時待辦
  useEffect(() => {
    let alive = true;
    setLoading(true);
    fetchWorkspaceRecommendation(scope)
      .then((r) => {
        if (!alive) return;
        setTodos(r.todos ?? []);
        setSummary(r.summary ?? "");
        setModel(r.model);
        setDrafts(r.drafts ?? []);
      })
      .catch((e) => console.error("讀取工作推薦失敗:", e))
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [scope]);

  /** 產生工作建議：SSE 串流，先收 todos/drafts 再逐字收 AI 總結。 */
  function handleGenerate() {
    setGenerating(true);
    setSummary("");
    setDrafts([]);
    const onChunk = (chunk: SseChunk) => {
      if (chunk.type === "todos") setTodos((chunk.items as WorkspaceTodoItem[]) ?? []);
      else if (chunk.type === "drafts") setDrafts((chunk.items as SuggestedOpportunityDraft[]) ?? []);
      else if (chunk.type === "content" && chunk.delta) setSummary((prev) => prev + chunk.delta);
    };
    streamWorkspaceRecommendation(scope, onChunk, () => setGenerating(false), (e) => {
      console.error("產生工作建議失敗:", e);
      setGenerating(false);
    });
  }

  /** 開啟 AI 歷程。 */
  async function openHistory() {
    setHistoryOpen(true);
    setCallsLoading(true);
    try {
      setCalls(await fetchWorkspaceHistory());
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
        <div className="modal-content report-modal workspace-ai-modal" onClick={(e) => e.stopPropagation()}>
          <div className="report-header">
            <div>
              <h3>AI 工作建議 <AiBadge onDark /></h3>
              <small>依待辦自動排序今日優先工作</small>
            </div>
            <div className="workspace-modal-head-actions">
              {canSwitchScope ? (
                <select value={scope} onChange={(e) => setScope(e.target.value as "self" | "all")} aria-label="資料範圍">
                  <option value="self">範圍：自己</option>
                  <option value="all">範圍：全部</option>
                </select>
              ) : null}
              <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
            </div>
          </div>

          <div className="report-body workspace-ai-grid">
            {/* 左：待辦清單 */}
            <div className="workspace-col">
              <h4 className="workspace-col-title">今日待辦</h4>
              {loading ? (
                <AiThinkingIndicator label="載入中" />
              ) : todos.length === 0 ? (
                <p className="workspace-empty">目前沒有待辦事項 🎉</p>
              ) : (
                <ul className="workspace-todos">
                  {todos.map((t, i) => {
                    const meta = TODO_META[t.type] ?? { label: t.type, cls: "medium" };
                    return (
                      <li key={i}>
                        <button type="button" className={`todo-item sev-${meta.cls}`} onClick={() => { onClose(); navigate(`/customers/${t.customerId}`); }}>
                          <span className="todo-tag">{meta.label}</span>
                          <span className="todo-customer">{t.customerName}</span>
                          <span className="todo-reason">{t.reason}</span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            {/* 右：AI 總結 + 建議商機 */}
            <div className="workspace-col">
              <h4 className="workspace-col-title">AI 工作建議</h4>
              <div className="workspace-ai-summary">
                {!summary && generating ? (
                  <AiThinkingIndicator label="AI 正在分析" />
                ) : summary ? (
                  <div className={generating ? "markdown-body ai-streaming-body" : "markdown-body"}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{summary}</ReactMarkdown>
                    {generating && <span className="ai-stream-cursor" />}
                    {!generating && model ? <small className="workspace-ai-model">（{model}）</small> : null}
                  </div>
                ) : (
                  <p className="workspace-empty">點下方「產生我的工作建議」由 AI 依待辦給出今日優先順序。</p>
                )}
              </div>

              {drafts.length > 0 ? (
                <div className="workspace-drafts">
                  <h4 className="workspace-col-title">AI 建議商機</h4>
                  <ul>
                    {drafts.map((d, i) => (
                      <li key={i} className="draft-card">
                        <div className="draft-main"><strong>{d.customerName}</strong><span>{d.name}</span></div>
                        <div className="draft-rationale">{d.rationale}</div>
                        <button type="button" className="btn-secondary draft-create" onClick={() => setDraftFor(d)}>＋ 建立商機</button>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </div>
          </div>

          <div className="report-footer">
            <button type="button" className="btn-secondary" onClick={openHistory}>🕘 AI 歷程</button>
            <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
              {generating ? "產生中…" : "✨ 產生我的工作建議"}
            </button>
            <button type="button" onClick={onClose}>關閉</button>
          </div>
        </div>
      </div>

      {/* AI 草稿 → 預填新增商機 Modal */}
      {draftFor ? (
        <AddOpportunityModal
          customerName={draftFor.customerName}
          initialValues={{ name: draftFor.name, stage: draftFor.suggestedStage }}
          onClose={() => setDraftFor(null)}
          onSubmit={async (data) => {
            try {
              await createOpportunity({ customerId: draftFor.customerId, ...data });
              setDrafts((prev) => prev.filter((x) => x !== draftFor));
              setDraftFor(null);
            } catch (e) {
              console.error("建立商機失敗:", e);
            }
          }}
        />
      ) : null}

      {/* AI 歷程彈窗 */}
      {historyOpen ? (
        <AiCallHistoryModal title="我的工作檯 AI 歷程" calls={calls} loading={callsLoading} onClose={() => setHistoryOpen(false)} />
      ) : null}
    </>
  );
}
