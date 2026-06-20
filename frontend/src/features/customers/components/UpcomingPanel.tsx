import { useMemo } from "react";
import type { CustomerDetail } from "../../../types";
import { formatDate, formatMoney, stageLabel } from "../../../lib/format";

/** 待跟進事件的種類與對應顯示 class。 */
type UpcomingKind = { label: string; cls: string };
const KIND_INTERACTION: UpcomingKind = { label: "即將互動", cls: "interaction" };
const KIND_RENEWAL: UpcomingKind = { label: "續約到期", cls: "renewal" };
const KIND_OPPORTUNITY: UpcomingKind = { label: "商機成交", cls: "opportunity" };

/** 未來幾天視為「即將」。 */
const AHEAD_DAYS = 7;

/**
 * 本週待跟進：彙整未來 7 天內的「即將互動、續約到期、商機預計成交」三類事件,依日期排序。
 * 函式級註解：完全以前端現有的 CustomerDetail 計算(互動 occurredAt、客戶 renewalDueDate、
 * 商機 expectedCloseDate),不需後端;讓業務一眼看出本週該主動跟進的事項。
 *
 * @param detail 客戶詳情
 */
export function UpcomingPanel({ detail }: { detail: CustomerDetail }) {
  const events = useMemo(() => {
    const now = new Date();
    const end = new Date();
    end.setDate(now.getDate() + AHEAD_DAYS);
    // 判斷日期字串是否落在 [now, now+7d]
    const within = (dateStr: string | null | undefined) => {
      if (!dateStr) return false;
      const t = new Date(dateStr).getTime();
      return t >= now.getTime() && t <= end.getTime();
    };
    const list: { kind: UpcomingKind; date: string; label: string }[] = [];
    // ① 未來日期的互動
    detail.interactions.filter((i) => within(i.occurredAt)).forEach((i) =>
      list.push({ kind: KIND_INTERACTION, date: i.occurredAt, label: `${i.type}：${i.content}` })
    );
    // ② 續約日落在本週
    if (within(detail.customer.renewalDueDate)) {
      list.push({ kind: KIND_RENEWAL, date: detail.customer.renewalDueDate as string, label: "合約續約到期日" });
    }
    // ③ 商機預計成交日落在本週
    detail.opportunities.filter((o) => within(o.expectedCloseDate)).forEach((o) =>
      list.push({ kind: KIND_OPPORTUNITY, date: o.expectedCloseDate as string, label: `${o.name}（${stageLabel(o.stage)}・${formatMoney(o.amount)}）` })
    );
    // 依日期由近到遠排序
    return list.sort((a, b) => a.date.localeCompare(b.date));
  }, [detail]);

  return (
    <section className="panel upcoming-panel">
      <div className="panel-title"><h3>本週待跟進</h3><span>未來 {AHEAD_DAYS} 天</span></div>
      {events.length === 0 ? (
        <p className="trace-empty">未來 {AHEAD_DAYS} 天沒有排定的互動、續約或商機到期。</p>
      ) : (
        <div className="upcoming-list">
          {events.map((e, i) => (
            <div className="upcoming-item" key={i}>
              <span className={`upcoming-kind k-${e.kind.cls}`}>{e.kind.label}</span>
              <strong>{formatDate(e.date)}</strong>
              <p>{e.label}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
