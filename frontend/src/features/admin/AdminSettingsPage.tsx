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
  const [saving, setSaving] = useState(false);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  if (user?.role !== "ADMIN") return <Navigate to="/dashboard" replace />;

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

  function addModel() {
    const m = newModel.trim();
    if (!m || options.includes(m)) return;
    setOptions([...options, m]);
    setNewModel("");
  }

  function removeModel(m: string) {
    setOptions(options.filter((o) => o !== m));
    if (currentModel === m) setCurrentModel("");
  }

  async function save() {
    setSaving(true);
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
    } finally {
      setSaving(false);
    }
  }

  const envLabel = settings?.envDefaultModel || "未設定";
  const sourceTag = currentModel
    ? <span style={{ background: "#dcfce7", color: "#166534", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>系統設定</span>
    : <span style={{ background: "#f1f5f9", color: "#475569", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>環境變數</span>;

  return (
    <div style={{ padding: "24px 28px", maxWidth: 680 }}>
      {/* 頁首 */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: "#122232", margin: 0 }}>系統設定</h1>
        <p style={{ color: "#64748b", marginTop: 4, fontSize: 14 }}>管理 AI 對話模型；留空則使用環境變數預設模型，修改後即時生效。</p>
      </div>

      {/* 錯誤 / 成功通知 */}
      {error && (
        <div style={{ background: "#fef2f2", border: "1px solid #fca5a5", borderRadius: 8, padding: "10px 14px", marginBottom: 16, color: "#b91c1c", fontSize: 14 }}>
          ⚠️ {error}
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
          {/* 目前模型卡片 */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>AI 對話模型</span>
              {sourceTag}
            </div>

            <label style={{ display: "block", fontSize: 13, color: "#5e7280", marginBottom: 6, fontWeight: 600 }}>
              選用模型
            </label>
            <select
              value={currentModel}
              onChange={(e) => setCurrentModel(e.target.value)}
              style={{
                width: "100%", padding: "9px 12px", border: "1px solid #d1e0db",
                borderRadius: 8, fontSize: 14, color: "#122232", background: "#fff",
                outline: "none", appearance: "auto", cursor: "pointer"
              }}
            >
              <option value="">（使用環境變數預設：{envLabel}）</option>
              {options.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>

            {!currentModel && (
              <p style={{ fontSize: 12, color: "#94a3b8", marginTop: 6 }}>
                目前回退到環境變數：<code style={{ background: "#f1f5f9", padding: "1px 5px", borderRadius: 4 }}>{envLabel}</code>
              </p>
            )}
          </div>

          {/* 候選清單卡片 */}
          <div className="panel" style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: "#122232", marginBottom: 14 }}>
              候選模型清單
              <span style={{ marginLeft: 8, fontSize: 12, color: "#94a3b8", fontWeight: 400 }}>{options.length} 個</span>
            </div>

            {options.length === 0 ? (
              <p style={{ fontSize: 13, color: "#94a3b8", margin: "0 0 12px" }}>尚無候選模型，請在下方新增。</p>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 14 }}>
                {options.map((o) => (
                  <div
                    key={o}
                    style={{
                      display: "flex", alignItems: "center", justifyContent: "space-between",
                      padding: "8px 12px", background: currentModel === o ? "#f0fdf4" : "#f8fafc",
                      border: `1px solid ${currentModel === o ? "#86efac" : "#e2e8f0"}`,
                      borderRadius: 8, fontSize: 14
                    }}
                  >
                    <span style={{ color: "#122232", fontFamily: "monospace" }}>{o}</span>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      {currentModel === o && (
                        <span style={{ fontSize: 11, color: "#166534", background: "#dcfce7", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>使用中</span>
                      )}
                      <button
                        type="button"
                        className="btn-danger"
                        style={{ padding: "3px 10px", fontSize: 12 }}
                        onClick={() => removeModel(o)}
                      >
                        刪除
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* 新增模型 */}
            <div style={{ display: "flex", gap: 8 }}>
              <input
                value={newModel}
                placeholder="輸入模型名，如 gpt-4o-mini"
                onChange={(e) => setNewModel(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addModel(); } }}
                style={{
                  flex: 1, padding: "8px 12px", border: "1px solid #d1e0db",
                  borderRadius: 8, fontSize: 14, outline: "none"
                }}
              />
              <button
                type="button"
                className="btn-secondary"
                style={{ whiteSpace: "nowrap", padding: "8px 16px" }}
                onClick={addModel}
              >
                + 新增
              </button>
            </div>
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
        </>
      )}
    </div>
  );
}
