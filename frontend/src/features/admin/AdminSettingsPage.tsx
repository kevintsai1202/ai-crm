import { useEffect, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import {
  fetchAiSettings, saveAiSettings, streamModelTest,
  streamModelScore, fetchModelScoreCalls,
  createAiProvider, updateAiProvider, deleteAiProvider,
  refreshAiProviderModels, saveModelCapabilities, saveAiModelAssignments,
  logModelTest, fetchModelTestCalls,
} from "../../api/index";
import type {
  AiSettingsResponse, AiCallHistoryItem, ModelResultItem,
  AiProviderItem, ModelOptionItem, ModelCapability,
} from "../../types";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";
import { ReportModal } from "../../components/common/ReportModal";
import { AiThinkingIndicator } from "../../components/common/AiThinkingIndicator";
import { downloadMarkdown, downloadZip } from "../../lib/download";
import {
  buildModelAssignments,
  canEditCapabilities,
  filterModelsForCapability,
  hasCapability,
  isSameModelPair,
  modelOptionKey,
  modelPairKey,
} from "./modelCapabilities";

/** 單一模型的競速測試結果。 */
interface ModelRaceResult {
  status: "idle" | "waiting" | "streaming" | "done" | "error";
  content: string;
  firstTokenMs: number | null;
  totalMs: number | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  reasoningTokens: number | null;     // 推理 token（推理模型才有）
  visibleOutputTokens: number | null; // 可見回答 token = completion - reasoning
  finishReason?: string;
  errorMsg?: string;
}

/** 固定任務說明（grounding context 由後端從真實 DB 建構，前端不需傳問題）。 */
const TEST_TASK_LABEL = "分析全公司客戶組合，找出最需立即關注的前 3 名客戶（含風險原因 + 建議行動）";

/**
 * 系統設定頁（限 ADMIN）：AI 供應商管理 + 模型設定 + 多模型競速測試。
 */
export default function AdminSettingsPage() {
  const { user } = useAuth();

  /* ── 設定區狀態 ─────────────────────────────── */
  const [settings, setSettings] = useState<AiSettingsResponse | null>(null);
  const [currentModel, setCurrentModel] = useState("");
  const [currentProviderId, setCurrentProviderId] = useState<number | null>(null);
  const [options, setOptions] = useState<ModelOptionItem[]>([]);
  // 可編輯模型參數（以字串存供輸入框；空字串=未設定）
  const [temperature, setTemperature] = useState("");
  const [maxCompletionTokens, setMaxCompletionTokens] = useState("");
  const [reasoningEffort, setReasoningEffort] = useState("");
  const [newModel, setNewModel] = useState("");
  /** 新增模型時選擇的供應商 ID */
  const [newModelProviderId, setNewModelProviderId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [settingError, setSettingError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [ocrModelKey, setOcrModelKey] = useState("");
  const [transcriptionModelKey, setTranscriptionModelKey] = useState("");

  /* ── Provider 管理狀態 ─────────────────────────────── */
  /** 已設定的 AI 供應商清單 */
  const [providers, setProviders] = useState<AiProviderItem[]>([]);
  /** 供應商表單（新增/編輯用） */
  const [providerForm, setProviderForm] = useState({ name: "", baseUrl: "", apiKey: "" });
  /** 正在編輯的供應商 ID（null 代表新增模式） */
  const [editingProviderId, setEditingProviderId] = useState<number | null>(null);
  /** 供應商操作的錯誤訊息 */
  const [providerError, setProviderError] = useState<string | null>(null);
  /** 供應商儲存中旗標 */
  const [savingProvider, setSavingProvider] = useState(false);

  /* ── 測試區狀態 ─────────────────────────────── */
  const [raceResults, setRaceResults] = useState<Record<string, ModelRaceResult>>({});
  const [racing, setRacing] = useState(false);
  /** 勾選加入競速的模型名稱集合（初始為全部勾選）。 */
  const [raceModels, setRaceModels] = useState<Set<string>>(
    () => new Set(options.map(o => o.model))
  );
  const startTimeRef = useRef<Record<string, number>>({});

  /* ── 評分區狀態 ─────────────────────────────── */
  const [scoreReport, setScoreReport] = useState<{ open: boolean; loading: boolean; streaming?: boolean; markdown: string; callId?: number | null } | null>(null);
  const [scoring, setScoring] = useState(false);
  const [scoreHistoryOpen, setScoreHistoryOpen] = useState(false);
  const [scoreCalls, setScoreCalls] = useState<AiCallHistoryItem[]>([]);
  const [scoreCallsLoading, setScoreCallsLoading] = useState(false);
  /** 個別模型歷程 Modal 狀態（null 代表關閉）。 */
  const [modelHistoryState, setModelHistoryState] = useState<{
    open: boolean; model: string; calls: AiCallHistoryItem[]; loading: boolean;
  } | null>(null);
  /** 當前競速批次的 sessionId（startRace 時產生，startScore 時傳遞）。 */
  const [currentSessionId, setCurrentSessionId] = useState<string>("");

  if (user?.role !== "ADMIN") return <Navigate to="/dashboard" replace />;

  /* ── 設定區方法 ─────────────────────────────── */
  /** 將後端回傳的模型參數同步到輸入框狀態。 */
  function syncParams(data: AiSettingsResponse) {
    setTemperature(data.temperature != null ? String(data.temperature) : "");
    setMaxCompletionTokens(data.maxCompletionTokens != null ? String(data.maxCompletionTokens) : "");
    setReasoningEffort(data.reasoningEffort ?? "");
  }

  /** 由輸入框狀態組出儲存用參數（空字串→null，沿用預設）。 */
  function paramsPayload() {
    return {
      temperature: temperature.trim() === "" ? null : Number(temperature),
      maxCompletionTokens: maxCompletionTokens.trim() === "" ? null : Number(maxCompletionTokens),
      reasoningEffort: reasoningEffort.trim() === "" ? null : reasoningEffort.trim()
    };
  }

  async function load() {
    setLoading(true);
    setSettingError(null);
    try {
      const data = await fetchAiSettings();
      setSettings(data);
      setCurrentModel(data.currentModel);
      setCurrentProviderId(data.currentProviderId);
      setOptions(data.modelOptions);
      setProviders(data.providers);
      setOcrModelKey(modelPairKey(data.ocrModel, data.ocrProviderId));
      setTranscriptionModelKey(modelPairKey(data.transcriptionModel, data.transcriptionProviderId));
      syncParams(data);
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "載入失敗");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  /**
   * 選取模型，同時更新對應的 provider ID，並立即儲存至後端。
   * 若再次點擊已選模型則取消選取（model 設為空字串）。
   */
  async function selectModel(m: string, pid: number | null) {
    const isCurrentPair = currentModel === m && currentProviderId === pid;
    const newModel = isCurrentPair ? "" : m;
    const newPid = isCurrentPair ? null : pid;
    setCurrentModel(newModel);
    setCurrentProviderId(newPid);
    setActionMsg(null);
    setSaving(true);
    setSettingError(null);
    try {
      const data = await saveAiSettings(newModel, newPid, options, paramsPayload());
      setSettings(data);
      setCurrentModel(data.currentModel);
      setCurrentProviderId(data.currentProviderId);
      setOptions(data.modelOptions);
      setProviders(data.providers);
      syncParams(data);
      const msg = data.currentModel
        ? `✓ 已設定 ${data.currentModel} 為默認模型`
        : "✓ 已清除默認模型，改用環境變數預設";
      setActionMsg(msg);
      setTimeout(() => setActionMsg(null), 3000);
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "儲存失敗");
    } finally {
      setSaving(false);
    }
  }

  /** 新增候選模型（含供應商關聯）；僅加入清單，不自動選取為默認模型。 */
  function addModel() {
    const m = newModel.trim();
    if (!m) return;
    if (!options.find((option) => isSameModelPair(option, m, newModelProviderId))) {
      setOptions(prev => [...prev, {
        model: m,
        providerId: newModelProviderId,
        capabilities: [],
        capabilitySource: "UNKNOWN",
      }]);
      setRaceModels(prev => new Set([...prev, m]));
    }
    setNewModel("");
    setNewModelProviderId(null);
    setActionMsg(null);
  }

  /** 從候選清單移除指定模型。 */
  function removeModel(optionToRemove: ModelOptionItem) {
    setOptions(prev => prev.filter((option) => modelOptionKey(option) !== modelOptionKey(optionToRemove)));
    const m = optionToRemove.model;
    setRaceModels(prev => { const s = new Set(prev); s.delete(m); return s; });
    if (currentModel === m && currentProviderId === optionToRemove.providerId) {
      setCurrentModel("");
      setCurrentProviderId(null);
    }
    setActionMsg(null);
  }

  async function save() {
    setSaving(true);
    setSettingError(null);
    setActionMsg(null);
    try {
      const data = await saveAiSettings(currentModel, currentProviderId, options, paramsPayload());
      setSettings(data);
      setCurrentModel(data.currentModel);
      setCurrentProviderId(data.currentProviderId);
      setOptions(data.modelOptions);
      setProviders(data.providers);
      syncParams(data);
      setActionMsg("已儲存，AI 呼叫即時生效。");
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "儲存失敗");
    } finally {
      setSaving(false);
    }
  }

  /* ── Provider 管理方法 ─────────────────────────────── */

  /** 儲存供應商表單（新增或更新）。 */
  async function saveProviderForm() {
    setSavingProvider(true);
    setProviderError(null);
    try {
      if (editingProviderId !== null) {
        // 更新模式：apiKey 留空表示保留現有金鑰
        const apiKey = providerForm.apiKey.trim() || null;
        const updated = await updateAiProvider(editingProviderId, providerForm.name, providerForm.baseUrl, apiKey);
        setProviders(prev => prev.map(p => p.id === editingProviderId ? updated : p));
      } else {
        // 新增模式
        const created = await createAiProvider(providerForm.name, providerForm.baseUrl || null, providerForm.apiKey);
        setProviders(prev => [...prev, created]);
      }
      setProviderForm({ name: "", baseUrl: "", apiKey: "" });
      setEditingProviderId(null);
    } catch (e) {
      setProviderError(e instanceof Error ? e.message : "儲存失敗");
    } finally {
      setSavingProvider(false);
    }
  }

  /** 點擊「編輯」後填入表單並切換至編輯模式。 */
  function startEditProvider(p: AiProviderItem) {
    setEditingProviderId(p.id);
    setProviderForm({ name: p.name, baseUrl: p.baseUrl ?? "", apiKey: "" });
    setProviderError(null);
  }

  /** 刪除供應商，並清除相關模型選項的 provider 關聯，同步儲存至 DB。 */
  async function handleDeleteProvider(id: number) {
    if (!window.confirm("確定刪除此供應商？相關模型選項的 provider 關聯將清除為 null。")) return;
    try {
      await deleteAiProvider(id);
      // 先計算清理後的值，再一次性更新 state，確保傳給 saveAiSettings 的是清理後的結果
      const cleanedOptions = options.map(o => o.providerId === id ? { ...o, providerId: null } : o);
      const cleanedProviderId = currentProviderId === id ? null : currentProviderId;
      setProviders(prev => prev.filter(p => p.id !== id));
      setOptions(cleanedOptions);
      setCurrentProviderId(cleanedProviderId);
      // 自動同步清理後的設定至 DB，避免孤立的 providerId 留在 model_options
      await saveAiSettings(currentModel, cleanedProviderId, cleanedOptions, paramsPayload());
    } catch (e) {
      setProviderError(e instanceof Error ? e.message : "刪除失敗");
    }
  }

  /** 重新查詢 Provider 模型目錄並以後端回傳能力更新候選清單。 */
  async function handleRefreshProviderModels(id: number) {
    setSavingProvider(true);
    setProviderError(null);
    try {
      const refreshedOptions = await refreshAiProviderModels(id);
      setOptions(refreshedOptions);
      setActionMsg("模型目錄與能力已更新。");
    } catch (e) {
      setProviderError(e instanceof Error ? e.message : "模型目錄更新失敗");
    } finally {
      setSavingProvider(false);
    }
  }

  /** 切換人工模型能力；AUTO metadata 為唯讀，避免覆蓋 Provider 可信資料。 */
  async function toggleCapability(option: ModelOptionItem, capability: ModelCapability, enabled: boolean) {
    if (option.providerId == null || option.capabilitySource === "AUTO") return;
    setSaving(true);
    setSettingError(null);
    try {
      const nextCapabilities = enabled
        ? Array.from(new Set([...option.capabilities, capability]))
        : option.capabilities.filter((item) => item !== capability);
      const updated = await saveModelCapabilities(option.model, option.providerId, nextCapabilities);
      setOptions((current) => current.map((item) =>
        item.model === option.model && item.providerId === option.providerId ? updated : item));
      setActionMsg(`已更新 ${option.model} 的模型能力。`);
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "模型能力儲存失敗");
    } finally {
      setSaving(false);
    }
  }

  /** 儲存 OCR／Transcription assignment，並以目前 Chat model 一併送出完整契約。 */
  async function saveAssignments() {
    setSaving(true);
    setSettingError(null);
    try {
      const assignments = buildModelAssignments(options, {
        chatModel: currentModel,
        chatProviderId: currentProviderId,
        ocrKey: ocrModelKey,
        transcriptionKey: transcriptionModelKey,
      });
      const data = await saveAiModelAssignments(assignments);
      setSettings(data);
      setOptions(data.modelOptions);
      setOcrModelKey(modelPairKey(data.ocrModel, data.ocrProviderId));
      setTranscriptionModelKey(modelPairKey(data.transcriptionModel, data.transcriptionProviderId));
      setActionMsg("OCR 與語音轉錄模型已儲存。");
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : "用途模型儲存失敗");
    } finally {
      setSaving(false);
    }
  }

  /* ── 競速測試方法 ─────────────────────────────── */
  function startRace() {
    if (options.length === 0 || racing) return;
    const activeOptions = options.filter(o => raceModels.has(o.model));
    if (activeOptions.length === 0) return;
    setRacing(true);
    setScoreReport(null); // 清空上次評分

    const init: Record<string, ModelRaceResult> = {};
    activeOptions.forEach((opt) => {
      init[opt.model] = { status: "waiting", content: "", firstTokenMs: null, totalMs: null,
                  promptTokens: null, completionTokens: null, totalTokens: null,
                  reasoningTokens: null, visibleOutputTokens: null };
    });
    setRaceResults(init);
    startTimeRef.current = {};

    const sessionId = crypto.randomUUID();
    setCurrentSessionId(sessionId);

    let doneCount = 0;
    const total = activeOptions.length;

    activeOptions.forEach((opt) => {
      const t0 = performance.now();
      startTimeRef.current[opt.model] = t0;
      /** 此模型的串流內容累積（用於測試完成後儲存至後端）。 */
      let accContent = "";
      let accPromptTokens = 0;
      let accCompletionTokens = 0;
      let accTotalTokens = 0;

      streamModelTest(
        "", opt.model, opt.providerId ?? null,
        (chunk: any) => {
          if (chunk.type === "content" && chunk.delta) {
            accContent += chunk.delta;
            const now = performance.now();
            setRaceResults((prev) => {
              const cur = prev[opt.model] ?? {
                status: "streaming" as const, content: "", firstTokenMs: null, totalMs: null,
                promptTokens: null, completionTokens: null, totalTokens: null,
                reasoningTokens: null, visibleOutputTokens: null
              };
              return {
                ...prev,
                [opt.model]: {
                  ...cur,
                  status: "streaming",
                  content: cur.content + chunk.delta,
                  firstTokenMs: cur.firstTokenMs === null ? Math.round(now - t0) : cur.firstTokenMs,
                }
              };
            });
          } else if (chunk.type === "tokens") {
            accPromptTokens = chunk.promptTokens ?? 0;
            accCompletionTokens = chunk.completionTokens ?? 0;
            accTotalTokens = chunk.totalTokens ?? 0;
            setRaceResults((prev) => ({
              ...prev,
              [opt.model]: {
                ...prev[opt.model],
                promptTokens: chunk.promptTokens ?? null,
                completionTokens: chunk.completionTokens ?? null,
                totalTokens: chunk.totalTokens ?? null,
                reasoningTokens: chunk.reasoningTokens ?? null,
                visibleOutputTokens: chunk.visibleOutputTokens ?? null,
                finishReason: chunk.finishReason ?? undefined,
              }
            }));
          }
        },
        () => {
          const totalMs = Math.round(performance.now() - (startTimeRef.current[opt.model] ?? 0));
          setRaceResults((prev) => ({
            ...prev,
            [opt.model]: { ...prev[opt.model], status: "done", totalMs }
          }));
          // 非同步儲存測試結果至後端（fire-and-forget，不阻斷 UI）
          logModelTest({
            model: opt.model,
            sessionId,
            promptTokens: accPromptTokens,
            completionTokens: accCompletionTokens,
            totalTokens: accTotalTokens,
            answer: accContent,
          }).catch(err => console.warn("logModelTest 失敗：", err));
          doneCount++;
          if (doneCount >= total) setRacing(false);
        },
        (err: any) => {
          setRaceResults((prev) => ({
            ...prev,
            [opt.model]: { ...prev[opt.model], status: "error", errorMsg: err?.message ?? "連線失敗" }
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
      currentSessionId,
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

  /** 開啟指定模型的歷程 Modal（載入 MODEL_TEST 歷史記錄）。 */
  async function openModelHistory(model: string) {
    setModelHistoryState({ open: true, model, calls: [], loading: true });
    try {
      const calls = await fetchModelTestCalls(model);
      setModelHistoryState(prev => prev ? { ...prev, calls, loading: false } : null);
    } catch {
      setModelHistoryState(prev => prev ? { ...prev, calls: [], loading: false } : null);
    }
  }

  /* ── 渲染 ─────────────────────────────── */
  const envLabel = settings?.envDefaultModel || "未設定";
  const hasRaceResults = Object.keys(raceResults).length > 0;
  const allDone = hasRaceResults && Object.values(raceResults).every((r) => r.status === "done" || r.status === "error");
  const hasDoneResults = hasRaceResults && Object.values(raceResults).some((r) => r.status === "done");
  /** OCR 與語音轉錄下拉各自只保留後端已確認相容的模型。 */
  const visionOptions = filterModelsForCapability(options, "VISION");
  const transcriptionOptions = filterModelsForCapability(options, "AUDIO_TRANSCRIPTION");
  /** 能力被移除後保留的舊 assignment 不得再提交，須由 Admin 改選或清除。 */
  const hasInvalidAssignment =
    (ocrModelKey !== "" && !visionOptions.some((option) => modelOptionKey(option) === ocrModelKey))
    || (transcriptionModelKey !== "" && !transcriptionOptions.some((option) => modelOptionKey(option) === transcriptionModelKey));

  return (
    <div className="admin-settings-page" style={{ padding: "24px 28px", maxWidth: 900 }}>
      {/* 頁首 */}
      <h1 style={{ fontSize: 22, fontWeight: 700, color: "#122232", margin: "0 0 4px" }}>系統設定</h1>
      <p style={{ color: "#64748b", fontSize: 14, margin: "0 0 20px" }}>
        管理 AI 供應商與對話模型；點選清單項目選用，留空則回退環境變數預設，修改後儲存即時生效。
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
          {/* ── Provider 管理卡片 ── */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div className="settings-section-heading" style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>🔑 AI 供應商</span>
              <span style={{ fontSize: 12, color: "#94a3b8" }}>管理 API 金鑰與 Base URL，模型選項關聯至供應商</span>
            </div>

            {providerError && (
              <div style={{ background: "#fef2f2", border: "1px solid #fca5a5", borderRadius: 8,
                padding: "8px 12px", marginBottom: 10, color: "#b91c1c", fontSize: 13 }}>
                ⚠️ {providerError}
              </div>
            )}

            {/* 供應商清單 */}
            <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
              {providers.length === 0 && (
                <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>尚無供應商，請在下方新增。</p>
              )}
              {providers.map(p => (
                <div key={p.id} className="settings-responsive-row" style={{
                  display: "flex", alignItems: "center", justifyContent: "space-between",
                  padding: "10px 14px", background: "#f8fafc",
                  border: `1.5px solid ${editingProviderId === p.id ? "#6366f1" : "#e2e8f0"}`,
                  borderRadius: 8,
                }}>
                  <div className="settings-row-copy">
                    <span style={{ fontWeight: 600, fontSize: 14, color: "#122232", marginRight: 8 }}>{p.name}</span>
                    <span style={{ fontSize: 12, color: "#64748b" }}>{p.baseUrl || "預設 OpenAI URL"}</span>
                    <span style={{
                      marginLeft: 8, fontSize: 11, padding: "1px 6px", borderRadius: 4,
                      color: p.apiKeySet ? "#166534" : "#b91c1c",
                      background: p.apiKeySet ? "#dcfce7" : "#fee2e2",
                    }}>
                      {p.apiKeySet ? "🔐 金鑰已設定" : "⚠️ 未設定金鑰"}
                    </span>
                  </div>
                  <div className="settings-inline-actions" style={{ display: "flex", gap: 6 }}>
                    <button type="button" className="btn-secondary"
                      aria-label={`重新查詢 ${p.name} 模型`}
                      disabled={savingProvider}
                      style={{ fontSize: 12, padding: "3px 10px" }}
                      onClick={() => handleRefreshProviderModels(p.id)}>更新模型</button>
                    <button type="button" className="btn-secondary"
                      style={{ fontSize: 12, padding: "3px 10px" }}
                      onClick={() => startEditProvider(p)}>編輯</button>
                    <button type="button" className="btn-danger"
                      style={{ fontSize: 12, padding: "3px 10px" }}
                      onClick={() => handleDeleteProvider(p.id)}>刪除</button>
                  </div>
                </div>
              ))}
            </div>

            {/* 新增 / 編輯表單 */}
            <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "12px 14px" }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#475569", marginBottom: 10 }}>
                {editingProviderId !== null ? "✏️ 編輯供應商" : "➕ 新增供應商"}
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                <input
                  value={providerForm.name}
                  placeholder="供應商名稱，如 OpenAI、Anthropic"
                  onChange={e => setProviderForm(prev => ({ ...prev, name: e.target.value }))}
                  style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
                />
                <input
                  value={providerForm.baseUrl}
                  placeholder="Base URL（留空使用 OpenAI 預設：https://api.openai.com）"
                  onChange={e => setProviderForm(prev => ({ ...prev, baseUrl: e.target.value }))}
                  style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
                />
                <input
                  type="password"
                  value={providerForm.apiKey}
                  placeholder={editingProviderId !== null ? "API Key（留空保留現有金鑰）" : "API Key"}
                  onChange={e => setProviderForm(prev => ({ ...prev, apiKey: e.target.value }))}
                  style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
                />
                <div className="settings-inline-actions" style={{ display: "flex", gap: 8 }}>
                  <button type="button" className="btn-primary"
                    disabled={savingProvider || !providerForm.name.trim()}
                    onClick={saveProviderForm}
                    style={{ flex: 1, padding: "8px", fontWeight: 700 }}>
                    {savingProvider ? "儲存中…" : editingProviderId !== null ? "更新供應商" : "新增供應商"}
                  </button>
                  {editingProviderId !== null && (
                    <button type="button" className="btn-secondary"
                      onClick={() => {
                        setEditingProviderId(null);
                        setProviderForm({ name: "", baseUrl: "", apiKey: "" });
                        setProviderError(null);
                      }}
                      style={{ padding: "8px 16px" }}>取消</button>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* ── 模型設定卡片 ── */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div className="settings-section-heading" style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
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

            {/* 候選清單（顯示 provider badge） */}
            <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
              {options.length === 0 && (
                <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>尚無候選模型，請在下方輸入後新增。</p>
              )}
              {options.map((opt) => {
                const isSelected = isSameModelPair(opt, currentModel, currentProviderId);
                /** 從 providers 清單查找此模型對應的供應商名稱 */
                const providerName = providers.find(p => p.id === opt.providerId)?.name;
                return (
                  <div
                    className="settings-model-row"
                    key={`${opt.model}-${opt.providerId ?? "none"}`}
                    data-model-name={opt.model}
                    data-provider-id={opt.providerId ?? ""}
                    onClick={() => { if (!saving) selectModel(opt.model, opt.providerId ?? null); }}
                    style={{
                      display: "flex", alignItems: "center", justifyContent: "space-between",
                      padding: "10px 14px",
                      background: isSelected ? "#f0fdf4" : "#f8fafc",
                      border: `1.5px solid ${isSelected ? "#4ade80" : "#e2e8f0"}`,
                      borderRadius: 8, cursor: saving ? "not-allowed" : "pointer",
                      opacity: saving ? 0.7 : 1,
                      transition: "border-color 0.15s, background 0.15s",
                    }}
                  >
                    <div className="settings-model-copy" style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      {/* checkbox 控制是否加入競速，stopPropagation 避免觸發 selectModel */}
                      <input
                        type="checkbox"
                        checked={raceModels.has(opt.model)}
                        onChange={e => {
                          e.stopPropagation();
                          setRaceModels(prev => {
                            const s = new Set(prev);
                            e.target.checked ? s.add(opt.model) : s.delete(opt.model);
                            return s;
                          });
                        }}
                        onClick={e => e.stopPropagation()}
                        style={{ width: 16, height: 16, cursor: "pointer", flexShrink: 0 }}
                        title="勾選加入競速比較"
                      />
                      <span style={{ fontFamily: "monospace", fontSize: 14, color: "#122232" }}>{opt.model}</span>
                      {isSelected && (
                        <span style={{ fontSize: 11, color: "#166534", background: "#dcfce7", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>使用中</span>
                      )}
                      {/* 供應商 badge */}
                      {providerName && (
                        <span style={{ fontSize: 11, color: "#6366f1", background: "#ede9fe",
                          padding: "1px 6px", borderRadius: 4 }}>
                          {providerName}
                        </span>
                      )}
                      {hasCapability(opt, "VISION") && (
                        <span title="支援圖片輸入" aria-label="Vision capability">👁</span>
                      )}
                      {hasCapability(opt, "AUDIO_TRANSCRIPTION") && (
                        <span title="支援語音轉錄" aria-label="Audio transcription capability">👂</span>
                      )}
                      <span data-testid={`capability-source-${opt.model}`} style={{
                        fontSize: 10, color: opt.capabilitySource === "UNKNOWN" ? "#b45309" : "#475569",
                        background: opt.capabilitySource === "UNKNOWN" ? "#fef3c7" : "#e2e8f0",
                        padding: "1px 6px", borderRadius: 4,
                      }}>{opt.capabilitySource}</span>
                    </div>
                    {canEditCapabilities(opt) && (
                      <div className="settings-capability-actions" style={{ display: "flex", alignItems: "center", gap: 10, marginLeft: "auto", marginRight: 10 }}>
                        <label style={{ fontSize: 11, color: "#475569" }}>
                          <input type="checkbox"
                            aria-label={`${opt.model} Vision`}
                            checked={hasCapability(opt, "VISION")}
                            disabled={saving}
                            onClick={(event) => event.stopPropagation()}
                            onChange={(event) => {
                              event.stopPropagation();
                              void toggleCapability(opt, "VISION", event.target.checked);
                            }} /> Vision
                        </label>
                        <label style={{ fontSize: 11, color: "#475569" }}>
                          <input type="checkbox"
                            aria-label={`${opt.model} Audio transcription`}
                            checked={hasCapability(opt, "AUDIO_TRANSCRIPTION")}
                            disabled={saving}
                            onClick={(event) => event.stopPropagation()}
                            onChange={(event) => {
                              event.stopPropagation();
                              void toggleCapability(opt, "AUDIO_TRANSCRIPTION", event.target.checked);
                            }} /> Audio
                        </label>
                      </div>
                    )}
                    <button
                      type="button"
                      className="btn-danger"
                      style={{ padding: "3px 10px", fontSize: 12 }}
                      onClick={(e) => { e.stopPropagation(); removeModel(opt); }}
                    >
                      刪除
                    </button>
                  </div>
                );
              })}
            </div>

            {/* 新增輸入列（含供應商下拉） */}
            <div className="settings-add-model-row" style={{ display: "flex", gap: 8, paddingTop: 12, borderTop: "1px solid #f1f5f9", marginBottom: 16, flexWrap: "wrap" }}>
              <select
                className="settings-fluid-select"
                value={newModelProviderId ?? ""}
                onChange={e => setNewModelProviderId(e.target.value ? Number(e.target.value) : null)}
                style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none", minWidth: 140 }}
              >
                <option value="">選擇供應商</option>
                {providers.map(p => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
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

            {/* OCR／語音轉錄用途模型只顯示已確認相容的候選。 */}
            <div data-testid="model-assignments" style={{ paddingTop: 12, borderTop: "1px solid #f1f5f9", marginBottom: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#122232", marginBottom: 8 }}>AI 用途模型</div>
              <div className="settings-form-row" style={{ display: "flex", gap: 10, alignItems: "flex-end", flexWrap: "wrap" }}>
                <label className="settings-form-field" style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 12, color: "#64748b" }}>
                  OCR（僅 👁 Vision）
                  <select className="settings-fluid-select" data-testid="ocr-model-select" value={ocrModelKey} onChange={(event) => setOcrModelKey(event.target.value)}
                    style={{ minWidth: 220, padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8 }}>
                    <option value="">未設定</option>
                    {visionOptions.map((option) => {
                      const providerName = providers.find((provider) => provider.id === option.providerId)?.name;
                      return <option key={modelOptionKey(option)} value={modelOptionKey(option)}>
                        {option.model}{providerName ? ` — ${providerName}` : ""}
                      </option>;
                    })}
                  </select>
                </label>
                <label className="settings-form-field" style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 12, color: "#64748b" }}>
                  Transcription（僅 👂 Audio）
                  <select className="settings-fluid-select" data-testid="transcription-model-select" value={transcriptionModelKey}
                    onChange={(event) => setTranscriptionModelKey(event.target.value)}
                    style={{ minWidth: 220, padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8 }}>
                    <option value="">未設定</option>
                    {transcriptionOptions.map((option) => {
                      const providerName = providers.find((provider) => provider.id === option.providerId)?.name;
                      return <option key={modelOptionKey(option)} value={modelOptionKey(option)}>
                        {option.model}{providerName ? ` — ${providerName}` : ""}
                      </option>;
                    })}
                  </select>
                </label>
                <button type="button" className="btn-assess" data-testid="save-model-assignments"
                  disabled={saving || hasInvalidAssignment} onClick={saveAssignments} style={{ padding: "9px 18px" }}>
                  儲存用途模型
                </button>
              </div>
              {hasInvalidAssignment && (
                <p role="alert" style={{ color: "#b91c1c", fontSize: 12, margin: "8px 0 0" }}>
                  已指派模型不再具備所需能力，請先清除或改選相容模型。
                </p>
              )}
            </div>

            {/* 模型參數（留空＝用預設；套用於 AI 呼叫與模型測試） */}
            <div style={{ paddingTop: 12, borderTop: "1px solid #f1f5f9" }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#122232", marginBottom: 8 }}>
                模型參數 <span style={{ fontSize: 11, color: "#94a3b8", fontWeight: 400 }}>（留空＝用預設；套用於 AI 呼叫與模型測試）</span>
              </div>
              <div className="settings-form-row" style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
                <label style={{ fontSize: 12, color: "#64748b", display: "flex", flexDirection: "column", gap: 4 }}>
                  Temperature (0~2)
                  <input type="number" step="0.1" min="0" max="2" value={temperature} placeholder="預設"
                    onChange={e => setTemperature(e.target.value)}
                    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, width: 110 }} />
                </label>
                <label style={{ fontSize: 12, color: "#64748b", display: "flex", flexDirection: "column", gap: 4 }}>
                  Max Completion Tokens
                  <input type="number" min="1" value={maxCompletionTokens} placeholder="如 8000"
                    onChange={e => setMaxCompletionTokens(e.target.value)}
                    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, width: 150 }} />
                </label>
                <label style={{ fontSize: 12, color: "#64748b", display: "flex", flexDirection: "column", gap: 4 }}>
                  Reasoning Effort（推理模型）
                  <select value={reasoningEffort} onChange={e => setReasoningEffort(e.target.value)}
                    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, minWidth: 120 }}>
                    <option value="">預設</option>
                    <option value="minimal">minimal</option>
                    <option value="low">low</option>
                    <option value="medium">medium</option>
                    <option value="high">high</option>
                  </select>
                </label>
                <button type="button" className="btn-assess" disabled={saving} onClick={save}
                  style={{ padding: "9px 18px", borderRadius: 8, fontWeight: 700 }}>
                  {saving ? "儲存中…" : "儲存參數"}
                </button>
              </div>
              <p style={{ fontSize: 11, color: "#94a3b8", marginTop: 8, marginBottom: 0 }}>
                提示：推理型模型（gpt-5 系）reasoning_effort 設 low/minimal 可大幅減少思考 token 與延遲；max tokens 太低會導致空輸出。
              </p>
            </div>

          </div>

          {/* ── 多模型競速測試卡片 ── */}
          <div className="panel">
            <div className="settings-section-heading" style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>🏁 多模型競速測試</span>
              <span style={{ fontSize: 12, color: "#94a3b8" }}>同時呼叫所有候選模型，比較回應速度與品質</span>
            </div>

            {options.length === 0 ? (
              <p style={{ fontSize: 13, color: "#94a3b8" }}>請先在上方新增候選模型才能執行測試。</p>
            ) : (
              <>
                {/* 固定任務說明 + 啟動按鈕 */}
                <div className="settings-responsive-row" style={{
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
                    <div className="settings-race-grid" style={{
                      display: "grid",
                      gridTemplateColumns: `repeat(${Math.min(options.length, 2)}, 1fr)`,
                      gap: 12,
                      marginBottom: 16
                    }}>
                      {options.filter(o => raceResults[o.model] !== undefined).map((opt) => {
                        const r = raceResults[opt.model];
                        if (!r) return null;
                        const statusColor = { idle: "#94a3b8", waiting: "#f59e0b", streaming: "#3b82f6", done: "#16a34a", error: "#dc2626" }[r.status];
                        const statusLabel = { idle: "–", waiting: "等待中", streaming: "生成中", done: "完成", error: "失敗" }[r.status];
                        /** 競速結果卡片的供應商名稱 */
                        const providerName = providers.find(p => p.id === opt.providerId)?.name;

                        return (
                          <div key={`${opt.model}-${opt.providerId ?? "none"}`} style={{ border: `1.5px solid ${r.status === "done" ? "#86efac" : r.status === "error" ? "#fca5a5" : "#e2e8f0"}`, borderRadius: 10, overflow: "hidden" }}>
                            {/* 模型標頭 */}
                            <div style={{ padding: "8px 12px", background: r.status === "done" ? "#f0fdf4" : r.status === "error" ? "#fef2f2" : "#f8fafc", borderBottom: "1px solid #f1f5f9" }}>
                              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 3 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                                  <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 600, color: "#122232" }}>{opt.model}</span>
                                  {/* 競速卡片供應商 badge */}
                                  {providerName && (
                                    <span style={{ fontSize: 10, color: "#6366f1", background: "#ede9fe",
                                      padding: "1px 5px", borderRadius: 3, marginLeft: 4 }}>
                                      {providerName}
                                    </span>
                                  )}
                                </div>
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
                                  <span style={{ fontSize: 11, color: "#8b5cf6" }}>
                                    🔢 {r.promptTokens ?? "–"} in / {r.completionTokens ?? "–"} out
                                    {r.reasoningTokens ? `（可見 ${r.visibleOutputTokens ?? "–"} + 推理 ${r.reasoningTokens}）` : ""}
                                  </span>
                                )}
                                {/* 只在真正異常時標示：LENGTH=被 max tokens 截斷、CONTENT_FILTER=內容過濾；
                                    _UNKNOWN/STOP 等（gateway 未回標準 finish_reason）有內容即正常，不標示避免誤解 */}
                                {r.finishReason && /LENGTH|CONTENT_FILTER/i.test(r.finishReason) && (
                                  <span style={{ fontSize: 11, color: "#d97706" }} title="回應因此原因結束">⚑ {r.finishReason}</span>
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
                              <div style={{ padding: "6px 12px", borderTop: "1px solid #f1f5f9",
                                display: "flex", justifyContent: "flex-end", gap: 6 }}>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ fontSize: 12, padding: "3px 10px" }}
                                  onClick={() => openModelHistory(opt.model)}
                                >
                                  🕘 歷程
                                </button>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ fontSize: 12, padding: "3px 10px" }}
                                  onClick={() => downloadMarkdown(`${opt.model}-回答`, r.content)}
                                >
                                  ⬇ 下載 MD
                                </button>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>

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

                {/* 評分相關按鈕列（永遠顯示，條件控制 disabled） */}
                {options.length > 0 && (
                  <div style={{ display: "flex", gap: 8, justifyContent: "flex-end",
                    paddingTop: 8, borderTop: "1px solid #f1f5f9", flexWrap: "wrap", marginTop: 8 }}>
                    <button type="button" className="btn-secondary" onClick={openScoreHistory}>📊 評分歷程</button>
                    <button
                      type="button"
                      className="btn-secondary"
                      disabled={!hasDoneResults}
                      onClick={() => {
                        const ts = new Date().toISOString().slice(0, 16).replace("T", "_").replace(":", "-");
                        const files: Record<string, string> = {};
                        Object.entries(raceResults).forEach(([model, r]) => {
                          if (r.content) {
                            const safeName = model.replace(/[/\\:*?"<>|]/g, "_");
                            files[`${safeName}.md`] = r.content;
                          }
                        });
                        if (scoreReport?.markdown) files["00_評分報告.md"] = scoreReport.markdown;
                        downloadZip(`競速測試_${ts}`, files);
                      }}
                    >
                      📦 下載全部 ZIP
                    </button>
                    <button
                      type="button"
                      className="btn-assess"
                      disabled={scoring || !allDone || !hasDoneResults}
                      onClick={startScore}
                      style={{ fontWeight: 700, padding: "9px 20px", borderRadius: 8 }}
                    >
                      {scoring ? "評分中…" : "🏆 claude-opus-4-8 評分"}
                    </button>
                  </div>
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
          onClose={() => setScoreReport((prev) => prev ? { ...prev, open: false } : null)}
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

      {/* 個別模型歷程 Modal */}
      {modelHistoryState?.open && (
        <AiCallHistoryModal
          title={`${modelHistoryState.model} 測試歷程`}
          calls={modelHistoryState.calls}
          loading={modelHistoryState.loading}
          onClose={() => setModelHistoryState(null)}
        />
      )}
    </div>
  );
}
