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
 * 互動時間線：每則互動顯示類型、時間、情緒色點（SP6）與意圖中文標籤，並提供編輯 / 刪除入口。
 *
 * @param interactions 互動清單
 * @param onEdit 點擊編輯某互動 callback
 * @param onDelete 點擊刪除某互動 callback
 */
export function Timeline({
  interactions,
  onEdit,
  onDelete
}: {
  interactions: CustomerDetail["interactions"];
  onEdit: (interaction: CustomerDetail["interactions"][number]) => void;
  onDelete: (interaction: CustomerDetail["interactions"][number]) => void;
}) {
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
              <div className="row-actions">
                <button type="button" className="row-btn" onClick={() => onEdit(item)}>編輯</button>
                <button type="button" className="row-btn row-btn-danger" onClick={() => onDelete(item)}>刪除</button>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
