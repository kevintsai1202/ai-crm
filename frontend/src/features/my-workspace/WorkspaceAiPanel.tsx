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

/** 工作檯問答訊息。 */
interface ChatMsg {
  role: "user" | "assistant";
  content: string;
  pending?: boolean;
}

/** 待辦類型對應的中文標籤與色票。 */
const TODO_META: Record<string, { label: string; cls: string }> = {
  HIGH_RISK: { label: "高風險", cls: "sev-high" },
  RENEWAL_DUE: { label: "即將續約", cls: "sev-medium" },
  STALE_OPPORTUNITY: { label: "停滯商機", cls: "sev-medium" }
};

/**
 * 我的工作檯「個人 AI 助理」區塊：待辦清單 + AI 工作建議（逐字串流）+ AI 建議商機草稿。
 * 函式級註解：SALES 僅能看自己；MANAGER/ADMIN 顯示「自己 / 全部」範圍切換。
 * 待辦由後端 DB 規則即時計算（可點跳客戶詳情）；AI 總結以待辦接地、逐字串流。
 */
export function WorkspaceAiPanel({ customers = [] }: { customers?: CustomerSummary[] }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  // MANAGER/ADMIN 可切換範圍；SALES 強制自己
  const canSwitchScope = user?.role !== "SALES";
  const [scope, setScope] = useState<"self" | "all">("self");

  const [todos, setTodos] = useState<WorkspaceTodoItem[]>([]);
  const [drafts, setDrafts] = useState<SuggestedOpportunityDraft[]>([]);
  const [summary, setSummary] = useState("");
  const [generating, setGenerating] = useState(false);
  const [model, setModel] = useState<string | null>(null);
  // 正在以哪一筆草稿開啟新增商機 Modal（null 為未開）
  const [draftFor, setDraftFor] = useState<SuggestedOpportunityDraft | null>(null);

  // 個人問答狀態
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [question, setQuestion] = useState("");
  const [chatCustomerId, setChatCustomerId] = useState<number | null>(null); // null=總覽
  const [chatSending, setChatSending] = useState(false);

  // AI 歷程狀態
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
    const onDone = () => setGenerating(false);
    const onError = (e: any) => {
      console.error("產生工作建議失敗:", e);
      setGenerating(false);
    };
    streamWorkspaceRecommendation(scope, onChunk, onDone, onError);
  }

  /** 送出個人問答：以 SSE 串流逐字填入最後一則 AI 訊息。 */
  function handleAsk() {
    const q = question.trim();
    if (!q || chatSending) return;
    setChatSending(true);
    setQuestion("");
    // 先放入使用者訊息與一則 pending 的 AI 訊息
    setMessages((prev) => [...prev, { role: "user", content: q }, { role: "assistant", content: "", pending: true }]);

    const onChunk = (chunk: SseChunk) => {
      if (chunk.type === "content" && chunk.delta) {
        setMessages((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          if (last && last.role === "assistant") next[next.length - 1] = { ...last, content: last.content + chunk.delta };
          return next;
        });
      }
    };
    const finish = () => {
      setMessages((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last && last.role === "assistant") next[next.length - 1] = { ...last, pending: false };
        return next;
      });
      setChatSending(false);
    };
    streamWorkspaceChat(scope, chatCustomerId, q, onChunk, finish, (e) => {
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
              <option value="self">自己</option>
              <option value="all">全部</option>
            </select>
          ) : null}
          <button type="button" className="btn-secondary" onClick={openHistory}>🕘 AI 歷程</button>
          <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
            {generating ? "產生中…" : "產生我的工作建議"}
          </button>
        </div>
      </div>

      {/* 待辦清單（可點跳客戶詳情） */}
      <div className="workspace-todos">
        {todos.length === 0 ? (
          <p className="trace-empty">目前沒有待辦事項。</p>
        ) : (
          <ul>
            {todos.map((t, i) => {
              const meta = TODO_META[t.type] ?? { label: t.type, cls: "sev-medium" };
              return (
                <li key={i} className={`todo-item ${meta.cls}`} onClick={() => navigate(`/customers/${t.customerId}`)}>
                  <span className="todo-tag">{meta.label}</span>
                  <span className="todo-customer">{t.customerName}</span>
                  <span className="todo-reason">{t.reason}</span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* AI 總結 */}
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
          <p className="trace-empty">點「產生我的工作建議」由 AI 依待辦給出今日優先建議。</p>
        )}
      </div>

      {/* AI 建議商機草稿（Task 9 接預填建立） */}
      {drafts.length > 0 ? (
        <div className="workspace-drafts">
          <h4>AI 建議商機</h4>
          <ul>
            {drafts.map((d, i) => (
              <li key={i} className="draft-card">
                <div><strong>{d.customerName}</strong>｜{d.name}</div>
                <div className="draft-rationale">{d.rationale}</div>
                <button type="button" className="btn-secondary" onClick={() => setDraftFor(d)}>建立</button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {/* 個人問答：總覽或深入單一客戶 */}
      <div className="workspace-chat">
        <div className="workspace-chat-head">
          <h4>個人問答</h4>
          <select
            value={chatCustomerId ?? ""}
            onChange={(e) => setChatCustomerId(e.target.value ? Number(e.target.value) : null)}
            aria-label="問答範圍"
          >
            <option value="">全部我的客戶（總覽）</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
        <div className="workspace-chat-messages">
          {messages.length === 0 ? (
            <p className="trace-empty">可詢問「哪些客戶該優先跟進？」等問題；選客戶可深入單一客戶。</p>
          ) : (
            messages.map((m, i) => (
              <div key={i} className={`chat-bubble ${m.role}`}>
                {m.pending && !m.content ? (
                  <AiThinkingIndicator label="思考中" />
                ) : m.role === "assistant" ? (
                  <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown></div>
                ) : (
                  <span>{m.content}</span>
                )}
              </div>
            ))
          )}
        </div>
        <div className="workspace-chat-input">
          <input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") handleAsk(); }}
            placeholder="輸入問題後按 Enter"
            disabled={chatSending}
          />
          <button type="button" disabled={chatSending} onClick={handleAsk}>{chatSending ? "回覆中…" : "送出"}</button>
        </div>
      </div>

      {/* AI 草稿 → 預填新增商機 Modal；確認後走既有建立流程 */}
      {draftFor ? (
        <AddOpportunityModal
          customerName={draftFor.customerName}
          initialValues={{ name: draftFor.name, stage: draftFor.suggestedStage }}
          onClose={() => setDraftFor(null)}
          onSubmit={async (data) => {
            try {
              await createOpportunity({ customerId: draftFor.customerId, ...data });
              // 建立成功後關閉並移除該草稿
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
        <AiCallHistoryModal
          title="我的工作檯 AI 歷程"
          calls={calls}
          loading={callsLoading}
          onClose={() => setHistoryOpen(false)}
        />
      ) : null}
    </section>
  );
}
