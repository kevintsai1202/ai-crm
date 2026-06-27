import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchCustomers } from "../../api";
import type { CustomerSummary } from "../../types";
import { useAuth } from "../../context/AuthContext";
import { formatMoney } from "../../lib/format";
import { CustomerList } from "../customers/components/CustomerList";
import { Pagination } from "../customers/components/Pagination";
import { WorkspaceAiPanel } from "./WorkspaceAiPanel";

/** 每頁筆數。 */
const PAGE_SIZE = 20;
/** KPI 取樣的客戶上限(業務個人客戶數通常遠小於此)。 */
const KPI_SAMPLE_SIZE = 50;

/**
 * 業務個人工作台:只呈現「登入者本人負責(owner)」的客戶資料。
 * 函式級註解：以登入者 displayName 作為 owner 條件呼叫既有客戶查詢端點(後端 Specification 動態組條件),
 * 上方顯示個人 KPI(我的客戶數 / Pipeline / 高風險 / 本週續約到期),下方為可搜尋分頁的我的客戶列表。
 * 點客戶沿用既有 /customers/:id 詳情頁。
 */
export function MyWorkspacePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  // 以登入者顯示名稱作為負責業務過濾值(SALES 帳號的 displayName 即客戶 ownerName)
  const owner = user?.displayName ?? "";

  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  // KPI 以「我的客戶取樣」計算,與分頁列表分開,避免每翻頁重算
  const [sample, setSample] = useState<CustomerSummary[]>([]);

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

  // 進頁:載入列表 + KPI 取樣
  useEffect(() => {
    if (!owner) return;
    void load(0, "");
    void (async () => {
      const all = await fetchCustomers({ owner, page: 0, size: KPI_SAMPLE_SIZE });
      setSample(all.items);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [owner]);

  // 個人 KPI:客戶數(以總數為準)、Pipeline 金額、高風險數、本週(未來 7 天)續約到期數
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

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>我的工作台</h2>
        </div>
        <form className="search-box" onSubmit={handleSearch}>
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜尋我的客戶(名稱/Email/電話/統編)" />
          <button type="submit">搜尋</button>
        </form>
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

          {/* 個人 AI 助理：待辦 + 工作建議 + 商機草稿 + 問答 */}
          <WorkspaceAiPanel />

          {/* 我的客戶列表(點選沿用既有詳情頁) */}
          <div className="mywork-list">
            <CustomerList customers={customers} onSelect={(id) => navigate(`/customers/${id}`)} loading={loading} />
            <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={(p) => { setPage(p); void load(p, keyword); }} />
          </div>
        </>
      )}
    </>
  );
}
