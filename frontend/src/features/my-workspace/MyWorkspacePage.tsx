import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchCustomers, streamWorkspaceChat, type SseChunk } from "../../api";
import type { CustomerSummary } from "../../types";
import { useAuth } from "../../context/AuthContext";
import { formatMoney } from "../../lib/format";
import { CustomerList } from "../customers/components/CustomerList";
import { Pagination } from "../customers/components/Pagination";
import { ChatLauncher } from "../ai-assistant/components/ChatLauncher";
import { ChatWindow } from "../ai-assistant/components/ChatWindow";
import type { ChatMessage } from "../ai-assistant/useAiChat";
import { WorkspaceAiModal } from "./WorkspaceAiModal";

/** 每頁筆數。 */
const PAGE_SIZE = 20;
/** KPI 取樣的客戶上限(業務個人客戶數通常遠小於此)。 */
const KPI_SAMPLE_SIZE = 50;
/** 個人問答的合成對象（id=0 代表跨「我的所有客戶」總覽，非真實客戶）。 */
const CHAT_TARGET = { id: 0, name: "我的客戶組合" } as CustomerSummary;

/**
 * 業務個人工作台:只呈現「登入者本人負責(owner)」的客戶資料。
 * 函式級註解：上方個人 KPI + 我的客戶列表（與客戶工作台一致的版面）；
 * AI 功能不常駐頁面 —「AI 工作建議」由 topbar 按鈕開 Modal，個人問答由右下角浮動對話視窗負責。
 */
export function MyWorkspacePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const owner = user?.displayName ?? "";

  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [sample, setSample] = useState<CustomerSummary[]>([]);

  // AI 工作建議 Modal
  const [aiModalOpen, setAiModalOpen] = useState(false);

  // 個人問答（沿用全站浮動對話視窗）
  const [chatOpen, setChatOpen] = useState(false);
  const [chatSending, setChatSending] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  /** 載入我的客戶分頁列表(owner 鎖定為登入者)。 */
  async function load(targetPage: number, kw: string) {
    if (!owner) return;
    setLoading(true);
    try {
      const list = await fetchCustomers({ owner, keyword: kw, page: targetPage, size: PAGE_SIZE });
      setCustomers(list.items);
      setTotalPages(list.totalPages);
      setTotalElements(list.totalElements);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!owner) return;
    void load(0, "");
    void (async () => {
      const all = await fetchCustomers({ owner, page: 0, size: KPI_SAMPLE_SIZE });
      setSample(all.items);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [owner]);

  const kpis = useMemo(() => {
    const now = new Date();
    const week = new Date();
    week.setDate(now.getDate() + 7);
    const within7 = (d: string | null) => {
      if (!d) return false;
      const t = new Date(d).getTime();
      return t >= now.getTime() && t <= week.getTime();
    };
    return {
      count: totalElements,
      pipeline: sample.reduce((sum, c) => sum + (c.opportunityAmount ?? 0), 0),
      highRisk: sample.filter((c) => c.riskLevel === "HIGH").length,
      weekRenewals: sample.filter((c) => within7(c.renewalDueDate)).length
    };
  }, [sample, totalElements]);

  /** 搜尋我的客戶(重置頁碼)。 */
  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    void load(0, keyword);
  }

  /** 送出個人問答（總覽，跨我的所有客戶）：以 SSE 串流逐字填入最後一則 AI 訊息。 */
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
    streamWorkspaceChat("self", null, text, onChunk, finish, (e) => {
      console.error("個人問答失敗:", e);
      finish();
    });
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>我的工作台</h2>
        </div>
        <div className="topbar-actions">
          <button type="button" className="btn-assess" onClick={() => setAiModalOpen(true)}>✨ AI 工作建議</button>
          <form className="search-box" onSubmit={handleSearch}>
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜尋我的客戶(名稱/Email/電話/統編)" />
            <button type="submit">搜尋</button>
          </form>
        </div>
      </section>

      {!owner ? (
        <section className="panel empty-state-box"><p>無法取得登入身分。</p></section>
      ) : (
        <>
          {/* 個人 KPI 摘要 */}
          <div className="kpi-row">
            <div className="kpi-card"><span className="kpi-label">我的客戶</span><span className="kpi-value">{kpis.count}</span></div>
            <div className="kpi-card"><span className="kpi-label">我的 Pipeline</span><span className="kpi-value kpi-value-accent">{formatMoney(kpis.pipeline)}</span></div>
            <div className="kpi-card"><span className="kpi-label">高風險客戶</span><span className="kpi-value">{kpis.highRisk}</span></div>
            <div className="kpi-card"><span className="kpi-label">本週續約到期</span><span className="kpi-value">{kpis.weekRenewals}</span></div>
          </div>

          {/* 我的客戶列表(點選沿用既有詳情頁) */}
          <div className="mywork-list">
            <CustomerList customers={customers} onSelect={(id) => navigate(`/customers/${id}`)} loading={loading} />
            <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={(p) => { setPage(p); void load(p, keyword); }} />
          </div>
        </>
      )}

      {/* AI 工作建議 Modal（由 topbar 按鈕開啟） */}
      {aiModalOpen ? <WorkspaceAiModal onClose={() => setAiModalOpen(false)} /> : null}

      {/* 個人問答：右下角浮動對話視窗 + 啟動鈕（與客戶工作台一致） */}
      {owner ? (
        <>
          <ChatLauncher open={chatOpen} unread={messages.length} customerName={CHAT_TARGET.name} onOpen={() => setChatOpen(true)} />
          {chatOpen ? (
            <ChatWindow customer={CHAT_TARGET} messages={messages} sending={chatSending} onSend={handleSend} onClose={() => setChatOpen(false)} />
          ) : null}
        </>
      ) : null}
    </>
  );
}
