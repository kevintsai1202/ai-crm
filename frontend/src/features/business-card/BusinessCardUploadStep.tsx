import { useState } from "react";
import { useTranslation } from "react-i18next";

/** 上傳步驟屬性。 */
interface UploadStepProps {
  /** 是否辨識處理中（上傳後輪詢期間禁用互動）。 */
  busy: boolean;
  /** 錯誤訊息，null 代表無錯誤。 */
  error: string | null;
  /** 使用者選定檔案並送出辨識。 */
  onSubmit: (file: File) => void;
}

/** 名片精靈第一步：選擇圖片並送出辨識。 */
export function BusinessCardUploadStep({ busy, error, onSubmit }: UploadStepProps) {
  const { t } = useTranslation("operations");
  // 目前選定的名片圖片，尚未送出。
  const [file, setFile] = useState<File | null>(null);

  return (
    <div data-testid="bc-upload-step" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <p style={{ margin: 0, color: "#475569", fontSize: 14 }}>
        {t("businessCard.uploadHint")}
      </p>
      <input
        type="file"
        name="businessCardFile"
        accept="image/jpeg,image/png,image/webp"
        disabled={busy}
        onChange={(e) => setFile(e.target.files?.[0] ?? null)}
      />
      {error && <div data-testid="bc-upload-error" style={{ color: "#b91c1c", fontSize: 13 }}>⚠️ {error}</div>}
      <button
        type="button"
        className="btn-primary"
        disabled={busy || !file}
        onClick={() => file && onSubmit(file)}
        style={{ alignSelf: "flex-start", padding: "8px 20px", fontWeight: 700 }}
      >
        {busy ? t("businessCard.recognizing") : t("businessCard.startRecognition")}
      </button>
    </div>
  );
}
