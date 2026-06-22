import { useEffect, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import {
  fetchAiSettings, saveAiSettings, streamModelTest,
  streamModelScore, fetchModelScoreCalls
} from "../../api";
import type { AiSettingsResponse, AiCallHistoryItem, ModelResultItem } from "../../types";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";
import { ReportModal } from "../../components/common/ReportModal";
import { AiThinkingIndicator } from "../../components/common/AiThinkingIndicator";
import { downloadMarkdown } from "../../lib/download";

/** 單一模型的競速測試結果。 */
interface ModelRaceResult {
  status: "idle" | "waiting" | "streaming" | "done" | "error";
  content: string;
  firstTokenMs: number | null;
  totalMs: number | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  errorMsg?: string;
}

/** 固定任務說明（grounding context 由後端從真實 DB 建構，前端不需傳問題）。 */
const TEST_TASK_LABEL = "分析全公司客戶組合，找出最需立即關注的前 3 名客戶（含風險原因 + 建議行動）";

/**
 * 系統設定頁（限 ADMIN）：AI 模型設定 + 多模型競速測試。
 */
export default function AdminSettingsPage() {
  const { user } = useAuth();

  /* ── 設定區狀態 ─────────────────────────────── */
  const [settings, setSettings] = useState<AiSettingsResponse | null>(null);
  const [currentModel, setCurrentModel] = useState("");
  const [options, setOptions] = useState<string[]>([]);
  const [newModel, setNewModel] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [settingError, setSettingError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  /* ── 測試區狀態 ─────────────────────────────── */
  const [raceResults, setRaceResults] = useState<Record<string, ModelRaceResult>>({});
  const [racing, setRacing] = useState(false);
  const startTimeRef = useRef<Record<string, number>>({});

  /* ── 評分區狀態 ─────────────────────────────── */
  const [scoreReport, setScoreReport] = useState<{ open: boolean; loading: boolean; streaming?: boolean; markdown: string; callId?: number | null } | null>(null);
  const [scoring, setScoring] = useState(false);
  const [scoreHistoryOpen, setScoreHistoryOpen] = useState(false);
  const [scoreCalls, setScoreCalls] = useState<AiCallHistoryItem[]>([]);
  const [scoreCallsLoading, setScoreCallsLoading] = useState(false);

  if (user?.role !== "ADMIN") return <Navigate to="/dashboard" replace />;

  /* ── 設定區方法 ─────────────────────────────── */
  async function load() {
    setLoading(true);
    setSettingError(null);
    try {
      const data = await fetchAiSettings();
      setSettings(data);
      setCurrentModel(data.currentModel);
      setOptions(data.modelOptions);
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "載入失敗");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  function selectModel(m: string) {
    setCurrentModel((prev) => prev === m ? "" : m);
    setActionMsg(null);
  }

  function addModel() {
    const m = newModel.trim();
    if (!m) return;
    if (!options.includes(m)) setOptions((prev) => [...prev, m]);
    setCurrentModel(m);
    setNewModel("");
    setActionMsg(null);
  }

  function removeModel(m: string) {
    setOptions((prev) => prev.filter((o) => o !== m));
    if (currentModel === m) setCurrentModel("");
    setActionMsg(null);
  }

  async function save() {
    setSaving(true);
    setSettingError(null);
    setActionMsg(null);
    try {
      const data = await saveAiSettings(currentModel, options);
      setSettings(data);
      setCurrentModel(data.currentModel);
      setOptions(data.modelOptions);
      setActionMsg("已儲存，AI 呼叫即時生效。");
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "儲存失敗");
    } finally {
      setSaving(false);
    }
  }

  /* ── 競速測試方法 ─────────────────────────────── */
  function startRace() {
    if (options.length === 0 || racing) return;
    setRacing(true);
    setScoreReport(null); // 清空上次評分

    const init: Record<string, ModelRaceResult> = {};
    options.forEach((m) => {
      init[m] = { status: "waiting", content: "", firstTokenMs: null, totalMs: null,
                  promptTokens: null, completionTokens: null, totalTokens: null };
    });
    setRaceResults(init);
    startTimeRef.current = {};

    let doneCount = 0;
    const total = options.length;

    options.forEach((model) => {
      const t0 = performance.now();
      startTimeRef.current[model] = t0;

      streamModelTest(
        "", model,
        (chunk: any) => {
          if (chunk.type === "content" && chunk.delta) {
            const now = performance.now();
            setRaceResults((prev) => {
              const cur = prev[model] ?? { status: "streaming", content: "", firstTokenMs: null, totalMs: null,
                                           promptTokens: null, completionTokens: null, totalTokens: null };
              return {
                ...prev,
                [model]: {
                  ...cur,
                  status: "streaming",
                  content: cur.content + chunk.delta,
                  firstTokenMs: cur.firstTokenMs === null ? Math.round(now - t0) : cur.firstTokenMs,
                }
              };
            });
          } else if (chunk.type === "tokens") {
            // 後端串流完成後送出的 token 用量
            setRaceResults((prev) => ({
              ...prev,
              [model]: {
                ...prev[model],
                promptTokens: chunk.promptTokens ?? null,
                completionTokens: chunk.completionTokens ?? null,
                totalTokens: chunk.totalTokens ?? null,
              }
            }));
          }
        },
        () => {
          const totalMs = Math.round(performance.now() - (startTimeRef.current[model] ?? 0));
          setRaceResults((prev) => ({
            ...prev,
            [model]: { ...prev[model], status: "done", totalMs }
          }));
          doneCount++;
          if (doneCount >= total) setRacing(false);
        },
        (err) => {
          setRaceResults((prev) => ({
            ...prev,
            [model]: { ...prev[model], status: "error", errorMsg: err?.message ?? "連線失敗" }
          }));
          doneCount++;
          if (doneCount >= total) setRacing(false);
        }
      );
    });
  }

  /* ── 評分方法 ─────────────────────────────── */
  function startScore() {
    const doneResults = Object.entries(raceResults)
      .filter(([, r]) => r.status === "done")
      .map(([model, r]): ModelResultItem => ({
        model,
        firstTokenMs: r.firstTokenMs ?? 0,
        totalMs: r.totalMs ?? 0,
        promptTokens: r.promptTokens ?? 0,
        completionTokens: r.completionTokens ?? 0,
        totalTokens: r.totalTokens ?? 0,
        content: r.content,
      }));

    if (doneResults.length === 0) return;
    setScoring(true);
    setScoreReport({ open: true, loading: true, streaming: true, markdown: "" });

    streamModelScore(
      doneResults,
      (chunk: any) => {
        if (chunk.type === "content" && chunk.delta) {
          setScoreReport((prev) => prev
            ? { ...prev, loading: false, streaming: true, markdown: (prev.markdown ?? "") + chunk.delta }
            : prev);
        } else if (chunk.type === "callId" && chunk.callId) {
          setScoreReport((prev) => prev ? { ...prev, callId: chunk.callId } : prev);
        }
      },
      () => {
        setScoreReport((prev) => prev ? { ...prev, loading: false, streaming: false } : prev);
        setScoring(false);
      },
      (err) => {
        console.error("評分失敗:", err);
        setScoreReport((prev) => prev
          ? { ...prev, loading: false, streaming: false, markdown: "⚠️ 評分失敗，請稍後再試。" }
          : prev);
        setScoring(false);
      }
    );
  }

  async function openScoreHistory() {
    setScoreHistoryOpen(true);
    setScoreCallsLoading(true);
    try {
      setScoreCalls(await fetchModelScoreCalls());
    } catch {
      setScoreCalls([]);
    } finally {
      setScoreCallsLoading(false);
    }
  }

  /* ── 渲染 ─────────────────────────────── */
  const envLabel = settings?.envDefaultModel || "未設定";
  const hasRaceResults = Object.keys(raceResults).length > 0;
  const allDone = hasRaceResults && Object.values(raceResults).every((r) => r.status === "done" || r.status === "error");
  const hasDoneResults = hasRaceResults && Object.values(raceResults).some((r) => r.status === "done");

  return (
    <div style={{ padding: "24px 28px", maxWidth: 900 }}>
      {/* 頁首 */}
      <h1 style={{ fontSize: 22, fontWeight: 700, color: "#122232", margin: "0 0 4px" }}>系統設定</h1>
      <p style={{ color: "#64748b", fontSize: 14, margin: "0 0 20px" }}>
        管理 AI 對話模型；點選清單項目選用，留空則回退環境變數預設，修改後儲存即時生效。
      </p>

      {/* 設定通知 */}
      {settingError && (
        <div style={{ background: "#fef2f2", border: "1px solid #fca5a5", borderRadius: 8, padding: "10px 14px", marginBottom: 16, color: "#b91c1c", fontSize: 14 }}>
          ⚠️ {settingError}
        </div>
      )}
      {actionMsg && (
        <div style={{ background: "#f0fdf4", border: "1px solid #86efac", borderRadius: 8, padding: "10px 14px", marginBottom: 16, color: "#166534", fontSize: 14 }}>
          ✓ {actionMsg}
        </div>
      )}

      {loading ? (
        <div className="panel" style={{ color: "#64748b", fontSize: 14 }}>載入中…</div>
      ) : (
        <>
          {/* ── 模型設定卡片 ── */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>AI 對話模型</span>
              {currentModel
                ? <span style={{ background: "#dcfce7", color: "#166534", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>系統設定</span>
                : <span style={{ background: "#f1f5f9", color: "#475569", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>環境變數回退</span>
              }
            </div>

            {!currentModel && (
              <div style={{ padding: "10px 12px", background: "#f8fafc", border: "1px dashed #cbd5e1", borderRadius: 8, fontSize: 13, color: "#64748b", marginBottom: 12 }}>
                未選用模型，AI 呼叫將使用環境變數預設：
                <code style={{ background: "#e2e8f0", padding: "1px 6px", borderRadius: 4, marginLeft: 4 }}>{envLabel}</code>
              </div>
            )}

            {/* 候選清單 */}
            <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
              {options.length === 0 && (
                <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>尚無候選模型，請在下方輸入後新增。</p>
              )}
              {options.map((o) => {
                const isSelected = currentModel === o;
                return (
                  <div
                    key={o}
                    onClick={() => selectModel(o)}
                    style={{
                      display: "flex", alignItems: "center", justifyContent: "space-between",
                      padding: "10px 14px",
                      background: isSelected ? "#f0fdf4" : "#f8fafc",
                      border: `1.5px solid ${isSelected ? "#4ade80" : "#e2e8f0"}`,
                      borderRadius: 8, cursor: "pointer",
                      transition: "border-color 0.15s, background 0.15s",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <div style={{
                        width: 16, height: 16, borderRadius: "50%",
                        border: `2px solid ${isSelected ? "#16a34a" : "#cbd5e1"}`,
                        background: isSelected ? "#16a34a" : "transparent",
                        flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center"
                      }}>
                        {isSelected && <div style={{ width: 6, height: 6, borderRadius: "50%", background: "#fff" }} />}
                      </div>
                      <span style={{ fontFamily: "monospace", fontSize: 14, color: "#122232" }}>{o}</span>
                      {isSelected && (
                        <span style={{ fontSize: 11, color: "#166534", background: "#dcfce7", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>使用中</span>
                      )}
                    </div>
                    <button
                      type="button"
                      className="btn-danger"
                      style={{ padding: "3px 10px", fontSize: 12 }}
                      onClick={(e) => { e.stopPropagation(); removeModel(o); }}
                    >
                      刪除
                    </button>
                  </div>
                );
              })}
            </div>

            {/* 新增輸入列 */}
            <div style={{ display: "flex", gap: 8, paddingTop: 12, borderTop: "1px solid #f1f5f9", marginBottom: 16 }}>
              <input
                value={newModel}
                placeholder="輸入模型名，如 claude-sonnet-4-6"
                onChange={(e) => setNewModel(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addModel(); } }}
                style={{ flex: 1, padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
              />
              <button type="button" className="btn-secondary" style={{ whiteSpace: "nowrap", padding: "8px 16px" }} onClick={addModel}>
                + 新增
              </button>
            </div>

            {/* 儲存 */}
            <button
              type="button"
              className="btn-primary"
              disabled={saving}
              onClick={save}
              style={{ width: "100%", padding: "11px", fontSize: 15, fontWeight: 700, borderRadius: 10 }}
            >
              {saving ? "儲存中…" : "儲存設定"}
            </button>
          </div>

          {/* ── 多模型競速測試卡片 ── */}
          <div className="panel">
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>🏁 多模型競速測試</span>
              <span style={{ fontSize: 12, color: "#94a3b8" }}>同時呼叫所有候選模型，比較回應速度與品質</span>
            </div>

            {options.length === 0 ? (
              <p style={{ fontSize: 13, color: "#94a3b8" }}>請先在上方新增候選模型才能執行測試。</p>
            ) : (
              <>
                {/* 固定任務說明 + 啟動按鈕 */}
                <div style={{
                  display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
                  padding: "12px 14px", background: "#f0fdf4",
                  border: "1px solid #bbf7d0", borderRadius: 8, marginBottom: 16
                }}>
                  <div>
                    <div style={{ fontSize: 12, color: "#16a34a", fontWeight: 600, marginBottom: 3 }}>
                      📊 測試任務（使用真實 CRM 資料）
                    </div>
                    <div style={{ fontSize: 13, color: "#334155" }}>{TEST_TASK_LABEL}</div>
                  </div>
                  <button
                    type="button"
                    className="btn-assess"
                    disabled={racing}
                    onClick={startRace}
                    style={{ whiteSpace: "nowrap", padding: "9px 18px", borderRadius: 8, fontWeight: 700, flexShrink: 0 }}
                  >
                    {racing ? "測試中…" : "▶ 開始比較"}
                  </button>
                </div>

                {/* 競速結果並排顯示 */}
                {hasRaceResults && (
                  <>
                    <div style={{
                      display: "grid",
                      gridTemplateColumns: `repeat(${Math.min(options.length, 2)}, 1fr)`,
                      gap: 12,
                      marginBottom: 16
                    }}>
                      {options.map((model) => {
                        const r = raceResults[model];
                        if (!r) return null;
                        const statusColor = { idle: "#94a3b8", waiting: "#f59e0b", streaming: "#3b82f6", done: "#16a34a", error: "#dc2626" }[r.status];
                        const statusLabel = { idle: "–", waiting: "等待中", streaming: "生成中", done: "完成", error: "失敗" }[r.status];

                        return (
                          <div key={model} style={{ border: `1.5px solid ${r.status === "done" ? "#86efac" : r.status === "error" ? "#fca5a5" : "#e2e8f0"}`, borderRadius: 10, overflow: "hidden" }}>
                            {/* 模型標頭 */}
                            <div style={{ padding: "8px 12px", background: r.status === "done" ? "#f0fdf4" : r.status === "error" ? "#fef2f2" : "#f8fafc", borderBottom: "1px solid #f1f5f9" }}>
                              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 3 }}>
                                <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 600, color: "#122232" }}>{model}</span>
                                <span style={{ fontSize: 11, color: statusColor, fontWeight: 600 }}>● {statusLabel}</span>
                              </div>
                              {/* 速度 + Token 統計列 */}
                              <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                                {r.firstTokenMs !== null && (
                                  <span style={{ fontSize: 11, color: "#64748b" }}>⚡ 首字 {r.firstTokenMs}ms</span>
                                )}
                                {r.totalMs !== null && (
                                  <span style={{ fontSize: 11, color: "#64748b" }}>⏱ 總計 {(r.totalMs / 1000).toFixed(1)}s</span>
                                )}
                                {r.promptTokens !== null && (
                                  <span style={{ fontSize: 11, color: "#8b5cf6" }}>🔢 {r.promptTokens ?? "–"} in / {r.completionTokens ?? "–"} out</span>
                                )}
                              </div>
                            </div>
                            {/* 串流內容 */}
                            <div
                              className={r.status === "streaming" ? "ai-streaming-body" : ""}
                              style={{ padding: "10px 12px", minHeight: 120, maxHeight: 280, overflowY: "auto", fontSize: 13, lineHeight: 1.6, color: r.status === "error" ? "#dc2626" : "#334155", whiteSpace: "pre-wrap", wordBreak: "break-word" }}
                            >
                              {r.status === "waiting" && <AiThinkingIndicator label="等待回應" />}
                              {r.status === "error" && <span>⚠️ {r.errorMsg}</span>}
                              {r.content}
                              {r.status === "streaming" && <span className="ai-stream-cursor" />}
                            </div>
                            {/* 完成後顯示下載按鈕 */}
                            {r.status === "done" && r.content && (
                              <div style={{ padding: "6px 12px", borderTop: "1px solid #f1f5f9", textAlign: "right" }}>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ fontSize: 12, padding: "3px 10px" }}
                                  onClick={() => downloadMarkdown(`${model}-回答`, r.content)}
                                >
                                  ⬇ 下載 MD
                                </button>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>

                    {/* 評分按鈕（全部完成後顯示） */}
                    {allDone && hasDoneResults && (
                      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", paddingTop: 8, borderTop: "1px solid #f1f5f9" }}>
                        <button type="button" className="btn-secondary" onClick={openScoreHistory}>🕘 評分歷程</button>
                        <button
                          type="button"
                          className="btn-assess"
                          disabled={scoring}
                          onClick={startScore}
                          style={{ fontWeight: 700, padding: "9px 20px", borderRadius: 8 }}
                        >
                          {scoring ? "評分中…" : "🏆 claude-opus-4-8 評分"}
                        </button>
                      </div>
                    )}

                    {/* 評分進行中的提示（尚未有內容時顯示） */}
                    {scoreReport && scoring && !scoreReport.markdown && (
                      <div style={{ marginTop: 12, padding: "12px 14px", background: "#fafafa", border: "1px solid #e2e8f0", borderRadius: 10 }}>
                        <div style={{ fontSize: 13, fontWeight: 700, color: "#122232", marginBottom: 8 }}>
                          🏆 評分報告 <span style={{ fontSize: 11, color: "#64748b", fontWeight: 400 }}>（claude-opus-4-8）</span>
                          {scoreReport.streaming && <AiThinkingIndicator label="" />}
                        </div>
                      </div>
                    )}
                  </>
                )}
              </>
            )}
          </div>
        </>
      )}

      {/* 評分報告 Modal */}
      {scoreReport?.open && (
        <ReportModal
          report={{ title: "🏆 多模型競速評分報告", loading: scoreReport.loading, streaming: scoreReport.streaming, markdown: scoreReport.markdown, callId: scoreReport.callId }}
          onClose={() => setScoreReport(null)}
        />
      )}

      {/* 評分歷程 Modal */}
      {scoreHistoryOpen && (
        <AiCallHistoryModal
          title="評分 AI 歷程（claude-opus-4-8）"
          calls={scoreCalls}
          loading={scoreCallsLoading}
          onClose={() => setScoreHistoryOpen(false)}
        />
      )}
    </div>
  );
}
