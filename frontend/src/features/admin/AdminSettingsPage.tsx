import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { fetchAiSettings, saveAiSettings } from "../../api";
import type { AiSettingsResponse } from "../../types";

/**
 * 系統設定頁（限 ADMIN）：設定 AI 對話模型。
 * 函式級註解：清單中點選即選用；點已選中的列再次點擊取消選用（回退環境變數）；輸入後新增即加入清單；儲存後即時生效。
 */
export default function AdminSettingsPage() {
  const { user } = useAuth();

  const [settings, setSettings] = useState<AiSettingsResponse | null>(null);
  const [currentModel, setCurrentModel] = useState("");
  const [options, setOptions] = useState<string[]>([]);
  const [newModel, setNewModel] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
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

  /** 點選清單：已選中則取消（回退環境變數），否則切換為新選擇。 */
  function selectModel(m: string) {
    setCurrentModel((prev) => prev === m ? "" : m);
    setActionMsg(null);
  }

  /** 新增候選模型（去重、非空）；新增後自動選用。 */
  function addModel() {
    const m = newModel.trim();
    if (!m) return;
    if (!options.includes(m)) {
      setOptions((prev) => [...prev, m]);
    }
    setCurrentModel(m);
    setNewModel("");
    setActionMsg(null);
  }

  /** 刪除候選模型；若刪掉的是目前選用的，退回空（回退環境變數）。 */
  function removeModel(m: string) {
    setOptions((prev) => prev.filter((o) => o !== m));
    if (currentModel === m) setCurrentModel("");
    setActionMsg(null);
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

  return (
    <div style={{ padding: "24px 28px", maxWidth: 640 }}>
      {/* 頁首 */}
      <h1 style={{ fontSize: 22, fontWeight: 700, color: "#122232", margin: "0 0 4px" }}>系統設定</h1>
      <p style={{ color: "#64748b", fontSize: 14, margin: "0 0 20px" }}>
        管理 AI 對話模型；點選清單項目選用，留空則回退環境變數預設，修改後儲存即時生效。
      </p>

      {/* 通知 */}
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
        <div className="panel">
          {/* 標題列 */}
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
            <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>AI 對話模型</span>
            {currentModel
              ? <span style={{ background: "#dcfce7", color: "#166534", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>系統設定</span>
              : <span style={{ background: "#f1f5f9", color: "#475569", padding: "2px 8px", borderRadius: 6, fontSize: 12, fontWeight: 600 }}>環境變數回退</span>
            }
          </div>

          {/* 回退提示（未選任何模型時） */}
          {!currentModel && (
            <div style={{ padding: "10px 12px", background: "#f8fafc", border: "1px dashed #cbd5e1", borderRadius: 8, fontSize: 13, color: "#64748b", marginBottom: 12 }}>
              未選用模型，AI 呼叫將使用環境變數預設：
              <code style={{ background: "#e2e8f0", padding: "1px 6px", borderRadius: 4, marginLeft: 4 }}>{envLabel}</code>
            </div>
          )}

          {/* 候選模型清單 */}
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
                    {/* 選取圓圈 */}
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
                      <span style={{ fontSize: 11, color: "#166534", background: "#dcfce7", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>
                        使用中
                      </span>
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
          <div style={{ display: "flex", gap: 8, marginBottom: 20, paddingTop: 4, borderTop: "1px solid #f1f5f9" }}>
            <input
              value={newModel}
              placeholder="輸入模型名，如 claude-sonnet-4-6"
              onChange={(e) => setNewModel(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addModel(); } }}
              style={{
                flex: 1, padding: "8px 12px", border: "1px solid #d1e0db",
                borderRadius: 8, fontSize: 14, outline: "none",
                marginTop: 12
              }}
            />
            <button
              type="button"
              className="btn-secondary"
              style={{ whiteSpace: "nowrap", padding: "8px 16px", marginTop: 12 }}
              onClick={addModel}
            >
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
      )}
    </div>
  );
}
