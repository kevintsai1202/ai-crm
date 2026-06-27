import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  fetchWorkspaceRecommendation,
  streamWorkspaceRecommendation,
  streamWorkspaceChat,
  fetchWorkspaceHistory,
  createOpportunity,
  type SseChunk
} from "../../api";
import type {
  WorkspaceTodoItem,
  SuggestedOpportunityDraft,
  AiCallHistoryItem,
  CustomerSummary
} from "../../types";
import { useAuth } from "../../context/AuthContext";
import { AiBadge } from "../../components/common/AiBadge";
import { AiThinkingIndicator } from "../../components/common/AiThinkingIndicator";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";
import { AddOpportunityModal } from "../customers/components/AddOpportunityModal";
import { ChatLauncher } from "../ai-assistant/components/ChatLauncher";
import { ChatWindow } from "../ai-assistant/components/ChatWindow";
import type { ChatMessage } from "../ai-assistant/useAiChat";

/** 待辦類型對應的中文標籤與色票 class。 */
const TODO_META: Record<string, { label: string; cls: string }> = {
  HIGH_RISK: { label: "高風險", cls: "high" },
  RENEWAL_DUE: { label: "即將續約", cls: "medium" },
  STALE_OPPORTUNITY: { label: "停滯商機", cls: "medium" }
};

/** 總覽問答的合成客戶物件（id=0 代表跨「我的所有客戶」總覽，非真實客戶）。 */
const OVERVIEW_TARGET = { id: 0, name: "我的客戶組合（總覽）" } as CustomerSummary;

/**
 * 我的工作檯「個人 AI 助理」區塊：待辦清單 + AI 工作建議（逐字串流）+ AI 建議商機草稿。
 * 個人問答沿用全站一致的浮動對話視窗（ChatWindow/ChatLauncher）。
 * 函式級註解：SALES 僅能看自己；MANAGER/ADMIN 顯示「自己 / 全部」範圍切換。
 */
export function WorkspaceAiPanel({ customers = [] }: { customers?: CustomerSummary[] }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canSwitchScope = user?.role !== "SALES";
  const [scope, setScope] = useState<"self" | "all">("self");

  const [todos, setTodos] = useState<WorkspaceTodoItem[]>([]);
  const [drafts, setDrafts] = useState<SuggestedOpportunityDraft[]>([]);
  const [summary, setSummary] = useState("");
  const [generating, setGenerating] = useState(false);
  const [model, setModel] = useState<string | null>(null);
  const [draftFor, setDraftFor] = useState<SuggestedOpportunityDraft | null>(null);

  // 個人問答（沿用全站浮動對話視窗）
  const [chatOpen, setChatOpen] = useState(false);
  const [chatSending, setChatSending] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  // 問答對象：OVERVIEW_TARGET（總覽）或某真實客戶
  const [chatTarget, setChatTarget] = useState<CustomerSummary>(OVERVIEW_TARGET);

  // AI 歷程
  const [historyOpen, setHistoryOpen] = useState(false);
  const [calls, setCalls] = useState<AiCallHistoryItem[]>([]);
  const [callsLoading, setCallsLoading] = useState(false);

  // 進區塊 / 切換範圍：讀上次總結 + 即時待辦
  useEffect(() => {
    let alive = true;
    fetchWorkspaceRecommendation(scope)
      .then((r) => {
        if (!alive) return;
        setTodos(r.todos ?? []);
        setSummary(r.summary ?? "");
        setModel(r.model);
        setDrafts(r.drafts ?? []);
      })
      .catch((e) => console.error("讀取工作推薦失敗:", e));
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

  /** 送出個人問答：以 SSE 串流逐字填入最後一則 AI 訊息（沿用 ChatMessage / ChatBubble）。 */
  function handleSend(text: string) {
    if (chatSending) return;
    setChatSending(true);
    setMessages((prev) => [...prev, { role: "user", content: text }, { role: "assistant", content: "", pending: true }]);
    const patchLast = (fn: (m: ChatMessage) => ChatMessage) => {
      setMessages((prev) => {
        const next = [...prev];
        const i = next.length - 1;
        if (i >= 0 && next[i].role === "assistant") next[i] = fn(next[i]);
        return next;
      });
    };
    const onChunk = (chunk: SseChunk) => {
      if (chunk.type === "content" && chunk.delta) patchLast((m) => ({ ...m, content: m.content + chunk.delta, pending: false }));
    };
    const finish = () => { patchLast((m) => ({ ...m, pending: false })); setChatSending(false); };
    const customerId = chatTarget.id === 0 ? null : chatTarget.id;
    streamWorkspaceChat(scope, customerId, text, onChunk, finish, (e) => {
      console.error("個人問答失敗:", e);
      finish();
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
    <section className="panel workspace-ai-panel">
      <div className="workspace-ai-header">
        <h3>我的 AI 助理 <AiBadge /></h3>
        <div className="workspace-ai-actions">
          {canSwitchScope ? (
            <select value={scope} onChange={(e) => setScope(e.target.value as "self" | "all")} aria-label="資料範圍">
              <option value="self">範圍：自己</option>
              <option value="all">範圍：全部</option>
            </select>
          ) : null}
          <button type="button" className="btn-secondary" onClick={openHistory}>🕘 AI 歷程</button>
          <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
            {generating ? "產生中…" : "✨ 產生我的工作建議"}
          </button>
        </div>
      </div>

      <div className="workspace-ai-grid">
        {/* 左：待辦清單 */}
        <div className="workspace-col">
          <h4 className="workspace-col-title">今日待辦</h4>
          {todos.length === 0 ? (
            <p className="workspace-empty">目前沒有待辦事項 🎉</p>
          ) : (
            <ul className="workspace-todos">
              {todos.map((t, i) => {
                const meta = TODO_META[t.type] ?? { label: t.type, cls: "medium" };
                return (
                  <li key={i}>
                    <button type="button" className={`todo-item sev-${meta.cls}`} onClick={() => navigate(`/customers/${t.customerId}`)}>
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
              <p className="workspace-empty">點右上「產生我的工作建議」由 AI 依待辦給出今日優先順序。</p>
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

      {/* 個人問答對象選擇（總覽 / 深入單客戶）；對話本身用全站浮動視窗 */}
      <div className="workspace-chat-bar">
        <span>個人問答對象：</span>
        <select
          value={chatTarget.id}
          onChange={(e) => {
            const id = Number(e.target.value);
            setChatTarget(id === 0 ? OVERVIEW_TARGET : (customers.find((c) => c.id === id) ?? OVERVIEW_TARGET));
            setMessages([]); // 切換對象清空對話
          }}
          aria-label="問答對象"
        >
          <option value={0}>全部我的客戶（總覽）</option>
          {customers.map((c) => (<option key={c.id} value={c.id}>{c.name}</option>))}
        </select>
        <button type="button" className="btn-secondary" onClick={() => setChatOpen(true)}>💬 開啟對話</button>
      </div>

      {/* 浮動對話視窗 + 啟動鈕（與客戶頁一致） */}
      <ChatLauncher open={chatOpen} unread={messages.length} customerName={chatTarget.name} onOpen={() => setChatOpen(true)} />
      {chatOpen ? (
        <ChatWindow
          customer={chatTarget}
          messages={messages}
          sending={chatSending}
          onSend={handleSend}
          onClose={() => setChatOpen(false)}
        />
      ) : null}

      {/* AI 草稿 → 預填新增商機 Modal；確認後走既有建立流程 */}
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
    </section>
  );
}
