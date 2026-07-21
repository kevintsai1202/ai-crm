import { useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { OpportunityHealthResponse } from "../../types";
import { fetchOpportunityHealth, recalculateOpportunityHealth } from "../../api/index";
import { healthTier, sortedTrend } from "./healthView";
import { useTranslation } from "react-i18next";

/** 顏色語意對應。 */
const TONE_COLOR: Record<"good" | "warn" | "bad", string> = {
  good: "#16a34a", warn: "#b45309", bad: "#b91c1c",
};

/** V26 商機智能：健康度總分、可解釋分項、趨勢與下一最佳行動，並可建立 Task／產生跟進信。 */
export function OpportunityIntelligenceTab() {
  const { t } = useTranslation("operations");
  const navigate = useNavigate();
  const { opportunityId } = useParams();
  const [searchParams] = useSearchParams();
  const customerId = searchParams.get("customerId");

  const [health, setHealth] = useState<OpportunityHealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [recalculating, setRecalculating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!opportunityId) return;
    (async () => {
      try {
        setHealth(await fetchOpportunityHealth(Number(opportunityId)));
      } catch (e) {
        setError(e instanceof Error ? e.message : t("opportunityIntelligence.loadError"));
      } finally {
        setLoading(false);
      }
    })();
  }, [opportunityId]);

  /** 重算健康度。 */
  async function recalculate() {
    if (!opportunityId) return;
    setRecalculating(true);
    setError(null);
    try {
      setHealth(await recalculateOpportunityHealth(Number(opportunityId)));
    } catch (e) {
      setError(e instanceof Error ? e.message : t("opportunityIntelligence.recalculateError"));
    } finally {
      setRecalculating(false);
    }
  }

  const tier = health ? healthTier(health.totalScore) : null;

  return (
    <div style={{ maxWidth: 860, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 800, color: "#122232", marginBottom: 6 }}>{t("opportunityIntelligence.title")}</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        {t("opportunityIntelligence.intro")}
      </p>

      {error && <div data-testid="oi-error" style={{ color: "#b91c1c", fontSize: 13, marginBottom: 12 }}>⚠️ {error}</div>}
      {loading && <div data-testid="oi-loading" style={{ color: "#475569" }}>{t("opportunityIntelligence.calculating")}</div>}

      {health && tier && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <div data-testid="oi-total" style={{ fontSize: 40, fontWeight: 800, color: TONE_COLOR[tier.tone] }}>
              {health.totalScore}
              <span style={{ fontSize: 16, color: "#94a3b8" }}> / 100</span>
            </div>
            <span data-testid="oi-tier" style={{ fontSize: 14, fontWeight: 700, color: TONE_COLOR[tier.tone],
              background: "#f8fafc", border: `1px solid ${TONE_COLOR[tier.tone]}`, borderRadius: 999, padding: "4px 12px" }}>
              {t(`opportunityIntelligence.tiers.${tier.tone}`)}
            </span>
            <button type="button" className="btn-secondary" data-testid="oi-recalculate"
              disabled={recalculating} onClick={recalculate} style={{ marginLeft: "auto", padding: "8px 16px" }}>
              {recalculating ? t("opportunityIntelligence.recalculating") : t("opportunityIntelligence.recalculate")}
            </button>
          </div>

          <div data-testid="oi-next-action" style={{ background: "#eef2ff", border: "1px solid #c7d2fe",
            borderRadius: 8, padding: "12px 14px", fontSize: 14, color: "#3730a3" }}>
            <strong>{t("opportunityIntelligence.nextAction")}</strong>{health.nextBestAction}
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#475569" }}>{t("opportunityIntelligence.components")}</div>
            {health.components.map((component) => (
              <div key={component.key} data-testid={`oi-component-${component.key}`}
                style={{ border: "1px solid #e2e8f0", borderRadius: 8, padding: "10px 12px", background: "#f8fafc" }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 14, fontWeight: 600, color: "#122232" }}>
                  <span>{component.label}</span>
                  <span>{component.score} / {component.maxScore}</span>
                </div>
                <div style={{ fontSize: 13, color: "#475569", marginTop: 4 }}>{component.reason}</div>
                {component.evidence && <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 2 }}>{t("opportunityIntelligence.evidence")}{component.evidence}</div>}
              </div>
            ))}
          </div>

          <div style={{ fontSize: 12, color: "#94a3b8" }}>
            {t("opportunityIntelligence.trend")}{sortedTrend(health.trend).map((point) => point.totalScore).join(" → ") || "—"}
            <span style={{ marginLeft: 8 }}>{t("opportunityIntelligence.ruleVersion", { version: health.ruleVersion })}{health.model ? ` / ${t("opportunityIntelligence.model", { model: health.model })}` : ` (${t("opportunityIntelligence.deterministic")})`}</span>
          </div>

          {customerId && (
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" className="btn-secondary" data-testid="oi-create-task"
                onClick={() => navigate(`/customers/${customerId}`)} style={{ padding: "8px 16px" }}>
                {t("opportunityIntelligence.createTask")}
              </button>
              <button type="button" className="btn-primary" data-testid="oi-follow-up"
                onClick={() => navigate(`/customers/${customerId}/follow-up?opportunityId=${opportunityId}`)}
                style={{ padding: "8px 16px" }}>
                {t("opportunityIntelligence.followUp")}
              </button>
            </div>
          )}
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <button type="button" onClick={() => navigate(customerId ? `/customers/${customerId}` : "/customers")}
          style={{ background: "none", border: "none", color: "#64748b", cursor: "pointer", fontSize: 13 }}>
          ← {t("opportunityIntelligence.back")}
        </button>
      </div>
    </div>
  );
}

export default OpportunityIntelligenceTab;
