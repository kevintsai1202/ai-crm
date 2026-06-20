import type { CustomerDetail } from "../../../types";
import { formatDateTime, intentLabel } from "../../../lib/format";

/**
 * 將情緒值（POSITIVE / NEUTRAL / NEGATIVE）轉成色點 CSS class（SP6）。
 */
function sentimentClass(sentiment: string | null | undefined) {
  const map: Record<string, string> = { POSITIVE: "pos", NEUTRAL: "neu", NEGATIVE: "neg" };
  return map[sentiment || ""] || "neu";
}

/**
 * 互動時間線：每則互動顯示類型、時間、情緒色點（SP6）與意圖中文標籤。
 */
export function Timeline({ interactions }: { interactions: CustomerDetail["interactions"] }) {
  return (
    <section className="panel">
      <div className="panel-title"><h3>互動時間線</h3></div>
      <div className="timeline">
        {interactions.map((item) => {
          // 意圖中文標籤；OTHER 或 null 回傳空字串時不渲染標籤
          const intentText = intentLabel(item.intent);
          return (
            <article className="timeline-item" key={item.id}>
              <span className="timeline-meta">
                {/* 情緒色點：僅在有情緒分析結果時顯示 */}
                {item.sentiment ? <i className={`sr-dot ${sentimentClass(item.sentiment)}`} title={item.sentiment} /> : null}
                {item.type}
                {intentText ? <span className="sr-tag">{intentText}</span> : null}
              </span>
              <strong>{formatDateTime(item.occurredAt)}</strong>
              <p>{item.content}</p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
