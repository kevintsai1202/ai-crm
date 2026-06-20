import { FormEvent, useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  addInteraction, createCustomer, fetchAgentTrace, fetchCustomerAssessment,
  fetchCustomerDetail, fetchCustomerOptions, fetchCustomers
} from "../../api";
import type { AgentTraceResponse, CustomerDetail, CustomerSummary, DrilldownSource, OwnerOption } from "../../types";
import { Breadcrumb } from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import { useAiChat } from "../ai-assistant/useAiChat";
import { ReportModal } from "../../components/common/ReportModal";
import { CustomerList } from "./components/CustomerList";
import { Pagination } from "./components/Pagination";
import { CustomerDetailPanel } from "./components/CustomerDetailPanel";
import { AddCustomerModal } from "./components/AddCustomerModal";
import { AddInteractionModal } from "./components/AddInteractionModal";
import { ChatLauncher } from "../ai-assistant/components/ChatLauncher";
import { ChatWindow } from "../ai-assistant/components/ChatWindow";

/**
 * 操作頁（做事）：搜尋/篩選 + 客戶列表 + 詳情 + 商機看板 + 互動 + AI 助理。
 * 函式級註解：選取客戶以 URL :id 為單一真實來源；切換 :id 時載入詳情與 Agent Trace。
 */
