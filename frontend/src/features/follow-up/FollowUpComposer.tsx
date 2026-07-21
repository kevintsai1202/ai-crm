import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { FollowUpDraftResponse, OutboundEmailResponse } from "../../types";
import { createFollowUpDraft, updateFollowUpDraft, approveAndSendFollowUp, retryOutboundEmail } from "../../api/index";
import { canRetry, isDirty } from "./followUpState";
import { useTranslation } from "react-i18next";

/** V25 AI 跟進信撰寫：左側 AI 引用依據、右側可編輯草稿，人工核准後經 Zeabur Sendmail 寄送。 */
export function FollowUpComposer() {
  const { t } = useTranslation("operations");
  const navigate = useNavigate();
  const { customerId } = useParams();
  const [searchParams] = useSearchParams();
  const opportunityId = searchParams.get("opportunityId");

  const [draft, setDraft] = useState<FollowUpDraftResponse | null>(null);
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingVersion, setSavingVersion] = useState(false);
  const [sending, setSending] = useState(false);
  const [email, setEmail] = useState<OutboundEmailResponse | null>(null);
  // 冪等鍵在同一封草稿的核准寄送（含重試觸發前）維持不變。
  const idempotencyKeyRef = useRef<string | null>(null);
  // 避免 React 18 StrictMode 於開發期重複產生草稿。
  const generatedRef = useRef(false);

  // 進入頁面即產生初始草稿。
  useEffect(() => {
    if (!customerId || generatedRef.current) return;
    generatedRef.current = true;
    (async () => {
      try {
        const created = await createFollowUpDraft(Number(customerId), opportunityId ? Number(opportunityId) : null);
        setDraft(created);
        setSubject(created.subject);
        setBody(created.body);
      } catch (e) {
        setError(e instanceof Error ? e.message : t("followUp.draftError"));
      } finally {
        setLoading(false);
      }
    })();
  }, [customerId, opportunityId]);

  const dirty = draft ? isDirty({ subject: draft.subject, body: draft.body }, subject, body) : false;

  /** 將人工修改存為新版本。 */
  async function saveNewVersion() {
    if (!draft) return;
    setSavingVersion(true);
    setError(null);
    try {
      const updated = await updateFollowUpDraft(draft.id, subject, body);
      setDraft(updated);
      setSubject(updated.subject);
      setBody(updated.body);
      // 換版本後重置冪等鍵。
      idempotencyKeyRef.current = null;
    } catch (e) {
      setError(e instanceof Error ? e.message : t("followUp.saveError"));
    } finally {
      setSavingVersion(false);
    }
  }

  /** 核准並寄送；若有未存修改先擋下。 */
  async function send() {
    if (!draft) return;
    if (dirty) { setError(t("followUp.saveFirst")); return; }
    if (!idempotencyKeyRef.current) idempotencyKeyRef.current = crypto.randomUUID();
    setSending(true);
    setError(null);
    try {
      const result = await approveAndSendFollowUp(draft.id, idempotencyKeyRef.current);
      setEmail(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("followUp.sendError"));
    } finally {
      setSending(false);
    }
  }

  /** 重試先前失敗的寄送。 */
  async function retry() {
    if (!email) return;
    setSending(true);
    setError(null);
    try {
      setEmail(await retryOutboundEmail(email.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : t("followUp.retryError"));
    } finally {
      setSending(false);
    }
  }

  return (
    <div style={{ maxWidth: 960, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 800, color: "#122232", marginBottom: 6 }}>{t("followUp.title")}</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        {t("followUp.intro")}
      </p>

      {error && <div data-testid="fu-error" style={{ background: "#fef2f2", border: "1px solid #fca5a5",
        borderRadius: 8, padding: "8px 12px", marginBottom: 12, color: "#b91c1c", fontSize: 13 }}>⚠️ {error}</div>}

      {loading && <div data-testid="fu-loading" style={{ color: "#475569" }}>{t("followUp.drafting")}</div>}

      {draft && !email && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1.4fr", gap: 20 }}>
          <div data-testid="fu-grounding" style={{ minWidth: 0 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#475569", marginBottom: 6 }}>{t("followUp.grounding")}</div>
            <div style={{ fontSize: 13, color: "#334155", background: "#f8fafc", border: "1px solid #e2e8f0",
              borderRadius: 8, padding: "10px 12px", whiteSpace: "pre-wrap", maxHeight: 380, overflowY: "auto" }}>
              {draft.grounding || t("followUp.noGrounding")}
            </div>
            <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 8 }}>{t("followUp.version", { version: draft.versionNumber })}{draft.edited ? t("followUp.edited") : ""}</div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 10, minWidth: 0 }}>
            <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
              <span>{t("followUp.subject")}</span>
              <input name="fu-subject" value={subject} onChange={(e) => setSubject(e.target.value)}
                style={{ padding: "8px 10px", border: "1px solid #d1e0db", borderRadius: 6, fontSize: 14 }} />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 13, color: "#475569" }}>
              <span>{t("followUp.body")}</span>
              <textarea name="fu-body" value={body} onChange={(e) => setBody(e.target.value)} rows={12}
                style={{ padding: "8px 10px", border: "1px solid #d1e0db", borderRadius: 6, fontSize: 14, resize: "vertical" }} />
            </label>

            <div data-testid="fu-send-preview" style={{ fontSize: 12, color: "#64748b", background: "#f8fafc",
              border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 10px" }}>
              {t("followUp.sendPreview")}
            </div>

            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" className="btn-secondary" data-testid="fu-save-version"
                disabled={savingVersion || !dirty} onClick={saveNewVersion} style={{ padding: "8px 16px" }}>
                {savingVersion ? t("followUp.saving") : t("followUp.saveVersion")}
              </button>
              <button type="button" className="btn-primary" data-testid="fu-send"
                disabled={sending || dirty} onClick={send} style={{ padding: "8px 20px", fontWeight: 700 }}>
                {sending ? t("followUp.sending") : t("followUp.send")}
              </button>
            </div>
          </div>
        </div>
      )}

      {email && (
        <div data-testid="fu-result" style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{
            background: email.status === "SENT" ? "#f0fdf4" : "#fef2f2",
            border: `1px solid ${email.status === "SENT" ? "#86efac" : "#fca5a5"}`,
            borderRadius: 8, padding: 14, color: email.status === "SENT" ? "#166534" : "#b91c1c", fontWeight: 700,
          }}>
            <span data-testid="fu-status">{t(`followUp.status.${email.status}`)}</span>
            {email.messageId && <span style={{ fontWeight: 400, fontSize: 12, marginLeft: 8 }}>({t("followUp.messageId", { id: email.messageId })})</span>}
          </div>
          <ul style={{ listStyle: "none", padding: 0, margin: 0, fontSize: 13, color: "#334155", display: "flex", flexDirection: "column", gap: 4 }}>
            <li>{t("followUp.from")}：<span data-testid="fu-from">{email.from}</span></li>
            <li>{t("followUp.replyTo")}：<span data-testid="fu-replyto">{email.replyTo}</span></li>
            <li>{t("followUp.recipient")}：<span data-testid="fu-to">{email.recipient}</span></li>
            <li>{t("followUp.subject")}：{email.subject}</li>
          </ul>
          {email.errorSummary && <div style={{ fontSize: 13, color: "#b91c1c" }}>{email.errorSummary}</div>}
          {canRetry(email.status) && (
            <button type="button" className="btn-primary" data-testid="fu-retry" disabled={sending}
              onClick={retry} style={{ alignSelf: "flex-start", padding: "8px 20px" }}>{t("followUp.retry")}</button>
          )}
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <button type="button" onClick={() => navigate(`/customers/${customerId}`)}
          style={{ background: "none", border: "none", color: "#64748b", cursor: "pointer", fontSize: 13 }}>
          ← {t("followUp.back")}
        </button>
      </div>
    </div>
  );
}

export default FollowUpComposer;
