import { FormEvent, useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  addInteraction, createContact, createCustomer, createOpportunity, deleteContact, deleteCustomer,
  deleteInteraction, deleteOpportunity, fetchAgentTrace, fetchCustomerAiCalls, fetchCustomerAssessmentStream,
  fetchCustomerDetail, fetchCustomerOptions, fetchCustomers, updateContact, updateCustomer,
  updateInteraction, updateOpportunity
} from "../../api";
import type {
  AgentTraceResponse, AiCallHistoryItem, ContactResponse, CustomerDetail, CustomerSummary, DrilldownSource,
  InteractionResponse, OpportunityResponse, OwnerOption
} from "../../types";
import { Breadcrumb } from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import { useAiChat } from "../ai-assistant/useAiChat";
import { ReportModal } from "../../components/common/ReportModal";
import { CustomerList } from "./components/CustomerList";
import { Pagination } from "./components/Pagination";
import { CustomerDetailPanel } from "./components/CustomerDetailPanel";
import { AddCustomerModal } from "./components/AddCustomerModal";
import { AddInteractionModal } from "./components/AddInteractionModal";
import { AddOpportunityModal } from "./components/AddOpportunityModal";
import { EditCustomerModal } from "./components/EditCustomerModal";
import { ContactModal } from "./components/ContactModal";
import { EditOpportunityModal } from "./components/EditOpportunityModal";
import { EditInteractionModal } from "./components/EditInteractionModal";
import { AiHistoryModal } from "./components/AiHistoryModal";
import { ChatLauncher } from "../ai-assistant/components/ChatLauncher";
import { ChatWindow } from "../ai-assistant/components/ChatWindow";
import { WorkspaceAiModal } from "../my-workspace/WorkspaceAiModal";
import { TaskFormModal } from "../tasks/TaskFormModal";
import { TaskPanel } from "../tasks/TaskPanel";

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
  // 進階篩選:客戶狀態、風險等級、續約到期日區間(皆空字串表示未篩選)
  const [status, setStatus] = useState("");
  const [riskLevel, setRiskLevel] = useState("");
  const [renewalFrom, setRenewalFrom] = useState("");
  const [renewalTo, setRenewalTo] = useState("");
  // 篩選下拉選項：所有產業 + 所有可指派業務（SALES 帳號），與當前頁客戶無關
  const [filterOptions, setFilterOptions] = useState<{ industries: string[]; owners: OwnerOption[] }>({ industries: [], owners: [] });
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [showAddCustomer, setShowAddCustomer] = useState(false);
  const [showAddInteraction, setShowAddInteraction] = useState(false);
  const [showAddOpportunity, setShowAddOpportunity] = useState(false);
  // 編輯客戶 Modal 開關
  const [showEditCustomer, setShowEditCustomer] = useState(false);
  // 聯絡人 Modal：open 控制開關，editing 為編輯對象（null 表示新增）
  const [contactModal, setContactModal] = useState<{ open: boolean; editing: ContactResponse | null }>({ open: false, editing: null });
  // 編輯商機對象（null 表示未開啟）
  const [editingOpportunity, setEditingOpportunity] = useState<OpportunityResponse | null>(null);
  // 編輯互動對象（null 表示未開啟）
  const [editingInteraction, setEditingInteraction] = useState<InteractionResponse | null>(null);
  const [report, setReport] = useState<{ open: boolean; title: string; loading: boolean; streaming?: boolean; markdown: string; meta?: string; callId?: number | null } | null>(null);
  // AI 歷程 Modal：open 控制開關、loading 載入中、calls 為該客戶歷次 AI 呼叫
  const [aiHistory, setAiHistory] = useState<{ open: boolean; loading: boolean; calls: AiCallHistoryItem[] } | null>(null);
  const [showTaskForm, setShowTaskForm] = useState(false);
  const [taskRefreshKey, setTaskRefreshKey] = useState(0);

  const { messages, chatSending, chatOpen, setChatOpen, sendChat, resetChat, loadHistory, historyLoading } = useAiChat();

  /** 載入客戶列表（支援多條件篩選與分頁）。 */
  async function loadCustomers(overrides?: { keyword?: string; industry?: string; owner?: string; page?: number; status?: string; riskLevel?: string; renewalFrom?: string; renewalTo?: string }) {
    setLoading(true);
    try {
      const list = await fetchCustomers({
        keyword: overrides?.keyword ?? keyword,
        industry: overrides?.industry ?? industry,
        owner: overrides?.owner ?? owner,
        status: overrides?.status ?? status,
        riskLevel: overrides?.riskLevel ?? riskLevel,
        renewalFrom: overrides?.renewalFrom ?? renewalFrom,
        renewalTo: overrides?.renewalTo ?? renewalTo,
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

  // :id 變動 → 載入詳情與 Trace、hydrate 對話歷史
  useEffect(() => {
    if (!selectedId) {
      setSelected(null);
      setTrace(null);
      return;
    }
    // 競態防護：快速切換客戶時，cleanup 設旗標忽略過期回應，避免舊請求覆蓋新客戶資料
    let cancelled = false;
    setLoading(true);
    setSelected(null); // 先清空以顯示 skeleton，避免殘留上一客戶
    resetChat();
    void (async () => {
      try {
        const [detail, traceResult] = await Promise.all([
          fetchCustomerDetail(selectedId),
          fetchAgentTrace(selectedId),
          loadHistory(selectedId)
        ]);
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
    await loadCustomers({ keyword, industry, owner, status, riskLevel, renewalFrom, renewalTo, page: 0 });
  }

  /** 清除所有篩選條件並重新載入。 */
  async function handleResetFilters() {
    setKeyword(""); setIndustry(""); setOwner(""); setStatus(""); setRiskLevel(""); setRenewalFrom(""); setRenewalTo(""); setPage(0);
    await loadCustomers({ keyword: "", industry: "", owner: "", status: "", riskLevel: "", renewalFrom: "", renewalTo: "", page: 0 });
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

  /** 新增商機後重載詳情。 */
  async function handleAddOpportunity(data: { name: string; stage: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) {
    if (!selected) return;
    await createOpportunity({ customerId: selected.customer.id, ...data });
    setShowAddOpportunity(false);
    // 重新載入目前客戶詳情，讓新商機出現在看板
    const detail = await fetchCustomerDetail(selected.customer.id);
    setSelected(detail);
  }

  /** 重載目前選取客戶的詳情（CRUD 成功後沿用）。 */
  async function reloadSelectedDetail() {
    if (!selected) return;
    const detail = await fetchCustomerDetail(selected.customer.id);
    setSelected(detail);
  }

  /** 編輯客戶後重載詳情與列表。 */
  async function handleUpdateCustomer(data: {
    name: string; email: string; phone: string; taxId: string; industry: string; ownerId: number;
    contractStartDate: string | null; contractEndDate: string | null; renewalDueDate: string | null;
  }) {
    if (!selected) return;
    await updateCustomer(selected.customer.id, data);
    setShowEditCustomer(false);
    await reloadSelectedDetail();
    await loadCustomers();
  }

  /** 刪除客戶：確認後刪除，成功導回列表並重載。 */
  async function handleDeleteCustomer() {
    if (!selected) return;
    if (!window.confirm("確定刪除此客戶?此動作無法復原。")) return;
    await deleteCustomer(selected.customer.id);
    navigate("/customers");
    await loadCustomers();
  }

  /** 送出聯絡人 Modal：依是否有編輯對象決定新增或更新，成功後重載詳情。 */
  async function handleSubmitContact(data: { name: string; title: string; email: string }) {
    if (!selected) return;
    if (contactModal.editing) {
      await updateContact(contactModal.editing.id, data);
    } else {
      await createContact(selected.customer.id, data);
    }
    setContactModal({ open: false, editing: null });
    await reloadSelectedDetail();
  }

  /** 刪除聯絡人：確認後刪除，成功後重載詳情。 */
  async function handleDeleteContact(contact: ContactResponse) {
    if (!window.confirm(`確定刪除聯絡人「${contact.name}」?此動作無法復原。`)) return;
    await deleteContact(contact.id);
    await reloadSelectedDetail();
  }

  /** 編輯商機後重載詳情。 */
  async function handleUpdateOpportunity(data: { name: string; amount: number; expectedCloseDate: string | null; type: string; leadSource: string; probability: number | null }) {
    if (!editingOpportunity) return;
    await updateOpportunity(editingOpportunity.id, data);
    setEditingOpportunity(null);
    await reloadSelectedDetail();
  }

  /** 刪除商機：確認後刪除，成功後重載詳情。 */
  async function handleDeleteOpportunity(opportunity: OpportunityResponse) {
    if (!window.confirm(`確定刪除商機「${opportunity.name}」?此動作無法復原。`)) return;
    await deleteOpportunity(opportunity.id);
    await reloadSelectedDetail();
  }

  /** 編輯互動後重載詳情。 */
  async function handleUpdateInteraction(data: { type: string; occurredAt: string; content: string }) {
    if (!editingInteraction) return;
    await updateInteraction(editingInteraction.id, data);
    setEditingInteraction(null);
    await reloadSelectedDetail();
  }

  /** 刪除互動:確認後刪除,成功後重載詳情。 */
  async function handleDeleteInteraction(interaction: InteractionResponse) {
    if (!window.confirm("確定刪除此互動紀錄?此動作無法復原。")) return;
    await deleteInteraction(interaction.id);
    await reloadSelectedDetail();
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

  /**
   * 開啟目前客戶 360° 整體評估（SSE 串流：邊產生邊渲染，避免長報告逾時）。
   * 函式級註解：content 區段持續累加進 markdown；risk/callId 區段補上 meta 與回饋 id；
   * 首個 content 到達即關閉 loading。錯誤時若已累積部分內容則保留，否則顯示失敗提示。
   */
  function openCustomerAssessment() {
    if (!selected) return;
    const name = selected.customer.name;
    const title = `整體評估 — ${name}`;
    setReport({ open: true, title, loading: true, streaming: true, markdown: "" });
    let acc = "";
    fetchCustomerAssessmentStream(
      selected.customer.id,
      (chunk) => {
        if (chunk.type === "content" && chunk.delta) {
          acc += chunk.delta;
          setReport((r) => (r ? { ...r, loading: false, streaming: true, markdown: acc } : r));
        } else if (chunk.type === "risk" && chunk.risk) {
          setReport((r) => (r ? { ...r, meta: `流失風險 ${chunk.risk.churnRisk} · 續約延遲 ${chunk.risk.renewalDelayRisk}` } : r));
        } else if (chunk.type === "callId") {
          setReport((r) => (r ? { ...r, callId: chunk.callId } : r));
        }
      },
      () => setReport((r) => (r ? { ...r, loading: false, streaming: false } : r)),
      (e) => {
        console.error("客戶整體評估失敗:", e);
        setReport((r) => (r ? { ...r, loading: false, streaming: false, markdown: acc || "⚠️ 產生評估失敗，請稍後再試。" } : r));
      }
    );
  }

  /** 開啟 AI 聊天（需先選客戶）。 */
  function openChat() {
    if (!selected) return;
    setChatOpen(true);
  }

  /** 開啟 AI 歷程 Modal：載入該客戶歷次 AI 呼叫紀錄（Agent Trace 用既有 trace state）。 */
  async function openAiHistory() {
    if (!selected) return;
    setAiHistory({ open: true, loading: true, calls: [] });
    try {
      const calls = await fetchCustomerAiCalls(selected.customer.id);
      setAiHistory({ open: true, loading: false, calls });
    } catch (e) {
      console.error("載入 AI 歷程失敗:", e);
      setAiHistory({ open: true, loading: false, calls: [] });
    }
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
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="名稱 / Email / 電話 / 統編" />
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
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">全部狀態</option>
            <option value="ACTIVE">使用中</option>
            <option value="INACTIVE">停用</option>
            <option value="LEVERAGED">重點客戶</option>
          </select>
          <select value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}>
            <option value="">全部風險</option>
            <option value="HIGH">高風險</option>
            <option value="MEDIUM">中風險</option>
            <option value="LOW">低風險</option>
          </select>
          {/* 續約到期日區間 */}
          <input type="date" value={renewalFrom} onChange={(e) => setRenewalFrom(e.target.value)} title="續約到期日(起)" />
          <input type="date" value={renewalTo} onChange={(e) => setRenewalTo(e.target.value)} title="續約到期日(迄)" />
          <button type="submit">搜尋</button>
          <button type="button" className="btn-secondary" onClick={handleResetFilters}>清除</button>
        </form>
      </section>

      <div className="action-bar">
        <button type="button" className="btn-assess" onClick={() => setAiModalOpen(true)}>✨ AI 工作建議</button>
        <button type="button" onClick={() => setShowAddCustomer(true)}>+ 新增客戶</button>
        <button type="button" onClick={() => navigate("/business-cards/new")}>📇 名片建檔</button>
        {selected ? <button type="button" onClick={() => setShowAddInteraction(true)}>+ 新增互動</button> : null}
        {selected ? <button type="button" onClick={() => setShowAddOpportunity(true)}>+ 新增商機</button> : null}
        {selected ? <button type="button" onClick={() => setShowTaskForm(true)}>☎ 安排電話</button> : null}
        {selected ? <button type="button" onClick={() => navigate(`/customers/${selected.customer.id}/meeting-copilot`)}>🎙 會議 Copilot</button> : null}
      </div>

      {selected ? <TaskPanel customerId={selected.customer.id} refreshKey={taskRefreshKey} /> : null}

      <div className="workspace-grid">
        <div className="customer-col">
          <CustomerList customers={customers} selectedId={selectedId} onSelect={selectCustomer} loading={loading} />
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={(p) => { setPage(p); void loadCustomers({ page: p }); }} />
        </div>
        <CustomerDetailPanel
          detail={selected}
          loading={loading}
          onStageChange={handleStageChange}
          onOpenChat={openChat}
          onAssess={openCustomerAssessment}
          onOpenAiHistory={openAiHistory}
          onEditCustomer={() => setShowEditCustomer(true)}
          onDeleteCustomer={handleDeleteCustomer}
          onAddContact={() => setContactModal({ open: true, editing: null })}
          onEditContact={(contact) => setContactModal({ open: true, editing: contact })}
          onDeleteContact={handleDeleteContact}
          onEditOpportunity={(opportunity) => setEditingOpportunity(opportunity)}
          onDeleteOpportunity={handleDeleteOpportunity}
          onEditInteraction={(interaction) => setEditingInteraction(interaction)}
          onDeleteInteraction={handleDeleteInteraction}
          userRole={user?.role}
        />
      </div>

      <ChatLauncher open={chatOpen} unread={messages.length} customerName={selected?.customer.name} onOpen={openChat} />
      {chatOpen ? (
        <ChatWindow
          customer={selected?.customer ?? null}
          messages={messages}
          sending={chatSending}
          historyLoading={historyLoading}
          onSend={(msg) => { if (selected) sendChat(selected.customer.id, msg); }}
          onClose={() => setChatOpen(false)}
        />
      ) : null}
      {aiModalOpen ? <WorkspaceAiModal onClose={() => setAiModalOpen(false)} /> : null}
      {report?.open ? <ReportModal report={report} onClose={() => setReport(null)} /> : null}
      {aiHistory?.open && selected ? <AiHistoryModal customerName={selected.customer.name} calls={aiHistory.calls} trace={trace} loading={aiHistory.loading} onClose={() => setAiHistory(null)} /> : null}
      {showAddCustomer ? <AddCustomerModal currentUserId={user?.id ?? 0} onSubmit={handleCreateCustomer} onClose={() => setShowAddCustomer(false)} /> : null}
      {showAddInteraction && selected ? <AddInteractionModal customerName={selected.customer.name} onSubmit={handleAddInteraction} onClose={() => setShowAddInteraction(false)} /> : null}
      {showAddOpportunity && selected ? <AddOpportunityModal customerName={selected.customer.name} onSubmit={handleAddOpportunity} onClose={() => setShowAddOpportunity(false)} /> : null}
      {showEditCustomer && selected ? <EditCustomerModal customer={selected.customer} owners={filterOptions.owners} onSubmit={handleUpdateCustomer} onClose={() => setShowEditCustomer(false)} /> : null}
      {contactModal.open && selected ? <ContactModal contact={contactModal.editing} onSubmit={handleSubmitContact} onClose={() => setContactModal({ open: false, editing: null })} /> : null}
      {editingOpportunity ? <EditOpportunityModal opportunity={editingOpportunity} onSubmit={handleUpdateOpportunity} onClose={() => setEditingOpportunity(null)} /> : null}
      {editingInteraction ? <EditInteractionModal interaction={editingInteraction} onSubmit={handleUpdateInteraction} onClose={() => setEditingInteraction(null)} /> : null}
      {showTaskForm && selected && user ? <TaskFormModal customerId={selected.customer.id} customerName={selected.customer.name} assigneeId={user.id} onCreated={() => { setShowTaskForm(false); setTaskRefreshKey((key) => key + 1); }} onClose={() => setShowTaskForm(false)} /> : null}
    </>
  );
}
