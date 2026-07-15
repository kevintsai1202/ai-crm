import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { MeetingCopilotConfirmResponse, MeetingCopilotSessionResponse } from "../../types";
import { createMeetingSession, fetchMeetingSession, confirmMeetingSession } from "../../api/index";
import { initialSelectedIds, selectedChangeIds, toggleSelection } from "./changeSelection";
import { TranscriptPane } from "./TranscriptPane";
import { ChangeReviewPane } from "./ChangeReviewPane";

/** 頁面步驟。 */
type Step = "upload" | "review" | "done";

/** 輪詢轉錄結果的間隔（毫秒）。 */
const POLL_INTERVAL_MS = 1000;

/** V24 會議 Copilot：上傳音訊 → 轉錄/草稿 → 並排審核選取 → 選擇性套用。 */
export function MeetingCopilotPage() {
  const navigate = useNavigate();
  const { customerId } = useParams();
  const [searchParams] = useSearchParams();
  const opportunityId = searchParams.get("opportunityId");

  const [step, setStep] = useState<Step>("upload");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [session, setSession] = useState<MeetingCopilotSessionResponse | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<MeetingCopilotConfirmResponse | null>(null);
  // 冪等鍵在同一次確認（含重試）維持不變。
  const idempotencyKeyRef = useRef<string | null>(null);
  // 選取狀態只在草稿首次到達時初始化一次。
  const selectionInitRef = useRef(false);

  /** 上傳音訊並建立 session。 */
  async function handleUpload() {
    if (!file || !customerId) return;
    setBusy(true);
    setUploadError(null);
    try {
      const created = await createMeetingSession(file, Number(customerId), opportunityId ? Number(opportunityId) : null);
      setSession(created);
      setStep("review");
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : "上傳失敗");
      setBusy(false);
    }
  }

  // review 步驟：仍在轉錄則輪詢；REVIEW_PENDING 時初始化選取一次。
  useEffect(() => {
    if (step !== "review" || !session) return;
    if (session.status === "REVIEW_PENDING") {
      setBusy(false);
      if (!selectionInitRef.current) {
        selectionInitRef.current = true;
        setSelected(initialSelectedIds(session.changes));
      }
      return;
    }
    if (session.status === "FAILED") { setBusy(false); return; }
    if (session.status !== "PROCESSING" && session.status !== "UPLOADED") return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const next = await fetchMeetingSession(session.id);
        if (!cancelled) setSession(next);
      } catch {
        if (!cancelled) setSession({ ...session });
      }
    }, POLL_INTERVAL_MS);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [step, session]);

  /** 送出選擇性確認。 */
  async function handleConfirm() {
    if (!session) return;
    if (!idempotencyKeyRef.current) idempotencyKeyRef.current = crypto.randomUUID();
    setSubmitting(true);
    setConfirmError(null);
    try {
      const confirmed = await confirmMeetingSession(session.id, selectedChangeIds(selected), idempotencyKeyRef.current);
      setResult(confirmed);
      setStep("done");
    } catch (e) {
      setConfirmError(e instanceof Error ? e.message : "套用失敗");
    } finally {
      setSubmitting(false);
    }
  }

  const failed = session?.status === "FAILED";
  const processing = session?.status === "PROCESSING" || session?.status === "UPLOADED";

  return (
    <div style={{ maxWidth: 960, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 800, color: "#122232", marginBottom: 6 }}>會議 Copilot</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        上傳會議或電話錄音，AI 轉錄並草擬 CRM 變更；逐項確認後才寫入，逐字稿確認後保留為互動依據。
      </p>

      {step === "upload" && (
        <div data-testid="mc-upload-step" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <input
            type="file"
            name="meetingAudio"
            accept="audio/mpeg,audio/mp3,audio/mp4,audio/x-m4a,audio/wav,audio/x-wav"
            disabled={busy}
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          {uploadError && <div data-testid="mc-upload-error" style={{ color: "#b91c1c", fontSize: 13 }}>⚠️ {uploadError}</div>}
          <button type="button" className="btn-primary" data-testid="mc-upload-submit"
            disabled={busy || !file} onClick={handleUpload}
            style={{ alignSelf: "flex-start", padding: "8px 20px", fontWeight: 700 }}>
            {busy ? "上傳中…" : "上傳並轉錄"}
          </button>
        </div>
      )}

      {step === "review" && failed && (
        <div data-testid="mc-failed" style={{ background: "#fef2f2", border: "1px solid #fca5a5", borderRadius: 8,
          padding: 12, color: "#b91c1c" }}>
          ⚠️ 轉錄失敗：{session?.errorSummary ?? "無法處理音訊"}。
          <div style={{ marginTop: 8 }}>
            <button type="button" className="btn-secondary" onClick={() => { setSession(null); setStep("upload"); }}>重新上傳</button>
          </div>
        </div>
      )}

      {step === "review" && processing && (
        <div data-testid="mc-processing" style={{ color: "#475569" }}>AI 轉錄與草擬中，請稍候…</div>
      )}

      {step === "review" && session?.status === "REVIEW_PENDING" && (
        <div>
          {confirmError && <div data-testid="mc-confirm-error" style={{ color: "#b91c1c", fontSize: 13, marginBottom: 10 }}>⚠️ {confirmError}</div>}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>
            <TranscriptPane summary={session.summary} transcript={session.transcript} />
            <ChangeReviewPane
              changes={session.changes}
              selected={selected}
              onToggle={(id) => setSelected((prev) => toggleSelection(prev, id))}
              submitting={submitting}
              onConfirm={handleConfirm}
            />
          </div>
        </div>
      )}

      {step === "done" && result && (
        <div data-testid="mc-summary-done" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <div style={{ background: "#f0fdf4", border: "1px solid #86efac", borderRadius: 8, padding: 14,
            color: "#166534", fontWeight: 700 }}>
            ✅ 已套用 {result.appliedChangeIds.length} 項變更。
          </div>
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 6, fontSize: 14 }}>
            {result.interactionId != null && <li data-testid="mc-done-interaction">互動紀錄 #{result.interactionId}</li>}
            {result.taskIds.length > 0 && <li data-testid="mc-done-tasks">後續任務 {result.taskIds.map((id) => `#${id}`).join("、")}</li>}
            {result.opportunityId != null && <li data-testid="mc-done-opportunity">商機更新 #{result.opportunityId}</li>}
            {result.stakeholderSuggestionCount > 0 && <li data-testid="mc-done-stakeholder">決策鏈建議 {result.stakeholderSuggestionCount} 項</li>}
          </ul>
          <div>
            <button type="button" className="btn-primary" onClick={() => navigate(`/customers/${customerId}`)}
              style={{ padding: "8px 20px", fontWeight: 700 }}>返回客戶</button>
          </div>
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <button type="button" onClick={() => navigate(`/customers/${customerId}`)}
          style={{ background: "none", border: "none", color: "#64748b", cursor: "pointer", fontSize: 13 }}>
          ← 返回客戶工作台
        </button>
      </div>
    </div>
  );
}

export default MeetingCopilotPage;
