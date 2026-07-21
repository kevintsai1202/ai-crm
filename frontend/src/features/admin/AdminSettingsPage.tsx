import { useEffect, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../context/AuthContext";
import {
  fetchAiSettings, saveAiSettings, streamModelTest,
  streamModelScore, fetchModelScoreCalls,
  createAiProvider, updateAiProvider, deleteAiProvider,
  refreshAiProviderModels, saveModelCapabilities, saveAiModelAssignments,
  testAiPurposeModel, logModelTest, fetchModelTestCalls,
} from "../../api/index";
import type {
  AiSettingsResponse, AiCallHistoryItem, ModelResultItem,
  AiProviderItem, ModelOptionItem, ModelCapability, AiPurposeModelTestResponse,
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
  purposeDeploymentDefaultLabel,
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

/**
 * 系統設定頁（限 ADMIN）：AI 供應商管理 + 模型設定 + 多模型競速測試。
 */
export default function AdminSettingsPage() {
  const { user } = useAuth();
  const { t } = useTranslation("operations");

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
  /** 用途模型測試檔案只保留在瀏覽器記憶體，不會由設定頁另行保存。 */
  const [purposeTestFiles, setPurposeTestFiles] = useState<{ ocr: File | null; transcription: File | null }>({
    ocr: null,
    transcription: null,
  });
  const [purposeTesting, setPurposeTesting] = useState<"ocr" | "transcription" | null>(null);
  const [purposeTestResults, setPurposeTestResults] = useState<Partial<Record<"ocr" | "transcription", AiPurposeModelTestResponse>>>({});

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
      setSettingError(e instanceof Error ? e.message : t("adminSettings.errors.load"));
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
        ? t("adminSettings.messages.defaultSet", { model: data.currentModel })
        : t("adminSettings.messages.chatCleared");
      setActionMsg(msg);
      setTimeout(() => setActionMsg(null), 3000);
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : t("adminSettings.errors.save"));
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
      setActionMsg(t("adminSettings.messages.saved"));
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : t("adminSettings.errors.save"));
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
      setProviderError(e instanceof Error ? e.message : t("adminSettings.errors.providerSave"));
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
    if (!window.confirm(t("adminSettings.confirmDeleteProvider"))) return;
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
      setProviderError(e instanceof Error ? e.message : t("adminSettings.errors.providerDelete"));
    }
  }

  /** 重新查詢 Provider 模型目錄並以後端回傳能力更新候選清單。 */
  async function handleRefreshProviderModels(id: number) {
    setSavingProvider(true);
    setProviderError(null);
    try {
      const refreshedOptions = await refreshAiProviderModels(id);
      setOptions(refreshedOptions);
      setActionMsg(t("adminSettings.messages.catalogUpdated"));
    } catch (e) {
      setProviderError(e instanceof Error ? e.message : t("adminSettings.errors.catalogRefresh"));
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
      setActionMsg(t("adminSettings.messages.capabilityUpdated", { model: option.model }));
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : t("adminSettings.errors.capabilitySave"));
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
      setActionMsg(t("adminSettings.messages.assignmentsSaved"));
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : t("adminSettings.errors.assignmentSave"));
    } finally {
      setSaving(false);
    }
  }

  /** 以上傳實檔呼叫目前已儲存且實際生效的 OCR／語音轉錄模型。 */
  async function runPurposeModelTest(purpose: "ocr" | "transcription") {
    const file = purposeTestFiles[purpose];
    if (!file) {
      setSettingError(t(purpose === "ocr" ? "adminSettings.errors.selectCard" : "adminSettings.errors.selectAudio"));
      return;
    }
    setPurposeTesting(purpose);
    setSettingError(null);
    setActionMsg(null);
    try {
      const result = await testAiPurposeModel(purpose, file);
      setPurposeTestResults((current) => ({ ...current, [purpose]: result }));
      const purposeName = t(purpose === "ocr" ? "adminSettings.purposes.ocrName" : "adminSettings.purposes.transcriptionName");
      setActionMsg(t("adminSettings.messages.testSuccess", { purpose: purposeName, model: result.model, latency: result.latencyMs }));
    } catch (e) {
      setSettingError(e instanceof Error ? e.message : t("adminSettings.errors.purposeTest"));
    } finally {
      setPurposeTesting(null);
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
            [opt.model]: { ...prev[opt.model], status: "error", errorMsg: err?.message ?? t("adminSettings.errors.connection") }
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
          ? { ...prev, loading: false, streaming: false, markdown: t("adminSettings.errors.score") }
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
  const envLabel = settings?.envDefaultModel || t("adminSettings.unset");
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
      <h1 style={{ fontSize: 22, fontWeight: 700, color: "#122232", margin: "0 0 4px" }}>{t("adminSettings.title")}</h1>
      <p style={{ color: "#64748b", fontSize: 14, margin: "0 0 20px" }}>
        {t("adminSettings.intro")}
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
        <div className="panel" style={{ color: "#64748b", fontSize: 14 }}>{t("adminSettings.loading")}</div>
      ) : (
        <>
          {/* ── Provider 管理卡片 ── */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div className="settings-section-heading" style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>🔑 {t("adminSettings.providers.title")}</span>
              <span style={{ fontSize: 12, color: "#94a3b8" }}>{t("adminSettings.providers.subtitle")}</span>
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
                <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>{t("adminSettings.providers.empty")}</p>
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
                    <span style={{ fontSize: 12, color: "#64748b" }}>{p.baseUrl || t("adminSettings.providers.defaultUrl")}</span>
                    <span style={{
                      marginLeft: 8, fontSize: 11, padding: "1px 6px", borderRadius: 4,
                      color: p.apiKeySet ? "#166534" : "#b91c1c",
                      background: p.apiKeySet ? "#dcfce7" : "#fee2e2",
                    }}>
                      {p.apiKeySet ? `🔐 ${t("adminSettings.providers.keySet")}` : `⚠️ ${t("adminSettings.providers.keyMissing")}`}
                    </span>
                  </div>
                  <div className="settings-inline-actions" style={{ display: "flex", gap: 6 }}>
                    <button type="button" className="btn-secondary"
                      aria-label={t("adminSettings.providers.refreshAria", { provider: p.name })}
                      disabled={savingProvider}
                      style={{ fontSize: 12, padding: "3px 10px" }}
                      onClick={() => handleRefreshProviderModels(p.id)}>{t("adminSettings.providers.refresh")}</button>
                    <button type="button" className="btn-secondary"
                      style={{ fontSize: 12, padding: "3px 10px" }}
                      onClick={() => startEditProvider(p)}>{t("adminSettings.providers.edit")}</button>
                    <button type="button" className="btn-danger"
                      style={{ fontSize: 12, padding: "3px 10px" }}
                      onClick={() => handleDeleteProvider(p.id)}>{t("adminSettings.providers.delete")}</button>
                  </div>
                </div>
              ))}
            </div>

            {/* 新增 / 編輯表單 */}
            <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "12px 14px" }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#475569", marginBottom: 10 }}>
                {editingProviderId !== null ? `✏️ ${t("adminSettings.providers.editTitle")}` : `➕ ${t("adminSettings.providers.addTitle")}`}
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                <input
                  value={providerForm.name}
                  placeholder={t("adminSettings.providers.namePlaceholder")}
                  onChange={e => setProviderForm(prev => ({ ...prev, name: e.target.value }))}
                  style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
                />
                <input
                  value={providerForm.baseUrl}
                  placeholder={t("adminSettings.providers.urlPlaceholder")}
                  onChange={e => setProviderForm(prev => ({ ...prev, baseUrl: e.target.value }))}
                  style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
                />
                <input
                  type="password"
                  value={providerForm.apiKey}
                  placeholder={editingProviderId !== null ? t("adminSettings.providers.keyEditPlaceholder") : t("adminSettings.providers.keyAddPlaceholder")}
                  onChange={e => setProviderForm(prev => ({ ...prev, apiKey: e.target.value }))}
                  style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
                />
                <div className="settings-inline-actions" style={{ display: "flex", gap: 8 }}>
                  <button type="button" className="btn-primary"
                    disabled={savingProvider || !providerForm.name.trim()}
                    onClick={saveProviderForm}
                    style={{ flex: 1, padding: "8px", fontWeight: 700 }}>
                    {savingProvider ? t("adminSettings.providers.saving") : editingProviderId !== null ? t("adminSettings.providers.update") : t("adminSettings.providers.add")}
                  </button>
                  {editingProviderId !== null && (
                    <button type="button" className="btn-secondary"
                      onClick={() => {
                        setEditingProviderId(null);
                        setProviderForm({ name: "", baseUrl: "", apiKey: "" });
                        setProviderError(null);
                      }}
                      style={{ padding: "8px 16px" }}>{t("adminSettings.providers.cancel")}</button>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* ── 模型設定卡片 ── */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div className="settings-section-heading" style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>{t("adminSettings.models.title")}</span>
              {currentModel
                ? <span style={{ background: "#dcfce7", color: "#166534", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>{t("adminSettings.models.system")}</span>
                : <span style={{ background: "#f1f5f9", color: "#475569", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>{t("adminSettings.models.env")}</span>
              }
            </div>

            {!currentModel && (
              <div style={{ padding: "10px 12px", background: "#f8fafc", border: "1px dashed #cbd5e1", borderRadius: 8, fontSize: 13, color: "#64748b", marginBottom: 12 }}>
                {t("adminSettings.models.envHint")}
                <code style={{ background: "#e2e8f0", padding: "1px 6px", borderRadius: 4, marginLeft: 4 }}>{envLabel}</code>
              </div>
            )}

            {/* 候選清單（顯示 provider badge） */}
            <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
              {options.length === 0 && (
                <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>{t("adminSettings.models.empty")}</p>
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
                        title={t("adminSettings.models.raceToggle")}
                      />
                      <span style={{ fontFamily: "monospace", fontSize: 14, color: "#122232" }}>{opt.model}</span>
                      {isSelected && (
                        <span style={{ fontSize: 11, color: "#166534", background: "#dcfce7", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>{t("adminSettings.models.active")}</span>
                      )}
                      {/* 供應商 badge */}
                      {providerName && (
                        <span style={{ fontSize: 11, color: "#6366f1", background: "#ede9fe",
                          padding: "1px 6px", borderRadius: 4 }}>
                          {providerName}
                        </span>
                      )}
                      {hasCapability(opt, "VISION") && (
                        <span title={t("adminSettings.models.visionTitle")} aria-label="Vision capability">👁</span>
                      )}
                      {hasCapability(opt, "AUDIO_TRANSCRIPTION") && (
                        <span title={t("adminSettings.models.audioTitle")} aria-label="Audio transcription capability">👂</span>
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
                      {t("adminSettings.models.delete")}
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
                <option value="">{t("adminSettings.models.chooseProvider")}</option>
                {providers.map(p => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
              <input
                value={newModel}
                placeholder={t("adminSettings.models.namePlaceholder")}
                onChange={(e) => setNewModel(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addModel(); } }}
                style={{ flex: 1, padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
              />
              <button type="button" className="btn-secondary" style={{ whiteSpace: "nowrap", padding: "8px 16px" }} onClick={addModel}>
                + {t("adminSettings.models.add")}
              </button>
            </div>

            {/* 三個用途共用模型目錄，但各自顯示獨立指派與部署預設。 */}
            <div data-testid="model-assignments" style={{ paddingTop: 12, borderTop: "1px solid #f1f5f9", marginBottom: 16 }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, marginBottom: 10 }}>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 700, color: "#122232" }}>{t("adminSettings.purposes.title")}</div>
                  <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>{t("adminSettings.purposes.hint")}</div>
                </div>
                <button type="button" className="btn-assess" data-testid="save-model-assignments"
                  disabled={saving || hasInvalidAssignment} onClick={saveAssignments} style={{ padding: "9px 18px", whiteSpace: "nowrap" }}>
                  {saving ? t("adminSettings.purposes.saving") : t("adminSettings.purposes.save")}
                </button>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 10 }}>
                <section data-testid="chat-purpose-card" style={{ padding: 12, border: "1px solid #dbeafe", borderRadius: 10, background: "#f8fbff" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8, marginBottom: 8 }}>
                    <strong style={{ color: "#122232", fontSize: 13 }}>💬 {t("adminSettings.purposes.chat")}</strong>
                    <span style={{ fontSize: 10, color: currentModel ? "#166534" : "#475569", background: currentModel ? "#dcfce7" : "#e2e8f0", padding: "2px 6px", borderRadius: 4 }}>
                      {currentModel ? t("adminSettings.purposes.admin") : t("adminSettings.purposes.deployment")}
                    </span>
                  </div>
                  <div style={{ fontFamily: "monospace", fontSize: 12, color: "#334155", overflowWrap: "anywhere" }}>
                    {currentModel || envLabel}
                  </div>
                  <p style={{ fontSize: 11, color: "#64748b", margin: "8px 0 0" }}>{t("adminSettings.purposes.chatHint")}</p>
                </section>

                <section data-testid="ocr-purpose-card" style={{ padding: 12, border: "1px solid #d1fae5", borderRadius: 10, background: "#f8fffb" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8, marginBottom: 8 }}>
                    <strong style={{ color: "#122232", fontSize: 13 }}>🪪 {t("adminSettings.purposes.ocrName")}</strong>
                    <span style={{ fontSize: 10, color: settings?.ocrSource === "UNSET" ? "#b45309" : "#166534", background: settings?.ocrSource === "UNSET" ? "#fef3c7" : "#dcfce7", padding: "2px 6px", borderRadius: 4 }}>
                      {settings?.ocrSource === "DB" ? t("adminSettings.purposes.admin") : settings?.ocrSource === "ENV" ? t("adminSettings.purposes.deployment") : t("adminSettings.purposes.unset")}
                    </span>
                  </div>
                  <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 11, color: "#64748b" }}>
                    {t("adminSettings.purposes.visionModel")}
                    <select className="settings-fluid-select" data-testid="ocr-model-select" value={ocrModelKey} onChange={(event) => setOcrModelKey(event.target.value)}
                      style={{ width: "100%", padding: "8px 10px", border: "1px solid #d1e0db", borderRadius: 8 }}>
                      <option value="">{settings?.envDefaultOcrProviderName && settings.envDefaultOcrModel ? t("adminSettings.purposes.useDefault") : t("adminSettings.purposes.unset")}</option>
                      {visionOptions.map((option) => {
                        const providerName = providers.find((provider) => provider.id === option.providerId)?.name;
                        return <option key={modelOptionKey(option)} value={modelOptionKey(option)}>
                          {option.model}{providerName ? ` — ${providerName}` : ""}
                        </option>;
                      })}
                    </select>
                  </label>
                  <div style={{ fontSize: 10, color: "#64748b", marginTop: 6, overflowWrap: "anywhere" }}>
                    {t("adminSettings.purposes.defaultPrefix")}{purposeDeploymentDefaultLabel(settings, "ocr", t("adminSettings.purposes.unset"))}
                  </div>
                  <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 11, color: "#64748b", marginTop: 10 }}>
                    {t("adminSettings.purposes.cardFile")}
                    <input data-testid="ocr-test-file" type="file" accept="image/jpeg,image/png,image/webp"
                      onChange={(event) => setPurposeTestFiles((current) => ({ ...current, ocr: event.target.files?.[0] ?? null }))} />
                  </label>
                  <button type="button" className="btn-secondary" data-testid="ocr-test-button"
                    disabled={!purposeTestFiles.ocr || purposeTesting !== null}
                    onClick={() => void runPurposeModelTest("ocr")}
                    style={{ width: "100%", marginTop: 8, padding: "7px 10px" }}>
                    {purposeTesting === "ocr" ? t("adminSettings.purposes.testing") : t("adminSettings.purposes.testEffective")}
                  </button>
                  {purposeTestResults.ocr && <p style={{ fontSize: 11, color: "#166534", margin: "7px 0 0" }}>
                    ✓ {purposeTestResults.ocr.summary}（{purposeTestResults.ocr.latencyMs} ms）
                  </p>}
                </section>

                <section data-testid="transcription-purpose-card" style={{ padding: 12, border: "1px solid #ede9fe", borderRadius: 10, background: "#fbfaff" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8, marginBottom: 8 }}>
                    <strong style={{ color: "#122232", fontSize: 13 }}>🎙️ {t("adminSettings.purposes.transcriptionName")}</strong>
                    <span style={{ fontSize: 10, color: settings?.transcriptionSource === "UNSET" ? "#b45309" : "#166534", background: settings?.transcriptionSource === "UNSET" ? "#fef3c7" : "#dcfce7", padding: "2px 6px", borderRadius: 4 }}>
                      {settings?.transcriptionSource === "DB" ? t("adminSettings.purposes.admin") : settings?.transcriptionSource === "ENV" ? t("adminSettings.purposes.deployment") : t("adminSettings.purposes.unset")}
                    </span>
                  </div>
                  <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 11, color: "#64748b" }}>
                    {t("adminSettings.purposes.audioModel")}
                    <select className="settings-fluid-select" data-testid="transcription-model-select" value={transcriptionModelKey}
                      onChange={(event) => setTranscriptionModelKey(event.target.value)}
                      style={{ width: "100%", padding: "8px 10px", border: "1px solid #d1e0db", borderRadius: 8 }}>
                      <option value="">{settings?.envDefaultTranscriptionProviderName && settings.envDefaultTranscriptionModel ? t("adminSettings.purposes.useDefault") : t("adminSettings.purposes.unset")}</option>
                      {transcriptionOptions.map((option) => {
                        const providerName = providers.find((provider) => provider.id === option.providerId)?.name;
                        return <option key={modelOptionKey(option)} value={modelOptionKey(option)}>
                          {option.model}{providerName ? ` — ${providerName}` : ""}
                        </option>;
                      })}
                    </select>
                  </label>
                  <div style={{ fontSize: 10, color: "#64748b", marginTop: 6, overflowWrap: "anywhere" }}>
                    {t("adminSettings.purposes.defaultPrefix")}{purposeDeploymentDefaultLabel(settings, "transcription", t("adminSettings.purposes.unset"))}
                  </div>
                  <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 11, color: "#64748b", marginTop: 10 }}>
                    {t("adminSettings.purposes.audioFile")}
                    <input data-testid="transcription-test-file" type="file" accept="audio/mpeg,audio/mp4,audio/wav,.mp3,.m4a,.wav"
                      onChange={(event) => setPurposeTestFiles((current) => ({ ...current, transcription: event.target.files?.[0] ?? null }))} />
                  </label>
                  <button type="button" className="btn-secondary" data-testid="transcription-test-button"
                    disabled={!purposeTestFiles.transcription || purposeTesting !== null}
                    onClick={() => void runPurposeModelTest("transcription")}
                    style={{ width: "100%", marginTop: 8, padding: "7px 10px" }}>
                    {purposeTesting === "transcription" ? t("adminSettings.purposes.testing") : t("adminSettings.purposes.testEffective")}
                  </button>
                  {purposeTestResults.transcription && <p style={{ fontSize: 11, color: "#166534", margin: "7px 0 0" }}>
                    ✓ {purposeTestResults.transcription.summary}（{purposeTestResults.transcription.latencyMs} ms）
                  </p>}
                </section>
              </div>
              {hasInvalidAssignment && (
                <p role="alert" style={{ color: "#b91c1c", fontSize: 12, margin: "8px 0 0" }}>
                  {t("adminSettings.purposes.invalid")}
                </p>
              )}
            </div>

            {/* 模型參數（留空＝用預設；套用於 AI 呼叫與模型測試） */}
            <div style={{ paddingTop: 12, borderTop: "1px solid #f1f5f9" }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#122232", marginBottom: 8 }}>
                {t("adminSettings.params.title")} <span style={{ fontSize: 11, color: "#94a3b8", fontWeight: 400 }}>{t("adminSettings.params.hint")}</span>
              </div>
              <div className="settings-form-row" style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
                <label style={{ fontSize: 12, color: "#64748b", display: "flex", flexDirection: "column", gap: 4 }}>
                  Temperature (0~2)
                  <input type="number" step="0.1" min="0" max="2" value={temperature} placeholder={t("adminSettings.params.default")}
                    onChange={e => setTemperature(e.target.value)}
                    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, width: 110 }} />
                </label>
                <label style={{ fontSize: 12, color: "#64748b", display: "flex", flexDirection: "column", gap: 4 }}>
                  Max Completion Tokens
                  <input type="number" min="1" value={maxCompletionTokens} placeholder={t("adminSettings.params.tokensPlaceholder")}
                    onChange={e => setMaxCompletionTokens(e.target.value)}
                    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, width: 150 }} />
                </label>
                <label style={{ fontSize: 12, color: "#64748b", display: "flex", flexDirection: "column", gap: 4 }}>
                  {t("adminSettings.params.reasoning")}
                  <select value={reasoningEffort} onChange={e => setReasoningEffort(e.target.value)}
                    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, minWidth: 120 }}>
                    <option value="">{t("adminSettings.params.default")}</option>
                    <option value="minimal">minimal</option>
                    <option value="low">low</option>
                    <option value="medium">medium</option>
                    <option value="high">high</option>
                  </select>
                </label>
                <button type="button" className="btn-assess" disabled={saving} onClick={save}
                  style={{ padding: "9px 18px", borderRadius: 8, fontWeight: 700 }}>
                  {saving ? t("adminSettings.params.saving") : t("adminSettings.params.save")}
                </button>
              </div>
              <p style={{ fontSize: 11, color: "#94a3b8", marginTop: 8, marginBottom: 0 }}>
                {t("adminSettings.params.tip")}
              </p>
            </div>

          </div>

          {/* ── 多模型競速測試卡片 ── */}
          <div className="panel">
            <div className="settings-section-heading" style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>🏁 {t("adminSettings.race.title")}</span>
              <span style={{ fontSize: 12, color: "#94a3b8" }}>{t("adminSettings.race.subtitle")}</span>
            </div>

            {options.length === 0 ? (
              <p style={{ fontSize: 13, color: "#94a3b8" }}>{t("adminSettings.race.empty")}</p>
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
                      📊 {t("adminSettings.race.taskTitle")}
                    </div>
                    <div style={{ fontSize: 13, color: "#334155" }}>{t("adminSettings.race.task")}</div>
                  </div>
                  <button
                    type="button"
                    className="btn-assess"
                    disabled={racing}
                    onClick={startRace}
                    style={{ whiteSpace: "nowrap", padding: "9px 18px", borderRadius: 8, fontWeight: 700, flexShrink: 0 }}
                  >
                    {racing ? t("adminSettings.race.testing") : `▶ ${t("adminSettings.race.start")}`}
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
                        const statusLabel = t(`adminSettings.race.status.${r.status}`);
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
                                  <span style={{ fontSize: 11, color: "#64748b" }}>⚡ {t("adminSettings.race.firstToken")} {r.firstTokenMs}ms</span>
                                )}
                                {r.totalMs !== null && (
                                  <span style={{ fontSize: 11, color: "#64748b" }}>⏱ {t("adminSettings.race.total")} {(r.totalMs / 1000).toFixed(1)}s</span>
                                )}
                                {r.promptTokens !== null && (
                                  <span style={{ fontSize: 11, color: "#8b5cf6" }}>
                                    🔢 {r.promptTokens ?? "–"} in / {r.completionTokens ?? "–"} out
                                    {r.reasoningTokens ? ` (${t("adminSettings.race.visibleReasoning", { visible: r.visibleOutputTokens ?? "–", reasoning: r.reasoningTokens })})` : ""}
                                  </span>
                                )}
                                {/* 只在真正異常時標示：LENGTH=被 max tokens 截斷、CONTENT_FILTER=內容過濾；
                                    _UNKNOWN/STOP 等（gateway 未回標準 finish_reason）有內容即正常，不標示避免誤解 */}
                                {r.finishReason && /LENGTH|CONTENT_FILTER/i.test(r.finishReason) && (
                                  <span style={{ fontSize: 11, color: "#d97706" }} title={t("adminSettings.race.finishTitle")}>⚑ {r.finishReason}</span>
                                )}
                              </div>
                            </div>
                            {/* 串流內容 */}
                            <div
                              className={r.status === "streaming" ? "ai-streaming-body" : ""}
                              style={{ padding: "10px 12px", minHeight: 120, maxHeight: 280, overflowY: "auto", fontSize: 13, lineHeight: 1.6, color: r.status === "error" ? "#dc2626" : "#334155", whiteSpace: "pre-wrap", wordBreak: "break-word" }}
                            >
                              {r.status === "waiting" && <AiThinkingIndicator label={t("adminSettings.race.waitingResponse")} />}
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
                                  🕘 {t("adminSettings.race.history")}
                                </button>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  style={{ fontSize: 12, padding: "3px 10px" }}
                                  onClick={() => downloadMarkdown(`${opt.model}-${t("adminSettings.race.answerFile")}`, r.content)}
                                >
                                  ⬇ {t("adminSettings.race.download")}
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
                          🏆 {t("adminSettings.race.scoreReport")} <span style={{ fontSize: 11, color: "#64748b", fontWeight: 400 }}>（claude-opus-4-8）</span>
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
                    <button type="button" className="btn-secondary" onClick={openScoreHistory}>📊 {t("adminSettings.race.scoreHistory")}</button>
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
                        if (scoreReport?.markdown) files[t("adminSettings.race.scoreFile")] = scoreReport.markdown;
                        downloadZip(t("adminSettings.race.zipName", { timestamp: ts }), files);
                      }}
                    >
                      📦 {t("adminSettings.race.downloadZip")}
                    </button>
                    <button
                      type="button"
                      className="btn-assess"
                      disabled={scoring || !allDone || !hasDoneResults}
                      onClick={startScore}
                      style={{ fontWeight: 700, padding: "9px 20px", borderRadius: 8 }}
                    >
                      {scoring ? t("adminSettings.race.scoring") : `🏆 ${t("adminSettings.race.score")}`}
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
          report={{ title: `🏆 ${t("adminSettings.race.reportTitle")}`, loading: scoreReport.loading, streaming: scoreReport.streaming, markdown: scoreReport.markdown, callId: scoreReport.callId }}
          onClose={() => setScoreReport((prev) => prev ? { ...prev, open: false } : null)}
        />
      )}

      {/* 評分歷程 Modal */}
      {scoreHistoryOpen && (
        <AiCallHistoryModal
          title={t("adminSettings.race.historyTitle")}
          calls={scoreCalls}
          loading={scoreCallsLoading}
          onClose={() => setScoreHistoryOpen(false)}
        />
      )}

      {/* 個別模型歷程 Modal */}
      {modelHistoryState?.open && (
        <AiCallHistoryModal
          title={t("adminSettings.race.modelHistoryTitle", { model: modelHistoryState.model })}
          calls={modelHistoryState.calls}
          loading={modelHistoryState.loading}
          onClose={() => setModelHistoryState(null)}
        />
      )}
    </div>
  );
}
