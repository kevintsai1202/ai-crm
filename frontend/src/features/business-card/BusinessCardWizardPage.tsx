import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { BusinessCardConfirmResponse, BusinessCardIntakeResponse } from "../../types";
import { createBusinessCardIntake, fetchBusinessCardIntake, confirmBusinessCardIntake } from "../../api/index";
import {
  buildConfirmRequest, buildConfirmSummary, canProceedFromReview, initialFormFromRecognized,
  type BusinessCardForm, type DuplicateStrategy,
} from "./businessCardState";
import { BusinessCardUploadStep } from "./BusinessCardUploadStep";
import { BusinessCardReviewStep } from "./BusinessCardReviewStep";
import { BusinessCardConfirmStep } from "./BusinessCardConfirmStep";

/** 精靈目前步驟。 */
type WizardStep = "upload" | "review" | "confirm" | "done";

/** 輪詢辨識結果的間隔（毫秒）。 */
const POLL_INTERVAL_MS = 1000;

/** 名片三步精靈：上傳 → 校正／解重複 → 確認建檔 → 結果導向。 */
export function BusinessCardWizardPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<WizardStep>("upload");
  const [busy, setBusy] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [intake, setIntake] = useState<BusinessCardIntakeResponse | null>(null);
  const [form, setForm] = useState<BusinessCardForm>(initialFormFromRecognized(null));
  const [strategy, setStrategy] = useState<DuplicateStrategy | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<BusinessCardConfirmResponse | null>(null);
  // 冪等鍵在同一次建檔（含重試）維持不變，避免重複寫入。
  const idempotencyKeyRef = useRef<string | null>(null);
  // 辨識結果只用於初始化表單一次，避免步驟切換時覆蓋使用者校正。
  const formInitializedRef = useRef(false);

  /** 更新單一表單欄位。 */
  const updateField = useCallback((field: keyof BusinessCardForm, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  }, []);

  /** 上傳圖片並建立辨識工作。 */
  async function handleUpload(file: File) {
    setBusy(true);
    setUploadError(null);
    try {
      const created = await createBusinessCardIntake(file);
      setIntake(created);
      setStep("review");
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : "上傳失敗");
      setBusy(false);
    }
  }

  // 僅在 review 步驟處理辨識狀態；切到其他步驟時不干擾表單，保留使用者校正。
  useEffect(() => {
    if (step !== "review" || !intake) return;
    if (intake.status === "REVIEW_PENDING") {
      setBusy(false);
      // 表單只在辨識結果首次到達時初始化，之後（含返回上一步）不再覆蓋。
      if (!formInitializedRef.current) {
        formInitializedRef.current = true;
        setForm(initialFormFromRecognized(intake.recognized));
      }
      return;
    }
    if (intake.status === "FAILED") { setBusy(false); return; }
    if (intake.status !== "PROCESSING") return;
    // 仍在辨識中：定時輪詢直到狀態改變。
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const next = await fetchBusinessCardIntake(intake.id);
        if (!cancelled) setIntake(next);
      } catch {
        // 輪詢暫時失敗時保留現狀，下一輪 effect 會再嘗試。
        if (!cancelled) setIntake({ ...intake });
      }
    }, POLL_INTERVAL_MS);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [step, intake]);

  /** 送出確認建檔。 */
  async function handleConfirm() {
    if (!intake) return;
    if (!idempotencyKeyRef.current) idempotencyKeyRef.current = crypto.randomUUID();
    setSubmitting(true);
    setConfirmError(null);
    try {
      const request = buildConfirmRequest(form, strategy);
      const confirmed = await confirmBusinessCardIntake(intake.id, request, idempotencyKeyRef.current);
      setResult(confirmed);
      setStep("done");
    } catch (e) {
      setConfirmError(e instanceof Error ? e.message : "建檔失敗");
    } finally {
      setSubmitting(false);
    }
  }

  const candidates = intake?.duplicateCandidates ?? [];
  const failed = intake?.status === "FAILED";

  return (
    <div style={{ maxWidth: 760, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 800, color: "#122232", marginBottom: 6 }}>名片建檔精靈</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        以 AI 辨識名片，人工校正後一次建立客戶、聯絡人、商機與電話任務。
      </p>

      {failed && (
        <div data-testid="bc-failed" style={{ background: "#fef2f2", border: "1px solid #fca5a5", borderRadius: 8, padding: 12, marginBottom: 16, color: "#b91c1c" }}>
          ⚠️ 名片辨識失敗：{intake?.errorSummary ?? "無法解析名片內容"}。請返回重新上傳。
          <div style={{ marginTop: 8 }}>
            <button type="button" className="btn-secondary" onClick={() => { setIntake(null); setStep("upload"); }}>
              重新上傳
            </button>
          </div>
        </div>
      )}

      {step === "upload" && (
        <BusinessCardUploadStep busy={busy} error={uploadError} onSubmit={handleUpload} />
      )}

      {step === "review" && !failed && (
        intake?.status === "PROCESSING"
          ? <div data-testid="bc-processing" style={{ color: "#475569" }}>AI 辨識中，請稍候…</div>
          : <BusinessCardReviewStep
              recognized={intake?.recognized ?? null}
              form={form}
              onChange={updateField}
              candidates={candidates}
              strategy={strategy}
              onStrategyChange={setStrategy}
              canProceed={canProceedFromReview(candidates, strategy)}
              onNext={() => setStep("confirm")}
            />
      )}

      {step === "confirm" && (
        <BusinessCardConfirmStep
          form={form}
          onChange={updateField}
          submitting={submitting}
          error={confirmError}
          onConfirm={handleConfirm}
          onBack={() => setStep("review")}
        />
      )}

      {step === "done" && result && (
        <div data-testid="bc-summary" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <div style={{ background: "#f0fdf4", border: "1px solid #86efac", borderRadius: 8, padding: 14, color: "#166534", fontWeight: 700 }}>
            ✅ 建檔完成！已建立以下正式資料：
          </div>
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 8 }}>
            {buildConfirmSummary(result).map((item) => (
              <li key={item.entity} data-testid={`bc-summary-${item.entity}`} style={{ fontSize: 14 }}>
                {item.label}：<span data-testid={`bc-summary-${item.entity}-id`}>#{item.id}</span>
              </li>
            ))}
          </ul>
          <div style={{ display: "flex", gap: 8 }}>
            <Link to={`/customers/${result.customerId}`} className="btn-primary"
              data-testid="bc-goto-customer" style={{ padding: "8px 20px", fontWeight: 700, textDecoration: "none" }}>
              前往客戶
            </Link>
            <button type="button" className="btn-secondary"
              onClick={() => { setIntake(null); setStrategy(null); setResult(null); idempotencyKeyRef.current = null; formInitializedRef.current = false; setForm(initialFormFromRecognized(null)); setStep("upload"); }}
              style={{ padding: "8px 16px" }}>
              再建一張
            </button>
          </div>
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <button type="button" className="btn-link" onClick={() => navigate("/customers")}
          style={{ background: "none", border: "none", color: "#64748b", cursor: "pointer", fontSize: 13 }}>
          ← 返回客戶工作台
        </button>
      </div>
    </div>
  );
}

export default BusinessCardWizardPage;
