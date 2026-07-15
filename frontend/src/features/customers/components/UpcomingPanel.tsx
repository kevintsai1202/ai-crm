import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { CustomerDetail } from "../../../types";
import { formatDate, formatMoney, stageLabel } from "../../../lib/format";

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
  const { t, i18n } = useTranslation(["customers", "common"]);

  // 事件種類 → CSS class 與 i18n key 對照（元件內部常數，依賴 t，故放函式體內而非模組層級）
  const KIND_INTERACTION = { labelKey: "customers:upcoming.kindInteraction", cls: "interaction" } as const;
  const KIND_RENEWAL = { labelKey: "customers:upcoming.kindRenewal", cls: "renewal" } as const;
  const KIND_OPPORTUNITY = { labelKey: "customers:upcoming.kindOpportunity", cls: "opportunity" } as const;

  const events = useMemo(() => {
    const now = new Date();
    const end = new Date();
    end.setDate(now.getDate() + AHEAD_DAYS);
    // 判斷日期字串是否落在 [now, now+7d]
    const within = (dateStr: string | null | undefined) => {
      if (!dateStr) return false;
      const t2 = new Date(dateStr).getTime();
      return t2 >= now.getTime() && t2 <= end.getTime();
    };
    const list: { kind: { labelKey: string; cls: string }; date: string; label: string }[] = [];
    // ① 未來日期的互動
    detail.interactions.filter((i) => within(i.occurredAt)).forEach((i) =>
      list.push({ kind: KIND_INTERACTION, date: i.occurredAt, label: t("customers:upcoming.interactionLabel", { type: i.type, content: i.content }) })
    );
    // ② 續約日落在本週
    if (within(detail.customer.renewalDueDate)) {
      list.push({ kind: KIND_RENEWAL, date: detail.customer.renewalDueDate as string, label: t("customers:upcoming.renewalLabel") });
    }
    // ③ 商機預計成交日落在本週
    detail.opportunities.filter((o) => within(o.expectedCloseDate)).forEach((o) =>
      list.push({ kind: KIND_OPPORTUNITY, date: o.expectedCloseDate as string, label: t("customers:upcoming.opportunityLabel", { name: o.name, stage: t(stageLabel(o.stage)), amount: formatMoney(o.amount, i18n.language) }) })
    );
    // 依日期由近到遠排序
    return list.sort((a, b) => a.date.localeCompare(b.date));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail, i18n.language]);

  return (
    <section className="panel upcoming-panel">
      <div className="panel-title"><h3>{t("customers:upcoming.title")}</h3><span>{t("customers:upcoming.aheadDays", { days: AHEAD_DAYS })}</span></div>
      {events.length === 0 ? (
        <p className="trace-empty">{t("customers:upcoming.empty", { days: AHEAD_DAYS })}</p>
      ) : (
        <div className="upcoming-list">
          {events.map((e, i) => (
            <div className="upcoming-item" key={i}>
              <span className={`upcoming-kind k-${e.kind.cls}`}>{t(e.kind.labelKey)}</span>
              <strong>{formatDate(e.date, i18n.language, t("common:noData"))}</strong>
              <p>{e.label}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
