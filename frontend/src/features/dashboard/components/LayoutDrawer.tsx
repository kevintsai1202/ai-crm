import { useTranslation } from "react-i18next";

/**
 * 版面抽屜：右側滑入，列出目前隱藏的儀表板區塊，可逐一加回，並提供還原預設順序。
 * 函式級註解：加回與還原都委派給父層（DashboardPage）統一走存檔邏輯，本元件只負責呈現與觸發。
 * 本身是 React 元件（非純函式產生器），直接用 useTranslation()。
 */
interface HiddenBlock {
  /** 區塊 id */
  id: string;
  /** 區塊顯示名（已由呼叫端翻譯） */
  title: string;
}

export function LayoutDrawer({ hiddenBlocks, onAdd, onReset, onClose }: {
  hiddenBlocks: HiddenBlock[];
  onAdd: (id: string) => void;
  onReset: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("dashboard");
  return (
    <div className="drawer-overlay" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header">
          <strong>{t("drawer.title")}</strong>
          <button type="button" className="chat-close" onClick={onClose} aria-label={t("drawer.close")}>✕</button>
        </div>
        <div className="drawer-body">
          <p className="drawer-hint">{t("drawer.hint")}</p>
          {hiddenBlocks.length === 0 ? (
            <div className="sr-empty">{t("drawer.allShown")}</div>
          ) : (
            hiddenBlocks.map((b) => (
              <div className="drawer-item" key={b.id}>
                <span>{b.title}</span>
                <button type="button" className="btn-secondary" onClick={() => onAdd(b.id)}>{t("drawer.add")}</button>
              </div>
            ))
          )}
        </div>
        <div className="drawer-footer">
          <button type="button" className="btn-reset-layout" onClick={onReset}>{t("drawer.reset")}</button>
        </div>
      </aside>
    </div>
  );
}
