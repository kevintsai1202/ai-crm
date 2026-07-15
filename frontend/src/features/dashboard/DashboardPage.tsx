import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";
import { fetchAiUsage, fetchDashboard, fetchDashboardLayout, fetchDashboardReports, fetchDrilldown, fetchPortfolioCalls, fetchRfm, fetchSentimentRadar, generateDemoData, saveDashboardLayout, streamPortfolioAssessment } from "../../api";
import type { AiCallHistoryItem, DashboardReports, DashboardSummary, DrilldownResponse, DrilldownSource, RfmResponse, SentimentRadarResponse, UsageSummaryResponse } from "../../types";
import { formatMoney } from "../../lib/format";
import { useAuth } from "../../context/AuthContext";
import { AiBadge } from "../../components/common/AiBadge";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";
import { ReportModal } from "../../components/common/ReportModal";
import { DrilldownModal } from "../../components/common/DrilldownModal";
import { kpiBlocks } from "./components/DashboardCards";
import { reportBlocks } from "./components/ReportsSection";
import { rfmBlock } from "./components/RfmSection";
import { sentimentBlocks } from "./components/SentimentRadarSection";
import { usageBlock } from "./components/AiUsageCard";
import { LayoutDrawer } from "./components/LayoutDrawer";
import type { DashboardBlock } from "./blockTypes";
import { blockDefaultSize, defaultLayout, findFreeSlot, parseLayout, resolveOverlaps, swapPreferred, RGL_COLS, RGL_MARGIN, RGL_ROW_HEIGHT, serializeLayout } from "./layout";
import RGL, { WidthProvider, type Layout, type LayoutItem } from "react-grid-layout/legacy";
import "react-grid-layout/css/styles.css";

/** RGL 自動量測容器寬度（取代手動寬度計算）。 */
const GridLayout = WidthProvider(RGL);

/**
 * 儀表板頁（看數據）：統計卡 + 7 張 CRM 圖表 + 圖表下鑽 + 全公司 Portfolio 整體評估。
 * 函式級註解：本頁為唯讀的管理層視角，所有資料進頁時載入；點客戶會跳到操作頁。
 * 區塊以座標型大網格呈現：每張卡記錄自己在哪一格（col,row），格子可留空、關閉不回補。
 */
