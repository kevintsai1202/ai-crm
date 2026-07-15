/** 逐字稿面板屬性。 */
interface TranscriptPaneProps {
  /** AI 摘要，可能為 null。 */
  summary: string | null;
  /** 會議逐字稿全文，可能為 null。 */
  transcript: string | null;
}

/** 會議審核左側：AI 摘要與逐字稿（確認後逐字稿保留為互動依據）。 */
export function TranscriptPane({ summary, transcript }: TranscriptPaneProps) {
  return (
    <div data-testid="mc-transcript-pane" style={{ display: "flex", flexDirection: "column", gap: 12, minWidth: 0 }}>
      <div>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#475569", marginBottom: 6 }}>AI 摘要</div>
        <div data-testid="mc-summary" style={{ fontSize: 14, color: "#122232", background: "#f8fafc",
          border: "1px solid #e2e8f0", borderRadius: 8, padding: "10px 12px", whiteSpace: "pre-wrap" }}>
          {summary || "（無摘要）"}
        </div>
      </div>
      <div style={{ minHeight: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#475569", marginBottom: 6 }}>逐字稿</div>
        <div data-testid="mc-transcript" style={{ fontSize: 13, color: "#334155", background: "#fff",
          border: "1px solid #e2e8f0", borderRadius: 8, padding: "10px 12px", maxHeight: 360,
          overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>
          {transcript || "（無逐字稿）"}
        </div>
      </div>
    </div>
  );
}
