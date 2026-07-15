import type { MeetingChange } from "../../types";

/** 變更審核面板屬性。 */
interface ChangeReviewPaneProps {
  /** 結構化 CRM 變更清單。 */
  changes: MeetingChange[];
  /** 目前選取的 changeId 集合。 */
  selected: Set<string>;
  /** 切換單一變更選取。 */
  onToggle: (changeId: string) => void;
  /** 送出中。 */
  submitting: boolean;
  /** 確認送出。 */
  onConfirm: () => void;
}

/** 變更類型對應的中文標籤。 */
const TYPE_LABEL: Record<MeetingChange["type"], string> = {
  INTERACTION: "互動紀錄",
  TASK: "後續任務",
  OPPORTUNITY_PATCH: "商機更新",
  STAKEHOLDER_SUGGESTION: "決策鏈建議",
};

/** 會議審核右側：逐項勾選要套用的 CRM 變更，確認鈕顯示實際套用數。 */
export function ChangeReviewPane({ changes, selected, onToggle, submitting, onConfirm }: ChangeReviewPaneProps) {
  const selectedCount = changes.filter((change) => selected.has(change.changeId)).length;

  return (
    <div data-testid="mc-change-pane" style={{ display: "flex", flexDirection: "column", gap: 10, minWidth: 0 }}>
      <div style={{ fontSize: 13, fontWeight: 700, color: "#475569" }}>套用哪些 CRM 變更？</div>
      {changes.length === 0 && (
        <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>本次會議未產生可套用的變更。</p>
      )}
      {changes.map((change) => {
        const checked = selected.has(change.changeId);
        return (
          <label
            key={change.changeId}
            data-testid={`mc-change-${change.changeId}`}
            style={{
              display: "flex", gap: 10, alignItems: "flex-start", padding: "10px 12px",
              background: checked ? "#f0fdf4" : "#f8fafc",
              border: `1.5px solid ${checked ? "#4ade80" : "#e2e8f0"}`, borderRadius: 8, cursor: "pointer",
            }}
          >
            <input
              type="checkbox"
              checked={checked}
              onChange={() => onToggle(change.changeId)}
              style={{ marginTop: 3 }}
            />
            <div style={{ minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 2 }}>
                <span style={{ fontSize: 11, color: "#6366f1", background: "#ede9fe", padding: "1px 6px", borderRadius: 4 }}>
                  {TYPE_LABEL[change.type]}
                </span>
                {change.lowConfidence && (
                  <span data-testid={`mc-lowconf-${change.changeId}`} style={{ fontSize: 11, color: "#b45309",
                    background: "#fef3c7", padding: "1px 6px", borderRadius: 4 }}>
                    低信心・預設不套用
                  </span>
                )}
              </div>
              <div style={{ fontSize: 14, color: "#122232" }}>{change.description}</div>
            </div>
          </label>
        );
      })}
      <button
        type="button"
        className="btn-primary"
        data-testid="mc-confirm-submit"
        disabled={submitting}
        onClick={onConfirm}
        style={{ alignSelf: "flex-start", padding: "8px 20px", fontWeight: 700 }}
      >
        {submitting ? "套用中…" : `確認套用 ${selectedCount} 項變更`}
      </button>
    </div>
  );
}