export function DashboardPage() {
  const { t, i18n } = useTranslation(["dashboard", "common"]);
  const navigate = useNavigate();
  const location = useLocation();
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null);
  const [reports, setReports] = useState<DashboardReports | null>(null);
  const [drilldown, setDrilldown] = useState<{ open: boolean; loading: boolean; title: string; data: DrilldownResponse | null } | null>(null);
  const [report, setReport] = useState<{ open: boolean; title: string; loading: boolean; streaming?: boolean; markdown: string; meta?: string; callId?: number | null } | null>(null);
  const [rfm, setRfm] = useState<RfmResponse[] | null>(null);
  const [sentiment, setSentiment] = useState<SentimentRadarResponse | null>(null);
  const [usage, setUsage] = useState<UsageSummaryResponse | null>(null);
  // 產生示範資料的進行中旗標（僅 ADMIN 使用），用於禁用按鈕與顯示「產生中…」
  const [generatingDemo, setGeneratingDemo] = useState(false);
  // 全公司評估 AI 歷程彈窗
  const [portfolioHistoryOpen, setPortfolioHistoryOpen] = useState(false);
  const [portfolioCalls, setPortfolioCalls] = useState<AiCallHistoryItem[]>([]);
  const [portfolioCallsLoading, setPortfolioCallsLoading] = useState(false);
  const { user } = useAuth();
  // AI 用量治理僅 MANAGER / ADMIN 可見（後端亦以 RBAC 限制，前端避免發出註定 403 的請求）
  const canSeeUsage = user?.role === "MANAGER" || user?.role === "ADMIN";
  // 產生示範資料按鈕僅 ADMIN 可見
  const isAdmin = user?.role === "ADMIN";

  // RGL 版面（每項 {i,x,y,w,h}）；單一真實來源。採自訂重排：拖曳「對調優先」（單一同尺寸相鄰碰撞→對調，
  // 其餘→下擠）、resize→下擠；每幀從拖曳開始的乾淨基準重算，故移開即回彈、絕不重疊、保留空格。
  const [layout, setLayout] = useState<Layout>([]);
  // 抽屜開關
  const [drawerOpen, setDrawerOpen] = useState(false);
  // 進行中模式：drag（對調優先）/ resize（下擠）/ null（非進行中）
  const modeRef = useRef<"drag" | "resize" | null>(null);
  // 拖曳/縮放開始時的「乾淨基準版面」快照，供每幀回彈重算
  const dragBaseRef = useRef<Layout>([]);
  // 目前被操作的卡 id
  const dragIdRef = useRef<string | null>(null);
  // 永遠映射目前 layout，供開始時取乾淨基準（避免閉包取到舊值）
  const layoutRef = useRef<Layout>([]);
  useEffect(() => { layoutRef.current = layout; }, [layout]);

  /** 載入儀表板全部資料；各 API 獨立回來即 setState，不相互阻塞。 */
  function loadDashboardData() {
    fetchDashboard()
      .then(setDashboard)
      .catch((e) => console.error("摘要載入失敗:", e));
    fetchDashboardReports()
      .then(setReports)
      .catch((e) => console.error("報表載入失敗:", e));
    fetchRfm()
      .then(setRfm)
      .catch((e) => console.error("RFM 載入失敗:", e));
    fetchSentimentRadar()
      .then(setSentiment)
      .catch((e) => console.error("情緒雷達載入失敗:", e));
  }

  // 進頁載入摘要、報表、RFM 分群與情緒雷達
  useEffect(() => {
    void loadDashboardData();
  }, []);

  // 進頁載入個人版面：先套預設避免空白，再以後端偏好（"id:x:y:w:h"）覆蓋；格式不符（舊版）則維持預設
  useEffect(() => {
    setLayout(defaultLayout(fullCatalog));
    void (async () => {
      try {
        const saved = await fetchDashboardLayout();
        if (saved && saved.length > 0) {
          const parsed = parseLayout(saved);
          // 格式正確即採用；舊存檔殘留重疊用 resolveOverlaps 收尾（保留排列、僅擠開重疊）
          if (parsed) setLayout(resolveOverlaps(parsed));
        }
      } catch (e) {
        console.error("載入版面偏好失敗:", e);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 自客戶頁返回時，依 location.state.scrollTo 捲到原區塊，並清掉 state 避免重複觸發
  useEffect(() => {
    const scrollTo = (location.state as { scrollTo?: string } | null)?.scrollTo;
    if (!scrollTo) return;
    // 等資料載入後再捲，確保區塊已渲染（dashboard 為依賴）
    if (!dashboard) return;
    const el = document.getElementById(`block-${scrollTo}`);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    navigate(location.pathname, { replace: true, state: null });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.state, dashboard]);

  // AI 用量（角色符合才載入）
  useEffect(() => {
    if (!canSeeUsage) return;
    void (async () => {
      try {
        setUsage(await fetchAiUsage());
      } catch (e) {
        console.error("載入 AI 用量失敗:", e);
      }
    })();
  }, [canSeeUsage]);

  const riskCounts = useMemo<Record<string, number>>(() => ({}), []);

  // 下鑽請求序號:每次開啟/關閉都遞增,await 回來時若序號已變則丟棄該回應,
  // 避免「載入中關閉後,晚到的 fetch 回應又把 Modal 重新打開」的競態(關不掉的成因)。
  const drilldownReqRef = useRef(0);

  /** 關閉下鑽 Modal:遞增序號使進行中的請求回應失效,確保不會被重新打開。 */
  function closeDrilldown() {
    drilldownReqRef.current++;
    setDrilldown(null);
  }

  /** 開啟圖表下鑽明細 Modal。 */
  async function openDrilldown(type: string, key: string, title: string) {
    const reqId = ++drilldownReqRef.current;
    setDrilldown({ open: true, loading: true, title, data: null });
    try {
      const data = await fetchDrilldown(type, key);
      if (drilldownReqRef.current !== reqId) return; // 已關閉或有更新的請求 → 丟棄此回應
      setDrilldown({ open: true, loading: false, title, data });
    } catch (e) {
      if (drilldownReqRef.current !== reqId) return;
      console.error("下鑽明細載入失敗:", e);
      setDrilldown({ open: true, loading: false, title, data: null });
    }
  }

  /** 從下鑽/區塊跳到操作頁指定客戶，帶上來源區塊供麵包屑與返回定位。 */
  function jumpToCustomer(id: number, source: DrilldownSource) {
    closeDrilldown();
    navigate(`/customers/${id}`, { state: source });
  }

  /** 開啟 Portfolio 全公司整體評估報告（SSE 串流版）。 */
  function openPortfolioAssessment() {
    // 立即顯示 modal（loading=true），第一個 token 到達後轉為逐字渲染
    setReport({ open: true, title: "Portfolio 整體評估（全公司）", loading: true, streaming: true, markdown: "" });
    streamPortfolioAssessment(
      (chunk) => {
        if (chunk.type === "content" && chunk.delta) {
          setReport((prev) => prev
            ? { ...prev, loading: false, streaming: true, markdown: (prev.markdown ?? "") + chunk.delta }
            : prev);
        } else if (chunk.type === "callId" && chunk.callId) {
          setReport((prev) => prev ? { ...prev, callId: chunk.callId } : prev);
        }
      },
      () => setReport((prev) => prev ? { ...prev, loading: false, streaming: false } : prev),
      (err) => {
        console.error("Portfolio 整體評估串流失敗:", err);
        setReport({ open: true, title: "Portfolio 整體評估（全公司）", loading: false, streaming: false, markdown: "⚠️ 產生評估失敗，請稍後再試。" });
      }
    );
  }

  /** 開啟全公司評估的 AI 歷程。 */
  async function openPortfolioHistory() {
    setPortfolioHistoryOpen(true);
    setPortfolioCallsLoading(true);
    try {
      setPortfolioCalls(await fetchPortfolioCalls());
    } catch (e) {
      console.error("讀取全公司評估 AI 歷程失敗:", e);
      setPortfolioCalls([]);
    } finally {
      setPortfolioCallsLoading(false);
    }
  }

  /** 產生示範資料（ADMIN）：生成 200 位客戶樣本後觸發儀表板各區塊逐一更新。 */
  async function handleGenerateDemo() {
    setGeneratingDemo(true);
    try {
      await generateDemoData(200);
      loadDashboardData();
    } catch (e) {
      console.error("產生示範資料失敗:", e);
    } finally {
      setGeneratingDemo(false);
    }
  }

  // 完整區塊目錄（一律含 usage）：提供穩定的 id 與跨度供版面幾何/預設使用
  const fullCatalog: DashboardBlock[] = [
    ...kpiBlocks(dashboard, riskCounts, t, i18n.language),
    ...reportBlocks(reports, openDrilldown, jumpToCustomer),
    ...sentimentBlocks(sentiment, jumpToCustomer, t, i18n.language),
    rfmBlock(rfm, jumpToCustomer, t, i18n.language),
    usageBlock(usage)
  ];
  // 角色可見的區塊 id（usage 僅 MANAGER/ADMIN）
  const roleAllowed = new Set(fullCatalog.filter((b) => b.id !== "usage" || canSeeUsage).map((b) => b.id));
  // 實際要渲染的 layout：角色可見且存在於目錄
  const visibleLayout = layout.filter((it) => roleAllowed.has(it.i) && fullCatalog.some((b) => b.id === it.i));
  // 隱藏區塊（可加回）：角色可見但目前不在 layout 中
  const layoutIds = new Set(layout.map((it) => it.i));
  const hiddenBlocks = fullCatalog.filter((b) => roleAllowed.has(b.id) && !layoutIds.has(b.id));

  /** fire-and-forget 存後端（"id:x:y:w:h" 編碼）。 */
  function persistLayout(next: Layout) {
    void saveDashboardLayout(serializeLayout(next)).catch((e) => console.error("儲存版面失敗:", e));
  }
  /** 拖曳開始：記模式、被拖卡 id、快照乾淨基準。 */
  function handleDragStart(_layout: Layout, oldItem: LayoutItem | null) {
    modeRef.current = "drag";
    dragBaseRef.current = layoutRef.current;
    dragIdRef.current = oldItem?.i ?? null;
  }
  /** 縮放開始：記模式、被縮放卡 id、快照乾淨基準。 */
  function handleResizeStart(_layout: Layout, oldItem: LayoutItem | null) {
    modeRef.current = "resize";
    dragBaseRef.current = layoutRef.current;
    dragIdRef.current = oldItem?.i ?? null;
  }
  /**
   * 從乾淨基準重算當前預覽：drag→對調優先（swapPreferred）；resize→下擠（resolveOverlaps）。
   * 找不到被操作卡時退回對 next 直接 resolveOverlaps。
   */
  function computeFromBase(next: Layout): Layout {
    const id = dragIdRef.current;
    const base = dragBaseRef.current;
    const moved = id ? next.find((n) => n.i === id) : undefined;
    if (!id || !moved) return resolveOverlaps(next);
    if (modeRef.current === "drag") return swapPreferred(base, id, moved);
    // resize：以新尺寸套回基準後下擠
    return resolveOverlaps(base.map((it) => (it.i === id ? { ...it, x: moved.x, y: moved.y, w: moved.w, h: moved.h } : it)));
  }
  /**
   * RGL 內部變動：拖曳/縮放途中「不回饋」——讓 RGL 原生顯示被操作卡浮動、其他卡維持原位
   * （天然滿足「未放開前被擠的卡在原位」）。若在此 setLayout 會與 RGL 拖曳狀態打架（把卡彈回）。
   * 真正的對調/下擠在放開時（handleChangeStop）一次算定。掛載自動排版亦忽略。
   */
  function handleLayoutChange(_next: Layout) {
    // 故意留空：commit 由 handleChangeStop / closeBlock / addBlock / 載入負責
  }
  /** 拖曳或 resize 結束：從乾淨基準算最終版面（drag 對調優先 / resize 下擠）、清模式、更新並存回後端。 */
  function handleChangeStop(next: Layout) {
    const resolved = modeRef.current ? computeFromBase(next) : resolveOverlaps(next);
    modeRef.current = null;
    dragIdRef.current = null;
    setLayout(resolved);
    persistLayout(resolved);
  }
  /** 關閉區塊：自 layout 移除，留下空格不回補。 */
  function closeBlock(id: string) {
    const next = layout.filter((it) => it.i !== id);
    setLayout(next);
    persistLayout(next);
  }
  /** 加回區塊：以預設尺寸放到第一個空格（compactType:null 無壓縮器，須自算真實格位，不可用 y:Infinity）。 */
  function addBlock(id: string) {
    const b = fullCatalog.find((x) => x.id === id);
    if (!b) return;
    const { w, h } = blockDefaultSize(b);
    const { x, y } = findFreeSlot(layout, w, h);
    const next = [...layout, { i: id, x, y, w, h }];
    setLayout(next);
    persistLayout(next);
  }
  /** 還原預設版面（依角色過濾）。 */
  function resetLayout() {
    const next = defaultLayout(fullCatalog.filter((b) => roleAllowed.has(b.id)));
    setLayout(next);
    persistLayout(next);
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>儀表板</h2>
        </div>
        <div className="topbar-actions">
          {/* 募資課程問卷：儀表板首頁快捷入口，新分頁開啟 */}
          <a
            className="survey-link survey-link--topbar"
            href="https://survey.springai.world/"
            target="_blank"
            rel="noopener noreferrer"
          >
            📋 募資課程問卷
          </a>
          <button type="button" className="layout-btn" onClick={() => setDrawerOpen(true)}>⊞ 版面（隱藏 {hiddenBlocks.length}）</button>
          {isAdmin ? (
            <button type="button" className="btn-assess" onClick={handleGenerateDemo} disabled={generatingDemo}>
              {generatingDemo ? "產生中…" : "🧪 產生示範資料"}
            </button>
          ) : null}
          <button type="button" className="btn-assess topbar-assess" onClick={openPortfolioAssessment}>📊 整體評估（全公司）<AiBadge onDark /></button>
          <button type="button" className="btn-secondary" onClick={openPortfolioHistory}>🕘 AI 歷程</button>
        </div>
      </section>

      <GridLayout
        className="dashboard-grid"
        layout={visibleLayout}
        cols={RGL_COLS}
        rowHeight={RGL_ROW_HEIGHT}
        margin={RGL_MARGIN}
        compactType={null}
        preventCollision={false}
        isBounded
        draggableHandle=".block-drag-handle"
        resizeHandles={["se"]}
        onLayoutChange={handleLayoutChange}
        onDragStart={handleDragStart}
        onResizeStart={handleResizeStart}
        onDragStop={handleChangeStop}
        onResizeStop={handleChangeStop}
      >
        {visibleLayout.map((it) => {
          const block = fullCatalog.find((b) => b.id === it.i);
          if (!block) return null;
          return (
            <div key={it.i} id={`block-${it.i}`} className="block-wrapper">
              <div className="block-toolbar">
                <span className="block-drag-handle" title="拖拉移動">⠿</span>
                <button type="button" className="block-close" title="關閉區塊" onClick={() => closeBlock(it.i)}>✕</button>
              </div>
              {block.render()}
            </div>
          );
        })}
      </GridLayout>

      {report?.open ? <ReportModal report={report} onClose={() => setReport(null)} /> : null}
      {portfolioHistoryOpen ? (
        <AiCallHistoryModal
          title="全公司評估 AI 歷程"
          calls={portfolioCalls}
          loading={portfolioCallsLoading}
          onClose={() => setPortfolioHistoryOpen(false)}
        />
      ) : null}
      {drilldown?.open ? <DrilldownModal state={drilldown} onSelectCustomer={(id) => jumpToCustomer(id, { from: "dashboard", section: drilldown.title, blockId: "reports" })} onClose={closeDrilldown} /> : null}
      {drawerOpen ? (
        <LayoutDrawer hiddenBlocks={hiddenBlocks.map((b) => ({ id: b.id, title: b.title }))} onAdd={addBlock} onReset={resetLayout} onClose={() => setDrawerOpen(false)} />
      ) : null}
    </>
  );
}