export function CustomersPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { id } = useParams();
  const selectedId = id ? Number(id) : undefined;
  // 下鑽來源（自儀表板帶入）；重整後 state 消失即視為無來源，不顯示麵包屑
  const location = useLocation();
  const source = location.state as DrilldownSource | null;

  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [selected, setSelected] = useState<CustomerDetail | null>(null);
  const [trace, setTrace] = useState<AgentTraceResponse | null>(null);
  const [keyword, setKeyword] = useState("");
  const [industry, setIndustry] = useState("");
  const [owner, setOwner] = useState("");
  // 篩選下拉選項：所有產業 + 所有可指派業務（SALES 帳號），與當前頁客戶無關
  const [filterOptions, setFilterOptions] = useState<{ industries: string[]; owners: OwnerOption[] }>({ industries: [], owners: [] });
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [showAddCustomer, setShowAddCustomer] = useState(false);
  const [showAddInteraction, setShowAddInteraction] = useState(false);
  const [report, setReport] = useState<{ open: boolean; title: string; loading: boolean; markdown: string; meta?: string; callId?: number | null } | null>(null);

  const { messages, chatSending, chatOpen, setChatOpen, sendChat, resetChat } = useAiChat();

  /** 載入客戶列表（支援篩選與分頁）。 */
  async function loadCustomers(overrides?: { keyword?: string; industry?: string; owner?: string; page?: number }) {
    setLoading(true);
    try {
      const list = await fetchCustomers({
        keyword: overrides?.keyword ?? keyword,
        industry: overrides?.industry ?? industry,
        owner: overrides?.owner ?? owner,
        page: overrides?.page ?? page,
        size: 20
      });
      setCustomers(list.items);
      setTotalPages(list.totalPages);
      setTotalElements(list.totalElements);
      // 若尚未選任何客戶且有第一筆，導向第一筆詳情
      if (!selectedId && list.items[0]) {
        navigate(`/customers/${list.items[0].id}`, { replace: true });
      }
    } finally {
      setLoading(false);
    }
  }

  // 進頁載入列表（一次）
  useEffect(() => {
    void loadCustomers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 進頁載入篩選下拉選項（所有產業 + 所有業務帳號），與分頁/當前頁資料無關
  useEffect(() => {
    void (async () => {
      try {
        const options = await fetchCustomerOptions();
        setFilterOptions({ industries: options.industries, owners: options.owners });
      } catch (e) {
        console.error("載入篩選選項失敗:", e);
      }
    })();
  }, []);

  // :id 變動 → 載入詳情與 Trace、重置對話
  useEffect(() => {
    if (!selectedId) {
      setSelected(null);
      setTrace(null);
      return;
    }
    // 競態防護：快速切換客戶時，cleanup 設旗標忽略過期回應，避免舊請求覆蓋新客戶資料
    let cancelled = false;
    setLoading(true);
    resetChat();
    void (async () => {
      try {
        const [detail, traceResult] = await Promise.all([fetchCustomerDetail(selectedId), fetchAgentTrace(selectedId)]);
        if (cancelled) return;
        setSelected(detail);
        setTrace(traceResult);
      } catch (e) {
        console.error("載入客戶詳情或 Trace 失敗:", e);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  /** 搜尋（重置頁碼）。 */
  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    await loadCustomers({ keyword, industry, owner, page: 0 });
  }

  /** 選取客戶（改 URL）。 */
  function selectCustomer(cid: number) {
    navigate(`/customers/${cid}`);
  }

  /** 新增客戶後重載列表。 */
  async function handleCreateCustomer(data: { name: string; email: string; phone: string; taxId: string; industry: string; ownerId: number }) {
    await createCustomer(data);
    setShowAddCustomer(false);
    await loadCustomers();
  }

  /** 新增互動後重載詳情。 */
  async function handleAddInteraction(data: { type: string; occurredAt: string; content: string }) {
    if (!selected) return;
    await addInteraction(selected.customer.id, data);
    setShowAddInteraction(false);
    // 重新載入目前客戶詳情
    const detail = await fetchCustomerDetail(selected.customer.id);
    setSelected(detail);
  }

  /** 商機階段變更（樂觀更新本地）。 */
  function handleStageChange(opportunityId: number, newStage: string) {
    setSelected((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        opportunities: prev.opportunities.map((opp) =>
          opp.id === opportunityId ? { ...opp, stage: newStage as any } : opp
        )
      };
    });
  }

  /** 開啟目前客戶 360° 整體評估。 */
  async function openCustomerAssessment() {
    if (!selected) return;
    const name = selected.customer.name;
    setReport({ open: true, title: `整體評估 — ${name}`, loading: true, markdown: "" });
    try {
      const result = await fetchCustomerAssessment(selected.customer.id);
      setReport({
        open: true,
        title: `整體評估 — ${name}`,
        loading: false,
        markdown: result.answer,
        meta: `流失風險 ${result.risk.churnRisk} · 續約延遲 ${result.risk.renewalDelayRisk}`,
        callId: result.callId
      });
    } catch (e) {
      console.error("客戶整體評估失敗:", e);
      setReport({ open: true, title: `整體評估 — ${name}`, loading: false, markdown: "⚠️ 產生評估失敗，請稍後再試。" });
    }
  }

  /** 開啟 AI 聊天（需先選客戶）。 */
  function openChat() {
    if (!selected) return;
    setChatOpen(true);
  }

  return (
    <>
      {source?.from === "dashboard" ? (
        <Breadcrumb
          crumbs={[
            { label: "儀表板", onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: source.section, onClick: () => navigate("/dashboard", { state: { scrollTo: source.blockId } }) },
            { label: selected?.customer.name ?? "客戶" }
          ]}
        />
      ) : null}
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>客戶工作台</h2>
        </div>
        <form className="search-box" onSubmit={handleSearch}>
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜尋客戶名稱" />
          <select value={industry} onChange={(e) => setIndustry(e.target.value)}>
            <option value="">全部產業</option>
            {filterOptions.industries.map((it) => (
              <option key={it} value={it}>{it}</option>
            ))}
          </select>
          <select value={owner} onChange={(e) => setOwner(e.target.value)}>
            <option value="">全部業務</option>
            {filterOptions.owners.map((o) => (
              <option key={o.id} value={o.displayName}>{o.displayName}</option>
            ))}
          </select>
          <button type="submit">搜尋</button>
        </form>
      </section>

      <div className="action-bar">
        <button type="button" onClick={() => setShowAddCustomer(true)}>+ 新增客戶</button>
        {selected ? <button type="button" onClick={() => setShowAddInteraction(true)}>+ 新增互動</button> : null}
      </div>

      <div className="workspace-grid">
        <div className="customer-col">
          <CustomerList customers={customers} selectedId={selectedId} onSelect={selectCustomer} loading={loading} />
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={(p) => { setPage(p); void loadCustomers({ page: p }); }} />
        </div>
        <CustomerDetailPanel detail={selected} loading={loading} trace={trace} onStageChange={handleStageChange} onOpenChat={openChat} onAssess={openCustomerAssessment} userRole={user?.role} />
      </div>

      <ChatLauncher open={chatOpen} unread={messages.length} customerName={selected?.customer.name} onOpen={openChat} />
      {chatOpen ? (
        <ChatWindow
          customer={selected?.customer ?? null}
          messages={messages}
          sending={chatSending}
          onSend={(msg) => { if (selected) sendChat(selected.customer.id, msg); }}
          onClose={() => setChatOpen(false)}
        />
      ) : null}
      {report?.open ? <ReportModal report={report} onClose={() => setReport(null)} /> : null}
      {showAddCustomer ? <AddCustomerModal currentUserId={user?.id ?? 0} onSubmit={handleCreateCustomer} onClose={() => setShowAddCustomer(false)} /> : null}
      {showAddInteraction && selected ? <AddInteractionModal customerName={selected.customer.name} onSubmit={handleAddInteraction} onClose={() => setShowAddInteraction(false)} /> : null}
    </>
  );
}
