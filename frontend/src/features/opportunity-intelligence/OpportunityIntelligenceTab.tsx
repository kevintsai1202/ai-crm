import { useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { OpportunityHealthResponse } from "../../types";
import { fetchOpportunityHealth, recalculateOpportunityHealth } from "../../api/index";
import { healthTier, sortedTrend } from "./healthView";

/** 顏色語意對應。 */
const TONE_COLOR: Record<"good" | "warn" | "bad", string> = {
  good: "#16a34a", warn: "#b45309", bad: "#b91c1c",
};

/** V26 商機智能：健康度總分、可解釋分項、趨勢與下一最佳行動，並可建立 Task／產生跟進信。 */
export function OpportunityIntelligenceTab() {
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
        setError(e instanceof Error ? e.message : "載入健康度失敗");
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
      setError(e instanceof Error ? e.message : "重算失敗");
    } finally {
      setRecalculating(false);
    }
  }

  const tier = health ? healthTier(health.totalScore) : null;

  return (
    <div style={{ maxWidth: 860, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 800, color: "#122232", marginBottom: 6 }}>商機智能</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        以可解釋的規則評分呈現商機健康度與下一最佳行動；分數不會自動改變商機階段或成交機率。
      </p>

      {error && <div data-testid="oi-error" style={{ color: "#b91c1c", fontSize: 13, marginBottom: 12 }}>⚠️ {error}</div>}
      {loading && <div data-testid="oi-loading" style={{ color: "#475569" }}>計算中…</div>}

      {health && tier && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <div data-testid="oi-total" style={{ fontSize: 40, fontWeight: 800, color: TONE_COLOR[tier.tone] }}>
              {health.totalScore}
              <span style={{ fontSize: 16, color: "#94a3b8" }}> / 100</span>
            </div>
            <span data-testid="oi-tier" style={{ fontSize: 14, fontWeight: 700, color: TONE_COLOR[tier.tone],
              background: "#f8fafc", border: `1px solid ${TONE_COLOR[tier.tone]}`, borderRadius: 999, padding: "4px 12px" }}>
              {tier.label}
            </span>
            <button type="button" className="btn-secondary" data-testid="oi-recalculate"
              disabled={recalculating} onClick={recalculate} style={{ marginLeft: "auto", padding: "8px 16px" }}>
              {recalculating ? "重算中…" : "重新計算"}
            </button>
          </div>

          <div data-testid="oi-next-action" style={{ background: "#eef2ff", border: "1px solid #c7d2fe",
            borderRadius: 8, padding: "12px 14px", fontSize: 14, color: "#3730a3" }}>
            <strong>下一最佳行動：</strong>{health.nextBestAction}
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#475569" }}>分數構成與依據</div>
            {health.components.map((component) => (
              <div key={component.key} data-testid={`oi-component-${component.key}`}
                style={{ border: "1px solid #e2e8f0", borderRadius: 8, padding: "10px 12px", background: "#f8fafc" }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 14, fontWeight: 600, color: "#122232" }}>
                  <span>{component.label}</span>
                  <span>{component.score} / {component.maxScore}</span>
                </div>
                <div style={{ fontSize: 13, color: "#475569", marginTop: 4 }}>{component.reason}</div>
                {component.evidence && <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 2 }}>依據：{component.evidence}</div>}
              </div>
            ))}
          </div>

          <div style={{ fontSize: 12, color: "#94a3b8" }}>
            趨勢：{sortedTrend(health.trend).map((point) => point.totalScore).join(" → ") || "—"}
            <span style={{ marginLeft: 8 }}>規則版本 {health.ruleVersion}{health.model ? `／模型 ${health.model}` : "（deterministic）"}</span>
          </div>

          {customerId && (
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" className="btn-secondary" data-testid="oi-create-task"
                onClick={() => navigate(`/customers/${customerId}`)} style={{ padding: "8px 16px" }}>
                建立後續 Task
              </button>
              <button type="button" className="btn-primary" data-testid="oi-follow-up"
                onClick={() => navigate(`/customers/${customerId}/follow-up?opportunityId=${opportunityId}`)}
                style={{ padding: "8px 16px" }}>
                產生跟進信
              </button>
            </div>
          )}
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <button type="button" onClick={() => navigate(customerId ? `/customers/${customerId}` : "/customers")}
          style={{ background: "none", border: "none", color: "#64748b", cursor: "pointer", fontSize: 13 }}>
          ← 返回客戶工作台
        </button>
      </div>
    </div>
  );
}

export default OpportunityIntelligenceTab;
