import { useState } from "react";
import { sendAiFeedback } from "../../api";
import { useTranslation } from "react-i18next";

/**
 * AI 回答採納/拒絕回饋按鈕（SP4）。
 * 函式級註解：自帶送出狀態，對指定 callId 呼叫 /api/ai/calls/{id}/feedback；送出後鎖定並顯示結果。
 */
export function FeedbackButtons({ callId }: { callId?: number | null }) {
  const { t } = useTranslation("common");
  // 已送出的決定（null 表示尚未回饋）
  const [decision, setDecision] = useState<"ADOPTED" | "REJECTED" | null>(null);
  const [sending, setSending] = useState(false);

  // 無 callId（例如串流尚未結束）不顯示
  if (callId == null) return null;

  /** 送出回饋並鎖定狀態。 */
  async function submit(choice: "ADOPTED" | "REJECTED") {
    if (sending || decision) return;
    setSending(true);
    try {
      await sendAiFeedback(callId as number, choice);
      setDecision(choice);
    } catch (e) {
      console.error("送出 AI 回饋失敗:", e);
    } finally {
      setSending(false);
    }
  }

  if (decision) {
    return (
      <div className="ai-feedback done">
        {decision === "ADOPTED" ? t("ai.adopted") : t("ai.rejected")}
      </div>
    );
  }

  return (
    <div className="ai-feedback">
      <span>{t("ai.helpful")}</span>
      <button type="button" disabled={sending} onClick={() => submit("ADOPTED")} title={t("ai.adopt")}>👍</button>
      <button type="button" disabled={sending} onClick={() => submit("REJECTED")} title={t("ai.reject")}>👎</button>
    </div>
  );
}
