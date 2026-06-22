import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { fetchAiSettings, saveAiSettings } from "../../api";
import type { AiSettingsResponse } from "../../types";

/**
 * 系統設定頁（限 ADMIN）：設定 AI 對話模型。
 * 函式級註解：下拉選目前模型（選項來自候選清單）；可新增/刪除候選模型；留空代表使用環境變數預設，即時生效免重啟。
 */
export default function AdminSettingsPage() {
  const { user } = useAuth();

  const [settings, setSettings] = useState<AiSettingsResponse | null>(null);
  /** 目前選用模型（空字串=用環境變數）。 */
  const [currentModel, setCurrentModel] = useState("");
  /** 候選模型清單。 */
  const [options, setOptions] = useState<string[]>([]);
  /** 新增模型輸入框的暫存值。 */
  const [newModel, setNewModel] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  // 非 ADMIN 直接導回儀表板
  if (user?.role !== "ADMIN") {
    return <Navigate to="/dashboard" replace />;
  }

  /** 載入目前設定。 */
  async function load() {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAiSettings();
      setSettings(data);
      setCurrentModel(data.currentModel);
      setOptions(data.modelOptions);
    } catch (e) {
      setError(e instanceof Error ? e.message : "載入失敗");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  /** 新增候選模型（去重、非空）。 */
  function addModel() {
    const m = newModel.trim();
    if (!m || options.includes(m)) return;
    setOptions([...options, m]);
    setNewModel("");
  }

  /** 刪除候選模型；若刪掉的是目前選用模型，currentModel 退回空（用環境變數）。 */
  function removeModel(m: string) {
    setOptions(options.filter((o) => o !== m));
    if (currentModel === m) setCurrentModel("");
  }

  /** 儲存設定。 */
  async function save() {
    setError(null);
    setActionMsg(null);
    try {
      const data = await saveAiSettings(currentModel, options);
      setSettings(data);
      setCurrentModel(data.currentModel);
      setOptions(data.modelOptions);
      setActionMsg("已儲存，AI 呼叫即時生效。");
    } catch (e) {
      setError(e instanceof Error ? e.message : "儲存失敗");
    }
  }

  return (
    <div className="admin-page">
      <header className="admin-page__header">
        <h1>系統設定</h1>
        <p className="admin-page__subtitle">設定 AI 對話模型；留空則使用環境變數預設模型。</p>
      </header>

      {error && <div className="admin-alert admin-alert--error">{error}</div>}
      {actionMsg && <div className="admin-alert admin-alert--ok">{actionMsg}</div>}

      {loading ? (
        <p className="admin-muted">載入中…</p>
      ) : (
        <section className="admin-card">
          <label className="admin-label">
            目前模型
            <select
              className="admin-select"
              value={currentModel}
              onChange={(e) => setCurrentModel(e.target.value)}
            >
              <option value="">（使用環境變數預設：{settings?.envDefaultModel || "未設定"}）</option>
              {options.map((o) => (
                <option key={o} value={o}>{o}</option>
              ))}
            </select>
          </label>

          <p className="admin-muted">
            來源：{currentModel
              ? `系統設定（${currentModel}）`
              : `環境變數（${settings?.envDefaultModel || "未設定"}）`}
          </p>

          <h2 className="admin-section-title">候選模型清單</h2>
          <ul className="admin-model-list">
            {options.map((o) => (
              <li key={o} className="admin-model-item">
                <span>{o}</span>
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => removeModel(o)}>刪除</button>
              </li>
            ))}
            {options.length === 0 && <li className="admin-muted">尚無候選模型</li>}
          </ul>

          <div className="admin-model-add">
            <input
              className="admin-input"
              value={newModel}
              placeholder="輸入模型名，如 gpt-4o-mini"
              onChange={(e) => setNewModel(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addModel(); } }}
            />
            <button type="button" className="btn btn--ghost" onClick={addModel}>新增</button>
          </div>

          <div className="admin-actions">
            <button type="button" className="btn btn--primary" onClick={save}>儲存</button>
          </div>
        </section>
      )}
    </div>
  );
}
