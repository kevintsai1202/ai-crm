import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  fetchManagerAnalytics,
  fetchTeamInsight,
  generateTeamInsight,
  fetchOwnerInsight,
  generateOwnerInsight
} from "../../api";
import type { ManagerAnalyticsResponse, ManagerInsightResponse, OwnerStats } from "../../types";
import { formatCompactMoney, formatDateTime } from "../../lib/format";

/** 業務績效表可排序的欄位鍵。 */
type SortKey = "wonAmount" | "winRate" | "pipelineAmount" | "customerCount" | "highRiskCount";

/**
 * 業務分析頁（MANAGER/ADMIN）：團隊 KPI + 可排序業務績效表 + 兩個 AI 區塊。
 * 函式級註解：純統計來自 /api/manager/analytics；AI 區塊進頁先讀快取，按鈕才呼叫 LLM 生成並更新快取顯示。
 */
export function TeamAnalyticsPage() {
  const [data, setData] = useState<ManagerAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("wonAmount");
  // 被選取做 coaching 的業務名（null 表示未選）
  const [selectedOwner, setSelectedOwner] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(null);
      try {
        setData(await fetchManagerAnalytics());
      } catch (e) {
        console.error("載入業務分析失敗:", e);
        setError("載入業務分析失敗");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // 依選定欄位降序排序（不可變副本，避免改動原陣列）
  const sortedOwners = useMemo(() => {
    if (!data) return [];
    return [...data.owners].sort((a, b) => Number(b[sortKey]) - Number(a[sortKey]));
  }, [data, sortKey]);

  if (loading) return <div className="panel"><div className="sr-empty">載入中…</div></div>;
  if (error || !data) return <div className="panel"><div className="sr-empty">{error ?? "無資料"}</div></div>;

  const t = data.team;
  return (
    <>
      <section className="topbar">
        <div>
          <p>Manager Console</p>
          <h2>業務分析</h2>
        </div>
      </section>

      {/* 團隊 KPI 列 */}
      <div className="team-kpi-row">
        <Kpi label="客戶總數" value={String(t.totalCustomers)} />
        <Kpi label="全團隊成交金額" value={formatCompactMoney(t.totalWonAmount)} />
        <Kpi label="進行中商機" value={formatCompactMoney(t.totalPipeline)} />
        <Kpi label="高風險客戶" value={String(t.totalHighRisk)} />
        <Kpi label="平均成交率" value={`${Math.round(t.avgWinRate * 100)}%`} />
        <Kpi label="業務人數" value={String(t.ownerCount)} />
      </div>

      {/* 業務績效表 */}
      <div className="panel">
        <div className="panel-head">
          <h3>各業務績效</h3>
          <label className="sort-select">
            排序：
            <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)}>
              <option value="wonAmount">成交金額</option>
              <option value="winRate">成交率</option>
              <option value="pipelineAmount">進行中商機</option>
              <option value="customerCount">客戶數</option>
              <option value="highRiskCount">高風險數</option>
            </select>
          </label>
        </div>
        <table className="admin-user-table">
          <thead>
            <tr>
              <th>業務</th><th>客戶</th><th>高風險</th><th>進行中商機</th>
              <th>已成交</th><th>成交率</th><th>平均互動間隔</th><th>本季續約</th><th>Coaching</th>
            </tr>
          </thead>
          <tbody>
            {sortedOwners.map((o) => (
              <tr key={o.ownerName} className={selectedOwner === o.ownerName ? "row-selected" : ""}>
                <td>{o.ownerName}</td>
                <td>{o.customerCount}</td>
                <td>{o.highRiskCount}</td>
                <td>{formatCompactMoney(o.pipelineAmount)}（{o.activeOpportunityCount}）</td>
                <td>{formatCompactMoney(o.wonAmount)}（{o.wonCount}）</td>
                <td>{Math.round(o.winRate * 100)}%</td>
                <td>{o.avgDaysSinceInteraction == null ? "—" : `${Math.round(o.avgDaysSinceInteraction)} 天`}</td>
                <td>{o.renewalsThisQuarter}</td>
                <td>
                  <button type="button" className="btn-secondary" onClick={() => setSelectedOwner(o.ownerName)}>
                    輔導報告
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* AI-A 團隊整體診斷 */}
      <InsightPanel
        title="🤖 團隊整體診斷"
        emptyHint="尚未產生團隊診斷，點「重新分析」由 AI 產出團隊整體診斷與逐業務點評。"
        load={fetchTeamInsight}
        generate={generateTeamInsight}
        reloadKey="team"
      />

      {/* AI-B 個別業務 coaching */}
      {selectedOwner ? (
        <InsightPanel
          title={`🎯 ${selectedOwner} 的輔導報告`}
          emptyHint={`尚未產生「${selectedOwner}」的輔導報告，點「重新分析」由 AI 產出。`}
          load={() => fetchOwnerInsight(selectedOwner)}
          generate={() => generateOwnerInsight(selectedOwner)}
          reloadKey={`owner:${selectedOwner}`}
        />
      ) : (
        <div className="panel"><div className="sr-empty">點上方任一業務的「輔導報告」可查看 / 產生個別 AI 輔導建議。</div></div>
      )}
    </>
  );
}

/** 單一 KPI 卡。 */
function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi-card">
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value">{value}</strong>
    </div>
  );
}

/**
 * AI 區塊：進頁（或 reloadKey 變更）先讀快取顯示；按「重新分析」呼叫 LLM 生成後更新顯示。
 * 函式級註解：以 reloadKey 區分團隊 / 不同業務，切換業務時自動重讀對應快取。
 */
function InsightPanel({
  title, emptyHint, load, generate, reloadKey
}: {
  title: string;
  emptyHint: string;
  load: () => Promise<ManagerInsightResponse | null>;
  generate: () => Promise<ManagerInsightResponse>;
  reloadKey: string;
}) {
  const [insight, setInsight] = useState<ManagerInsightResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setErr(null);
    load()
      .then((r) => { if (alive) setInsight(r); })
      .catch((e) => { console.error("讀取 AI 分析快取失敗:", e); if (alive) setErr("讀取失敗"); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // reloadKey 變更（切換業務）時重讀；load/generate 為穩定引用之外的依賴刻意忽略
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadKey]);

  /** 點按生成：呼叫 LLM 並更新顯示。 */
  async function handleGenerate() {
    setGenerating(true);
    setErr(null);
    try {
      setInsight(await generate());
    } catch (e) {
      console.error("產生 AI 分析失敗:", e);
      setErr("產生失敗，請稍後再試");
    } finally {
      setGenerating(false);
    }
  }

  return (
    <div className="panel ai-insight-panel">
      <div className="panel-head">
        <h3>{title}</h3>
        <div className="ai-insight-actions">
          {insight ? <small className="ai-insight-meta">
            上次分析：{formatDateTime(insight.generatedAt)}{insight.model ? `（${insight.model}）` : "（教學版摘要）"}
          </small> : null}
          <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
            {generating ? "分析中…" : "重新分析"}
          </button>
        </div>
      </div>
      {loading ? (
        <div className="sr-empty">載入中…</div>
      ) : err ? (
        <div className="sr-empty">{err}</div>
      ) : insight ? (
        <div className="report-markdown">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{insight.content}</ReactMarkdown>
        </div>
      ) : (
        <div className="sr-empty">{emptyHint}</div>
      )}
    </div>
  );
}
