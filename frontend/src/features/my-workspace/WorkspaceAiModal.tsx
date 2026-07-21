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
import { TaskFormModal } from "../tasks/TaskFormModal";
import { TaskPanel } from "../tasks/TaskPanel";
import { useTranslation } from "react-i18next";

/** 待辦類型對應的色票 class；顯示文字由 i18n 資源提供。 */
const TODO_CLASS: Record<string, string> = {
  HIGH_RISK: "high",
  RENEWAL_DUE: "medium",
  STALE_OPPORTUNITY: "medium"
};

/**
 * 我的工作檯「AI 工作建議」對話框：待辦清單（可點跳客戶）+ AI 總結（逐字串流）+ AI 建議商機草稿。
 * 以 Modal 呈現（由按鈕開啟），不常駐頁面；個人問答另由浮動對話視窗負責。
 *
 * @param onClose 關閉 callback
 */
export function WorkspaceAiModal({ onClose }: { onClose: () => void }) {
  const { t, i18n } = useTranslation("operations");
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
  // 一鍵建立：勾選的草稿索引（預設全選）、建立中狀態、結果訊息
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [creating, setCreating] = useState(false);
  const [createResult, setCreateResult] = useState<{ ok: number; fail: number } | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [calls, setCalls] = useState<AiCallHistoryItem[]>([]);
  const [callsLoading, setCallsLoading] = useState(false);
  const [taskCustomer, setTaskCustomer] = useState<{ id: number; name: string } | null>(null);
  const [taskRefreshKey, setTaskRefreshKey] = useState(0);

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
    setCreateResult(null);
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

  // drafts 變動時預設全選（不在此清建立訊息，否則建立後移除草稿會把成功訊息清掉）
  useEffect(() => {
    setSelected(new Set(drafts.map((_, i) => i)));
  }, [drafts]);

  /** 切換某筆草稿的勾選。 */
  function toggleSelect(i: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i); else next.add(i);
      return next;
    });
  }

  /** 一鍵建立所有勾選的 AI 建議商機（重用既有建立流程，逐筆送出）。 */
  async function handleCreateAll() {
    const picks = drafts.filter((_, i) => selected.has(i));
    if (picks.length === 0 || creating) return;
    setCreating(true);
    setCreateResult(null);
    const results = await Promise.allSettled(picks.map((d) =>
      createOpportunity({
        customerId: d.customerId,
        name: d.name,
        stage: d.suggestedStage,
        amount: d.amount ?? 100000, // LLM 未給金額時以預設值帶入，建立後可再編輯
        expectedCloseDate: null,
        type: "NEW_BUSINESS",
        leadSource: "OUTBOUND",
        probability: null
      })
    ));
    const ok = results.filter((r) => r.status === "fulfilled").length;
    const fail = results.length - ok;
    // 移除已成功建立的草稿
    const createdSet = new Set(picks.filter((_, idx) => results[idx].status === "fulfilled"));
    setDrafts((prev) => prev.filter((d) => !createdSet.has(d)));
    setCreateResult({ ok, fail });
    setCreating(false);
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
              <h3>{t("workspace.title")} <AiBadge onDark /></h3>
              <small>{t("workspace.subtitle")}</small>
            </div>
            <div className="workspace-modal-head-actions">
              {canSwitchScope ? (
                <select value={scope} onChange={(e) => setScope(e.target.value as "self" | "all")} aria-label={t("workspace.scope")}>
                  <option value="self">{t("workspace.self")}</option>
                  <option value="all">{t("workspace.all")}</option>
                </select>
              ) : null}
              <button type="button" className="chat-close" onClick={onClose} aria-label={t("workspace.close")}>✕</button>
            </div>
          </div>

          <div className="report-body workspace-ai-grid">
            {/* 左：待辦清單 */}
            <div className="workspace-col">
              <h4 className="workspace-col-title">{t("workspace.today")}</h4>
              {loading ? (
                <AiThinkingIndicator label={t("workspace.loading")} />
              ) : todos.length === 0 ? (
                <p className="workspace-empty">{t("workspace.empty")}</p>
              ) : (
                <ul className="workspace-todos">
                  {todos.map((todo, i) => {
                    const cls = TODO_CLASS[todo.type] ?? "medium";
                    return (
                      <li key={i}>
                        <button type="button" className={`todo-item sev-${cls}`} onClick={() => { onClose(); navigate(`/customers/${todo.customerId}`); }}>
                          <span className="todo-tag">{t(`workspace.todoTypes.${todo.type}`, { defaultValue: todo.type })}</span>
                          <span className="todo-customer">{todo.customerName}</span>
                          <span className="todo-reason">{todo.reason}</span>
                        </button>
                        <button type="button" className="btn-secondary todo-call-task" onClick={() => setTaskCustomer({ id: todo.customerId, name: todo.customerName })}>☎ {t("workspace.scheduleCall")}</button>
                      </li>
                    );
                  })}
                </ul>
              )}
              <TaskPanel compact refreshKey={taskRefreshKey} />
            </div>

            {/* 右：AI 總結 + 建議商機 */}
            <div className="workspace-col">
              <h4 className="workspace-col-title">{t("workspace.title")}</h4>
              <div className="workspace-ai-summary">
                {!summary && generating ? (
                  <AiThinkingIndicator label={t("workspace.analyzing")} />
                ) : summary ? (
                  <div className={generating ? "markdown-body ai-streaming-body" : "markdown-body"}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{summary}</ReactMarkdown>
                    {generating && <span className="ai-stream-cursor" />}
                    {!generating && model ? <small className="workspace-ai-model">（{model}）</small> : null}
                  </div>
                ) : (
                  <p className="workspace-empty">{t("workspace.summaryEmpty")}</p>
                )}
              </div>

              {/* 建立結果訊息（放區塊外，草稿建立後清空仍可見） */}
              {createResult ? <p className="draft-create-msg">{t("workspace.createResult", { ok: createResult.ok, failed: createResult.fail > 0 ? t("workspace.failedSuffix", { count: createResult.fail }) : "" })}</p> : null}

              {drafts.length > 0 ? (
                <div className="workspace-drafts">
                  <div className="workspace-drafts-head">
                    <h4 className="workspace-col-title">{t("workspace.drafts")}</h4>
                    <button type="button" className="btn-assess draft-create-all" disabled={creating || selected.size === 0} onClick={handleCreateAll}>
                      {creating ? t("workspace.creating") : `✅ ${t("workspace.createAll", { count: selected.size })}`}
                    </button>
                  </div>
                  <ul>
                    {drafts.map((d, i) => (
                      <li key={i} className="draft-card">
                        <label className="draft-check">
                          <input type="checkbox" checked={selected.has(i)} onChange={() => toggleSelect(i)} />
                          <div className="draft-body">
                            <div className="draft-main">
                              <strong>{d.customerName}</strong><span>{d.name}</span>
                              {d.amount ? <em className="draft-amount">{t("workspace.estimated", { amount: d.amount.toLocaleString(i18n.language) })}</em> : null}
                              <span className={`draft-stage stage-${d.suggestedStage.toLowerCase()}`}>{d.suggestedStage}</span>
                            </div>
                            <div className="draft-rationale">{d.rationale}</div>
                          </div>
                        </label>
                        <button type="button" className="btn-secondary draft-create" onClick={() => setDraftFor(d)}>{t("workspace.editCreate")}</button>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </div>
          </div>

          <div className="report-footer">
            <button type="button" className="btn-secondary" onClick={openHistory}>🕘 {t("workspace.history")}</button>
            <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
              {generating ? t("workspace.generating") : `✨ ${t("workspace.generate")}`}
            </button>
            <button type="button" onClick={onClose}>{t("workspace.close")}</button>
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
        <AiCallHistoryModal title={t("workspace.historyTitle")} calls={calls} loading={callsLoading} onClose={() => setHistoryOpen(false)} />
      ) : null}
      {taskCustomer && user ? <TaskFormModal customerId={taskCustomer.id} customerName={taskCustomer.name} assigneeId={user.id} onCreated={() => { setTaskCustomer(null); setTaskRefreshKey((key) => key + 1); }} onClose={() => setTaskCustomer(null)} /> : null}
    </>
  );
}
