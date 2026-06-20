import type { AgentTraceResponse } from "../../types";
import { AiBadge } from "../../components/common/AiBadge";

/**
 * Agent Trace 面板，呈現教學版 GOAP action 路徑。
 */
export function TracePanel({ trace }: { trace: AgentTraceResponse | null }) {
  return (
    <section className="panel trace-panel">
      <div className="panel-title"><h3>Agent Trace <AiBadge /></h3><span>{trace?.route || "待載入"}</span></div>
      {/* 用途說明框：說明此面板呈現 AI 助理分析客戶的決策步驟歷程，協助理解 AI 結論依據 */}
      <p className="trace-intro">
        這是 AI 助理分析此客戶的決策步驟歷程，顯示 AI 如何一步步檢索資料、評估風險並產生最終建議，讓你了解 AI 結論的依據。
      </p>
      {trace ? (
        <>
          <p className="recommendation">{trace.finalRecommendation}</p>
          <div className="trace-list">
            {trace.steps.map((step) => (
              <article className="trace-step" key={`${step.order}-${step.action}`}>
                {/* action 為主標、status/duration 為輔助資訊、output 為內文說明 */}
                <b className="trace-action">{step.order}. {step.action}</b>
                <span className="trace-meta">{step.status} / {step.durationMs}ms</span>
                <p>{step.output}</p>
              </article>
            ))}
          </div>
        </>
      ) : <p className="trace-empty">選取左側任一客戶後，這裡會顯示 AI 助理逐步分析該客戶的決策歷程。</p>}
    </section>
  );
}
