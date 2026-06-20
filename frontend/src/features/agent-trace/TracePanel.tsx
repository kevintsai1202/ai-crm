import type { AgentTraceResponse } from "../../types";
import { AiBadge } from "../../components/common/AiBadge";

/**
 * Agent Trace 面板，呈現教學版 GOAP action 路徑。
 */
export function TracePanel({ trace }: { trace: AgentTraceResponse | null }) {
  return (
    <section className="panel trace-panel">
      <div className="panel-title"><h3>Agent Trace <AiBadge /></h3><span>{trace?.route || "待載入"}</span></div>
      {trace ? (
        <>
          <p className="recommendation">{trace.finalRecommendation}</p>
          <div className="trace-list">
            {trace.steps.map((step) => (
              <article className="trace-step" key={`${step.order}-${step.action}`}>
                <b>{step.order}. {step.action}</b>
                <span>{step.status} / {step.durationMs}ms</span>
                <p>{step.output}</p>
              </article>
            ))}
          </div>
        </>
      ) : <p>選取客戶後載入 Trace。</p>}
    </section>
  );
}
