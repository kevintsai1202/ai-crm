import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { CustomerDetail } from "../../../types";
import { formatDateTime, intentLabel } from "../../../lib/format";

/** 預設呈現的時間窗(月):近 N 個月。 */
const MONTHS_WINDOW = 6;

/**
 * 將情緒值（POSITIVE / NEUTRAL / NEGATIVE）轉成色點 CSS class（SP6）。
 */
function sentimentClass(sentiment: string | null | undefined) {
  const map: Record<string, string> = { POSITIVE: "pos", NEUTRAL: "neu", NEGATIVE: "neg" };
  return map[sentiment || ""] || "neu";
}

/**
 * 互動時間線（橫向時間軸）：把近 6 個月的互動依日期定位在一條水平時間軸上,
 * 以情緒顏色標示;點選軸上的點即展開該次互動內容並提供編輯 / 刪除。
 * 函式級註解：相較原本逐列清單,橫向軸能一眼看出互動的疏密與分布,且不會因筆數多而過長。
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
  const { t, i18n } = useTranslation(["customers", "common"]);
  // 目前選取查看的互動 id（null 表示未選）
  const [selectedId, setSelectedId] = useState<number | null>(null);

  // 計算時間窗、軸上各互動的水平位置(left%)與月份刻度
  const { dots, ticks, hiddenCount } = useMemo(() => {
    const end = new Date();
    const start = new Date();
    start.setMonth(start.getMonth() - MONTHS_WINDOW);
    const span = end.getTime() - start.getTime();
    // 落在時間窗內的互動 → 換算 left 百分比
    const dots = interactions
      .map((i) => ({ item: i, t: new Date(i.occurredAt).getTime() }))
      .filter((d) => d.t >= start.getTime() && d.t <= end.getTime())
      .map((d) => ({ item: d.item, left: ((d.t - start.getTime()) / span) * 100 }));
    // 月份刻度:自 start 後的每個月初
    const ticks: { left: number; label: string }[] = [];
    const cursor = new Date(start.getFullYear(), start.getMonth() + 1, 1);
    while (cursor.getTime() <= end.getTime()) {
      ticks.push({ left: ((cursor.getTime() - start.getTime()) / span) * 100, label: t("customers:timeline.monthTick", { month: cursor.getMonth() + 1 }) });
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return { dots, ticks, hiddenCount: interactions.length - dots.length };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [interactions, i18n.language]);

  // 目前選取的互動(供下方詳情卡)
  const selected = interactions.find((i) => i.id === selectedId) ?? null;

  return (
    <section className="panel">
      <div className="panel-title">
        <h3>{t("customers:timeline.title")}</h3>
        <span>{t("customers:timeline.recentMonths", { months: MONTHS_WINDOW })}{hiddenCount > 0 ? t("customers:timeline.hiddenSuffix", { count: hiddenCount }) : ""}</span>
      </div>

      {dots.length === 0 ? (
        <p className="trace-empty">{t("customers:timeline.empty", { months: MONTHS_WINDOW })}</p>
      ) : (
        <>
          {/* 橫向時間軸:水平線 + 月份刻度 + 互動色點 */}
          <div className="tl-axis">
            <div className="tl-line" />
            {ticks.map((tick, i) => (
              <span className="tl-tick" key={i} style={{ left: `${tick.left}%` }}>{tick.label}</span>
            ))}
            {dots.map((d) => (
              <button
                type="button"
                key={d.item.id}
                className={`tl-dot ${sentimentClass(d.item.sentiment)} ${selectedId === d.item.id ? "sel" : ""}`}
                style={{ left: `${d.left}%` }}
                title={`${formatDateTime(d.item.occurredAt, i18n.language, t("common:noData"))}｜${d.item.type}`}
                onClick={() => setSelectedId(selectedId === d.item.id ? null : d.item.id)}
                aria-label={`${formatDateTime(d.item.occurredAt, i18n.language, t("common:noData"))} ${d.item.type}`}
              />
            ))}
          </div>

          {/* 選取後顯示該次互動的完整內容與操作;未選時提示 */}
          {selected ? (
            <article className="tl-detail">
              <span className="timeline-meta">
                {selected.sentiment ? <i className={`sr-dot ${sentimentClass(selected.sentiment)}`} title={selected.sentiment} /> : null}
                {selected.type}
                {intentLabel(selected.intent) ? <span className="sr-tag">{t(intentLabel(selected.intent))}</span> : null}
              </span>
              <strong>{formatDateTime(selected.occurredAt, i18n.language, t("common:noData"))}</strong>
              <p>{selected.content}</p>
              <div className="row-actions">
                <button type="button" className="row-btn" onClick={() => onEdit(selected)}>{t("common:actions.edit")}</button>
                <button type="button" className="row-btn row-btn-danger" onClick={() => onDelete(selected)}>{t("common:actions.delete")}</button>
              </div>
            </article>
          ) : (
            <p className="tl-hint">{t("customers:timeline.hint")}</p>
          )}
        </>
      )}
    </section>
  );
}
