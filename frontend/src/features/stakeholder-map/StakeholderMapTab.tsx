import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import type { StakeholderMapResponse, StakeholderSuggestionDto } from "../../types";
import {
  fetchStakeholderMap, suggestStakeholders, confirmStakeholderSuggestion, rejectStakeholderSuggestion,
} from "../../api/index";
import { StakeholderGraph } from "./StakeholderGraph";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";

/** 將建議摘要為可讀文字。 */
function suggestionSummary(suggestion: StakeholderSuggestionDto, t: TFunction): string {
  if (suggestion.kind === "ROLE" && suggestion.role) {
    const r = suggestion.role;
    return `${r.contactName}${r.contactTitle ? ` (${r.contactTitle})` : ""}: ${r.roleType} · ${t("stakeholder.influence", { value: r.influence })} · ${t("stakeholder.stance", { value: r.stance })}`;
  }
  if (suggestion.kind === "RELATION" && suggestion.relation) {
    const rel = suggestion.relation;
    return `${rel.fromContactName} ─ ${rel.relationType} ─ ${rel.toContactName}`;
  }
  return suggestion.suggestionId;
}

/** V27 決策鏈：已確認事實（實線）與 AI 待確認建議（虛線 + 待確認徽章）分區呈現，可逐則確認/拒絕。 */
export function StakeholderMapTab() {
  const { t } = useTranslation("operations");
  const navigate = useNavigate();
  const { customerId } = useParams();
  const [map, setMap] = useState<StakeholderMapResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** 重新載入決策鏈圖。 */
  async function reload() {
    if (!customerId) return;
    setMap(await fetchStakeholderMap(Number(customerId)));
  }

  useEffect(() => {
    if (!customerId) return;
    (async () => {
      try {
        await reload();
      } catch (e) {
        setError(e instanceof Error ? e.message : t("stakeholder.loadError"));
      } finally {
        setLoading(false);
      }
    })();
  }, [customerId]);

  /** 產生 AI 建議。 */
  async function generate() {
    setBusy(true);
    setError(null);
    try {
      await suggestStakeholders(Number(customerId));
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : t("stakeholder.generateError"));
    } finally {
      setBusy(false);
    }
  }

  /** 確認或拒絕一則建議後重載。 */
  async function act(suggestionId: string, kind: "confirm" | "reject") {
    setBusy(true);
    setError(null);
    try {
      if (kind === "confirm") await confirmStakeholderSuggestion(suggestionId);
      else await rejectStakeholderSuggestion(suggestionId);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : t("stakeholder.actionError"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 860, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 800, color: "#122232", marginBottom: 6 }}>{t("stakeholder.title")}</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        {t("stakeholder.intro")}
      </p>

      {error && <div data-testid="sm-error" style={{ color: "#b91c1c", fontSize: 13, marginBottom: 12 }}>⚠️ {error}</div>}
      {loading && <div data-testid="sm-loading" style={{ color: "#475569" }}>{t("stakeholder.loading")}</div>}

      {map && (
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <div>
            <button type="button" className="btn-primary" data-testid="sm-suggest" disabled={busy} onClick={generate}
              style={{ padding: "8px 16px", fontWeight: 700 }}>
              {busy ? t("stakeholder.processing") : t("stakeholder.generate")}
            </button>
          </div>

          <StakeholderGraph roles={map.confirmedRoles} relations={map.confirmedRelations} />

          <div data-testid="sm-suggestions">
            <div style={{ fontSize: 13, fontWeight: 700, color: "#475569", marginBottom: 6 }}>{t("stakeholder.suggestions")}</div>
            {map.suggestions.length === 0 && <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>{t("stakeholder.emptySuggestions")}</p>}
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {map.suggestions.map((suggestion) => (
                <div key={suggestion.suggestionId} data-testid={`sm-suggestion-${suggestion.suggestionId}`}
                  style={{
                    display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10,
                    padding: "10px 12px", borderRadius: 8,
                    border: "2px dashed #a78bfa", background: "#faf5ff",
                  }}>
                  <div style={{ minWidth: 0 }}>
                    <span style={{ fontSize: 11, color: "#6d28d9", background: "#ede9fe", padding: "1px 6px",
                      borderRadius: 4, marginRight: 8 }}>{t("stakeholder.pending")}</span>
                    <span style={{ fontSize: 14, color: "#122232" }}>{suggestionSummary(suggestion, t)}</span>
                  </div>
                  <div style={{ display: "flex", gap: 6, flexShrink: 0 }}>
                    <button type="button" className="btn-primary" data-testid={`sm-confirm-${suggestion.suggestionId}`}
                      disabled={busy} onClick={() => act(suggestion.suggestionId, "confirm")}
                      style={{ padding: "4px 12px", fontSize: 13 }}>{t("stakeholder.confirm")}</button>
                    <button type="button" className="btn-danger" data-testid={`sm-reject-${suggestion.suggestionId}`}
                      disabled={busy} onClick={() => act(suggestion.suggestionId, "reject")}
                      style={{ padding: "4px 12px", fontSize: 13 }}>{t("stakeholder.reject")}</button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <button type="button" onClick={() => navigate(`/customers/${customerId}`)}
          style={{ background: "none", border: "none", color: "#64748b", cursor: "pointer", fontSize: 13 }}>
          ← {t("stakeholder.back")}
        </button>
      </div>
    </div>
  );
}

export default StakeholderMapTab;
